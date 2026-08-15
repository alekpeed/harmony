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
    /** Spoken by accessibility services. */
    val label: String,
    val destination: HomeDestination,
) {
    Menu("HIT / Menu", "Open menu", HomeDestination.Menu),

    NavHome("HIT / Nav Home", "Home", HomeDestination.Home),
    NavMap("HIT / Nav Map", "Campaign map", HomeDestination.Campaign),
    NavPractice("HIT / Nav Practice", "Quick practice", HomeDestination.QuickPractice),
    NavStats("HIT / Nav Stats", "Progress", HomeDestination.Progress),
    NavLibrary("HIT / Nav Library", "Library", HomeDestination.Library),
    NavProfile("HIT / Nav Profile", "Profile", HomeDestination.Profile),
    NavSettings("HIT / Nav Settings", "Settings", HomeDestination.Settings),

    ProfileSummary("HIT / Profile Summary", "Profile summary", HomeDestination.Profile),

    ChordGates("HIT / Chord Gates", "Chord gates", HomeDestination.ChordGate),
    EarTrainer("HIT / Ear Trainer", "Ear trainer", HomeDestination.EarTraining),
    SightReading("HIT / Sight Reading", "Sight reading", HomeDestination.SightReading),
    ProgressionRun("HIT / Progression Run", "Progression run", HomeDestination.ProgressionLab),
    VoiceLeading("HIT / Voice Leading", "Voice leading", HomeDestination.VoicingLab),

    // The theory lab is the one destination that exists today: it is the Phase 1 harness over
    // core:music. Wiring it up now means the artwork has at least one live control the moment
    // it lands, rather than twenty dead ones.
    TheoryLab("HIT / Theory Lab", "Theory lab", HomeDestination.TheoryLab),

    DailyChallenge("HIT / Daily Challenge", "Daily challenge", HomeDestination.DailyChallenge),
    MyJourney("HIT / My Journey", "My journey", HomeDestination.Campaign),
    NextGateCard("HIT / Next Gate Card", "Next gate", HomeDestination.NextGate),
    Continue("HIT / Continue", "Continue where you left off", HomeDestination.Resume),
    StreakSummary("HIT / Streak Summary", "Streak summary", HomeDestination.Progress),
    ;

    companion object {
        fun forRegion(regionId: String): HomeAction? = entries.firstOrNull { it.figmaRegionId == regionId }
    }
}

/**
 * Where a home action leads.
 *
 * The routes mirror 10_ANDROID_ARCHITECTURE.md §7. [isImplemented] is honest rather than
 * aspirational: it is what lets the home screen tell a player that a door is not open yet
 * instead of navigating them into an empty room.
 */
enum class HomeDestination(val isImplemented: Boolean, val arrivesInPhase: Int) {
    /** The Phase 1 harness over the music domain. Live now. */
    TheoryLab(isImplemented = true, arrivesInPhase = 1),

    Home(isImplemented = true, arrivesInPhase = 1),

    Menu(isImplemented = false, arrivesInPhase = 6),
    Campaign(isImplemented = false, arrivesInPhase = 6),
    NextGate(isImplemented = false, arrivesInPhase = 6),
    Resume(isImplemented = false, arrivesInPhase = 6),
    DailyChallenge(isImplemented = false, arrivesInPhase = 6),
    ChordGate(isImplemented = false, arrivesInPhase = 4),
    QuickPractice(isImplemented = false, arrivesInPhase = 4),
    Progress(isImplemented = false, arrivesInPhase = 5),
    Profile(isImplemented = false, arrivesInPhase = 5),
    EarTraining(isImplemented = false, arrivesInPhase = 8),
    SightReading(isImplemented = false, arrivesInPhase = 9),
    ProgressionLab(isImplemented = false, arrivesInPhase = 10),
    VoicingLab(isImplemented = false, arrivesInPhase = 10),
    Library(isImplemented = false, arrivesInPhase = 13),
    Settings(isImplemented = false, arrivesInPhase = 6),
}

/**
 * The approved home frame and where its controls sit.
 *
 * The native size comes from `interface/README.md`: 1536 x 1024 landscape. [regionBounds] is
 * empty until the export supplies coordinates; see docs/INTERFACE_INTEGRATION.md for the
 * three-step process of dropping the asset in.
 *
 * An empty table is not a stub that needs replacing — [spec] builds correctly from whatever is
 * present, so regions can be filled in a few at a time as they are measured.
 */
object HomeArtwork {

    const val NATIVE_WIDTH: Int = 1536
    const val NATIVE_HEIGHT: Int = 1024

    /**
     * Drawable resource for the approved artwork, or null while it is pending.
     *
     * Set this to `R.drawable.home_approved` once the export is committed to
     * `app/src/main/res/drawable-nodpi/`. It is a single named constant rather than a lookup by
     * name so the reference is checked at compile time and shows up in a build failure if the
     * asset is ever removed.
     */
    val drawableResId: Int? = null

    /**
     * Region bounds as fractions of the artwork, keyed by action.
     *
     * Measured from the Figma frame's transparent `HIT / ...` layers and converted with
     * `ArtworkGeometry.normalize`. Fractions rather than pixels, so the same table serves every
     * tablet size and both orientations.
     */
    val regionBounds: Map<HomeAction, NormalizedRect> = emptyMap()

    /** True once both the artwork and at least one region are available. */
    val isAvailable: Boolean get() = drawableResId != null && regionBounds.isNotEmpty()

    val spec: ArtworkSpec = ArtworkSpec(
        nativeWidth = NATIVE_WIDTH,
        nativeHeight = NATIVE_HEIGHT,
        regions = HomeAction.entries.mapNotNull { action ->
            regionBounds[action]?.let { bounds ->
                HitRegion(id = action.figmaRegionId, bounds = bounds, contentDescription = action.label)
            }
        },
    )
}
