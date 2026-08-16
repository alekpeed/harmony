package com.harmonygates.core.designsystem.state

/**
 * What a piano key is being asked to be.
 *
 * 12_UI_UX_AND_FIGMA_HANDOFF.md §7 lists nine states and says they layer: a key can be a
 * required tone, physically held and judged correct all at once. So this is a small record of
 * independent facts rather than one enum, which is the only shape that can express "required,
 * held, and wrong" — the state a player most needs to see.
 */
public data class PianoKeyState(
    val role: KeyRole = KeyRole.INACTIVE,
    /** A finger is on it now. */
    val held: Boolean = false,
    /** Sounding because of the pedal, not because of a finger. */
    val sustained: Boolean = false,
    val verdict: KeyVerdict = KeyVerdict.NONE,
) {
    public companion object {
        public val Inactive: PianoKeyState = PianoKeyState()
    }
}

/** What the exercise wants of this key, before anything is played. */
public enum class KeyRole {
    INACTIVE,

    /** Shown as a target: "play here". */
    TARGET,

    /** A tone the chord cannot do without. */
    REQUIRED,

    /** A tone that belongs but may be left out — the fifth of a thirteenth chord. */
    OPTIONAL,
}

/** What evaluation made of this key afterwards. */
public enum class KeyVerdict {
    NONE,

    /** Played, and part of the answer. */
    CORRECT,

    /** Played, and not part of the answer. */
    EXTRA,

    /** Part of the answer, and not played. */
    MISSING,
}

/**
 * How a key is drawn, said without naming a colour.
 *
 * 12 §7: "Do not encode these states only by color. Shape/outline/marker differences should be
 * available." The mapping is a pure function returning shape decisions, so that rule is
 * structural rather than aspirational — this file has no access to the palette, and
 * `KeyPaintingTest` can check that no two states share a marker and an outline.
 */
public data class KeyPainting(
    val fill: KeyFill,
    val marker: KeyMarker,
    val outline: KeyOutline,
)

/** Which token fills the key. Named by meaning; the composable resolves it to a colour. */
public enum class KeyFill {
    NATURAL,
    PRESSED,
    TARGET,
    SUSTAINED,
    ERROR,
}

/** A glyph drawn in the lower part of the key, where a hand does not cover it. */
public enum class KeyMarker {
    NONE,

    /** A filled dot: this tone is required. */
    DOT,

    /** A hollow ring: this tone is optional. */
    RING,

    /** A tick: played, and right. */
    TICK,

    /** A cross: played, and not part of the chord. */
    CROSS,

    /** A hollow diamond: part of the answer, and never played. */
    DIAMOND,

    /** A short bar: the exercise is pointing at this key. */
    BAR,
}

/** The border treatment. Separate from the marker so two states can differ by outline alone. */
public enum class KeyOutline {
    NONE,
    THIN,
    THICK,

    /** Held by the pedal rather than by a finger — present, but not being played. */
    DASHED,
}

/**
 * The state-to-drawing mapping.
 *
 * Order matters and is the pedagogical order: what evaluation concluded outweighs what is
 * currently held, which outweighs what was being asked for. A player who has already been told
 * their answer was wrong should not have the marker replaced by "this was a required tone" the
 * moment they let go of the key.
 */
public object KeyPaintings {

    public fun of(state: PianoKeyState): KeyPainting = KeyPainting(
        fill = fillFor(state),
        marker = markerFor(state),
        outline = outlineFor(state),
    )

    private fun fillFor(state: PianoKeyState): KeyFill = when {
        state.verdict == KeyVerdict.EXTRA -> KeyFill.ERROR
        state.held -> KeyFill.PRESSED
        state.sustained -> KeyFill.SUSTAINED
        state.role == KeyRole.TARGET -> KeyFill.TARGET
        else -> KeyFill.NATURAL
    }

    private fun markerFor(state: PianoKeyState): KeyMarker = when (state.verdict) {
        KeyVerdict.CORRECT -> KeyMarker.TICK
        KeyVerdict.EXTRA -> KeyMarker.CROSS
        KeyVerdict.MISSING -> KeyMarker.DIAMOND
        KeyVerdict.NONE -> when (state.role) {
            KeyRole.TARGET -> KeyMarker.BAR
            KeyRole.REQUIRED -> KeyMarker.DOT
            KeyRole.OPTIONAL -> KeyMarker.RING
            KeyRole.INACTIVE -> KeyMarker.NONE
        }
    }

    private fun outlineFor(state: PianoKeyState): KeyOutline = when {
        // Checked before `held`: a key that is both held and sustained is being played, and the
        // dashed outline is how a player tells a pedal-held note from a finger-held one.
        state.held -> KeyOutline.THICK
        state.sustained -> KeyOutline.DASHED
        state.role != KeyRole.INACTIVE -> KeyOutline.THIN
        state.verdict != KeyVerdict.NONE -> KeyOutline.THIN
        else -> KeyOutline.NONE
    }
}
