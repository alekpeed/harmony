package com.harmonygates.core.music.exercise

import com.harmonygates.core.music.chord.ChordFormulaId
import com.harmonygates.core.music.performance.OnsetPolicy
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.voicing.Inversion
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingPolicy

/** Stable identifiers. Content JSON references these strings (21_CONTENT_AUTHORING_GUIDE.md §6). */
@JvmInline
public value class ExercisePolicyId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Exercise policy id must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class ExerciseDefinitionId(public val value: String) {
    override fun toString(): String = value
}

@JvmInline
public value class ExerciseInstanceId(public val value: String) {
    override fun toString(): String = value
}

@JvmInline
public value class SkillId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Skill id must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * How an answer is judged.
 *
 * 01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §6: "Every exercise explicitly declares how it is
 * evaluated... No global 'chord equals set of pitch classes' shortcut is sufficient." So the
 * mode is part of the content, chosen per exercise, never inferred.
 */
public enum class AnswerMode {
    /** Any octave, any voicing: "play a Cmaj7". */
    PitchClasses,

    /** Degree-aware, against a voicing policy. Rootless and shell families need this. */
    ChordPolicy,

    /** The exact notes shown, including doublings. */
    ExactVoicing,
}

/**
 * What the player is shown (01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §5).
 *
 * "Do not build each combination as a separate screen. The exercise screen is compositional."
 * So these are independent switches rather than named difficulty levels, and Phase 7's
 * assistance profiles will set them rather than replace them.
 */
public data class PresentationSpec(
    val showChordSymbol: Boolean = true,
    val showSpelledNoteNames: Boolean = false,
    val showKeyboardTargets: Boolean = false,
    val showInversionLabel: Boolean = false,
    val showRomanNumeral: Boolean = false,
    val showStaffNotation: Boolean = false,
    val showTargetBassNote: Boolean = false,
    val showVoicingName: Boolean = false,
) {
    public companion object {
        /** Everything the player needs handed to them. The first trials of a gate. */
        public val Guided: PresentationSpec = PresentationSpec(
            showChordSymbol = true,
            showSpelledNoteNames = true,
            showKeyboardTargets = true,
            showInversionLabel = true,
        )

        /** Symbol only. The player supplies the rest. */
        public val Independent: PresentationSpec = PresentationSpec(showChordSymbol = true)
    }
}

/**
 * What an exercise may generate, and how it is judged.
 *
 * The checklist in 21_CONTENT_AUTHORING_GUIDE.md §4. Adding a lesson should mean writing one of
 * these, not writing a screen — so everything that varies between exercises of the same shape
 * lives here as data.
 *
 * @param rootPool roots the generator may choose from. Empty means all twelve.
 * @param formulaPool chord qualities it may choose from.
 * @param inversionPool inversions it may ask for. Empty means root position only.
 * @param voicingPolicy the answer policy, used when [answerMode] is degree-aware.
 * @param sessionLength how many exercises a session of this policy runs for.
 */
public data class ExercisePolicy(
    val id: ExercisePolicyId,
    val skillIds: Set<SkillId>,
    val rootPool: List<SpelledPitchClass> = emptyList(),
    val formulaPool: List<ChordFormulaId> = emptyList(),
    val inversionPool: List<Inversion> = listOf(Inversion.ROOT),
    val answerMode: AnswerMode = AnswerMode.PitchClasses,
    val voicingPolicy: VoicingPolicy? = null,
    /**
     * A named shape, resolved against each chord as it is generated.
     *
     * A rootless A voicing of `Cmaj7` and of `G7` are different sets of notes, so a family
     * cannot be flattened into one [VoicingPolicy] ahead of time the way a fixed constraint can.
     * The family is the authored intent; `VoicingFamilies.recipe` turns it into the policy for
     * a particular chord at generation time.
     */
    val voicingFamily: VoicingFamily? = null,
    val presentation: PresentationSpec = PresentationSpec.Independent,
    val onsetPolicy: OnsetPolicy = OnsetPolicy.NormalRoll,
    val pitchRange: IntRange = DEFAULT_RANGE,
    val sessionLength: Int = DEFAULT_SESSION_LENGTH,
) {
    init {
        require(formulaPool.isNotEmpty()) { "An exercise policy needs at least one chord quality" }
        require(inversionPool.isNotEmpty()) { "An exercise policy needs at least one inversion" }
        require(sessionLength > 0) { "A session needs at least one exercise" }
        require(answerMode != AnswerMode.ChordPolicy || voicingPolicy != null || voicingFamily != null) {
            "A degree-aware policy match needs either a voicing policy or a named family"
        }
    }

    public companion object {
        /** Two octaves either side of middle C. */
        public val DEFAULT_RANGE: IntRange = 36..96

        /** 02_GAME_LOOP_AND_PROGRESSION.md §3: a gate session runs 8–24 exercises. */
        public const val DEFAULT_SESSION_LENGTH: Int = 20
    }
}
