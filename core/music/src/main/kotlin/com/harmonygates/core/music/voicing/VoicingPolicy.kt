package com.harmonygates.core.music.voicing

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.PitchClass
import com.harmonygates.core.music.pitch.SpelledPitchClass

/** What the bass of a voicing must be. */
public sealed interface BassRequirement {
    /** Any chord tone may be lowest. */
    public data object Unconstrained : BassRequirement

    /** Root position. */
    public data object RootInBass : BassRequirement

    /** A specific inversion, e.g. third in the bass. */
    public data class DegreeInBass(val degree: ChordDegree) : BassRequirement

    /** A slash chord's bass, which may not be a chord tone at all. */
    public data class PitchClassInBass(val pitchClass: SpelledPitchClass) : BassRequirement

    /** Region 7: the root must *not* be the lowest note. */
    public data class DegreeNotInBass(val degree: ChordDegree) : BassRequirement
}

/** What the top voice of a voicing must be. Melody constraints in Regions 7 and 8. */
public sealed interface TopNoteRequirement {
    public data class DegreeOnTop(val degree: ChordDegree) : TopNoteRequirement

    public data class ExactNoteOnTop(val note: MidiNote) : TopNoteRequirement

    public data class PitchClassOnTop(val pitchClass: PitchClass) : TopNoteRequirement
}

/**
 * The bridge between harmony and exercise evaluation.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §8. A policy says what counts as an acceptable rendering of a
 * chord *for this exercise*: which tones must be there, which may be dropped, whether the
 * root is wanted at all, and what register the hands are working in.
 *
 * This is where "omit the fifth" lives — as a per-exercise instruction, never as a global
 * rule inside the evaluator.
 *
 * @param requiredDegrees tones the player must sound. Overrides the formula's own required set.
 * @param optionalDegrees tones the player may sound without penalty.
 * @param allowedOmissions tones from the formula that this exercise permits leaving out.
 * @param allowDoubling whether the same degree may appear in two octaves.
 * @param requireRoot false for the rootless families of Region 7.
 * @param pitchRange playable register, e.g. C3..C5 for a left-hand voicing.
 * @param maxVoices hand-size limit.
 */
public data class VoicingPolicy(
    val requiredDegrees: Set<ChordDegree>,
    val optionalDegrees: Set<ChordDegree> = emptySet(),
    val allowedOmissions: Set<ChordDegree> = emptySet(),
    val allowDoubling: Boolean = true,
    val requireRoot: Boolean = true,
    val bassRequirement: BassRequirement = BassRequirement.Unconstrained,
    val topNoteRequirement: TopNoteRequirement? = null,
    val pitchRange: IntRange? = null,
    val maxVoices: Int? = null,
    val namedFamily: VoicingFamily? = null,
    /** Tones that make the answer wrong in this exercise even though the chord allows them. */
    val disallowedDegrees: Set<ChordDegree> = emptySet(),
) {
    init {
        require(maxVoices == null || maxVoices > 0) { "maxVoices must be positive" }
        require(maxVoices == null || maxVoices >= requiredDegrees.size) {
            "maxVoices=$maxVoices cannot accommodate ${requiredDegrees.size} required degrees"
        }
        val contradiction = requiredDegrees intersect disallowedDegrees
        require(contradiction.isEmpty()) { "Degrees cannot be required and disallowed: $contradiction" }
        require(!(requireRoot && ChordDegree.ROOT in disallowedDegrees)) {
            "requireRoot is true but the root is disallowed"
        }
    }

    /** Every degree this policy will accept in a performance. */
    public val permittedDegrees: Set<ChordDegree> get() = requiredDegrees + optionalDegrees

    public fun permits(note: MidiNote): Boolean = pitchRange == null || note.value in pitchRange

    public companion object {
        /** The default for "play any Cmaj7": the formula's own required tones, any register. */
        public fun exactFormula(
            requiredDegrees: Set<ChordDegree>,
            optionalDegrees: Set<ChordDegree> = emptySet(),
        ): VoicingPolicy = VoicingPolicy(
            requiredDegrees = requiredDegrees,
            optionalDegrees = optionalDegrees,
        )
    }
}
