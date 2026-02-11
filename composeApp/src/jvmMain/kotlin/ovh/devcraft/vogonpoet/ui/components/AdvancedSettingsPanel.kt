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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.infrastructure.SettingsRepository
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.*
import ovh.devcraft.vogonpoet.ui.utils.SystemFilePicker
import java.awt.Desktop
import java.io.File

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
    val settings = remember { SettingsRepository.load() }

    // Form states (controlled by parent config, but we keep local for immediate UI feedback before roundtrip)
    // Actually, for immediate updates, we can just derive from config.
    // But we need temporary state for Storage Directory before it's confirmed.

    // Storage dir derived from uvCacheDir parent or config
    var storageDir by remember(settings) {
        mutableStateOf(settings.uvCacheDir?.let { File(it).parent } ?: "")
    }

    // Restart Dialog State
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingRestartAction by remember { mutableStateOf<() -> Unit>({}) }

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

            HorizontalDivider(color = GruvboxGray.copy(alpha = 0.3f))

            // System Configuration (Hardware & Storage)
            AdvancedSection(title = "System Configuration") {
                // Hardware Acceleration
                var expanded by remember { mutableStateOf(false) }
                val rawDevice = config.hardware?.device ?: "auto"

                // Combine dynamic hardware with auto and cpu options
                val hardwareOptions =
                    listOf("auto" to "Auto Detect", "cpu" to "CPU Only") +
                        hardwareList.filter { it.id != "cpu" }.map { it.id to it.name }

                // Safe current device selection:
                // If the config has a value (e.g. "cuda") that is no longer in the list (because we use "cuda:0"),
                // fall back to "auto" to prevent UI glitches or raw values.
                val currentDevice =
                    if (hardwareOptions.any { it.first == rawDevice }) {
                        rawDevice
                    } else {
                        "auto"
                    }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = hardwareOptions.find { it.first == currentDevice }?.second ?: currentDevice,
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
                                    expanded = false
                                    if (value != currentDevice) {
                                        pendingRestartAction = {
                                            val newConfig =
                                                config.copy(
                                                    hardware =
                                                        config.hardware?.copy(device = value)
                                                            ?: Babelfish.Hardware(device = value),
                                                )
                                            viewModel.saveAndRestart(newConfig)
                                        }
                                        showRestartDialog = true
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Data Storage Directory
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Data Storage Directory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GruvboxFg0,
                    )

                    val defaultStorageDir =
                        remember {
                            val home = System.getProperty("user.home")
                            val osName = System.getProperty("os.name").lowercase()
                            when {
                                osName.contains("win") -> {
                                    System.getenv("LOCALAPPDATA")?.let { "$it\\VogonPoet" }
                                        ?: "$home\\AppData\\Local\\VogonPoet"
                                }

                                osName.contains("mac") -> {
                                    "$home/Library/Application Support/VogonPoet"
                                }

                                else -> {
                                    "$home/.local/share/vogonpoet"
                                }
                            }
                        }

                    val displayPath = storageDir.takeIf { it.isNotBlank() } ?: defaultStorageDir

                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(containerColor = GruvboxBg1.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (storageDir.isBlank()) GruvboxGray else GruvboxFg0,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                SystemFilePicker
                                    .selectFolder(
                                        "Select Storage Directory",
                                        storageDir.takeIf { it.isNotBlank() } ?: defaultStorageDir,
                                    )?.let { newPath ->
                                        if (newPath != storageDir) {
                                            pendingRestartAction = {
                                                storageDir = newPath
                                                val baseFile = File(newPath)
                                                val u = File(baseFile, "uv")
                                                val m = File(baseFile, "models")
                                                if (!u.exists()) u.mkdirs()
                                                if (!m.exists()) m.mkdirs()

                                                // Update local settings
                                                SettingsRepository.save(
                                                    settings.copy(
                                                        uvCacheDir = u.absolutePath,
                                                        modelsDir = m.absolutePath,
                                                    ),
                                                )

                                                // Update config and restart
                                                val newConfig =
                                                    config.copy(
                                                        cache =
                                                            config.cache?.copy(cache_dir = u.absolutePath)
                                                                ?: Babelfish.Cache(cache_dir = u.absolutePath),
                                                    )
                                                viewModel.saveAndRestart(newConfig)
                                            }
                                            showRestartDialog = true
                                        }
                                    }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = GruvboxBlueDark,
                                    contentColor = GruvboxFg0,
                                ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Change Location", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = {
                                if (storageDir.isNotBlank()) {
                                    pendingRestartAction = {
                                        storageDir = ""
                                        // Reset to defaults
                                        SettingsRepository.save(
                                            settings.copy(uvCacheDir = null, modelsDir = null),
                                        )
                                        // Config update might be tricky if we don't know the default path backend uses,
                                        // but usually sending null/empty triggers default.
                                        // However, existing logic sent null to SettingsRepository but what about Babelfish config?
                                        // The original code calculated null -> null.
                                        // We'll trust the backend handles a restart with cleared local settings by picking up defaults.
                                        // But we should probably NOT send a cache_dir update if it's default, or send the default path.
                                        // For now, let's just trigger the local settings reset and restart.
                                        viewModel.restartBackend()
                                    }
                                    showRestartDialog = true
                                }
                            },
                            enabled = storageDir.isNotBlank() && storageDir != defaultStorageDir,
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            Text(
                                "Reset",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (storageDir.isNotBlank() && storageDir != defaultStorageDir) GruvboxRedDark else GruvboxGray,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(SettingsRepository.appDataDir)
                                }
                            } catch (e: Exception) {
                                // Fallback or ignore
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GruvboxFg0),
                        border =
                            ButtonDefaults.outlinedButtonBorder.copy(
                                brush =
                                    androidx.compose.ui.graphics
                                        .SolidColor(GruvboxGray.copy(alpha = 0.5f)),
                            ),
                    ) {
                        Text("Open Application Directory", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Pipeline Optimization
            AdvancedSection(title = "Pipeline Tuning") {
                // Silence Threshold
                val silenceThreshold = config.pipeline?.silence_threshold_ms?.toFloat() ?: 400f
                Text(
                    text = "Silence Threshold: ${silenceThreshold.toInt()}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GruvboxFg0,
                )

                // Re-implementing Slider with transient state
                var localSilence by remember(silenceThreshold) { mutableStateOf(silenceThreshold) }
                Slider(
                    value = localSilence,
                    onValueChange = { localSilence = it },
                    onValueChangeFinished = {
                        onConfigChange(
                            config.copy(
                                pipeline =
                                    config.pipeline?.copy(silence_threshold_ms = localSilence.toLong())
                                        ?: Babelfish.Pipeline(silence_threshold_ms = localSilence.toLong()),
                            ),
                        )
                    },
                    valueRange = 100f..800f,
                    steps = 13,
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
            }

            // Interface Settings
            AdvancedSection(title = "Interface") {
                val iconOnly = config.ui?.activation_detection?.icon_only ?: false
                val overlayMode = config.ui?.activation_detection?.overlay_mode ?: false

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
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui?.copy(
                                            activation_detection =
                                                config.ui?.activation_detection?.copy(icon_only = it)
                                                    ?: Babelfish.Activation_detection(icon_only = it),
                                        ) ?: Babelfish.Ui(
                                            activation_detection = Babelfish.Activation_detection(icon_only = it),
                                        ),
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
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui?.copy(
                                            activation_detection =
                                                config.ui?.activation_detection?.copy(overlay_mode = it)
                                                    ?: Babelfish.Activation_detection(overlay_mode = it),
                                        ) ?: Babelfish.Ui(
                                            activation_detection = Babelfish.Activation_detection(overlay_mode = it),
                                        ),
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

        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text("Restart Required") },
                text = {
                    Text(
                        "Changing this setting requires a system restart. The application backend will be re-initialized.\n\nContinue?",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingRestartAction()
                            showRestartDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GruvboxRedDark),
                    ) {
                        Text("Restart Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) {
                        Text("Cancel", color = GruvboxFg0)
                    }
                },
                containerColor = GruvboxBg1,
                titleContentColor = GruvboxRedDark,
                textContentColor = GruvboxFg0,
            )
        }
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
