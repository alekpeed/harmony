package com.harmonygates.campaign

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.data.content.ContentRepository
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.music.campaign.CampaignEvaluator
import com.harmonygates.core.music.campaign.CampaignState
import com.harmonygates.core.music.campaign.Curriculum
import com.harmonygates.core.music.campaign.CurriculumRegion
import com.harmonygates.core.music.campaign.GateProgress
import com.harmonygates.core.music.campaign.RegionId
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

/** One region, with its gates resolved against what the player has done. */
data class RegionProgress(
    val region: CurriculumRegion,
    val gates: List<GateProgress>,
    val isVisible: Boolean,
) {
    val id: RegionId get() = region.id

    val completedCount: Int get() = gates.count { it.status.name == "COMPLETE" }
}

/** What the campaign map draws. */
data class CampaignUiState(
    val regions: List<RegionProgress> = emptyList(),
    val nextGate: GateProgress? = null,
    val unlockedCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * The campaign map.
 *
 * Nothing here decides whether a gate is open. The map is
 * `CampaignEvaluator.evaluate(curriculum, mastery)` — a pure function of authored content and
 * stored evidence — so the screen cannot drift from the rules, and the same call in a test
 * produces the same map without a database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CampaignViewModel(
    application: Application,
    private val progress: ProgressRepository = HarmonyGraph.progress(application),
    private val content: ContentRepository = HarmonyGraph.content(application),
) : AndroidViewModel(application) {

    private val evaluator = CampaignEvaluator()
    private val curriculum = MutableStateFlow<Curriculum?>(null)
    private val failure = MutableStateFlow<String?>(null)

    val state: StateFlow<CampaignUiState> = curriculum
        .flatMapLatest { loaded ->
            if (loaded == null) return@flatMapLatest flowOf(CampaignUiState(loading = failure.value == null))
            val profile = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
            combine(
                progress.observeAllMastery(profile),
                progress.observeGateCompletions(profile),
                failure,
            ) { mastery, completions, error ->
                uiStateFor(loaded, evaluator.evaluate(loaded, mastery, completions), error)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CampaignUiState())

    init {
        viewModelScope.launch {
            runCatching { content.curriculum() }
                .onSuccess { curriculum.value = it }
                // A content pack that fails to load is a build mistake that reached a device.
                // Saying so beats an empty map that looks like lost progress.
                .onFailure { failure.value = it.message ?: "The curriculum could not be loaded" }
        }
    }

    private fun uiStateFor(
        curriculum: Curriculum,
        campaign: CampaignState,
        error: String?,
    ): CampaignUiState = CampaignUiState(
        regions = curriculum.regions.map { region ->
            RegionProgress(
                region = region,
                gates = region.gates.mapNotNull { gate -> campaign.gate(gate.id) },
                isVisible = region.id in campaign.visibleRegions,
            )
        },
        nextGate = campaign.nextGate,
        unlockedCount = campaign.unlocked.size,
        loading = false,
        error = error,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
