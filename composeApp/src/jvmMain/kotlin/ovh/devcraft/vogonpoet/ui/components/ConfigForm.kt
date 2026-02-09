package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigForm(
    config: Babelfish?,
    onSave: (Babelfish) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GruvboxGreenDark)
        }
        return
    }

    // Initialize form state with current config or defaults
    var device by remember(config) { mutableStateOf(config.hardware?.device ?: "cpu") }
    var doublePass by remember(config) { mutableStateOf(config.pipeline?.double_pass ?: false) }
    var ghostPreset by remember(config) { mutableStateOf(config.pipeline?.ghost_preset ?: "fast") }
    var anchorPreset by remember(config) { mutableStateOf(config.pipeline?.anchor_preset ?: "solid") }
    // Note: anchor_trigger_interval_ms is not in the generated schema yet, using default
    var anchorInterval by remember { mutableStateOf(2000f) }
    var wakeword by remember(config) { mutableStateOf(config.voice?.wakeword ?: "") }
    var wakewordSensitivity by remember(config) { mutableStateOf(config.voice?.wakeword_sensitivity?.toFloat() ?: 0.5f) }
    var stopWords by remember(config) { mutableStateOf(config.voice?.stop_words?.joinToString(", ") ?: "") }
    var toggleShortcut by remember(config) { mutableStateOf(config.ui?.shortcuts?.toggle_listening ?: "Ctrl+Shift+S") }
    var forceShortcut by remember(config) { mutableStateOf(config.ui?.shortcuts?.force_listen ?: "Ctrl+Shift+L") }

    val scrollState = rememberScrollState()

    // Force single pass when CPU mode is selected
    LaunchedEffect(device) {
        if (device == "cpu" && doublePass) {
            doublePass = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(end = 12.dp), // Extra padding for scrollbar
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Panel 1: Hardware Acceleration
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Hardware Acceleration",
                        style = MaterialTheme.typography.titleMedium,
                        color = GruvboxFg0,
                    )

                    // GPU Selector (mocked)
                    var expanded by remember { mutableStateOf(false) }
                    val gpuOptions = listOf("cpu" to "CPU Only", "cuda" to "NVIDIA GPU (Mock)")

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = gpuOptions.find { it.first == device }?.second ?: device,
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
                            gpuOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        device = value
                                        if (value == "cpu") {
                                            doublePass = false
                                        }
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    if (device == "cpu") {
                        Text(
                            text = "CPU mode detected. Double-pass pipeline will be disabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxYellowDark,
                        )
                    }
                }
            }

            // Panel 2: Pipeline Strategy
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Pipeline Strategy",
                        style = MaterialTheme.typography.titleMedium,
                        color = GruvboxFg0,
                    )

                    // Single vs Double Pass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !doublePass,
                            onClick = { doublePass = false },
                            label = { Text("Single Pass") },
                            enabled = device != "cpu",
                        )
                        FilterChip(
                            selected = doublePass,
                            onClick = {
                                if (device != "cpu") doublePass = true
                            },
                            label = { Text("Double Pass") },
                            enabled = device != "cpu",
                        )
                    }

                    if (device == "cpu") {
                        Text(
                            text = "Double-pass requires GPU acceleration.",
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
                                    },
                                )
                            }
                        }
                    }

                    // Anchor Trigger Interval (only for double pass)
                    if (doublePass) {
                        Text(
                            text = "Anchor Trigger Interval: ${anchorInterval.toInt()}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Slider(
                            value = anchorInterval,
                            onValueChange = { anchorInterval = it },
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

            // Panel 3: Voice Triggers
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Voice Triggers",
                        style = MaterialTheme.typography.titleMedium,
                        color = GruvboxFg0,
                    )

                    // Wakeword (optional)
                    OutlinedTextField(
                        value = wakeword,
                        onValueChange = { wakeword = it },
                        label = { Text("Wakeword (Optional)") },
                        placeholder = { Text("e.g., hey_jarvis") },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )

                    // Wakeword Sensitivity
                    Text(
                        text = "Wakeword Sensitivity: ${(wakewordSensitivity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GruvboxFg0,
                    )
                    Slider(
                        value = wakewordSensitivity,
                        onValueChange = { wakewordSensitivity = it },
                        valueRange = 0.1f..0.9f,
                        steps = 8,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = GruvboxGreenDark,
                                activeTrackColor = GruvboxGreenDark,
                            ),
                    )

                    // Stop Words (optional)
                    OutlinedTextField(
                        value = stopWords,
                        onValueChange = { stopWords = it },
                        label = { Text("Stop Words (Optional, comma-separated)") },
                        placeholder = { Text("e.g., stop talking, that's enough") },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )
                }
            }

            // Panel 4: Shortcuts
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Keyboard Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = GruvboxFg0,
                    )

                    ShortcutSelector(
                        label = "Toggle Listening",
                        shortcut = toggleShortcut,
                        onShortcutChange = { toggleShortcut = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ShortcutSelector(
                        label = "Push-to-Talk",
                        shortcut = forceShortcut,
                        onShortcutChange = { forceShortcut = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    val newConfig =
                        Babelfish(
                            hardware =
                                Babelfish.Hardware(
                                    device = device,
                                    vram_limit_gb = config.hardware?.vram_limit_gb,
                                    microphone_index = config.hardware?.microphone_index,
                                ),
                            pipeline =
                                Babelfish.Pipeline(
                                    double_pass = doublePass,
                                    ghost_preset = ghostPreset,
                                    anchor_preset = anchorPreset,
                                ),
                            voice =
                                Babelfish.Voice(
                                    wakeword = wakeword.takeIf { it.isNotBlank() },
                                    wakeword_sensitivity = wakewordSensitivity.toDouble(),
                                    stop_words = stopWords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                ),
                            ui =
                                Babelfish.Ui(
                                    verbose = config.ui?.verbose ?: false,
                                    show_timestamps = config.ui?.show_timestamps ?: true,
                                    shortcuts =
                                        Babelfish.Shortcuts(
                                            toggle_listening = toggleShortcut,
                                            force_listen = forceShortcut,
                                        ),
                                ),
                            server = config.server ?: Babelfish.Server(),
                        )
                    onSave(newConfig)
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = GruvboxGreenDark,
                        contentColor = GruvboxFg0,
                    ),
            ) {
                Text("Save Configuration")
            }
        }

        // Vertical scrollbar on the right
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
