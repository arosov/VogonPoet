package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    var wakeword by remember(config) { mutableStateOf(config.voice?.wakeword ?: "") }
    var wakewordSensitivity by remember(config) { mutableStateOf(config.voice?.wakeword_sensitivity?.toFloat() ?: 0.5f) }
    var stopWords by remember(config) { mutableStateOf(config.voice?.stop_words?.joinToString(", ") ?: "") }
    var toggleShortcut by remember(config) { mutableStateOf(config.ui?.shortcuts?.toggle_listening ?: "Ctrl+Shift+S") }
    var cacheDir by remember(config) { mutableStateOf(config.cache?.cache_dir ?: "") }

    // Microphone state
    val microphoneList by viewModel.microphoneList.collectAsState()
    val isMicTesting by viewModel.isMicTesting.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var micExpanded by remember { mutableStateOf(false) }
    var selectedMicIndex by remember(config) { mutableStateOf(config.hardware?.microphone_index ?: -1L) }

    // Load microphones and hardware when connected
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected || connectionState is ConnectionState.Bootstrapping) {
            viewModel.loadMicrophones()
            viewModel.loadHardware()
            viewModel.loadWakewords()
        }
    }

    // UV default cache path based on platform
    val defaultUvCacheDir =
        remember {
            val home = System.getProperty("user.home")
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("win") -> System.getenv("LOCALAPPDATA")?.let { "$it\\uv" } ?: "$home\\AppData\\Local\\uv"
                osName.contains("mac") -> "$home/Library/Caches/uv"
                else -> "$home/.cache/uv"
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
                                    selectedMicIndex = -1
                                    micExpanded = false
                                },
                            )
                            microphoneList.forEach { mic ->
                                DropdownMenuItem(
                                    text = { Text(mic.name + if (mic.isDefault) " (Default)" else "") },
                                    onClick = {
                                        selectedMicIndex = mic.index.toLong()
                                        micExpanded = false
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

                    ExposedDropdownMenuBox(
                        expanded = wakewordExpanded,
                        onExpandedChange = { if (isReady) wakewordExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = wakeword.takeIf { it.isNotBlank() } ?: "None",
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
                                    wakeword = ""
                                    wakewordExpanded = false
                                },
                            )
                            wakewordList.forEach { word ->
                                DropdownMenuItem(
                                    text = { Text(word.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        wakeword = word
                                        wakewordExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Sensitivity
                    Column {
                        Text(
                            text = "Sensitivity: ${(wakewordSensitivity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
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
                    ShortcutSelector(
                        label = "Toggle Listening",
                        shortcut = toggleShortcut,
                        onShortcutChange = { toggleShortcut = it },
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
                    OutlinedTextField(
                        value = stopWords,
                        onValueChange = { stopWords = it },
                        label = { Text("Words that stop listening") },
                        placeholder = { Text("comma, separated, words") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )
                }
            }
        }

        // Row 3: Cache Directory (Full Width)
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
                    text = "Cache Directory",
                    style = MaterialTheme.typography.titleSmall,
                    color = GruvboxGreenDark,
                )

                val displayPath = cacheDir.takeIf { it.isNotBlank() } ?: defaultUvCacheDir
                Text(
                    text = displayPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cacheDir.isBlank()) GruvboxGray else GruvboxFg0,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val chooser =
                                javax.swing.JFileChooser().apply {
                                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Select UV Cache Directory"
                                    if (cacheDir.isNotBlank()) {
                                        currentDirectory = java.io.File(cacheDir)
                                    } else {
                                        currentDirectory = java.io.File(defaultUvCacheDir)
                                    }
                                }
                            val result = chooser.showOpenDialog(null)
                            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                cacheDir = chooser.selectedFile.absolutePath
                            }
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = GruvboxBlueDark,
                                contentColor = GruvboxFg0,
                            ),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        Text("Browse", style = MaterialTheme.typography.bodySmall)
                    }

                    TextButton(
                        onClick = { cacheDir = "" },
                        enabled = cacheDir.isNotBlank(),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        Text(
                            "Reset",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cacheDir.isNotBlank()) GruvboxRedDark else GruvboxGray,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Save Button
        Button(
            onClick = {
                val newConfig =
                    Babelfish(
                        hardware =
                            config.hardware?.copy(
                                microphone_index = if (selectedMicIndex >= 0) selectedMicIndex else null,
                            ) ?: Babelfish.Hardware(microphone_index = if (selectedMicIndex >= 0) selectedMicIndex else null),
                        pipeline = config.pipeline ?: Babelfish.Pipeline(),
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
                                    ),
                                activation_detection = config.ui?.activation_detection ?: Babelfish.Activation_detection(),
                            ),
                        server = config.server ?: Babelfish.Server(),
                        cache =
                            Babelfish.Cache(
                                cache_dir = cacheDir.takeIf { it.isNotBlank() },
                            ),
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
}
