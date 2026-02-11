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
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigForm(
    viewModel: MainViewModel,
    config: Babelfish?,
    onConfigChange: (Babelfish) -> Unit,
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

    // We don't need local state for mic index, we can just use config directly,
    // unless we want to avoid jitter during selection animation? No, dropdown closes instantly.
    val selectedMicIndex = config.hardware?.microphone_index ?: -1L

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

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Row 1: Microphone | Voice Triggers
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Column 1: Microphone Selection
            OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxHeight(),
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
                                microphoneList.find { it.index.toLong() == selectedMicIndex }?.name
                                    ?: if (selectedMicIndex == -1L) "Default" else "Mic $selectedMicIndex",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Device") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = micExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                                                config.hardware?.copy(microphone_index = null)
                                                    ?: Babelfish.Hardware(microphone_index = null),
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
                                                    config.hardware?.copy(microphone_index = mic.index.toLong())
                                                        ?: Babelfish.Hardware(microphone_index = mic.index.toLong()),
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

            // Column 2: Voice Triggers (Wakeword + Sensitivity)
            OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxHeight(),
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
                    val currentWakeword = config.voice?.wakeword ?: ""

                    ExposedDropdownMenuBox(
                        expanded = wakewordExpanded,
                        onExpandedChange = { if (isReady) wakewordExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentWakeword.takeIf { it.isNotBlank() } ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Wakeword") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wakewordExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                                            voice = config.voice?.copy(wakeword = null) ?: Babelfish.Voice(wakeword = null),
                                        ),
                                    )
                                },
                            )
                            wakewordList.forEach { word ->
                                DropdownMenuItem(
                                    text = { Text(word.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        wakewordExpanded = false
                                        onConfigChange(
                                            config.copy(
                                                voice = config.voice?.copy(wakeword = word) ?: Babelfish.Voice(wakeword = word),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Sensitivity
                    val sensitivity = config.voice?.wakeword_sensitivity?.toFloat() ?: 0.5f
                    var localSensitivity by remember(sensitivity) { mutableStateOf(sensitivity) }

                    Column {
                        Text(
                            text = "Sensitivity: ${(localSensitivity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0,
                        )
                        Slider(
                            value = localSensitivity,
                            onValueChange = { localSensitivity = it },
                            onValueChangeFinished = {
                                onConfigChange(
                                    config.copy(
                                        voice =
                                            config.voice?.copy(wakeword_sensitivity = localSensitivity.toDouble())
                                                ?: Babelfish.Voice(wakeword_sensitivity = localSensitivity.toDouble()),
                                    ),
                                )
                            },
                            valueRange = 0.1f..0.9f,
                            steps = 8,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = GruvboxGreenDark,
                                    activeTrackColor = GruvboxGreenDark,
                                ),
                        )
                    }
                }
            }
        }

        // Row 2: Shortcut | Stop Words
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Shortcuts
            OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Shortcut",
                        style = MaterialTheme.typography.titleSmall,
                        color = GruvboxGreenDark,
                    )

                    val currentShortcut = config.ui?.shortcuts?.toggle_listening ?: "Ctrl+Shift+S"
                    var localShortcut by remember(currentShortcut) { mutableStateOf(currentShortcut) }

                    // ShortcutSelector component handles its own state internally usually?
                    // Let's check ShortcutSelector.kt. Assuming it exposes onShortcutChange.
                    // We need it to be live.
                    ShortcutSelector(
                        label = "Toggle Listening",
                        shortcut = localShortcut,
                        onShortcutChange = {
                            localShortcut = it
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui?.copy(
                                            shortcuts =
                                                config.ui?.shortcuts?.copy(toggle_listening = it)
                                                    ?: Babelfish.Shortcuts(toggle_listening = it),
                                        ) ?: Babelfish.Ui(shortcuts = Babelfish.Shortcuts(toggle_listening = it)),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Stop Words
            OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Stop Words",
                        style = MaterialTheme.typography.titleSmall,
                        color = GruvboxGreenDark,
                    )

                    val stopWordsList = config.voice?.stop_words ?: emptyList()
                    val stopWordsString = stopWordsList.joinToString(", ")
                    var localStopWords by remember(stopWordsString) { mutableStateOf(stopWordsString) }

                    OutlinedTextField(
                        value = localStopWords,
                        onValueChange = { localStopWords = it },
                        label = { Text("Words that stop listening") },
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
                                                    config.voice?.copy(stop_words = newList)
                                                        ?: Babelfish.Voice(stop_words = newList),
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
                                                config.voice?.copy(stop_words = newList)
                                                    ?: Babelfish.Voice(stop_words = newList),
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
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
