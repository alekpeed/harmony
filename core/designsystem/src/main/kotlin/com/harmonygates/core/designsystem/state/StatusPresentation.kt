package com.harmonygates.core.designsystem.state

/**
 * The MIDI connection states a player can be shown.
 *
 * 12_UI_UX_AND_FIGMA_HANDOFF.md §8 names these four and adds that connection state must always
 * be discoverable. Each carries its own glyph and wording, so the chip is readable without
 * colour and announceable to a screen reader without an extra lookup table.
 */
public enum class MidiPresentation(
    public val glyph: String,
    public val shortLabel: String,
) {
    DISCONNECTED("○", "No keyboard"),
    CONNECTING("◐", "Connecting"),
    CONNECTED("●", "Connected"),
    ERROR("⚠", "Connection error"),
    ;

    /** What a screen reader says. [deviceName] is used when connected, ignored otherwise. */
    public fun description(deviceName: String? = null): String = when (this) {
        CONNECTED -> deviceName?.let { "MIDI connected: $it" } ?: "MIDI connected"
        DISCONNECTED -> "No MIDI keyboard connected"
        CONNECTING -> "Connecting to MIDI keyboard"
        ERROR -> "MIDI connection error"
    }
}

/**
 * How a gate looks on the campaign map.
 *
 * 02_GAME_LOOP_AND_PROGRESSION.md derives gate status from evidence and never stores it, so this
 * is a presentation mirror of that derived value rather than a second source of truth. The
 * design system does not depend on `core:music`; the app maps one to the other.
 */
public enum class GatePresentation(
    public val glyph: String,
    public val shortLabel: String,
) {
    LOCKED("🔒", "Locked"),
    AVAILABLE("▶", "Open"),
    IN_PROGRESS("◧", "In progress"),
    MASTERED("★", "Mastered"),
    ;

    /** Whether a player can start it. Locked gates are shown, not hidden — the map is a map. */
    public val isPlayable: Boolean get() = this != LOCKED
}

/**
 * How an answer was judged.
 *
 * Mirrors the verdicts in 06_PERFORMANCE_EVALUATION_AND_SCORING.md §2. The glyph is the reason
 * this is an enum with data rather than a colour lookup: 18_ACCEPTANCE_CRITERIA.md says
 * correctness must never be carried by colour alone.
 */
public enum class FeedbackPresentation(
    public val glyph: String,
    public val shortLabel: String,
) {
    CORRECT("✓", "Correct"),
    PARTIAL("~", "Nearly"),
    INCORRECT("✕", "Not yet"),
    NEUTRAL("•", "Waiting"),
}
