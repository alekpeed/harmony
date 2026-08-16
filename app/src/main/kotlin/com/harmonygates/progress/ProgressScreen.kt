package com.harmonygates.progress

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.data.progress.StoredAttempt
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.data.HarmonyGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** What the progress screen draws. */
data class ProgressUiState(
    val skills: List<SkillMastery> = emptyList(),
    val attempts: List<StoredAttempt> = emptyList(),
    val dueForReview: Int = 0,
    val loading: Boolean = true,
) {
    val totalAttempts: Int get() = skills.sumOf { it.attempts }

    val skillsStarted: Int get() = skills.count { it.attempts > 0 }
}

/**
 * Progress and history.
 *
 * Phase 5's second acceptance criterion is that "historical attempts are inspectable", which is
 * the whole reason this screen exists: the attempt table is not much use if nothing ever reads
 * it back. What it shows comes straight out of storage, so opening it after a restart is the
 * demonstration that the first criterion holds too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    application: Application,
    private val progress: ProgressRepository = HarmonyGraph.progress(application),
) : AndroidViewModel(application) {

    private val profile = MutableStateFlow<com.harmonygates.core.data.progress.ProfileId?>(null)
    private val due = MutableStateFlow(0)

    val state: StateFlow<ProgressUiState> = profile
        .flatMapLatest { id ->
            if (id == null) return@flatMapLatest flowOf(ProgressUiState())
            combine(
                progress.observeAllMastery(id),
                progress.observeRecentAttempts(id),
                due,
            ) { mastery, attempts, dueCount ->
                ProgressUiState(
                    // Weakest first: the screen's job is to say what to practise, not to
                    // congratulate. A player scanning it should land on the useful row.
                    skills = mastery.values.sortedWith(compareBy({ it.estimate }, { it.skillId.value })),
                    attempts = attempts,
                    dueForReview = dueCount,
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgressUiState())

    init {
        viewModelScope.launch {
            val id = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
            profile.value = id
            due.value = progress.dueForReview(id, Instant.now()).size
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ProgressRoute(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(state = state, modifier = modifier)
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Text(
            text = "Progress",
            color = HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.heading,
        )

        when {
            state.loading -> Text(
                text = "Reading your history.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )

            state.skills.isEmpty() -> Text(
                text = "Nothing recorded yet. Play a gate and this fills up — and stays filled " +
                    "after you close the app.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )

            else -> ProgressBody(state)
        }
    }
}

@Composable
private fun ProgressBody(state: ProgressUiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium)) {
        item(key = "summary") {
            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
                    Text(
                        text = "${state.totalAttempts} attempts across ${state.skillsStarted} skills",
                        color = HarmonyTheme.colors.onSurface,
                        fontSize = HarmonyTheme.typography.body,
                    )
                    if (state.dueForReview > 0) {
                        Text(
                            text = "${state.dueForReview} due for review",
                            color = HarmonyTheme.colors.onSurfaceMuted,
                            fontSize = HarmonyTheme.typography.caption,
                        )
                    }
                }
            }
        }

        item(key = "skills-header") { SectionLabel("Skills") }
        items(state.skills, key = { it.skillId.value }) { SkillRow(it) }

        item(key = "attempts-header") { SectionLabel("Recent attempts") }
        items(state.attempts, key = { it.id }) { AttemptRow(it) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = HarmonyTheme.colors.onSurfaceMuted,
        fontSize = HarmonyTheme.typography.caption,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = HarmonyTheme.spacing.small),
    )
}

@Composable
private fun SkillRow(mastery: SkillMastery) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mastery.skillId.value,
                    color = HarmonyTheme.colors.onSurface,
                    fontSize = HarmonyTheme.typography.body,
                )
                Text(
                    text = "${(mastery.estimate * PERCENT).toInt()}%",
                    color = HarmonyTheme.colors.onSurface,
                    fontSize = HarmonyTheme.typography.body,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { mastery.estimate.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${mastery.successfulAttempts} of ${mastery.attempts} correct · " +
                    "${mastery.rootsCovered.size} roots" +
                    (mastery.medianResponseMillis?.let { " · ${it} ms median" } ?: ""),
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            // The commonest mistake, named. 06 §10: "specific recurring errors" is one of the
            // things the score display is supposed to emphasise.
            mastery.recurringErrors.firstOrNull()?.let { (errorClass, count) ->
                Text(
                    text = "Most often: ${errorClass.name.lowercase().replace('_', ' ')} ($count)",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
private fun AttemptRow(attempt: StoredAttempt) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attempt.chordSymbol,
                    color = HarmonyTheme.colors.onSurface,
                    fontSize = HarmonyTheme.typography.body,
                    fontWeight = FontWeight.SemiBold,
                )
                HarmonyStatusChip(
                    label = attempt.verdict.lowercase().replace('_', ' '),
                    tone = toneFor(attempt.verdict),
                )
            }
            Text(
                text = "asked ${attempt.expected}",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            Text(
                text = "played ${attempt.performed}",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            attempt.errors.take(MAX_ERRORS).forEach { error ->
                Text(
                    text = error,
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
            Text(
                text = ago(attempt.completedAt),
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
        }
    }
}

private fun toneFor(verdict: String): FeedbackTone = when (verdict) {
    "CORRECT", "CORRECT_WITH_ACCEPTED_VARIATION" -> FeedbackTone.CORRECT
    "PARTIAL" -> FeedbackTone.PARTIAL
    "INCORRECT" -> FeedbackTone.INCORRECT
    else -> FeedbackTone.NEUTRAL
}

/** Relative time, in the coarsest unit that is still true. */
private fun ago(instant: Instant): String {
    val elapsed = Duration.between(instant, Instant.now())
    return when {
        elapsed.toMinutes() < 1 -> "just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()} min ago"
        elapsed.toDays() < 1 -> "${elapsed.toHours()} h ago"
        else -> "${elapsed.toDays()} d ago"
    }
}

private const val PERCENT = 100
private const val MAX_ERRORS = 2
