package ovh.devcraft.vogonpoet

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.devcraft.vogonpoet.domain.model.EngineMode
import ovh.devcraft.vogonpoet.infrastructure.UpdateChecker
import ovh.devcraft.vogonpoet.infrastructure.UpdateInfo
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.components.AdvancedSettingsPanel
import ovh.devcraft.vogonpoet.ui.components.CollapsibleSidePanel
import ovh.devcraft.vogonpoet.ui.components.ConfigForm
import ovh.devcraft.vogonpoet.ui.components.StatusCard
import ovh.devcraft.vogonpoet.ui.components.UpdateBanner
import ovh.devcraft.vogonpoet.ui.theme.*

@Composable
fun App(
    viewModel: MainViewModel,
    updateInfo: UpdateInfo?,
    onDismissUpdate: () -> Unit,
    onOpenDownloadUrl: (String) -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val vadState by viewModel.vadState.collectAsState()
    val engineMode by viewModel.engineMode.collectAsState()
    val listeningText by viewModel.listeningText.collectAsState()
    val displayedEvent by viewModel.displayedEvent.collectAsState()
    val config by viewModel.config.collectAsState()
    val draftConfig by viewModel.draftConfig.collectAsState()
    var isPanelExpanded by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = GruvboxDarkColorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Main content area
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    StatusCard(
                        connectionState = connectionState,
                        vadState = vadState,
                        engineMode = engineMode,
                        listeningText = listeningText,
                        config = config,
                        displayedEvent = displayedEvent,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Interactive Configuration Form
                    ConfigForm(
                        viewModel = viewModel,
                        config = draftConfig,
                        onConfigChange = { newConfig ->
                            viewModel.updateDraft(newConfig)
                            viewModel.saveConfig(newConfig)
                        },
                        modifier = Modifier.weight(1f),
                    )

                    // Update banner at bottom
                    updateInfo?.let { info ->
                        UpdateBanner(
                            updateInfo = info,
                            onDismiss = onDismissUpdate,
                            onOpenDownload = { onOpenDownloadUrl(info.siteUrl) },
                            onInstallUpdate = {
                                UpdateChecker().triggerUpdate()
                            },
                        )
                    }
                }

                // Shade overlay when panel is expanded
                if (isPanelExpanded) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                                .clickable { isPanelExpanded = false },
                    )
                }

                // Collapsible side panel for advanced settings
                // Aligned to the right, overlaying the main content
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                ) {
                    CollapsibleSidePanel(
                        isExpanded = isPanelExpanded,
                        onToggle = { isPanelExpanded = !isPanelExpanded },
                    ) {
                        AdvancedSettingsPanel(
                            viewModel = viewModel,
                            config = draftConfig,
                            onConfigChange = { newConfig ->
                                viewModel.updateDraft(newConfig)
                                viewModel.saveConfig(newConfig)
                            },
                            onDismiss = { isPanelExpanded = false },
                        )
                    }
                }
            }
        }
    }
}
