package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.model.ModelType
import ovh.devcraft.vogonpoet.domain.model.RemoteModel
import ovh.devcraft.vogonpoet.domain.model.RemoteModelSource
import ovh.devcraft.vogonpoet.infrastructure.DownloadState
import ovh.devcraft.vogonpoet.infrastructure.GitHubModelRepositoryClient
import ovh.devcraft.vogonpoet.infrastructure.ModelDownloadService
import ovh.devcraft.vogonpoet.infrastructure.SettingsRepository
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBlueDark
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGray
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGreenDark
import ovh.devcraft.vogonpoet.ui.theme.GruvboxYellowDark
import java.io.File

/**
 * Window for browsing and downloading models from remote repositories.
 *
 * @param onCloseRequest Callback when the window is closed
 */
@Composable
fun ModelRepositoryBrowserWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(width = 700.dp, height = 600.dp)
    var currentScreen by remember { mutableStateOf<BrowserScreen>(BrowserScreen.SourceSelection) }
    var selectedModels by remember { mutableStateOf<List<RemoteModel>>(emptyList()) }
    var availableModels by remember { mutableStateOf<List<RemoteModel>>(emptyList()) }
    var downloadStates by remember { mutableStateOf<Map<RemoteModel, DownloadState>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val githubClient = remember { GitHubModelRepositoryClient() }
    val modelsDir = remember { SettingsRepository.openwakewordModelsDir }
    val downloadService = remember { ModelDownloadService(modelsDir) }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Model Repository Browser",
        state = windowState,
    ) {
        MaterialTheme(colorScheme = GruvboxDarkColorScheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = GruvboxBg1,
            ) {
                when (val screen = currentScreen) {
                    is BrowserScreen.SourceSelection -> {
                        SourceSelectionScreen(
                            onSourceSelected = { source ->
                                scope.launch {
                                    currentScreen = BrowserScreen.Loading
                                    try {
                                        val models = githubClient.fetchModels(source, "en")
                                        availableModels = models.sortedBy { it.name }
                                        currentScreen = BrowserScreen.ModelSelection(models)
                                    } catch (e: Exception) {
                                        // Handle error
                                        currentScreen = BrowserScreen.SourceSelection
                                    }
                                }
                            },
                            onCancel = onCloseRequest,
                        )
                    }

                    is BrowserScreen.Loading -> {
                        LoadingScreen()
                    }

                    is BrowserScreen.ModelSelection -> {
                        ModelSelectionScreen(
                            models = screen.models,
                            onDownload = { modelsToDownload ->
                                selectedModels = modelsToDownload
                                downloadStates = modelsToDownload.associateWith { DownloadState(it) }
                                currentScreen = BrowserScreen.Downloading

                                // Start downloads
                                scope.launch {
                                    modelsToDownload.forEach { model ->
                                        if (!downloadService.isModelAlreadyDownloaded(model)) {
                                            downloadService.downloadModel(model).collect { state ->
                                                downloadStates =
                                                    downloadStates.toMutableMap().apply {
                                                        put(model, state)
                                                    }
                                            }
                                        } else {
                                            // Skip already downloaded
                                            downloadStates =
                                                downloadStates.toMutableMap().apply {
                                                    put(
                                                        model,
                                                        DownloadState(
                                                            model = model,
                                                            progress = 100,
                                                            isComplete = true,
                                                            status = "Already downloaded",
                                                        ),
                                                    )
                                                }
                                        }
                                    }
                                }
                            },
                            onBack = {
                                currentScreen = BrowserScreen.SourceSelection
                            },
                        )
                    }

                    is BrowserScreen.Downloading -> {
                        DownloadProgressScreen(
                            downloadStates = downloadStates.values.toList(),
                            onDone = {
                                onCloseRequest()
                            },
                            onBack = {
                                currentScreen = BrowserScreen.ModelSelection(availableModels)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Available screens in the repository browser flow.
 */
private sealed class BrowserScreen {
    object SourceSelection : BrowserScreen()

    object Loading : BrowserScreen()

    data class ModelSelection(
        val models: List<RemoteModel>,
    ) : BrowserScreen()

    object Downloading : BrowserScreen()
}

/**
 * Predefined repository sources.
 */
val OPENWAKEWORD_COMMUNITY_EN =
    RemoteModelSource(
        name = "OpenWakeWord Community - EN",
        url = "https://github.com/fwartner/home-assistant-wakewords-collection",
        type = ModelType.WAKEWORD,
        language = "en",
    )

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = GruvboxGreenDark)
            Text(
                text = "Loading available models...",
                color = GruvboxFg0,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SourceSelectionScreen(
    onSourceSelected: (RemoteModelSource) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Select Model Repository",
            style = MaterialTheme.typography.headlineSmall,
            color = GruvboxFg0,
        )

        Text(
            text = "Choose a repository source to browse available wake word models.",
            style = MaterialTheme.typography.bodyMedium,
            color = GruvboxGray,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // OpenWakeWord Community Source
        Card(
            onClick = { onSourceSelected(OPENWAKEWORD_COMMUNITY_EN) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = GruvboxBlueDark.copy(alpha = 0.2f),
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = OPENWAKEWORD_COMMUNITY_EN.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = GruvboxFg0,
                    )
                    Text(
                        text = "${OPENWAKEWORD_COMMUNITY_EN.language.uppercase()} • Wake Word Models",
                        style = MaterialTheme.typography.bodySmall,
                        color = GruvboxGray,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = GruvboxGray)
            }
        }
    }
}

@Composable
private fun ModelSelectionScreen(
    models: List<RemoteModel>,
    onDownload: (List<RemoteModel>) -> Unit,
    onBack: () -> Unit,
) {
    var selectedModels by remember { mutableStateOf<Set<RemoteModel>>(emptySet()) }
    val allSelected = selectedModels.size == models.size && models.isNotEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Available Models",
            style = MaterialTheme.typography.headlineSmall,
            color = GruvboxFg0,
        )

        Text(
            text = "${models.size} models available from OpenWakeWord Community",
            style = MaterialTheme.typography.bodyMedium,
            color = GruvboxGray,
        )

        // Select All checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { checked ->
                    selectedModels = if (checked) models.toSet() else emptySet()
                },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = GruvboxGreenDark,
                        uncheckedColor = GruvboxGray,
                    ),
            )
            Text(
                text = "Select All",
                color = GruvboxFg0,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Model list
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            colors =
                CardDefaults.cardColors(
                    containerColor = GruvboxFg0.copy(alpha = 0.05f),
                ),
        ) {
            if (models.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No models available",
                        color = GruvboxGray,
                    )
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    models.forEach { model ->
                        val isSelected = model in selectedModels

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedModels =
                                        if (checked) {
                                            selectedModels + model
                                        } else {
                                            selectedModels - model
                                        }
                                },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = GruvboxGreenDark,
                                        uncheckedColor = GruvboxGray,
                                    ),
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    color = GruvboxFg0,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Version ${model.version}",
                                    color = GruvboxGray,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        if (model != models.last()) {
                            HorizontalDivider(color = GruvboxGray.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = GruvboxGray)
            }

            Button(
                onClick = { onDownload(selectedModels.toList()) },
                enabled = selectedModels.isNotEmpty(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = GruvboxGreenDark,
                        contentColor = GruvboxFg0,
                    ),
            ) {
                Text("Download Selected (${selectedModels.size})")
            }
        }
    }
}

