package com.harmonygates.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.harmonygates.core.music.pitch.MidiNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The sampler, playing through `AudioTrack`.
 *
 * Everything musical happens in [Mixer]; this is the sink and the thread that feeds it. The
 * split is what 09_AUDIO_SAMPLER_ENGINE.md §2's no-native-code constraint makes worth having:
 * the part that could be wrong is plain Kotlin and tested, and the part that cannot be tested
 * here is thirty lines of platform wiring.
 *
 * Banks are decoded before playback rather than on demand, because §7 requires that
 * "ear-training playback must not stall on file decode".
 */
public class AudioTrackPlayer(
    private val scope: CoroutineScope,
    private val sampleRate: Int = Mixer.DEFAULT_SAMPLE_RATE,
    private val bankFactory: (InstrumentId) -> SampleBank = { SynthesisedBanks.practiceTone(sampleRate) },
) : InstrumentPlayer {

    private val mixer = Mixer(sampleRate = sampleRate)
    private val loading = Mutex()

    @Volatile
    private var bank: SampleBank? = null

    @Volatile
    private var track: AudioTrack? = null
    private var renderJob: Job? = null

    /** True once a bank is decoded and the render loop is running. */
    public val isReady: Boolean get() = bank != null && renderJob?.isActive == true

    public val loadedInstrument: InstrumentId? get() = bank?.preset?.id

    /**
     * Decodes a bank and starts the render loop.
     *
     * Suspending, and on the IO dispatcher, so a caller cannot accidentally decode a piano on
     * the frame that is meant to play it.
     */
    override suspend fun load(preset: InstrumentId) {
        loading.withLock {
            if (bank?.preset?.id == preset && isReady) return
            val decoded = withContext(Dispatchers.IO) { bankFactory(preset) }
            bank = decoded
            startRendering()
        }
    }

    override fun noteOn(note: MidiNote, velocity: Int) {
        val loaded = bank ?: return
        mixer.noteOn(note.value, velocity.coerceIn(1, SampleZone.MAX_VELOCITY), loaded)
    }

    override fun noteOff(note: MidiNote) {
        val behaviour = bank?.preset?.pedalBehavior ?: PedalBehavior.SUSTAIN
        mixer.noteOff(note.value, behaviour)
    }

    override fun sustain(down: Boolean) {
        mixer.sustain(down)
    }

    override fun allNotesOff() {
        mixer.allNotesOff()
    }

    /** Stops the render loop and releases the track. */
    public fun release() {
        renderJob?.cancel()
        renderJob = null
        mixer.allNotesOff()
        track?.runCatching {
            stop()
            release()
        }
        track = null
    }

    private fun startRendering() {
        if (renderJob?.isActive == true) return

        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minimum, mixer.bufferFrameCount * BYTES_PER_FRAME * BUFFER_MULTIPLE)

        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Not `MEDIA`: this is a musical instrument responding to a key press, and
                    // the platform routes and ducks a game/sonification stream more tightly.
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = created
        created.play()

        renderJob = scope.launch(Dispatchers.Default) {
            // Allocated once, outside the loop. §6: no allocation in the render loop.
            val buffer = ShortArray(mixer.bufferFrameCount)
            while (isActive) {
                val frames = mixer.render(buffer)
                created.write(buffer, 0, frames, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    private companion object {
        const val BYTES_PER_FRAME = 2
        const val BUFFER_MULTIPLE = 4
    }
}
