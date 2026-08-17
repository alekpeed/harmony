package com.harmonygates.voiceleading

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.R
import com.harmonygates.artwork.DesignHit
import com.harmonygates.artwork.DesignImage
import com.harmonygates.artwork.DesignRect
import com.harmonygates.artwork.DesignScope
import com.harmonygates.artwork.DesignSurface
import com.harmonygates.artwork.LabelOverflow
import com.harmonygates.artwork.Plate
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.exercise.describe
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.harmonygates.voiceleadingmenu.VoiceLeadingDifficulty
import com.harmonygates.voiceleadingmenu.VoiceLeadingExercise
import com.harmonygates.voiceleadingmenu.VoiceLeadingMenu
import com.harmonygates.voiceleadingmenu.VoiceLeadingMenuState
import com.harmonygates.voiceleadingmenu.VoiceLeadingMotion
import com.harmonygates.voiceleadingmenu.VoiceLeadingRange

/**
 * Voice Leading, wearing the approved control library.
 *
 * The pack's rule — "visible interactive elements must be real Compose components, not invisible
 * hitboxes over screenshot controls" — forbids a flat picture of a whole UI with touch targets
 * floating over it. It does not mean the controls are drawn from scratch. The pack ships each
 * control as its own asset, cut to the sizes in `component-sizing.json`, precisely so that a real
 * component can wear the supplied artwork: this screen's buttons, chips, panels, note tokens,
 * meters and status pills are all `Image`s of those files with live Compose text over them and
 * their own click handling, laid out in the regions the pack's templates name.
 *
 * Everything that changes is Compose: chord names, progress, accuracy, MIDI wording, verdicts,
 * the per-voice motion. Nothing about the exercise is in an image.
 */
@Composable
fun VoiceLeadingRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceLeadingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VoiceLeadingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onExit = onExit,
        modifier = modifier,
    )
}

