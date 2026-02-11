package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.*

@Composable
fun CollapsibleSidePanel(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val panelWidth by animateDpAsState(
        targetValue = if (isExpanded) 400.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "panel_width",
    )

    Row(
        modifier = Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Handle button - nice compact left half circle
        Box(
            modifier =
                Modifier
                    .width(24.dp)
                    .height(48.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 24.dp,
                            bottomStart = 24.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    ).background(GruvboxYellowDark)
                    .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isExpanded) ">" else "<",
                color = GruvboxFg0,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.offset(x = (-2).dp),
            )
        }

        // Panel content
        if (panelWidth > 0.dp) {
            Box(
                modifier =
                    Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(GruvboxBg1)
                        .padding(12.dp),
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsPanel(
    viewModel: MainViewModel,
    config: Babelfish?,
    onConfigChange: (Babelfish) -> Unit,
) {
    if (config == null) return

    val hardwareList by viewModel.hardwareList.collectAsState()

    // Form states
    var device by remember(config) { mutableStateOf(config.hardware?.device ?: "auto") }
    var silenceThreshold by remember(config) {
        mutableStateOf(config.pipeline?.silence_threshold_ms?.toFloat() ?: 400f)
    }
    var updateInterval by remember(config) {
        mutableStateOf(config.pipeline?.update_interval_ms?.toFloat() ?: 100f)
    }
    var iconOnly by remember(config) { mutableStateOf(config.ui?.activation_detection?.icon_only ?: false) }
    var overlayMode by remember(config) { mutableStateOf(config.ui?.activation_detection?.overlay_mode ?: false) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp), // Add padding for scrollbar
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Advanced Settings",
                style = MaterialTheme.typography.titleLarge,
                color = GruvboxFg0,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Divider(color = GruvboxGray.copy(alpha = 0.3f))

            // Hardware Acceleration
            AdvancedSection(title = "Hardware Acceleration") {
                var expanded by remember { mutableStateOf(false) }

                // Combine dynamic hardware with auto and cpu options
                val hardwareOptions =
                    listOf("auto" to "Auto Detect", "cpu" to "CPU Only") +
                        hardwareList.filter { it.id != "cpu" }.map { it.id to it.name }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = hardwareOptions.find { it.first == device }?.second ?: device,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Processing Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        hardwareOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    device = value
                                    expanded = false
                                    onConfigChange(
                                        createUpdatedConfig(
                                            config,
                                            device,
                                            silenceThreshold,
                                            updateInterval,
                                            iconOnly,
                                            overlayMode,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Pipeline Optimization
            AdvancedSection(title = "Pipeline Performance") {
                // Silence Threshold
                Text(
                    text = "Silence Threshold: ${silenceThreshold.toInt()}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GruvboxFg0,
                )
                Slider(
                    value = silenceThreshold,
                    onValueChange = {
                        silenceThreshold = it
                        onConfigChange(
                            createUpdatedConfig(
                                config,
                                device,
                                silenceThreshold,
                                updateInterval,
                                iconOnly,
                                overlayMode,
                            ),
                        )
                    },
                    valueRange = 200f..1500f,
                    steps = 12,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = GruvboxGreenDark,
                            activeTrackColor = GruvboxGreenDark,
                        ),
                )
                Text(
                    text = "How long to wait after voice stops before finalizing",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxFg0.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Update Interval
                Text(
                    text = "Feedback Interval: ${updateInterval.toInt()}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GruvboxFg0,
                )
                Slider(
                    value = updateInterval,
                    onValueChange = {
                        updateInterval = it
                        onConfigChange(
                            createUpdatedConfig(
                                config,
                                device,
                                silenceThreshold,
                                updateInterval,
                                iconOnly,
                                overlayMode,
                            ),
                        )
                    },
                    valueRange = 50f..500f,
                    steps = 8,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = GruvboxBlueDark,
                            activeTrackColor = GruvboxBlueDark,
                        ),
                )
                Text(
                    text = "Frequency of intermediate 'ghost' text updates",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxFg0.copy(alpha = 0.6f),
                )
            }

            // Interface Settings
            AdvancedSection(title = "Interface") {
                // Icon Only Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Icon Only",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Text(
                            text = "Show only microphone icon in status area",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = iconOnly,
                        onCheckedChange = {
                            iconOnly = it
                            onConfigChange(
                                createUpdatedConfig(
                                    config,
                                    device,
                                    silenceThreshold,
                                    updateInterval,
                                    iconOnly,
                                    overlayMode,
                                ),
                            )
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Overlay Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overlay Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Text(
                            text = "Floating window without decorations",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = overlayMode,
                        onCheckedChange = {
                            overlayMode = it
                            onConfigChange(
                                createUpdatedConfig(
                                    config,
                                    device,
                                    silenceThreshold,
                                    updateInterval,
                                    iconOnly,
                                    overlayMode,
                                ),
                            )
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            ),
                    )
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            style =
                ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = GruvboxGray.copy(alpha = 0.5f),
                    hoverColor = GruvboxGreenDark.copy(alpha = 0.8f),
                ),
        )
    }
}

@Composable
private fun AdvancedSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = GruvboxGreenDark,
        )
        content()
    }
}

private fun createUpdatedConfig(
    original: Babelfish,
    device: String,
    silenceThreshold: Float,
    updateInterval: Float,
    iconOnly: Boolean,
    overlayMode: Boolean,
): Babelfish =
    Babelfish(
        hardware =
            Babelfish.Hardware(
                device = device,
                microphone_index = original.hardware?.microphone_index,
            ),
        pipeline =
            Babelfish.Pipeline(
                silence_threshold_ms = silenceThreshold.toLong(),
                update_interval_ms = updateInterval.toLong(),
            ),
        voice = original.voice ?: Babelfish.Voice(),
        ui =
            Babelfish.Ui(
                verbose = original.ui?.verbose ?: false,
                show_timestamps = original.ui?.show_timestamps ?: true,
                shortcuts = original.ui?.shortcuts ?: Babelfish.Shortcuts(),
                activation_detection =
                    Babelfish.Activation_detection(
                        icon_only = iconOnly,
                        overlay_mode = overlayMode,
                    ),
            ),
        server = original.server ?: Babelfish.Server(),
        cache = original.cache ?: Babelfish.Cache(),
    )
