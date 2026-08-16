package com.harmonygates.home

import com.harmonygates.core.designsystem.artwork.ArtworkSpec
import com.harmonygates.core.designsystem.artwork.HitRegion
import com.harmonygates.core.designsystem.artwork.NormalizedRect

/**
 * Every interactive region of the approved home frame, keyed to its Figma layer name.
 *
 * `interface/README.md` lists twenty named `HIT / ...` regions and asks that the existing app
 * behaviour be wired to them. This enum is that wiring: one entry per named region, carrying
 * the layer name verbatim so a control can be traced back to the design file, and the app
 * destination it drives.
 *
 * Defining the contract before the artwork arrives is deliberate. When the export lands, the
 * remaining work is a table of rectangles — not a decision about what any button does.
 */
enum class HomeAction(
    /** The Figma layer name, exactly as written in the design file. */
    val figmaRegionId: String,
    /** The semantic action id from `interface/maps/home.json`. */
    val mapActionId: String,
    /** Spoken by accessibility services. */
    val label: String,
    val destination: HomeDestination,
) {
    Menu("HIT / Menu", "toggle_menu", "Open menu", HomeDestination.Menu),

    NavHome("HIT / Nav Home", "navigate_home", "Home", HomeDestination.Home),
    NavMap("HIT / Nav Map", "navigate_map", "Campaign map", HomeDestination.Campaign),
    NavPractice("HIT / Nav Practice", "navigate_practice", "Quick practice", HomeDestination.QuickPractice),
    NavStats("HIT / Nav Stats", "navigate_stats", "Progress", HomeDestination.Progress),
    NavLibrary("HIT / Nav Library", "navigate_library", "Library", HomeDestination.Library),
    NavProfile("HIT / Nav Profile", "navigate_profile", "Profile", HomeDestination.Profile),
    NavSettings("HIT / Nav Settings", "navigate_settings", "Settings", HomeDestination.Settings),

    ProfileSummary("HIT / Profile Summary", "open_profile_summary", "Profile summary", HomeDestination.Profile),

    ChordGates("HIT / Chord Gates", "navigate_chord_gates", "Chord gates", HomeDestination.ChordGate),
    EarTrainer("HIT / Ear Trainer", "navigate_ear_trainer", "Ear trainer", HomeDestination.EarTraining),
    SightReading("HIT / Sight Reading", "navigate_sight_reading", "Sight reading", HomeDestination.SightReading),
    ProgressionRun(
        "HIT / Progression Run",
        "navigate_progression_run",
        "Progression run",
        HomeDestination.ProgressionLab,
    ),
    VoiceLeading("HIT / Voice Leading", "navigate_voice_leading", "Voice leading", HomeDestination.VoicingLab),

    // The theory lab is the one destination that exists today: it is the Phase 1 harness over
    // core:music. Wiring it up means the approved artwork has a live control from the moment it
    // lands, rather than twenty dead ones.
    TheoryLab("HIT / Theory Lab", "navigate_theory_lab", "Theory lab", HomeDestination.TheoryLab),

    DailyChallenge(
        "HIT / Daily Challenge",
        "navigate_daily_challenge",
        "Daily challenge",
        HomeDestination.DailyChallenge,
    ),
    MyJourney("HIT / My Journey", "navigate_my_journey", "My journey", HomeDestination.Campaign),
    NextGateCard("HIT / Next Gate Card", "open_next_gate", "Next gate", HomeDestination.NextGate),
    Continue("HIT / Continue", "continue_next_gate", "Continue where you left off", HomeDestination.Resume),
    StreakSummary("HIT / Streak Summary", "open_progress_summary", "Streak summary", HomeDestination.Progress),
    ;

    companion object {
        fun forRegion(regionId: String): HomeAction? = entries.firstOrNull { it.figmaRegionId == regionId }

        fun forMapAction(actionId: String): HomeAction? = entries.firstOrNull { it.mapActionId == actionId }

        /**
         * Resolves a mapped region, preferring the semantic action id.
         *
         * The map carries both. The action id is the more stable of the two — a designer may
         * rename a layer for tidiness, but `navigate_ear_trainer` says what it is for — so it
         * wins, with the layer name as the fallback.
         */
        fun forMappedRegion(actionId: String, figmaLayer: String): HomeAction? =
            forMapAction(actionId) ?: forRegion(figmaLayer)
    }
}

/**
 * Where a home action leads.
 *
 * The routes mirror 10_ANDROID_ARCHITECTURE.md §7. [isImplemented] is honest rather than
 * aspirational: it is what lets the home screen tell a player that a door is not open yet
 * instead of navigating them into an empty room.
 */