@Composable
fun VoiceLeadingScreen(
    state: VoiceLeadingUiState,
    onIntent: (VoiceLeadingIntent) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The menu owns its own expand/collapse and selections, exactly as supplied. It is the setup
    // surface: START EXERCISE hands the whole configuration to the engine and collapses, which is
    // what reveals the practice surface beneath. Saved across configuration change so rotating the
    // tablet does not reset a chosen setup.
    var menuState by rememberSaveable(stateSaver = VoiceLeadingMenuSaver) {
        mutableStateOf(VoiceLeadingMenuState())
    }

    Box(modifier = modifier.fillMaxSize()) {
        DesignSurface {
            // Environment only. Nothing about the exercise lives in this image.
            Plate(R.drawable.voice_leading_background)

            NavBar(state, onExit)

            // Only the practice surface is drawn behind the menu now; the menu is the setup UI,
            // and drawing the old panel of chips underneath it would be two ways to say the same
            // thing with one of them hidden.
            if (state.mode == VoiceLeadingMode.PRACTICE) {
                PracticeSurface(state, onIntent)
            }
        }

        VoiceLeadingMenu(
            state = menuState,
            onStateChange = { menuState = it },
            onStartExercise = { onIntent(VoiceLeadingIntent.StartFromMenu(it)) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        )
    }
}

// --- Navigation ----------------------------------------------------------------------------------

@Composable
private fun DesignScope.NavBar(state: VoiceLeadingUiState, onExit: () -> Unit) {
    val nav = VoiceLeadingLayout.Nav
    DesignImage(VoiceLeadingLayout.TopNavBar, nav)

    Label(
        text = "Voice Leading",
        rect = DesignRect(nav.x + 40f, nav.y, 420f, nav.height),
        size = 34f,
        colour = HarmonyTheme.colors.textPrimary,
        weight = FontWeight.SemiBold,
        align = TextAlign.Start,
    )

    val home = DesignRect.centred(
        centreX = nav.x + nav.width - 60f,
        centreY = nav.centreY,
        width = VoiceLeadingLayout.ICON_BUTTON,
        height = VoiceLeadingLayout.ICON_BUTTON,
    )
    DesignImage(VoiceLeadingLayout.IconButton, home)
    DesignImage(VoiceLeadingLayout.IconHome, home.inflated(-14f))
    DesignHit(home, "Leave voice leading", onExit)
}

// --- Practice, on the practice template ------------------------------------------------------------

@Composable
private fun DesignScope.PracticeSurface(
    state: VoiceLeadingUiState,
    onIntent: (VoiceLeadingIntent) -> Unit,
) {
    val stage = VoiceLeadingLayout.ExerciseVisualization
    DesignImage(VoiceLeadingLayout.PanelExerciseLarge, stage)

    if (state.phase == VoiceLeadingPhase.COMPLETE) {
        Label(
            text = "Run finished",
            rect = DesignRect(stage.x + 40f, stage.y + 200f, stage.width - 80f, 60f),
            size = 40f,
            colour = HarmonyTheme.colors.textPrimary,
            weight = FontWeight.SemiBold,
        )
        Label(
            text = "${state.correct} of ${state.attempted} correct. Runs are not recorded yet, " +
                "so nothing here changes your mastery.",
            rect = DesignRect(stage.x + 40f, stage.y + 280f, stage.width - 80f, 90f),
            size = 20f,
            colour = HarmonyTheme.colors.onSurfaceMuted,
            lines = 3,
        )
    } else {
        ChordHeading(state, stage)
        VoicingTokens(state, stage)
    }

    ControlsColumn(state, onIntent)
    FeedbackRegion(state)
    SessionStatusRegion(state)
}

@Composable
private fun DesignScope.ChordHeading(state: VoiceLeadingUiState, stage: DesignRect) {
    Label(
        text = "From",
        rect = DesignRect(stage.x + 48f, stage.y + 26f, 200f, 30f),
        size = 17f,
        colour = HarmonyTheme.colors.onSurfaceMuted,
        align = TextAlign.Start,
    )
    Label(
        text = state.sourceSymbol.orEmpty(),
        rect = DesignRect(stage.x + 48f, stage.y + 50f, 260f, 52f),
        size = 40f,
        colour = HarmonyTheme.colors.textSecondary,
        weight = FontWeight.SemiBold,
        align = TextAlign.Start,
    )
    DesignImage(
        VoiceLeadingLayout.MotionArrowRight,
        DesignRect(stage.x + 320f, stage.y + 56f, 72f, 44f),
    )
    Label(
        text = "Play",
        rect = DesignRect(stage.x + 410f, stage.y + 26f, 200f, 30f),
        size = 17f,
        colour = HarmonyTheme.colors.onSurfaceMuted,
        align = TextAlign.Start,
    )
    Label(
        text = state.targetSymbol.orEmpty(),
        rect = DesignRect(stage.x + 410f, stage.y + 50f, 300f, 52f),
        size = 40f,
        colour = HarmonyTheme.colors.textPrimary,
        weight = FontWeight.SemiBold,
        align = TextAlign.Start,
    )
    state.targetFunction?.let { function ->
        DesignImage(
            VoiceLeadingLayout.SectionTitlePlate,
            DesignRect(stage.x + stage.width - 260f, stage.y + 44f, 210f, 60f),
        )
        Label(
            text = function,
            rect = DesignRect(stage.x + stage.width - 260f, stage.y + 44f, 210f, 60f),
            size = 26f,
            colour = HarmonyTheme.colors.accentPrimary,
            weight = FontWeight.SemiBold,
        )
    }
}

/**
 * The two voicings as tokens, with a motion mark between them.
 *
 * Top row is the chord being moved from; bottom row is what the hand actually played, once it has.
 * The arrow between a pair is the pack's own motion primitive, chosen by what the voice did: a
 * common tone gets the common-tone line, a step gets the step line, anything wider gets the leap.
 */
@Composable
private fun DesignScope.VoicingTokens(state: VoiceLeadingUiState, stage: DesignRect) {
    state.sourceNotes.take(MAX_TOKENS).forEachIndexed { index, note ->
        val rect = VoiceLeadingLayout.noteToken(stage, index, row = 0)
        DesignImage(VoiceLeadingLayout.TokenNeutral, rect)
        Label(
            text = noteName(note),
            rect = rect,
            size = 20f,
            colour = HarmonyTheme.colors.textSecondary,
        )
    }

    state.motions.take(MAX_TOKENS).forEachIndexed { index, motion ->
        DesignImage(
            resourceId = when {
                motion.isCommonTone -> VoiceLeadingLayout.VoiceLineCommonTone
                motion.isLeap -> VoiceLeadingLayout.VoiceLineLeap
                else -> VoiceLeadingLayout.VoiceLineStep
            },
            rect = VoiceLeadingLayout.motionArrow(stage, index),
        )
        val rect = VoiceLeadingLayout.noteToken(stage, index, row = 1)
        DesignImage(
            resourceId = if (state.result?.verdict?.isCorrect == true) {
                VoiceLeadingLayout.TokenCorrect
            } else {
                VoiceLeadingLayout.TokenIncorrect
            },
            rect = rect,
        )
        Label(
            text = noteName(motion.toNote),
            rect = rect,
            size = 20f,
            colour = HarmonyTheme.colors.textPrimary,
        )
    }

    if (state.motions.isEmpty()) {
        state.soundingNotes.take(MAX_TOKENS).forEachIndexed { index, note ->
            val rect = VoiceLeadingLayout.noteToken(stage, index, row = 1)
            DesignImage(VoiceLeadingLayout.TokenActive, rect)
            Label(
                text = noteName(note),
                rect = rect,
                size = 20f,
                colour = HarmonyTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun DesignScope.ControlsColumn(
    state: VoiceLeadingUiState,
    onIntent: (VoiceLeadingIntent) -> Unit,
) {
    val controls = VoiceLeadingLayout.Controls
    DesignImage(VoiceLeadingLayout.PanelControl, controls)

    val centreX = controls.centreX
    IconControl(
        rect = DesignRect.centred(centreX, controls.y + 110f, 96f, 96f),
        icon = VoiceLeadingLayout.IconReplay,
        description = "Hear the chord you are moving from",
        onClick = { onIntent(VoiceLeadingIntent.PlaySource) },
    )
    Label(
        text = "Hear it",
        rect = DesignRect(controls.x, controls.y + 168f, controls.width, 32f),
        size = 17f,
        colour = HarmonyTheme.colors.onSurfaceMuted,
    )

    IconControl(
        rect = DesignRect.centred(centreX, controls.y + 268f, 96f, 96f),
        icon = VoiceLeadingLayout.IconNext,
        description = if (state.result != null) "Next move" else "Skip this move",
        enabled = state.phase != VoiceLeadingPhase.COMPLETE,
        active = state.result != null,
        onClick = { onIntent(VoiceLeadingIntent.Next) },
    )
    Label(
        text = if (state.result != null) "Next" else "Skip",
        rect = DesignRect(controls.x, controls.y + 326f, controls.width, 32f),
        size = 17f,
        colour = HarmonyTheme.colors.onSurfaceMuted,
    )

    SecondaryButton(
        rect = DesignRect.centred(
            centreX,
            controls.y + controls.height - 70f,
            VoiceLeadingLayout.SECONDARY_BUTTON_WIDTH,
            VoiceLeadingLayout.SECONDARY_BUTTON_HEIGHT,
        ),
        label = "Back to setup",
        description = "End this run and return to setup",
        onClick = { onIntent(VoiceLeadingIntent.ExitToSetup) },
    )
}

@Composable
private fun DesignScope.FeedbackRegion(state: VoiceLeadingUiState) {
    val region = VoiceLeadingLayout.Feedback
    val result = state.result
    if (result == null) {
        DesignImage(VoiceLeadingLayout.FeedbackNeutral, region)
        Label(
            text = when (state.phase) {
                VoiceLeadingPhase.LISTENING -> "Play the chord."
                VoiceLeadingPhase.EVALUATING -> "Listening…"
                else -> ""
            },
            rect = region,
            size = 24f,
            colour = HarmonyTheme.colors.onSurfaceMuted,
        )
        return
    }

    val correct = result.verdict.isCorrect
    DesignImage(
        resourceId = when (result.explanation.headline) {
            FeedbackModel.Headline.CORRECT, FeedbackModel.Headline.CORRECT_VARIATION ->
                VoiceLeadingLayout.FeedbackCorrect
            FeedbackModel.Headline.ALMOST -> VoiceLeadingLayout.FeedbackPartial
            FeedbackModel.Headline.NOT_YET -> VoiceLeadingLayout.FeedbackIncorrect
            else -> VoiceLeadingLayout.FeedbackNeutral
        },
        rect = region,
    )

    Label(
        text = headlineText(result.explanation.headline) +
            if (correct && state.totalMotionSemitones != null) {
                if (state.isSmoothest) " · smoothest route" else " · a smoother route exists"
            } else {
                ""
            },
        rect = DesignRect(region.x + 36f, region.y + 22f, region.width - 72f, 40f),
        size = 24f,
        colour = HarmonyTheme.colors.textPrimary,
        weight = FontWeight.SemiBold,
        align = TextAlign.Start,
    )

    val detail = if (state.totalMotionSemitones != null) {
        buildString {
            append("Moved ${state.totalMotionSemitones} semitones")
            state.bestTotalMotionSemitones?.let { append(", smoothest is $it") }
            append(" · largest leap ${state.maxLeapSemitones ?: 0}")
            append(" · ${state.commonToneCount ?: 0} common tones")
        }
    } else {
        result.explanation.errors.firstOrNull()?.let { describe(it) }.orEmpty()
    }
    Label(
        text = detail,
        rect = DesignRect(region.x + 36f, region.y + 70f, region.width - 72f, 66f),
        size = 18f,
        colour = HarmonyTheme.colors.onSurfaceMuted,
        align = TextAlign.Start,
        lines = 2,
    )
}

@Composable
private fun DesignScope.SessionStatusRegion(state: VoiceLeadingUiState) {
    val region = VoiceLeadingLayout.SessionStatus
    DesignImage(VoiceLeadingLayout.PanelStats, region)

    Label(
        text = "Move ${state.stepNumber} of ${state.stepCount}",
        rect = DesignRect(region.x + 30f, region.y + 20f, region.width - 60f, 34f),
        size = 19f,
        colour = HarmonyTheme.colors.textPrimary,
        align = TextAlign.Start,
    )

    val bar = DesignRect(
        x = region.x + 30f,
        y = region.y + 62f,
        width = region.width - 60f,
        height = VoiceLeadingLayout.PROGRESS_BAR_HEIGHT,
    )
    DesignImage(VoiceLeadingLayout.ProgressEmpty, bar)
    if (state.progress > 0f) {
        // The full bar, revealed to the fraction completed. One asset, clipped by width, rather
        // than the pack's four fixed 25/50/75/100 states, which cannot express three-of-fourteen.
        DesignImage(
            VoiceLeadingLayout.ProgressFull,
            DesignRect(bar.x, bar.y, bar.width * state.progress.coerceIn(0f, 1f), bar.height),
        )
    }

    state.accuracy?.let { accuracy ->
        Label(
            text = "Accuracy ${(accuracy * 100).toInt()}%",
            rect = DesignRect(region.x + 30f, region.y + 104f, region.width - 60f, 34f),
            size = 18f,
            colour = HarmonyTheme.colors.onSurfaceMuted,
            align = TextAlign.Start,
        )
    }
}

// --- Components ------------------------------------------------------------------------------------

@Composable
private fun DesignScope.MidiPill(state: VoiceLeadingUiState, rect: DesignRect) {
    DesignImage(
        resourceId = if (state.midiConnected) {
            VoiceLeadingLayout.MidiConnected
        } else {
            VoiceLeadingLayout.MidiDisconnected
        },
        rect = rect,
    )
    Label(
        text = state.midiStatus,
        rect = DesignRect(rect.x + 56f, rect.y, rect.width - 70f, rect.height),
        size = 17f,
        colour = HarmonyTheme.colors.textPrimary,
        align = TextAlign.Start,
    )
}

@Composable
private fun DesignScope.Chip(
    rect: DesignRect,
    label: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    DesignImage(
        resourceId = if (selected) VoiceLeadingLayout.ChipSelected else VoiceLeadingLayout.ChipDefault,
        rect = rect,
    )
    Label(
        text = label,
        rect = rect,
        size = 17f,
        colour = if (selected) HarmonyTheme.colors.textPrimary else HarmonyTheme.colors.textSecondary,
        weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
    DesignHit(rect, description + if (selected) ", selected" else "", onClick)
}

@Composable
private fun DesignScope.PrimaryButton(
    rect: DesignRect,
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DesignImage(
        resourceId = if (enabled) {
            VoiceLeadingLayout.ButtonPrimary
        } else {
            VoiceLeadingLayout.ButtonPrimaryDisabled
        },
        rect = rect,
    )
    Label(
        text = label,
        rect = rect,
        size = 26f,
        colour = HarmonyTheme.colors.onAccent,
        weight = FontWeight.SemiBold,
    )
    DesignHit(rect, description, onClick, enabled)
}

@Composable
private fun DesignScope.SecondaryButton(
    rect: DesignRect,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    DesignImage(VoiceLeadingLayout.ButtonSecondary, rect)
    Label(
        text = label,
        rect = rect,
        size = 20f,
        colour = HarmonyTheme.colors.textPrimary,
    )
    DesignHit(rect, description, onClick)
}

@Composable
private fun DesignScope.IconControl(
    rect: DesignRect,
    icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    DesignImage(
        resourceId = when {
            !enabled -> VoiceLeadingLayout.IconButtonDisabled
            active -> VoiceLeadingLayout.IconButtonActive
            else -> VoiceLeadingLayout.IconButton
        },
        rect = rect,
    )
    DesignImage(icon, rect.inflated(-22f))
    DesignHit(rect, description, onClick, enabled)
}

@Composable
private fun DesignScope.Label(
    text: String,
    rect: DesignRect,
    size: Float,
    colour: Color,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Center,
    lines: Int = 1,
) {
    if (text.isEmpty()) return
    Box(modifier = Modifier.designRect(rect), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = colour,
            fontSize = textSize(size),
            fontWeight = weight,
            textAlign = align,
            maxLines = lines,
            overflow = LabelOverflow,
        )
    }
}

private fun headlineText(headline: FeedbackModel.Headline): String = when (headline) {
    FeedbackModel.Headline.CORRECT -> "Correct"
    FeedbackModel.Headline.CORRECT_VARIATION -> "Correct"
    FeedbackModel.Headline.ALMOST -> "Almost"
    FeedbackModel.Headline.NOT_YET -> "Not yet"
    FeedbackModel.Headline.NOTHING_PLAYED -> "Nothing played"
    FeedbackModel.Headline.DEVICE_LOST -> "Keyboard disconnected"
}

/** Sounding pitch as a name. The engine spells chords; this is only for a token's caption. */
private fun noteName(midi: Int): String = NOTE_NAMES[midi.mod(NOTE_NAMES.size)]

private val NOTE_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

private const val MAX_TOKENS = 6

/**
 * Keeps the menu's selections across rotation.
 *
 * `rememberSaveable` needs a Saver for a type it does not know how to bundle, and losing a
 * half-configured setup because the tablet moved would be its own bug.
 */
private val VoiceLeadingMenuSaver: Saver<VoiceLeadingMenuState, Any> = listSaver(
    save = {
        listOf(
            it.exercise.name, it.key, it.motion.name, it.range.name, it.difficulty.name,
            it.tempoBpm, it.repetitions, it.metronome, it.showHints, it.collapsed,
        )
    },
    restore = {
        VoiceLeadingMenuState(
            exercise = VoiceLeadingExercise.valueOf(it[0] as String),
            key = it[1] as String,
            motion = VoiceLeadingMotion.valueOf(it[2] as String),
            range = VoiceLeadingRange.valueOf(it[3] as String),
            difficulty = VoiceLeadingDifficulty.valueOf(it[4] as String),
            tempoBpm = it[5] as Int,
            repetitions = it[6] as Int,
            metronome = it[7] as Boolean,
            showHints = it[8] as Boolean,
            collapsed = it[9] as Boolean,
        )
    },
)

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun VoiceLeadingPreview() {
    HarmonyTheme {
        VoiceLeadingScreen(
            state = VoiceLeadingUiState(
                mode = VoiceLeadingMode.SETUP,
                midiStatus = "Studio keyboard",
                midiConnected = true,
            ),
            onIntent = {},
            onExit = {},
        )
    }
}
