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
    var device by remember(config) { mutableStateOf(config.hardware?.device ?: "cpu") }
    var doublePass by remember(config) { mutableStateOf(config.pipeline?.double_pass ?: false) }
    var singlePreset by remember(config) { mutableStateOf(config.pipeline?.preset ?: "balanced") }
    var ghostPreset by remember(config) { mutableStateOf(config.pipeline?.ghost_preset ?: "fast") }
    var anchorPreset by remember(config) { mutableStateOf(config.pipeline?.anchor_preset ?: "solid") }
    var anchorInterval by remember(config) {
        mutableStateOf(config.pipeline?.anchor_trigger_interval_ms?.toFloat() ?: 2000f)
    }
    var silenceThreshold by remember(config) {
        mutableStateOf(config.pipeline?.silence_threshold_ms?.toFloat() ?: 700f)
    }
    var iconOnly by remember(config) { mutableStateOf(config.ui?.activation_detection?.icon_only ?: false) }
    var overlayMode by remember(config) { mutableStateOf(config.ui?.activation_detection?.overlay_mode ?: false) }

    // Force single pass when CPU mode is selected
    LaunchedEffect(device) {
        if (device == "cpu" && doublePass) {
            doublePass = false
        }
    }

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

                // Combine dynamic hardware with mandatory CPU option
                val hardwareOptions =
                    listOf("cpu" to "CPU Only") +
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
                                    if (value == "cpu") {
                                        doublePass = false
                                    }
                                    expanded = false
                                    onConfigChange(
                                        createUpdatedConfig(
                                            config,
                                            device,
                                            doublePass,
                                            singlePreset,
                                            ghostPreset,
                                            anchorPreset,
                                            anchorInterval,
                                            silenceThreshold,
                                            iconOnly,
                                            overlayMode,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                if (device == "cpu") {
                    Text(
                        text = "CPU mode disables double-pass pipeline",
                        style = MaterialTheme.typography.bodySmall,
                        color = GruvboxYellowDark,
                    )
                }
            }

            // Pipeline Strategy
            AdvancedSection(title = "Pipeline Strategy") {
                if (device == "cpu") {
                    // Single Preset for CPU
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = singlePreset.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Performance Preset") },
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
                            listOf("fast", "balanced", "accurate").forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        singlePreset = preset
                                        expanded = false
                                        onConfigChange(
                                            createUpdatedConfig(
                                                config,
                                                device,
                                                doublePass,
                                                singlePreset,
                                                ghostPreset,
                                                anchorPreset,
                                                anchorInterval,
                                                silenceThreshold,
                                                iconOnly,
                                                overlayMode,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        text = "CPU mode uses a single-pass optimized pipeline",
                        style = MaterialTheme.typography.bodySmall,
                        color = GruvboxGray,
                    )
                } else {
                    // Single vs Double Pass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !doublePass,
                            onClick = {
                                doublePass = false
                                onConfigChange(
                                    createUpdatedConfig(
                                        config,
                                        device,
                                        doublePass,
                                        singlePreset,
                                        ghostPreset,
                                        anchorPreset,
                                        anchorInterval,
                                        silenceThreshold,
                                        iconOnly,
                                        overlayMode,
                                    ),
                                )
                            },
                            label = { Text("Single Pass") },
                            enabled = device != "cpu",
                        )
                        FilterChip(
                            selected = doublePass,
                            onClick = {
                                if (device != "cpu") {
                                    doublePass = true
                                    onConfigChange(
                                        createUpdatedConfig(
                                            config,
                                            device,
                                            doublePass,
                                            singlePreset,
                                            ghostPreset,
                                            anchorPreset,
                                            anchorInterval,
                                            silenceThreshold,
                                            iconOnly,
                                            overlayMode,
                                        ),
                                    )
                                }
                            },
                            label = { Text("Double Pass") },
                            enabled = device != "cpu",
                        )
                    }

                    if (device == "cpu") {
                        Text(
                            text = "Double-pass requires GPU acceleration",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxGray,
                        )
                    }

                    // Ghost Preset
                    var ghostExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = ghostExpanded,
                        onExpandedChange = { ghostExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = ghostPreset.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ghost Preset") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ghostExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GruvboxGreenDark,
                                    focusedLabelColor = GruvboxGreenDark,
                                ),
                        )
                        ExposedDropdownMenu(
                            expanded = ghostExpanded,
                            onDismissRequest = { ghostExpanded = false },
                        ) {
                            listOf("fast", "balanced", "accurate").forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        ghostPreset = preset
                                        ghostExpanded = false
                                        onConfigChange(
                                            createUpdatedConfig(
                                                config,
                                                device,
                                                doublePass,
                                                singlePreset,
                                                ghostPreset,
                                                anchorPreset,
                                                anchorInterval,
                                                silenceThreshold,
                                                iconOnly,
                                                overlayMode,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Anchor Preset
                    var anchorExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = anchorExpanded,
                        onExpandedChange = { anchorExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = anchorPreset.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Anchor Preset") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = anchorExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GruvboxGreenDark,
                                    focusedLabelColor = GruvboxGreenDark,
                                ),
                        )
                        ExposedDropdownMenu(
                            expanded = anchorExpanded,
                            onDismissRequest = { anchorExpanded = false },
                        ) {
                            listOf("fast", "balanced", "solid").forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        anchorPreset = preset
                                        anchorExpanded = false
                                        onConfigChange(
                                            createUpdatedConfig(
                                                config,
                                                device,
                                                doublePass,
                                                singlePreset,
                                                ghostPreset,
                                                anchorPreset,
                                                anchorInterval,
                                                silenceThreshold,
                                                iconOnly,
                                                overlayMode,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Anchor Trigger Interval (only for double pass)
                    if (doublePass) {
                        Text(
                            text = "Anchor Interval: ${anchorInterval.toInt()}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Slider(
                            value = anchorInterval,
                            onValueChange = {
                                anchorInterval = it
                                onConfigChange(
                                    createUpdatedConfig(
                                        config,
                                        device,
                                        doublePass,
                                        singlePreset,
                                        ghostPreset,
                                        anchorPreset,
                                        anchorInterval,
                                        silenceThreshold,
                                        iconOnly,
                                        overlayMode,
                                    ),
                                )
                            },
                            valueRange = 500f..5000f,
                            steps = 9,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = GruvboxGreenDark,
                                    activeTrackColor = GruvboxGreenDark,
                                ),
                        )
                    }
                }
            }

            // Speech Detection
            AdvancedSection(title = "Speech Detection") {
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
                                doublePass,
                                singlePreset,
                                ghostPreset,
                                anchorPreset,
                                anchorInterval,
                                silenceThreshold,
                                iconOnly,
                                overlayMode,
                            ),
                        )
                    },
                    valueRange = 300f..2000f,
                    steps = 17,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = GruvboxGreenDark,
                            activeTrackColor = GruvboxGreenDark,
                        ),
                )
                Text(
                    text = "How long to keep listening after voice stops",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxFg0.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                            text = "Show only microphone icon",
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
                                    doublePass,
                                    singlePreset,
                                    ghostPreset,
                                    anchorPreset,
                                    anchorInterval,
                                    silenceThreshold,
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
                            text = "Always on top, no decorations",
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
                                    doublePass,
                                    singlePreset,
                                    ghostPreset,
                                    anchorPreset,
                                    anchorInterval,
                                    silenceThreshold,
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
    doublePass: Boolean,
    preset: String,
    ghostPreset: String,
    anchorPreset: String,
    anchorInterval: Float,
    silenceThreshold: Float,
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
                double_pass = doublePass,
                preset = preset,
                ghost_preset = ghostPreset,
                anchor_preset = anchorPreset,
                anchor_trigger_interval_ms = anchorInterval.toLong(),
                silence_threshold_ms = silenceThreshold.toLong(),
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
