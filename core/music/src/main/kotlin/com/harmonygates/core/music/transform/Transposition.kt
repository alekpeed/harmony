package com.harmonygates.core.music.transform

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.key.transposeDiatonic
import com.harmonygates.core.music.pitch.SpelledInterval
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.transposeBy
import com.harmonygates.core.music.voicing.VoicedTone
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.voicingOf

/**
 * Transposition that keeps spelling intact.
 *
 * A chromatic transposition by semitones alone cannot know whether the result should be
 * written with sharps or flats, so every function here takes a [SpelledInterval]. Transposing
 * by an interval and then by its inverse returns exactly the original value — the property
 * test 14_TESTING_AND_QUALITY.md §4 asks for.
 */
public object Transposition {

    /** Transposes a chord symbol, including any slash bass. */
    public fun transpose(spec: ChordSpec, interval: SpelledInterval): SpellingResult<ChordSpec> {
        val root = when (val result = spec.root.transposeBy(interval)) {
            is SpellingResult.Spelled -> result.value
            is SpellingResult.Overflow -> return result
        }
        val bass = spec.explicitBass?.let {
            when (val result = it.transposeBy(interval)) {
                is SpellingResult.Spelled -> result.value
                is SpellingResult.Overflow -> return result
            }
        }
        // Degrees, alterations, additions and omissions are all relative to the root, so they
        // survive transposition untouched. Only the two absolute pitch classes move.
        return SpellingResult.Spelled(spec.copy(root = root, explicitBass = bass))
    }

    /**
     * Transposes a played voicing.
     *
     * Each voice keeps its chord role, so a drop-2 stays a drop-2 and a rootless voicing stays
     * rootless. Returns null when the result would leave the MIDI range.
     */
    public fun transpose(voicing: Voicing, interval: SpelledInterval): SpellingResult<Voicing?> {
        val chord = when (val result = transpose(voicing.chord, interval)) {
            is SpellingResult.Spelled -> result.value
            is SpellingResult.Overflow -> return result
        }
        val tones = mutableListOf<VoicedTone>()
        for (tone in voicing.tones) {
            val moved = when (val result = tone.pitch.transposeBy(interval)) {
                is SpellingResult.Spelled -> result.value
                is SpellingResult.Overflow -> return result
            }
            if (moved.midiNoteOrNull == null) return SpellingResult.Spelled(null)
            tones += VoicedTone(moved, tone.degree)
        }
        return SpellingResult.Spelled(voicingOf(chord, tones, voicing.metadata.family))
    }

    /**
     * Moves a chord by scale steps inside a key, so a `ii7` becomes a `iii7` rather than
     * something a semitone away that no longer belongs to the key.
     *
     * Only the root and bass move diatonically; the sonority is preserved as written. Content
     * that wants the *diatonic* sonority of the new degree should build it from a
     * [com.harmonygates.core.music.key.FunctionalChord] instead — this function deliberately
     * does not guess.
     */
    public fun transposeDiatonically(
        spec: ChordSpec,
        steps: Int,
        key: KeyContext,
    ): SpellingResult<ChordSpec> {
        val root = when (val result = key.transposeDiatonic(spec.root, steps)) {
            is SpellingResult.Spelled -> result.value
            is SpellingResult.Overflow -> return result
        }
        val bass = spec.explicitBass?.let {
            when (val result = key.transposeDiatonic(it, steps)) {
                is SpellingResult.Spelled -> result.value
                is SpellingResult.Overflow -> return result
            }
        }
        return SpellingResult.Spelled(spec.copy(root = root, explicitBass = bass))
    }

    /** Transposes [spec] to every one of the twelve roots, in ascending chromatic order. */
    public fun toAllRoots(spec: ChordSpec, roots: List<SpelledPitchClass>): List<ChordSpec> =
        roots.map { spec.copy(root = it, explicitBass = null) }
}
