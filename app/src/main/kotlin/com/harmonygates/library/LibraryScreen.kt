package com.harmonygates.library

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FilterChip
import com.harmonygates.core.designsystem.component.HarmonyChordSymbol
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.progression.DefaultProgressionGenerator
import com.harmonygates.core.music.progression.ProgressionTemplate
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.data.HarmonyGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryChord(val symbol: String, val tones: String)

data class LibraryProgression(val title: String, val chords: String)

data class LibraryUiState(
    val root: String = "C",
    val chords: List<LibraryChord> = emptyList(),
    val progressions: List<LibraryProgression> = emptyList(),
) {
    val roots: List<String> = ROOTS

    companion object {
        val ROOTS: List<String> = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    }
}

/**
 * The vocabulary, to look at rather than be tested on.
 *
 * Everything here is already data the app loads at startup — thirty chord formulas in
 * `core:music` and the authored progressions in the content pack — so this is a reader, not a new
 * source of truth. A chord the engine cannot spell in the chosen root is left out rather than
 * shown wrong, which is the same rule the exercise generators follow.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val content = HarmonyGraph.content(application)
    private val realizer = DefaultChordRealizer()
    private val generator = DefaultProgressionGenerator()

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        chooseRoot("C")
    }

    fun chooseRoot(root: String) {
        val spelled = SpelledPitchClass.parseOrNull(root) ?: return
        val chords = ChordFormulas.all.mapNotNull { formula ->
            val spec = ChordSpec(spelled, formula.id)
            when (realizer.trySpell(spec)) {
                is SpellingResult.Spelled -> LibraryChord(
                    symbol = spec.symbol,
                    tones = realizer.chordTones(spec).joinToString(" "),
                )
                is SpellingResult.Overflow -> null
            }
        }
        _state.value = _state.value.copy(root = root, chords = chords)

        viewModelScope.launch {
            val templates = runCatching { content.allProgressions() }.getOrNull().orEmpty()
            _state.value = _state.value.copy(
                progressions = templates.values.mapNotNull { it.describedIn(spelled) },
            )
        }
    }

    private fun ProgressionTemplate.describedIn(root: SpelledPitchClass): LibraryProgression? =
        when (val placed = generator.generate(this, KeyContext(root), style = DEFAULT_STYLE)) {
            is SpellingResult.Spelled -> LibraryProgression(
                title = title,
                chords = placed.value.events.joinToString("  ") { it.displaySymbol },
            )
            is SpellingResult.Overflow -> null
        }

    private companion object {
        val DEFAULT_STYLE = com.harmonygates.core.music.progression.VoicingStyle.ANY_VOICING
    }
}

@Composable
fun LibraryRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state, viewModel::chooseRoot, onExit, modifier)
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onChooseRoot: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            Box(Modifier.weight(1f))
            SecondaryButton(label = "Leave", onClick = onExit)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            state.roots.forEach { root ->
                FilterChip(
                    label = root,
                    selected = root == state.root,
                    onToggle = { onChooseRoot(root) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        ) {
            HarmonyPanel(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = "Chords on ${state.root}",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
                    ) {
                        items(state.chords) { chord ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HarmonyChordSymbol(symbol = chord.symbol)
                                Text(
                                    text = chord.tones,
                                    color = HarmonyTheme.colors.onSurfaceMuted,
                                    fontSize = HarmonyTheme.typography.body,
                                )
                            }
                        }
                    }
                }
            }

            HarmonyPanel(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = "Progressions in ${state.root}",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    ) {
                        items(state.progressions) { progression ->
                            Column {
                                Text(
                                    text = progression.title,
                                    color = HarmonyTheme.colors.textPrimary,
                                    fontSize = HarmonyTheme.typography.body,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = progression.chords,
                                    color = HarmonyTheme.colors.onSurfaceMuted,
                                    fontSize = HarmonyTheme.typography.body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun LibraryPreview() {
    HarmonyTheme { LibraryScreen(LibraryUiState(), {}, {}) }
}
