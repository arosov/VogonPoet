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

    // Load microphones when connected or bootstrapping
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected || connectionState is ConnectionState.Bootstrapping) {
            viewModel.loadMicrophones()
        }
    }

    val scrollState = rememberScrollState()

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(4.dp)
                    .padding(end = 8.dp), // Extra padding for scrollbar
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Panel 1: Microphone Selection
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Microphone Selector with Test button inline
                    val currentConnectionState by viewModel.connectionState.collectAsState()
                    val isReady =
                        currentConnectionState is ConnectionState.Connected || currentConnectionState is ConnectionState.Bootstrapping

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Microphone Dropdown
                        ExposedDropdownMenuBox(
                            expanded = micExpanded,
                            onExpandedChange = { if (isReady) micExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value =
                                    microphoneList.find { it.index.toLong() == selectedMicIndex }?.name
                                        ?: if (selectedMicIndex == -1L) "Default" else "Mic $selectedMicIndex",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Microphone", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = micExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                enabled = isReady,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
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
                                // Default option
                                DropdownMenuItem(
                                    text = { Text("Default Microphone") },
                                    onClick = {
                                        selectedMicIndex = -1
                                        micExpanded = false
                                    },
                                )
                                // List available microphones
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
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(if (isMicTesting) "Stop" else "Test", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Connection hint when not ready
                    if (!isReady) {
                        Text(
                            text = "Connect to see microphones",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxGray,
                        )
                    }

                    // Voice activity indicator during test
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

            // Panel 2: Voice Triggers
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Wakeword row with sensitivity slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Wakeword input
                        OutlinedTextField(
                            value = wakeword,
                            onValueChange = { wakeword = it },
                            label = { Text("Wakeword", style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("Optional", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GruvboxGreenDark,
                                    focusedLabelColor = GruvboxGreenDark,
                                ),
                        )

                        // Sensitivity slider (compact)
                        Column(
                            modifier = Modifier.width(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "${(wakewordSensitivity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = GruvboxFg0,
                            )
                            Slider(
                                value = wakewordSensitivity,
                                onValueChange = { wakewordSensitivity = it },
                                valueRange = 0.1f..0.9f,
                                steps = 8,
                                modifier = Modifier.height(24.dp),
                                colors =
                                    SliderDefaults.colors(
                                        thumbColor = GruvboxGreenDark,
                                        activeTrackColor = GruvboxGreenDark,
                                    ),
                            )
                        }
                    }

                    // Stop Words (single line)
                    OutlinedTextField(
                        value = stopWords,
                        onValueChange = { stopWords = it },
                        label = { Text("Stop words", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("comma, separated, words", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                            ),
                    )
                }
            }

            // Panel 3: Shortcuts
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Toggle Shortcut",
                        style = MaterialTheme.typography.labelSmall,
                        color = GruvboxFg0,
                    )
                    ShortcutSelector(
                        label = "",
                        shortcut = toggleShortcut,
                        onShortcutChange = { toggleShortcut = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Panel 4: Cache Directory
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = GruvboxBg1.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Display current path on its own line
                    val displayPath = cacheDir.takeIf { it.isNotBlank() } ?: defaultUvCacheDir

                    Text(
                        text = "Cache: $displayPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cacheDir.isBlank()) GruvboxGray else GruvboxFg0,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Buttons row below
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Browse button
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
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("Browse", style = MaterialTheme.typography.bodySmall)
                        }

                        // Reset button
                        TextButton(
                            onClick = { cacheDir = "" },
                            enabled = cacheDir.isNotBlank(),
                            modifier = Modifier.height(32.dp),
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

            // Save Button
            Button(
                onClick = {
                    val newConfig =
                        Babelfish(
                            // Preserve hardware settings (managed by AdvancedSettingsPanel) but update microphone
                            hardware =
                                config.hardware?.copy(
                                    microphone_index = if (selectedMicIndex >= 0) selectedMicIndex else null,
                                ) ?: Babelfish.Hardware(microphone_index = if (selectedMicIndex >= 0) selectedMicIndex else null),
                            // Preserve pipeline settings (managed by AdvancedSettingsPanel)
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
                                    // Preserve activation detection settings (managed by AdvancedSettingsPanel)
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
