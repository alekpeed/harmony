package com.harmonygates.core.music.assistance

import com.harmonygates.core.music.exercise.PresentationSpec

/**
 * One thing an exercise can show the player before they answer.
 *
 * 01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §5 lists these as independent switches and then says the
 * thing that matters: "Do not build each combination as a separate screen. The exercise screen
 * is compositional." So this is a set, not a difficulty number, and every level and preset below
 * is just a particular set.
 */
public enum class AssistanceChannel(public val label: String) {
    CHORD_SYMBOL("Chord symbol"),
    ROMAN_NUMERAL("Roman numeral"),
    NOTE_NAMES("Note names"),
    STAFF_NOTATION("Staff notation"),
    KEYBOARD_TARGETS("Highlighted keys"),
    TARGET_BASS("Target bass note"),
    INVERSION_LABEL("Inversion"),
    VOICING_NAME("Voicing name"),
    FINGERING("Fingering"),
    REFERENCE_AUDIO("Reference audio"),
    METRONOME("Metronome"),
    CONTEXT_CHORDS("Surrounding chords"),
    ;

    /**
     * True for the channels that hand the player the notes rather than the question.
     *
     * Used to grade evidence: a chord symbol is what is being asked, note names are the answer
     * written down. `MasteryEvidence` reads this to decide whether a correct answer was
     * independent.
     */
    public val revealsTheAnswer: Boolean
        get() = this == NOTE_NAMES || this == KEYBOARD_TARGETS ||
            this == STAFF_NOTATION || this == TARGET_BASS || this == FINGERING
}

/**
 * What an exercise shows.
 *
 * The type Phase 7 exists to add. It carries no notion of *difficulty* — a set of channels is
 * all it is — because the harmonic material and the assistance are independent sliders in
 * 02_GAME_LOOP_AND_PROGRESSION.md §8, and collapsing them into one number is exactly what that
 * section forbids.
 */
public data class AssistanceProfile(val channels: Set<AssistanceChannel>) {

    public operator fun contains(channel: AssistanceChannel): Boolean = channel in channels

    public fun with(channel: AssistanceChannel): AssistanceProfile =
        AssistanceProfile(channels + channel)

    public fun without(channel: AssistanceChannel): AssistanceProfile =
        AssistanceProfile(channels - channel)

    /** True when nothing on screen gives the notes away. */
    public val isIndependent: Boolean get() = channels.none { it.revealsTheAnswer }

    /**
     * The presentation switches this profile turns on.
     *
     * The conversion exists so that assistance can be authored, adjusted and reasoned about as a
     * set of channels while the exercise pipeline keeps the [PresentationSpec] it already had.
     * Nothing downstream needed to change to gain a hint ladder.
     */
    public val presentation: PresentationSpec
        get() = PresentationSpec(
            showChordSymbol = AssistanceChannel.CHORD_SYMBOL in channels,
            showSpelledNoteNames = AssistanceChannel.NOTE_NAMES in channels,
            showKeyboardTargets = AssistanceChannel.KEYBOARD_TARGETS in channels,
            showInversionLabel = AssistanceChannel.INVERSION_LABEL in channels,
            showRomanNumeral = AssistanceChannel.ROMAN_NUMERAL in channels,
            showStaffNotation = AssistanceChannel.STAFF_NOTATION in channels,
            showTargetBassNote = AssistanceChannel.TARGET_BASS in channels,
            showVoicingName = AssistanceChannel.VOICING_NAME in channels,
        )

