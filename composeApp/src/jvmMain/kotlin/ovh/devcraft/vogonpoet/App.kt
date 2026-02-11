package ovh.devcraft.vogonpoet

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.components.AdvancedSettingsPanel
import ovh.devcraft.vogonpoet.ui.components.CollapsibleSidePanel
import ovh.devcraft.vogonpoet.ui.components.ConfigForm
import ovh.devcraft.vogonpoet.ui.components.StatusCard
import ovh.devcraft.vogonpoet.ui.theme.*

@Composable
fun App(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val vadState by viewModel.vadState.collectAsState()
    val transcribingText by viewModel.transcribingText.collectAsState()
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
                            .padding(4.dp), // Reduced margin
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    StatusCard(
                        connectionState = connectionState,
                        vadState = vadState,
                        transcribingText = transcribingText,
                        config = config,
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
                        )
                    }
                }
            }
        }
    }
}
