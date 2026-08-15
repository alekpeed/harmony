package com.harmonygates.harness

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the harness screen's state.
 *
 * The view model does no musical reasoning of its own — it forwards to [HarmonyLabAnalyzer],
 * which forwards to `core:music`. Analysis is cheap and synchronous here (a parse and one
 * voicing search, both well inside the 50 ms budget in 14_TESTING_AND_QUALITY.md §8), so it
 * runs inline; when generation grows teeth in Phase 6 it moves to the default dispatcher.
 */
class HarmonyLabViewModel(
    private val analyzer: HarmonyLabAnalyzer = HarmonyLabAnalyzer(),
) : ViewModel() {

    private val _state = MutableStateFlow(analyzer.analyze(INITIAL_SYMBOL))
    val state: StateFlow<HarmonyLabState> = _state.asStateFlow()

    fun onIntent(intent: HarmonyLabIntent) {
        when (intent) {
            is HarmonyLabIntent.SymbolChanged -> _state.value = analyzer.analyze(intent.text)
        }
    }

    private companion object {
        const val INITIAL_SYMBOL = "Cmaj7"
    }
}