    public companion object {
        public val Nothing: AssistanceProfile = AssistanceProfile(emptySet())

        public fun of(vararg channels: AssistanceChannel): AssistanceProfile =
            AssistanceProfile(channels.toSet())

        /** Reads an existing presentation back as a profile. */
        public fun from(spec: PresentationSpec): AssistanceProfile = AssistanceProfile(
            buildSet {
                if (spec.showChordSymbol) add(AssistanceChannel.CHORD_SYMBOL)
                if (spec.showSpelledNoteNames) add(AssistanceChannel.NOTE_NAMES)
                if (spec.showKeyboardTargets) add(AssistanceChannel.KEYBOARD_TARGETS)
                if (spec.showInversionLabel) add(AssistanceChannel.INVERSION_LABEL)
                if (spec.showRomanNumeral) add(AssistanceChannel.ROMAN_NUMERAL)
                if (spec.showStaffNotation) add(AssistanceChannel.STAFF_NOTATION)
                if (spec.showTargetBassNote) add(AssistanceChannel.TARGET_BASS)
                if (spec.showVoicingName) add(AssistanceChannel.VOICING_NAME)
            },
        )
    }
}

/**
 * The `A0`..`A7` ladder of 01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §4.
 *
 * Levels are a convenience for authors and for the difficulty slider, not a type the engine
 * cares about: each one resolves to a profile, and everything downstream sees only the profile.
 * That is what lets a gate ask for `A2` and a hint then reveal one extra channel without landing
 * on any named level at all.
 */
public enum class AssistanceLevel(
    public val id: String,
    public val profile: AssistanceProfile,
    public val description: String,
) {
    A0(
        "A0",
        AssistanceProfile.of(
            AssistanceChannel.CHORD_SYMBOL,
            AssistanceChannel.NOTE_NAMES,
            AssistanceChannel.KEYBOARD_TARGETS,
            AssistanceChannel.STAFF_NOTATION,
            AssistanceChannel.INVERSION_LABEL,
            AssistanceChannel.FINGERING,
            AssistanceChannel.REFERENCE_AUDIO,
        ),
        "Everything: symbol, notes, keys, notation, fingering and audio",
    ),

    A1(
        "A1",
        AssistanceProfile.of(
            AssistanceChannel.CHORD_SYMBOL,
            AssistanceChannel.NOTE_NAMES,
            AssistanceChannel.KEYBOARD_TARGETS,
            AssistanceChannel.INVERSION_LABEL,
        ),
        "Symbol, note names and highlighted keys",
    ),

    A2(
        "A2",
        AssistanceProfile.of(
            AssistanceChannel.CHORD_SYMBOL,
            AssistanceChannel.INVERSION_LABEL,
        ),
        "Symbol and the keyboard range; the notes are hidden",
    ),

    A3(
        "A3",
        AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL),
        "Chord symbol only",
    ),

    A4(
        "A4",
        AssistanceProfile.of(AssistanceChannel.STAFF_NOTATION),
        "Notation only",
    ),

    A5(
        "A5",
        AssistanceProfile.of(AssistanceChannel.ROMAN_NUMERAL),
        "Function only: read the numeral and work out the chord",
    ),

    A6(
        "A6",
        AssistanceProfile.of(AssistanceChannel.REFERENCE_AUDIO, AssistanceChannel.CONTEXT_CHORDS),
        "Audio, with the surrounding chords for context",
    ),

    A7(
        "A7",
        AssistanceProfile.of(AssistanceChannel.REFERENCE_AUDIO),
        "Audio only",
    ),
    ;

    public companion object {
        public fun byId(id: String): AssistanceLevel? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The friendly names a difficulty slider shows (02_GAME_LOOP_AND_PROGRESSION.md §8).
 *
 * §8 permits presets on the slider and then requires that "the internal model must preserve the
 * separate `H` and `A` values" — so a preset is a shortcut to a profile and never a thing the
 * engine stores. The harmonic-content slider is deliberately not modelled here; it is the
 * exercise policy's chord pool, which is a separate axis and stays one.
 */
public enum class DifficultyPreset(
    public val label: String,
    public val level: AssistanceLevel,
) {
    LEARN("Learn", AssistanceLevel.A0),
    GUIDED("Guided", AssistanceLevel.A1),
    PRACTICE("Practice", AssistanceLevel.A2),
    CHALLENGE("Challenge", AssistanceLevel.A3),
    BLIND("Blind", AssistanceLevel.A7),
    ;

    public val profile: AssistanceProfile get() = level.profile
}
