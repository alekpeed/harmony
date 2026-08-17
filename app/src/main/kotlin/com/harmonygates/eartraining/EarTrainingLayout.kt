package com.harmonygates.eartraining

import com.harmonygates.R

/**
 * Where every Ear Training control sits, in design pixels.
 *
 * These are read off the approved 1536 x 1024 plates and are the same numbers written into
 * `interface/maps/ear_training.json`. They live in one object rather than inline in the screen so
 * that the map and the code cannot drift: a control that moves in the design moves here once.
 *
 * The bottom transport bar has the same geometry in both modes. In setup it is drawn by
 * `ear_training_section_layout`; in training the console above it is gone and
 * `ear_training_training_bar_shell` draws the identical bar over the room. Sharing the numbers is
 * what makes the mode change look like the console lifting away rather than the bar jumping.
 */
object EarLayout {

    /** The compact bar, anchored where the section layout already draws it. */
    val TrainingBar = DesignRect(x = 43f, y = 797f, width = 1450f, height = 200f)

    // --- Setup console: exercise type -----------------------------------------------------------

    private const val FAMILY_ROW_X = 158f
    private const val FAMILY_ROW_WIDTH = 289f
    private const val FAMILY_ROW_HEIGHT = 72f
    private const val FAMILY_ROW_FIRST_TOP = 258f
    private const val FAMILY_ROW_PITCH = 92f

    fun familyRow(index: Int): DesignRect = DesignRect(
        x = FAMILY_ROW_X,
        y = FAMILY_ROW_FIRST_TOP + FAMILY_ROW_PITCH * index,
        width = FAMILY_ROW_WIDTH,
        height = FAMILY_ROW_HEIGHT,
    )

    /** The recessed circle at the left of a family row. */
    fun familyRowIcon(index: Int): DesignRect =
        DesignRect.circle(centreX = 196f, centreY = familyRow(index).centreY, radius = 24f)

    /** The blank label field to the right of the divider. */
    fun familyRowLabel(index: Int): DesignRect {
        val row = familyRow(index)
        return DesignRect(x = 240f, y = row.y, width = 200f, height = row.height)
    }

    // --- Setup console: centre panels ------------------------------------------------------------

    /**
     * The three centre panels the current engine has no state for.
     *
     * Kept here, unbound and without hit regions, because they are part of the approved plate and
     * a future engine feature belongs in them rather than somewhere new. Nothing draws into them.
     */
    val IntervalSettingsPanel = DesignRect(x = 508f, y = 268f, width = 555f, height = 77f)
    val ModePanel = DesignRect(x = 508f, y = 420f, width = 254f, height = 77f)
    val DirectionPanel = DesignRect(x = 793f, y = 420f, width = 270f, height = 77f)

    /** Root/key. The session cycles all twelve; this panel says so. */
    val RootKeyPanel = DesignRect(x = 508f, y = 562f, width = 555f, height = 78f)

    // --- Setup console: options and session ------------------------------------------------------

    private const val OPTION_ROW_X = 1128f
    private const val OPTION_ROW_WIDTH = 262f
    private const val OPTION_ROW_HEIGHT = 58f
    private const val OPTION_ROW_FIRST_TOP = 272f
    private const val OPTION_ROW_PITCH = 68f

    /** Four option rows, likewise unbound: the engine exposes no player-adjustable options yet. */
    fun optionRow(index: Int): DesignRect = DesignRect(
        x = OPTION_ROW_X,
        y = OPTION_ROW_FIRST_TOP + OPTION_ROW_PITCH * index,
        width = OPTION_ROW_WIDTH,
        height = OPTION_ROW_HEIGHT,
    )

    const val OPTION_ROW_COUNT = 4

    val SessionRow = DesignRect(x = 1128f, y = 600f, width = 262f, height = 55f)
    val SessionRowLabel = DesignRect(x = 1196f, y = 600f, width = 150f, height = 55f)

    /** The illuminated pill along the bottom of the console. */
    val StartPill = DesignRect(x = 588f, y = 672f, width = 364f, height = 70f)

    // --- Transport bar, shared by both modes -----------------------------------------------------

