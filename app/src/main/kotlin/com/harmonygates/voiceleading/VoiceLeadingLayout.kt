package com.harmonygates.voiceleading

import com.harmonygates.R
import com.harmonygates.artwork.DesignRect

/**
 * Where Voice Leading's regions sit, read off the pack's own layout templates.
 *
 * `10_templates/template-landing` and `template-practice` are 1536 x 1024 wireframes naming the
 * regions this screen is made of — nav, module intro, progress, practice modes, midi status for
 * setup; nav, exercise visualization, controls, feedback, session status for practice. These are
 * those rectangles.
 *
 * The templates themselves are never drawn. They are the instruction, not the picture, which is
 * why `SyncVoiceLeadingAssets` refuses to ship them.
 */
object VoiceLeadingLayout {

    // --- Shared ----------------------------------------------------------------------------------

    val Nav = DesignRect(x = 40f, y = 30f, width = 1456f, height = 84f)

    // --- Landing template ------------------------------------------------------------------------

    val ModuleIntro = DesignRect(x = 62f, y = 155f, width = 530f, height = 300f)
    val Progress = DesignRect(x = 910f, y = 157f, width = 560f, height = 272f)
    val PracticeModes = DesignRect(x = 62f, y = 498f, width = 1400f, height = 390f)
    val MidiStatusLanding = DesignRect(x = 1150f, y = 930f, width = 320f, height = 56f)

    // --- Practice template -----------------------------------------------------------------------

    val ExerciseVisualization = DesignRect(x = 62f, y = 128f, width = 975f, height = 680f)
    val Controls = DesignRect(x = 1070f, y = 128f, width = 405f, height = 680f)
    val Feedback = DesignRect(x = 62f, y = 805f, width = 975f, height = 158f)
    val SessionStatus = DesignRect(x = 1070f, y = 805f, width = 405f, height = 158f)

    // --- Components, at the sizes component-sizing.json declares ----------------------------------

    const val PRIMARY_BUTTON_WIDTH = 280f
    const val PRIMARY_BUTTON_HEIGHT = 72f
    const val SECONDARY_BUTTON_WIDTH = 240f
    const val SECONDARY_BUTTON_HEIGHT = 64f
    const val ICON_BUTTON = 60f
    const val STATUS_PILL_WIDTH = 220f
    const val STATUS_PILL_HEIGHT = 52f
    const val CHIP_WIDTH = 150f
    const val CHIP_HEIGHT = 44f
    const val NOTE_TOKEN = 68f
    const val PROGRESS_BAR_WIDTH = 400f
    const val PROGRESS_BAR_HEIGHT = 24f

    /** A chip in a wrapped row, laid out from a region's top-left. */
    fun chip(region: DesignRect, column: Int, row: Int): DesignRect = DesignRect(
        x = region.x + CHIP_GUTTER + column * (CHIP_WIDTH + CHIP_GUTTER),
        y = region.y + CHIP_TOP + row * (CHIP_HEIGHT + CHIP_GUTTER),
        width = CHIP_WIDTH,
        height = CHIP_HEIGHT,
    )

    /** A note token in the voicing row: source above, played below. */
    fun noteToken(region: DesignRect, index: Int, row: Int): DesignRect = DesignRect(
        x = region.x + TOKEN_INSET + index * (NOTE_TOKEN + TOKEN_GUTTER),
        y = region.y + TOKEN_TOP + row * (NOTE_TOKEN + TOKEN_ROW_GAP),
        width = NOTE_TOKEN,
        height = NOTE_TOKEN,
    )

    /** The motion arrow between a source token and the token beneath it. */
    fun motionArrow(region: DesignRect, index: Int): DesignRect = DesignRect(
        x = region.x + TOKEN_INSET + index * (NOTE_TOKEN + TOKEN_GUTTER) + (NOTE_TOKEN - ARROW) / 2f,
        y = region.y + TOKEN_TOP + NOTE_TOKEN + (TOKEN_ROW_GAP - ARROW) / 2f,
        width = ARROW,
        height = ARROW,
    )

    private const val CHIP_GUTTER = 18f
    private const val CHIP_TOP = 64f
    private const val TOKEN_INSET = 48f
    private const val TOKEN_TOP = 96f
    private const val TOKEN_GUTTER = 26f
    private const val TOKEN_ROW_GAP = 96f
    private const val ARROW = 56f

    /** Panels and shells. */
    val PanelControl = R.drawable.vl_control_panel
    val PanelExerciseLarge = R.drawable.vl_exercise_panel_large
    val PanelStats = R.drawable.vl_stats_panel
    val PanelVoicingCard = R.drawable.vl_voicing_card
    val PanelMiniCard = R.drawable.vl_mini_card
    val TopNavBar = R.drawable.vl_top_nav_bar
    val SectionTitlePlate = R.drawable.vl_section_title_plate

    /** Buttons. */
    val ButtonPrimary = R.drawable.vl_button_primary_default
    val ButtonPrimaryPressed = R.drawable.vl_button_primary_pressed
    val ButtonPrimaryDisabled = R.drawable.vl_button_primary_disabled
    val ButtonSecondary = R.drawable.vl_button_secondary_default
    val ButtonSecondaryPressed = R.drawable.vl_button_secondary_pressed
    val IconButton = R.drawable.vl_icon_button_default
    val IconButtonActive = R.drawable.vl_icon_button_active
    val IconButtonDisabled = R.drawable.vl_icon_button_disabled

    /** Icons that sit inside an icon button. */
    val IconPlay = R.drawable.vl_icon_play
    val IconReplay = R.drawable.vl_icon_replay
    val IconNext = R.drawable.vl_icon_next
    val IconBack = R.drawable.vl_icon_back
    val IconHome = R.drawable.vl_icon_home
    val IconSettings = R.drawable.vl_icon_settings

    /** Selectors. */
    val ChipDefault = R.drawable.vl_chip_default
    val ChipSelected = R.drawable.vl_chip_selected

    /** Status. */
    val MidiConnected = R.drawable.vl_midi_connected
    val MidiDisconnected = R.drawable.vl_midi_disconnected
    val StatusReady = R.drawable.vl_status_ready
    val StatusListening = R.drawable.vl_status_listening
    val StatusEvaluating = R.drawable.vl_status_evaluating
    val StatusComplete = R.drawable.vl_status_complete

    /** Feedback containers. */
    val FeedbackCorrect = R.drawable.vl_feedback_correct
    val FeedbackIncorrect = R.drawable.vl_feedback_incorrect
    val FeedbackPartial = R.drawable.vl_feedback_partial
    val FeedbackNeutral = R.drawable.vl_feedback_neutral

    /** Note tokens and voice motion. */
    val TokenNeutral = R.drawable.vl_note_token_neutral
    val TokenActive = R.drawable.vl_note_token_active
    val TokenTarget = R.drawable.vl_note_token_target
    val TokenCorrect = R.drawable.vl_note_token_correct
    val TokenIncorrect = R.drawable.vl_note_token_incorrect
    val MotionArrowRight = R.drawable.vl_motion_arrow_right
    val MotionArrowCurved = R.drawable.vl_motion_arrow_curved
    val VoiceLineCommonTone = R.drawable.vl_voice_line_common_tone
    val VoiceLineStep = R.drawable.vl_voice_line_step
    val VoiceLineLeap = R.drawable.vl_voice_line_leap

    /** Meters. */
    val ProgressEmpty = R.drawable.vl_progress_bar_empty
    val ProgressFull = R.drawable.vl_progress_bar_full
    val AccuracyMeter = R.drawable.vl_accuracy_meter
}