enum class HomeDestination(
    val isImplemented: Boolean,
    val arrivesInPhase: Int,
    /** What the screen will be. Shown on its placeholder until it exists. */
    val summary: String = "",
    /** What is already built behind it — which is usually more than the screen suggests. */
    val engineStatus: String = "",
) {
    /** The Phase 1 harness over the music domain. Live now. */
    TheoryLab(isImplemented = true, arrivesInPhase = 1),

    Home(isImplemented = true, arrivesInPhase = 1),

    /**
     * MIDI setup and diagnostics, from Phase 2.
     *
     * The approved home frame has no MIDI region of its own, and MIDI setup genuinely belongs
     * under settings, so the Settings control opens it. When the full settings screen arrives
     * in Phase 6 this becomes a row inside it rather than the whole destination.
     */
    Settings(isImplemented = true, arrivesInPhase = 2),

    /** The Phase 4 vertical slice: show a chord, play it, get a verdict. */
    ChordGate(isImplemented = true, arrivesInPhase = 4),

    /** Quick practice runs the same loop with no gate around it. */
    QuickPractice(isImplemented = true, arrivesInPhase = 4),

    Menu(
        isImplemented = false,
        arrivesInPhase = 7,
        summary = "A slide-out menu for everything the sidebar does not have room for.",
        engineStatus = "Every destination it would list is already reachable from the home " +
            "screen, so this is a convenience rather than a door.",
    ),

    /** The campaign map, from Phase 6. Next Gate and Resume open it at the gate to play. */
    Campaign(isImplemented = true, arrivesInPhase = 6),
    NextGate(isImplemented = true, arrivesInPhase = 6),
    Resume(isImplemented = true, arrivesInPhase = 6),

    DailyChallenge(
        isImplemented = false,
        arrivesInPhase = 13,
        summary = "One exercise a day, drawn from what you are weakest at.",
        engineStatus = "The mastery model already records per-skill weakness and schedules " +
            "review, so the selection this needs is a query rather than new code.",
    ),

    /** Mastery and attempt history, from Phase 5. */
    Progress(isImplemented = true, arrivesInPhase = 5),
    Profile(isImplemented = true, arrivesInPhase = 5),
    EarTraining(
        isImplemented = false,
        arrivesInPhase = 8,
        summary = "Hear a chord and play it back, name a quality, or say what moved.",
        engineStatus = "Built and tested: a pure-Kotlin mixer with a synthesised piano, four " +
            "exercise families, and four authored gates waiting in the campaign. What is " +
            "missing is only the screen.",
    ),
    SightReading(
        isImplemented = false,
        arrivesInPhase = 9,
        summary = "Read a line of notation and play it in time.",
        engineStatus = "Built and tested: exact rational durations, a Compose staff renderer, " +
            "and an evaluator that scores pitch and rhythm separately. Three gates are " +
            "authored. What is missing is the screen that puts a clock on it.",
    ),
    /** Progression Run: the track, from Phase 10. */
    ProgressionLab(isImplemented = true, arrivesInPhase = 10),
    VoicingLab(
        isImplemented = false,
        arrivesInPhase = 10,
        summary = "Move between chords with the least motion that works.",
        engineStatus = "The voice-leading engine scores total motion, largest leap and " +
            "retained common tones, and two gates practise the material as progressions. " +
            "The screen that shows a starting voicing and asks for the next one is not built.",
    ),
    Library(
        isImplemented = false,
        arrivesInPhase = 13,
        summary = "The chord and progression vocabulary, to browse rather than be tested on.",
        engineStatus = "Thirty chord formulas and eleven progressions are already data the app " +
            "reads at startup. This is a reader for them.",
    ),
}

/**
 * The approved home frame.
 *
 * Every piece is supplied through `interface/` and copied into resources by
 * `syncInterfaceArtwork`, so nothing here is transcribed by hand:
 *
 * - the artwork as `R.drawable.home_approved`, always resolvable, real export or placeholder
 * - whether it is the real one as `R.bool.home_approved_available`
 * - its true pixel size as `R.integer.home_approved_native_*`
 * - the twenty hit regions as `R.raw.home_interaction_map`
 *
 * See docs/INTERFACE_INTEGRATION.md.
 */
object HomeArtwork {

    /** Native artwork size promised by `interface/README.md`; a fallback, not an assumption. */
    const val DECLARED_WIDTH: Int = 1536
    const val DECLARED_HEIGHT: Int = 1024
}