    private const val BAR_CENTRE_Y = 897f

    val BarLeftCircle = DesignRect.circle(140f, BAR_CENTRE_Y, 32f)
    val BarLeftPanel = DesignRect(x = 205f, y = 868f, width = 365f, height = 57f)
    val BarReplay = DesignRect.circle(645f, BAR_CENTRE_Y, 26f)
    val BarPlay = DesignRect.circle(770f, 895f, 55f)
    val BarNext = DesignRect.circle(893f, BAR_CENTRE_Y, 26f)
    val BarRightPanel = DesignRect(x = 962f, y = 868f, width = 273f, height = 57f)
    val BarSettings = DesignRect.circle(1292f, BAR_CENTRE_Y, 26f)
    val BarRightCircle = DesignRect.circle(1405f, BAR_CENTRE_Y, 33f)

    /** The MIDI lamp and its wording, inside the bar's left panel. */
    val BarMidiLamp = DesignRect.circle(238f, BAR_CENTRE_Y, 16f)
    val BarMidiLabel = DesignRect(x = 268f, y = 868f, width = 290f, height = 57f)

    // --- Dynamic text over the room, during training ---------------------------------------------

    val TrainingInstruction = DesignRect(x = 368f, y = 636f, width = 800f, height = 48f)
    val TrainingVerdict = DesignRect(x = 368f, y = 692f, width = 800f, height = 56f)
    val TrainingKeyButton = DesignRect.centred(768f, 566f, 136f, 104f)
    val TrainingMessage = DesignRect(x = 368f, y = 560f, width = 800f, height = 120f)

    /** Resource for a note button, by the app's own spelling of the pitch class. */
    fun noteButton(spelling: String, active: Boolean): Int? =
        if (active) NOTE_ACTIVE[spelling] else NOTE_IDLE[spelling]

    /**
     * Seventeen spellings, not twelve pitch classes.
     *
     * The asset set ships `Db` and `C#` as different pictures, which is the only way a screen can
     * honour the engine's spelling discipline: `Db` and `C#` are not interchangeable anywhere else
     * in this project, and a button that renders one as the other would be the single place they
     * were.
     */
    private val NOTE_IDLE = mapOf(
        "C" to R.drawable.et_note_c_idle,
        "C#" to R.drawable.et_note_cs_idle,
        "Db" to R.drawable.et_note_db_idle,
        "D" to R.drawable.et_note_d_idle,
        "D#" to R.drawable.et_note_ds_idle,
        "Eb" to R.drawable.et_note_eb_idle,
        "E" to R.drawable.et_note_e_idle,
        "F" to R.drawable.et_note_f_idle,
        "F#" to R.drawable.et_note_fs_idle,
        "Gb" to R.drawable.et_note_gb_idle,
        "G" to R.drawable.et_note_g_idle,
        "G#" to R.drawable.et_note_gs_idle,
        "Ab" to R.drawable.et_note_ab_idle,
        "A" to R.drawable.et_note_a_idle,
        "A#" to R.drawable.et_note_as_idle,
        "Bb" to R.drawable.et_note_bb_idle,
        "B" to R.drawable.et_note_b_idle,
    )

    private val NOTE_ACTIVE = mapOf(
        "C" to R.drawable.et_note_c_active,
        "C#" to R.drawable.et_note_cs_active,
        "Db" to R.drawable.et_note_db_active,
        "D" to R.drawable.et_note_d_active,
        "D#" to R.drawable.et_note_ds_active,
        "Eb" to R.drawable.et_note_eb_active,
        "E" to R.drawable.et_note_e_active,
        "F" to R.drawable.et_note_f_active,
        "F#" to R.drawable.et_note_fs_active,
        "Gb" to R.drawable.et_note_gb_active,
        "G" to R.drawable.et_note_g_active,
        "G#" to R.drawable.et_note_gs_active,
        "Ab" to R.drawable.et_note_ab_active,
        "A" to R.drawable.et_note_a_active,
        "A#" to R.drawable.et_note_as_active,
        "Bb" to R.drawable.et_note_bb_active,
        "B" to R.drawable.et_note_b_active,
    )
}