@Composable
private fun DownloadProgressScreen(
    downloadStates: List<DownloadState>,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val allComplete = downloadStates.all { it.isComplete || it.isFailed }
    val successCount = downloadStates.count { it.isComplete }
    val failedCount = downloadStates.count { it.isFailed }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Download Progress",
            style = MaterialTheme.typography.headlineSmall,
            color = GruvboxFg0,
        )

        if (allComplete) {
            Text(
                text = "Downloads complete: $successCount succeeded, $failedCount failed",
                style = MaterialTheme.typography.bodyMedium,
                color = if (failedCount > 0) GruvboxYellowDark else GruvboxGreenDark,
            )
        } else {
            Text(
                text = "Downloading models...",
                style = MaterialTheme.typography.bodyMedium,
                color = GruvboxGray,
            )
        }

        // Progress list
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            colors =
                CardDefaults.cardColors(
                    containerColor = GruvboxFg0.copy(alpha = 0.05f),
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                downloadStates.forEach { state ->
                    DownloadProgressItem(state = state)
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (!allComplete) {
                TextButton(onClick = onBack) {
                    Text("Back", color = GruvboxGray)
                }
            }

            Button(
                onClick = onDone,
                enabled = allComplete,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = GruvboxGreenDark,
                        contentColor = GruvboxFg0,
                    ),
            ) {
                Text(if (allComplete) "Done" else "Downloading...")
            }
        }
    }
}

@Composable
private fun DownloadProgressItem(state: DownloadState) {
    val progressColor =
        when {
            state.isComplete -> GruvboxGreenDark
            state.isFailed -> GruvboxYellowDark
            else -> GruvboxBlueDark
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.model.displayName,
                color = GruvboxFg0,
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                state.isComplete -> {
                    Text(
                        text = "Complete",
                        color = GruvboxGreenDark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                state.isFailed -> {
                    Text(
                        text = "Failed",
                        color = GruvboxYellowDark,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                else -> {
                    Text(
                        text = "${state.progress}%",
                        color = GruvboxGray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text(
            text = state.status,
            color = GruvboxGray,
            style = MaterialTheme.typography.bodySmall,
        )

        if (!state.isComplete && !state.isFailed) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = GruvboxGray.copy(alpha = 0.2f),
            )
        }
    }
}
