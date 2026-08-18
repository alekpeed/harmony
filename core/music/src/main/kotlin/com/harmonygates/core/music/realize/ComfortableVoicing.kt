package com.harmonygates.core.music.realize

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingPolicy
import kotlin.math.abs

/**
 * Picks the voicing or the single note a player would actually want to hear, rather than the
 * one a policy's raw candidate order happens to put first.
 *
 * `ChordRealizer.generateVoicings` orders its candidates tightest-span-first, then
 * lowest-bass-second — a tie-break built for chord-gate reading, where a lower voicing is safer
 * to sight-read. Taken as-is for anything that plays a chord aloud rather than displaying it,
 * that tie-break always wins at the very bottom of the policy's pitch range: a stimulus a full
 * octave or more below middle C, both harder to sing back and, on a synthesised tone, noticeably
 * harsher than the same notes a register or two higher. Every place that renders a chord or a
 * single reference note as audio — ear training's stimulus playback, relative-pitch drills —
 * goes through here instead, so "what does this actually sound good played as" has one answer.
 */
public object ComfortableVoicing {

    /** Middle C. The register a reference note or a close voicing is placed nearest to. */
    public const val TARGET_MIDI: Int = 60

    private const val SEMITONES_PER_OCTAVE = 12

    /**
     * The tightest (closed) voicing available, in the octave closest to [TARGET_MIDI].
     *
     * [matching] narrows the candidate pool before ranking — e.g. "a different bass degree than
     * the first voicing" for a difference-detection pair — without disturbing how a candidate is
     * chosen from what remains.
     */
    public fun preferredVoicing(
        chord: ChordSpec,
        realizer: ChordRealizer,
        pitchRange: IntRange,
        matching: (Voicing) -> Boolean = { true },
    ): Voicing? {
        val policy = VoicingPolicy(requiredDegrees = emptySet(), pitchRange = pitchRange)
        val candidates = realizer.generateVoicings(chord, policy).filter(matching)
        if (candidates.isEmpty()) return null
        val tightest = candidates.minOf { it.metadata.spanSemitones }
        return candidates
            .filter { it.metadata.spanSemitones == tightest }
            .minByOrNull { abs(it.bass.value - TARGET_MIDI) }
    }

    /** The instance of [pitchClass] closest to [TARGET_MIDI], within [range]. */
    public fun nearestNote(pitchClass: SpelledPitchClass, range: IntRange): MidiNote =
        nearestNote(pitchClass, TARGET_MIDI, range)

    /** The instance of [pitchClass] closest to [target], within [range]. */
    public fun nearestNote(pitchClass: SpelledPitchClass, target: Int, range: IntRange): MidiNote {
        val degreeClass = pitchClass.pitchClass.value
        val octaves = range.filter { it.mod(SEMITONES_PER_OCTAVE) == degreeClass }
        return MidiNote(octaves.minByOrNull { abs(it - target) } ?: target.coerceIn(range))
    }
}
