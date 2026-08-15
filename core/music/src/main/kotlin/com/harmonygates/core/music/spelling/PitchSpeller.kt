package com.harmonygates.core.music.spelling

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.pitch.Accidental
import com.harmonygates.core.music.pitch.LetterName
import com.harmonygates.core.music.pitch.PitchClass
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.nearestOffset
import com.harmonygates.core.music.pitch.transposeBy

/** How to choose an accidental when nothing else determines it. */
public enum class AccidentalPolicy {
    PREFER_SHARPS,
    PREFER_FLATS,

    /** Follow the key signature: flat keys spell flats, sharp keys spell sharps. */
    FROM_KEY_SIGNATURE,
}

/**
 * Turns pitch classes into written notes.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §6 lists the cases this must get right:
 * - `Db7` spells Db-F-Ab-Cb, not C#-F-G#-B
 * - `F#maj7` contains E#, not F
 * - `C7#9` uses D#, not Eb
 *
 * All three fall out of one rule rather than a lookup table: the degree's *number* fixes the
 * letter, and the accidental is then whatever turns that letter into the required sound.
 */
public interface PitchSpeller {
    /** Spells [degree] above [root]. */
    public fun spell(root: SpelledPitchClass, degree: ChordDegree): SpellingResult<SpelledPitchClass>

    /**
     * Spells a bare pitch class that has no degree role — an ear-training answer key, say.
     *
     * @param keySignatureAccidentals signed count of sharps (positive) or flats (negative).
     */
    public fun spellFreely(
        pitchClass: PitchClass,
        policy: AccidentalPolicy,
        keySignatureAccidentals: Int = 0,
    ): SpelledPitchClass
}

/** The production speller. Stateless, so a single instance is shared. */
public object DegreeAwarePitchSpeller : PitchSpeller {

    override fun spell(root: SpelledPitchClass, degree: ChordDegree): SpellingResult<SpelledPitchClass> =
        root.transposeBy(degree.intervalFromRoot)

    override fun spellFreely(
        pitchClass: PitchClass,
        policy: AccidentalPolicy,
        keySignatureAccidentals: Int,
    ): SpelledPitchClass {
        val preferSharps = when (policy) {
            AccidentalPolicy.PREFER_SHARPS -> true
            AccidentalPolicy.PREFER_FLATS -> false
            AccidentalPolicy.FROM_KEY_SIGNATURE -> keySignatureAccidentals >= 0
        }
        LetterName.entries.firstOrNull { it.naturalPitchClass == pitchClass.value }
            ?.let { return SpelledPitchClass(it, Accidental.NATURAL) }

        val letter = if (preferSharps) {
            LetterName.entries.first { nearestOffset(it.naturalPitchClass, pitchClass.value) == 1 }
        } else {
            LetterName.entries.first { nearestOffset(it.naturalPitchClass, pitchClass.value) == -1 }
        }
        val accidental = if (preferSharps) Accidental.SHARP else Accidental.FLAT
        return SpelledPitchClass(letter, accidental)
    }
}
