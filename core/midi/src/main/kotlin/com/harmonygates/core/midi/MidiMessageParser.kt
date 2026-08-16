package com.harmonygates.core.midi

import com.harmonygates.core.music.pitch.MidiNote

/**
 * Turns a raw MIDI byte stream into [MidiEvent]s.
 *
 * The wire format is messier than it looks, and every one of these cases shows up on real
 * hardware:
 *
 * - **Running status.** A keyboard sending a fast passage omits the status byte on every
 *   message after the first, so `90 3C 64 3E 64 40 64` is three note-ons, not one and a mess.
 * - **Real-time bytes anywhere.** Clock and active-sensing bytes are legal *between the data
 *   bytes of another message*, so they have to be lifted out without disturbing what they
 *   interrupted.
 * - **Messages split across reads.** Android hands over whatever bytes have arrived, which may
 *   be half a message. Parser state therefore survives between calls.
 * - **Note On at velocity zero.** Almost every keyboard releases a note this way rather than
 *   sending a real Note Off (18_ACCEPTANCE_CRITERIA.md requires handling it).
 *
 * The class is stateful across calls and is not thread-safe: one parser per open port, used
 * only from that port's callback.
 */
public class MidiMessageParser {

    /** Last channel status byte seen, for running status. Zero when there is none. */
    private var runningStatus = 0

    /** Status of the message currently being filled. Zero when between messages. */
    private var pendingStatus = 0

    private val data = IntArray(2)
    private var dataCount = 0
    private var expectedDataCount = 0
    private var inSystemExclusive = false

    /**
     * Parses [count] bytes starting at [offset], returning every complete message.
     *
     * All events from one call share [timestampNanos]. That is deliberate: the bytes arrived in
     * a single transport callback, so attributing sub-microsecond differences to them would
     * invent precision the transport does not have.
     */
    public fun parse(
        bytes: ByteArray,
        offset: Int,
        count: Int,
        timestampNanos: Long,
    ): List<MidiEvent> {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size) {
            "Range $offset..${offset + count} is outside a ${bytes.size}-byte buffer"
        }
        if (count == 0) return emptyList()

