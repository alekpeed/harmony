package com.harmonygates.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.data.backup.BackupFormatException
import com.harmonygates.core.data.backup.ImportResult
import com.harmonygates.core.data.backup.retargetedTo
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.designsystem.component.HarmonyDialog
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.data.HarmonyGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** What the settings screen shows about the last export or import. */
data class SettingsUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Progress export and import, from Settings.
 *
 * `ProgressBackupService` (phase 14) and its Room store exist and are tested; this view model is
 * what finally calls them from a screen. An import is always retargeted at this install's own
 * profile ([ProgressRepository.currentProfile]) before it is applied — see
 * `ProgressBackup.retargetedTo` for why that matters.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val progress: ProgressRepository = HarmonyGraph.progress(application)
    private val backup = HarmonyGraph.progressBackup(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _state.value = SettingsUiState(busy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val profile = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
                    val json = backup.exportToJson(profile, System.currentTimeMillis())
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    } ?: throw IOException("Could not open the chosen file for writing")
                }
            }.onSuccess {
                _state.value = SettingsUiState(message = "Progress exported.")
            }.onFailure { error ->
                _state.value = SettingsUiState(error = error.readableMessage())
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _state.value = SettingsUiState(busy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: throw IOException("Could not open the chosen file for reading")
                    val local = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
                    backup.import(backup.parse(text).retargetedTo(local))
                }
            }.onSuccess { result ->
                _state.value = SettingsUiState(message = result.describe())
            }.onFailure { error ->
                _state.value = SettingsUiState(error = error.readableMessage())
            }
        }
    }

    fun dismissMessage() {
        _state.value = SettingsUiState()
    }

    private fun Throwable.readableMessage(): String = when (this) {
        is BackupFormatException -> message ?: "That file is not a Harmony Gates backup."
        else -> "Something went wrong: ${message ?: this::class.simpleName}"
    }

    private fun ImportResult.describe(): String =
        "Imported $sessionsRestored sessions, $attemptsRestored attempts and " +
            "$gateCompletionsRestored gate completions. $skillsRebuilt skills rebuilt." +
            if (orphanedAttempts > 0) " $orphanedAttempts attempts skipped (no matching session)." else ""
}

@Composable
fun SettingsRoute(
    onOpenMidiSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    SettingsScreen(
        state = state,
        onOpenMidiSetup = onOpenMidiSetup,
        onExport = { exportLauncher.launch(EXPORT_FILE_NAME) },
        onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenMidiSetup: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(HarmonyTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        ) {
            Text(
                text = "Settings",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )

            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    Text(
                        text = "MIDI",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    SecondaryButton(
                        label = "MIDI setup and diagnostics",
                        onClick = onOpenMidiSetup,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    Text(
                        text = "Progress data",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    Text(
                        text = "Every session, attempt and gate completion, as a JSON file you keep " +
                            "yourself. Skill mastery is not in it — importing rebuilds it from the " +
                            "attempts instead.",
                        color = HarmonyTheme.colors.textSecondary,
                        fontSize = HarmonyTheme.typography.body,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                        PrimaryButton(label = "Export", onClick = onExport, enabled = !state.busy)
                        SecondaryButton(label = "Import", onClick = onImport, enabled = !state.busy)
                    }
                }
            }
        }

        state.message?.let { message ->
            HarmonyDialog(
                title = "Done",
                message = message,
                confirmLabel = "OK",
                onConfirm = onDismissMessage,
            )
        }
        state.error?.let { error ->
            HarmonyDialog(
                title = "Could not do that",
                message = error,
                confirmLabel = "OK",
                onConfirm = onDismissMessage,
            )
        }
    }
}

private const val EXPORT_FILE_NAME = "harmony-gates-progress.json"
private val IMPORT_MIME_TYPES = arrayOf("application/json")

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun SettingsPreview() {
    HarmonyTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onOpenMidiSetup = {},
            onExport = {},
            onImport = {},
            onDismissMessage = {},
        )
    }
}
