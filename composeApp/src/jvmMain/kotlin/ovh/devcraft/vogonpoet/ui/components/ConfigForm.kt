package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.domain.model.VogonConfig
import ovh.devcraft.vogonpoet.domain.model.WakewordInfo
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigForm(
    viewModel: MainViewModel,
    config: VogonConfig?,
    onConfigChange: (VogonConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GruvboxGreenDark)
        }
        return
    }

    // Microphone state
    val microphoneList by viewModel.microphoneList.collectAsState()
    val isMicTesting by viewModel.isMicTesting.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var micExpanded by remember { mutableStateOf(false) }

    val selectedMicName = config.hardware.microphoneName ?: ""

    // Load microphones and hardware when connected
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            viewModel.loadMicrophones()
            viewModel.loadHardware()
            viewModel.loadWakewords()
        }
    }

    val currentConnectionState by viewModel.connectionState.collectAsState()
    val isReady = currentConnectionState is ConnectionState.Connected || currentConnectionState is ConnectionState.Bootstrapping

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Column 1: Microphone (Top) + Shortcuts (Bottom)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Microphone Selection
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Microphone",
                        style = MaterialTheme.typography.titleSmall,
                        color = GruvboxGreenDark,
                    )

                    // Microphone Dropdown
                    ExposedDropdownMenuBox(
                        expanded = micExpanded,
                        onExpandedChange = { if (isReady) micExpanded = it },
                    ) {
                        OutlinedTextField(
                            value =
                                microphoneList.find { it.name == selectedMicName }?.name
                                    ?: if (selectedMicName.isBlank()) "Default" else selectedMicName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Device") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = micExpanded) },
                            modifier =
                                Modifier
                                    .menuAnchor(
                                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = isReady,
                                    ).fillMaxWidth(),
                            enabled = isReady,
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GruvboxGreenDark,
                                    focusedLabelColor = GruvboxGreenDark,
                                ),
                        )
                        ExposedDropdownMenu(
                            expanded = micExpanded,
                            onDismissRequest = { micExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default Microphone") },
                                onClick = {
                                    micExpanded = false
                                    onConfigChange(
                                        config.copy(
                                            hardware =
                                                config.hardware.copy(microphoneName = null),
                                        ),
                                    )
                                },
                            )
                            microphoneList.forEach { mic ->
                                DropdownMenuItem(
                                    text = { Text(mic.name + if (mic.isDefault) " (Default)" else "") },
                                    onClick = {
                                        micExpanded = false
                                        onConfigChange(
                                            config.copy(
                                                hardware =
                                                    config.hardware.copy(microphoneName = mic.name),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Test Button
                    Button(
                        onClick = { viewModel.toggleMicTest(!isMicTesting) },
                        enabled = isReady,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = if (isMicTesting) GruvboxYellowDark else GruvboxGreenDark,
                                contentColor = GruvboxFg0,
                            ),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        Text(if (isMicTesting) "Stop Test" else "Test Mic")
                    }

                    // Voice activity indicator
                    if (isMicTesting) {
                        val vadState by viewModel.vadState.collectAsState()
                        if (vadState == VadState.Listening) {
                            Text(
                                text = "Voice detected!",
                                style = MaterialTheme.typography.bodySmall,
                                color = GruvboxGreenLight,
                            )
                        }
                    }
                }
            }

            // Shortcuts Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Shortcuts",
                        style = MaterialTheme.typography.titleSmall,
                        color = GruvboxGreenDark,
                    )

                    val toggleShortcut = config.ui.shortcuts.toggleListening
                    var localToggleShortcut by remember(toggleShortcut) { mutableStateOf(toggleShortcut) }

                    ShortcutSelector(
                        label = "Toggle Listening",
                        shortcut = localToggleShortcut,
                        onShortcutChange = {
                            localToggleShortcut = it
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui.copy(
                                            shortcuts =
                                                config.ui.shortcuts.copy(toggleListening = it),
                                        ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val pttShortcut = config.ui.shortcuts.forceListen
                    var localPttShortcut by remember(pttShortcut) { mutableStateOf(pttShortcut) }

                    SingleKeySelector(
                        label = "Push to Talk",
                        currentKey = localPttShortcut,
                        onKeyChange = {
                            localPttShortcut = it
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui.copy(
                                            shortcuts =
                                                config.ui.shortcuts.copy(forceListen = it),
                                        ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Listening will continue until you stop speaking.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GruvboxFg0.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // Column 2: Voice Triggers (Wakeword + Stop word + Stop words list)
        OutlinedCard(
            modifier = Modifier.weight(1f),
            colors =
                CardDefaults.outlinedCardColors(
                    containerColor = GruvboxBg1.copy(alpha = 0.5f),
                ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Voice Triggers",
                    style = MaterialTheme.typography.titleSmall,
                    color = GruvboxGreenDark,
                )

                // Wakeword selection
                var wakewordExpanded by remember { mutableStateOf(false) }
                val wakewordList by viewModel.wakewordList.collectAsState()
                val currentWakeword = config.voice.wakeword ?: ""

                // Helper function to format wakeword display name
                fun formatWakewordDisplay(info: WakewordInfo): String {
                    val baseName =
                        info.name
                            .removeSuffix("*")
                            .replace("_", " ")
                            .replaceFirstChar { it.uppercase() }
                    return if (info.isCustom) "$baseName *" else baseName
                }

                ExposedDropdownMenuBox(
                    expanded = wakewordExpanded,
                    onExpandedChange = { if (isReady) wakewordExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value =
                            currentWakeword
                                .takeIf { it.isNotBlank() }
                                ?.removeSuffix(
                                    "*",
                                )?.replace("_", " ")
                                ?.replaceFirstChar { it.uppercase() }
                                ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Wakeword") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wakewordExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = isReady).fillMaxWidth(),
                        enabled = isReady,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )
                    ExposedDropdownMenu(
                        expanded = wakewordExpanded,
                        onDismissRequest = { wakewordExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                wakewordExpanded = false
                                onConfigChange(
                                    config.copy(
                                        voice = config.voice.copy(wakeword = null),
                                    ),
                                )
                            },
                        )
                        // Group: Built-in models
                        val builtinModels = wakewordList.filter { !it.isCustom }
                        val customModels = wakewordList.filter { it.isCustom }

                        if (builtinModels.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("— Built-in —", color = GruvboxFg0.copy(alpha = 0.5f)) },
                                onClick = {},
                                enabled = false,
                            )
                            builtinModels.forEach { word ->
                                DropdownMenuItem(
                                    text = { Text(formatWakewordDisplay(word)) },
                                    onClick = {
                                        wakewordExpanded = false
                                        onConfigChange(
                                            config.copy(
                                                voice = config.voice.copy(wakeword = word.name),
                                            ),
                                        )
                                    },
                                )
                            }
                        }

                        // Group: Custom models
                        if (customModels.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("— Custom —", color = GruvboxFg0.copy(alpha = 0.5f)) },
                                onClick = {},
                                enabled = false,
                            )
                            customModels.forEach { word ->
                                DropdownMenuItem(
                                    text = { Text(formatWakewordDisplay(word)) },
                                    onClick = {
                                        wakewordExpanded = false
                                        onConfigChange(
                                            config.copy(
                                                voice = config.voice.copy(wakeword = word.name),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Stop word selection
                var stopWakewordExpanded by remember { mutableStateOf(false) }
                val currentStopWakeword = config.voice.stopWakeword ?: ""

                ExposedDropdownMenuBox(
                    expanded = stopWakewordExpanded,
                    onExpandedChange = { if (isReady) stopWakewordExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value =
                            currentStopWakeword
                                .takeIf { it.isNotBlank() }
                                ?.removeSuffix(
                                    "*",
                                )?.replace("_", " ")
                                ?.replaceFirstChar { it.uppercase() }
                                ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Stop word") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stopWakewordExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = isReady).fillMaxWidth(),
                        enabled = isReady,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )
                    ExposedDropdownMenu(
                        expanded = stopWakewordExpanded,
                        onDismissRequest = { stopWakewordExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                stopWakewordExpanded = false
                                onConfigChange(
                                    config.copy(
                                        voice = config.voice.copy(stopWakeword = null),
                                    ),
                                )
                            },
                        )
                        // Group: Built-in models
                        val builtinModels = wakewordList.filter { !it.isCustom }
                        val customModels = wakewordList.filter { it.isCustom }

                        if (builtinModels.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("— Built-in —", color = GruvboxFg0.copy(alpha = 0.5f)) },
                                onClick = {},
                                enabled = false,
                            )
                            builtinModels.forEach { word ->
                                DropdownMenuItem(
                                    text = { Text(formatWakewordDisplay(word)) },
                                    onClick = {
                                        stopWakewordExpanded = false
                                        onConfigChange(
                                            config.copy(
                                                voice = config.voice.copy(stopWakeword = word.name),
                                            ),
                                        )
                                    },
                                )
                            }
                        }

                        // Group: Custom models
                        if (customModels.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("— Custom —", color = GruvboxFg0.copy(alpha = 0.5f)) },
                                onClick = {},
                                enabled = false,
                            )
                            customModels.forEach { word ->
                                DropdownMenuItem(
                                    text = { Text(formatWakewordDisplay(word)) },
                                    onClick = {
                                        stopWakewordExpanded = false
                                        onConfigChange(
                                            config.copy(
                                                voice = config.voice.copy(stopWakeword = word.name),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "The above dropdowns rely on a dedicated model per word.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GruvboxFg0.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp),
                )

                // Stop words parsing transcript
                val stopWordsList = config.voice.stopWords ?: emptyList()
                val stopWordsString = stopWordsList.joinToString(", ")
                var localStopWords by remember(stopWordsString) { mutableStateOf(stopWordsString) }

                OutlinedTextField(
                    value = localStopWords,
                    onValueChange = { localStopWords = it },
                    label = { Text("Stop words (transcript parsing)") },
                    placeholder = { Text("comma, separated, words") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && localStopWords != stopWordsString) {
                                    val newList = localStopWords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    onConfigChange(
                                        config.copy(
                                            voice =
                                                config.voice.copy(stopWords = newList),
                                        ),
                                    )
                                }
                            },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                val newList = localStopWords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                onConfigChange(
                                    config.copy(
                                        voice =
                                            config.voice.copy(stopWords = newList),
                                    ),
                                )
                            },
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GruvboxGreenDark,
                            focusedLabelColor = GruvboxGreenDark,
                        ),
                )
                Text(
                    text = "Detected words that will immediately stop transcription.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GruvboxFg0.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