        val events = mutableListOf<MidiEvent>()
        for (index in offset until offset + count) {
            consume(bytes[index].toInt() and 0xFF, timestampNanos, events)
        }
        return events
    }

    /** Convenience for whole buffers and for tests. */
    public fun parse(bytes: ByteArray, timestampNanos: Long): List<MidiEvent> =
        parse(bytes, 0, bytes.size, timestampNanos)

    /**
     * Forgets all partial state.
     *
     * Called when a port closes. A half-received message that survived a reconnect would emit a
     * note nobody played.
     */
    public fun reset() {
        runningStatus = 0
        pendingStatus = 0
        dataCount = 0
        expectedDataCount = 0
        inSystemExclusive = false
    }

    private fun consume(byte: Int, timestampNanos: Long, events: MutableList<MidiEvent>) {
        // System real-time bytes are legal between the data bytes of another message, so they
        // are handled before anything else and leave the partial message untouched.
        if (byte >= SYSTEM_REALTIME_MIN) {
            if (byte == SYSTEM_RESET) reset()
            return
        }

        if (inSystemExclusive) {
            if (byte < STATUS_MIN) return // payload byte; nothing here is musically interesting
            inSystemExclusive = false
            if (byte == SYSEX_END) return
            // Any other status byte aborts the dump; fall through and treat it as that status.
        }

        if (byte >= STATUS_MIN) {
            beginMessage(byte, timestampNanos, events)
        } else {
            appendData(byte, timestampNanos, events)
        }
    }

    private fun beginMessage(status: Int, timestampNanos: Long, events: MutableList<MidiEvent>) {
        dataCount = 0
        when {
            status == SYSEX_START -> {
                inSystemExclusive = true
                // System common and SysEx both cancel running status.
                runningStatus = 0
                pendingStatus = 0
            }

            status == SYSEX_END -> {
                // A terminator with no dump in progress. Harmless, but it still cancels
                // running status like every other system message.
                runningStatus = 0
                pendingStatus = 0
            }

            status < SYSTEM_COMMON_MIN -> {
                runningStatus = status
                pendingStatus = status
                expectedDataCount = channelMessageDataCount(status)
            }

            else -> {
                runningStatus = 0
                pendingStatus = status
                expectedDataCount = systemCommonDataCount(status)
                // Tune Request carries no data and is complete the moment it arrives.
                if (expectedDataCount == 0) {
                    emit(timestampNanos, events)
                    pendingStatus = 0
                }
            }
        }
    }

    private fun appendData(byte: Int, timestampNanos: Long, events: MutableList<MidiEvent>) {
        if (pendingStatus == 0) {
            // Running status: a data byte with no status of its own reuses the last channel one.
            if (runningStatus == 0) return // orphan byte, e.g. the tail of a message we joined mid-way
            pendingStatus = runningStatus
            expectedDataCount = channelMessageDataCount(runningStatus)
            dataCount = 0
        }

        if (dataCount < data.size) data[dataCount] = byte
        dataCount++

        if (dataCount >= expectedDataCount) {
            emit(timestampNanos, events)
            dataCount = 0
            // Channel messages keep their status so the next data byte continues the run;
            // system common messages do not have running status.
            pendingStatus = if (pendingStatus < SYSTEM_COMMON_MIN) pendingStatus else 0
        }
    }

    private fun emit(timestampNanos: Long, events: MutableList<MidiEvent>) {
        val status = pendingStatus
        if (status >= SYSTEM_COMMON_MIN) return // song position, MTC and friends carry no musical meaning here

        val channel = status and CHANNEL_MASK
        val first = data[0]
        val second = data[1]

        val event = when (status and TYPE_MASK) {
            NOTE_OFF -> MidiEvent.NoteOff(
                note = MidiNote(first),
                velocity = second,
                channel = channel,
                timestampNanos = timestampNanos,
            )

            NOTE_ON -> if (second == 0) {
                // The usual way a keyboard releases a key.
                MidiEvent.NoteOff(
                    note = MidiNote(first),
                    velocity = 0,
                    channel = channel,
                    timestampNanos = timestampNanos,
                    fromZeroVelocityNoteOn = true,
                )
            } else {
                MidiEvent.NoteOn(
                    note = MidiNote(first),
                    velocity = second,
                    channel = channel,
                    timestampNanos = timestampNanos,
                )
            }

            POLY_AFTERTOUCH -> MidiEvent.PolyphonicAftertouch(
                note = MidiNote(first),
                pressure = second,
                channel = channel,
                timestampNanos = timestampNanos,
            )

            CONTROL_CHANGE -> MidiEvent.ControlChange(
                controller = first,
                value = second,
                channel = channel,
                timestampNanos = timestampNanos,
            )

            CHANNEL_PRESSURE -> MidiEvent.ChannelPressure(
                pressure = first,
                channel = channel,
                timestampNanos = timestampNanos,
            )

            PITCH_BEND -> MidiEvent.PitchBend(
                // Fourteen bits, least significant first, centred at 8192.
                value = ((second shl 7) or first) - PITCH_BEND_CENTRE,
                channel = channel,
                timestampNanos = timestampNanos,
            )

            // Program change carries no performance information this app uses.
            else -> null
        }

        if (event != null) events += event
    }

    private fun channelMessageDataCount(status: Int): Int = when (status and TYPE_MASK) {
        PROGRAM_CHANGE, CHANNEL_PRESSURE -> 1
        else -> 2
    }

    private fun systemCommonDataCount(status: Int): Int = when (status) {
        MTC_QUARTER_FRAME, SONG_SELECT -> 1
        SONG_POSITION -> 2
        else -> 0
    }

    private companion object {
        const val STATUS_MIN = 0x80
        const val SYSTEM_COMMON_MIN = 0xF0
        const val SYSTEM_REALTIME_MIN = 0xF8
        const val SYSTEM_RESET = 0xFF
        const val SYSEX_START = 0xF0
        const val SYSEX_END = 0xF7
        const val MTC_QUARTER_FRAME = 0xF1
        const val SONG_POSITION = 0xF2
        const val SONG_SELECT = 0xF3

        const val TYPE_MASK = 0xF0
        const val CHANNEL_MASK = 0x0F

        const val NOTE_OFF = 0x80
        const val NOTE_ON = 0x90
        const val POLY_AFTERTOUCH = 0xA0
        const val CONTROL_CHANGE = 0xB0
        const val PROGRAM_CHANGE = 0xC0
        const val CHANNEL_PRESSURE = 0xD0
        const val PITCH_BEND = 0xE0

        const val PITCH_BEND_CENTRE = 8192
    }
}
