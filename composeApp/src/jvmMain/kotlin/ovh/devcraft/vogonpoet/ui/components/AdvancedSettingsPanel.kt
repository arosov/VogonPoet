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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.ServerStatus
import ovh.devcraft.vogonpoet.domain.model.VogonConfig
import ovh.devcraft.vogonpoet.infrastructure.AppVersion
import ovh.devcraft.vogonpoet.infrastructure.SettingsRepository
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
    config: VogonConfig?,
    onConfigChange: (VogonConfig) -> Unit,
    onDismiss: () -> Unit = {},
) {
    if (config == null) return

    val hardwareList by viewModel.hardwareList.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isReady = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Bootstrapping
    val scope = rememberCoroutineScope()

    val settings by produceState<ovh.devcraft.vogonpoet.domain.VogonSettings?>(null) {
        value = SettingsRepository.load()
    }

    if (settings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GruvboxGreenDark)
        }
        return
    }

    val nonNullSettings = settings ?: return

    // Form states (controlled by parent config, but we keep local for immediate UI feedback before roundtrip)
    // Actually, for immediate updates, we can just derive from config.
    // But we need temporary state for Storage Directory before it's confirmed.

    // Storage dir derived from uvCacheDir parent or config
    var storageDir by remember(nonNullSettings) {
        mutableStateOf(nonNullSettings.uvCacheDir?.let { File(it).parent } ?: "")
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp), // Add padding for scrollbar
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Advanced Settings",
                style = MaterialTheme.typography.titleLarge,
                color = GruvboxFg0,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "v${AppVersion.version}",
                style = MaterialTheme.typography.bodySmall,
                color = GruvboxGray.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = GruvboxGray.copy(alpha = 0.3f))

            // System Configuration (Hardware & Storage)
            AdvancedSection(title = "System Configuration") {
                // Hardware Acceleration
                var expanded by remember { mutableStateOf(false) }
                val rawDevice = config.hardware.device
                val isAutoDetect = config.hardware.autoDetect

                // Combine dynamic hardware with auto and cpu options
                val hardwareOptions =
                    listOf("auto" to "Auto Detect (Recommended)", "cpu" to "CPU Only") +
                        hardwareList.filter { it.id != "cpu" && it.id != "auto" }.map { it.id to it.name }

                // Safe current device selection:
                val currentDevice =
                    when {
                        isAutoDetect -> "auto"
                        hardwareOptions.any { it.first == rawDevice } -> rawDevice
                        else -> "auto"
                    }

                ExposedDropdownMenuBox(
                    expanded = expanded && isReady,
                    onExpandedChange = { if (isReady) expanded = it },
                ) {
                    OutlinedTextField(
                        value = hardwareOptions.find { it.first == currentDevice }?.second ?: currentDevice,
                        onValueChange = {},
                        enabled = isReady,
                        readOnly = true,
                        label = { Text("Processing Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && isReady) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = isReady).fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GruvboxGreenDark,
                                focusedLabelColor = GruvboxGreenDark,
                                disabledBorderColor = GruvboxGray.copy(alpha = 0.5f),
                                disabledLabelColor = GruvboxGray,
                                disabledTextColor = GruvboxFg0.copy(alpha = 0.5f),
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
                                    onDismiss()
                                    if (value != currentDevice) {
                                        val activeDevice = config.hardware.activeDevice
                                        val isCurrentlyAuto = config.hardware.autoDetect

                                        // If switching from Auto to the device it's ALREADY using, skip restart.
                                        val isSameAsActive = isCurrentlyAuto && value == activeDevice && value != "cpu"

                                        val newConfig =
                                            config.copy(
                                                hardware =
                                                    config.hardware.copy(
                                                        device = if (value == "auto") "auto" else value,
                                                        autoDetect = value == "auto",
                                                    ),
                                            )

                                        if (isSameAsActive) {
                                            viewModel.saveConfig(newConfig)
                                        } else {
                                            viewModel.saveAndRestart(newConfig)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                config.hardware.vramTotalGb?.let { total ->
                    val isActiveCpu = config.hardware.activeDevice?.lowercase() == "cpu"
                    val isConfiguredCpu = config.hardware.device.lowercase() == "cpu"
                    if (total > 0 && !isActiveCpu && !isConfiguredCpu) {
                        Spacer(modifier = Modifier.height(8.dp))
                        VramUsageBar(
                            total = total,
                            baseline = config.hardware.vramUsedBaselineGb ?: 0.0,
                            model = config.hardware.vramUsedModelGb ?: 0.0,
                            deviceName = config.hardware.activeDeviceName ?: config.hardware.activeDevice,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
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
                                    System.getenv("LOCALAPPDATA")?.let { "$it\\VogonPoet\\Cache" }
                                        ?: "$home\\AppData\\Local\\VogonPoet\\Cache"
                                }

                                osName.contains("mac") -> {
                                    "$home/Library/Caches/VogonPoet"
                                }

                                else -> {
                                    "$home/.cache/vogonpoet"
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
                                            storageDir = newPath
                                            val baseFile = File(newPath)
                                            val u = File(baseFile, "uv")
                                            val m = File(baseFile, "models")
                                            if (!u.exists()) u.mkdirs()
                                            if (!m.exists()) m.mkdirs()

                                            // Update local settings
                                            scope.launch {
                                                SettingsRepository.save(
                                                    nonNullSettings.copy(
                                                        uvCacheDir = u.absolutePath,
                                                        modelsDir = m.absolutePath,
                                                    ),
                                                )
                                            }

                                            // Update config and restart immediately
                                            val newConfig =
                                                config.copy(
                                                    cache =
                                                        config.cache.copy(cacheDir = u.absolutePath),
                                                )
                                            viewModel.saveAndRestart(newConfig)
                                        }
                                    }
                            },
                            enabled = isReady,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = GruvboxBlueDark,
                                    contentColor = GruvboxFg0,
                                    disabledContainerColor = GruvboxBlueDark.copy(alpha = 0.5f),
                                    disabledContentColor = GruvboxFg0.copy(alpha = 0.5f),
                                ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Change Location", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = {
                                if (storageDir.isNotBlank()) {
                                    storageDir = ""
                                    // Reset to defaults
                                    scope.launch {
                                        SettingsRepository.save(
                                            nonNullSettings.copy(uvCacheDir = null, modelsDir = null),
                                        )
                                    }
                                    // Restart backend immediately with reset settings
                                    viewModel.restartBackend()
                                }
                            },
                            enabled = isReady && storageDir.isNotBlank() && storageDir != defaultStorageDir,
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            Text(
                                "Reset",
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (isReady && storageDir.isNotBlank() &&
                                        storageDir != defaultStorageDir
                                    ) {
                                        GruvboxRedDark
                                    } else {
                                        GruvboxGray
                                    },
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
                            ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush =
                                    androidx.compose.ui.graphics
                                        .SolidColor(GruvboxGray.copy(alpha = 0.5f)),
                            ),
                    ) {
                        Text("Open config & logs", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Pipeline Optimization
            AdvancedSection(title = "Pipeline Tuning") {
                Text(
                    text = "How long to wait after voice stops before finalizing a sentence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Silence Threshold
                val rawThreshold = config.pipeline.silenceThresholdMs.toFloat()
                val silenceThreshold = (Math.round(rawThreshold / 50.0) * 50).toFloat()

                Text(
                    text = "Silence Threshold: ${silenceThreshold.toInt()}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GruvboxFg0,
                )

                // Re-implementing Slider with transient state
                var localSilence by remember(silenceThreshold) { mutableStateOf(silenceThreshold) }
                Slider(
                    value = localSilence,
                    onValueChange = {
                        localSilence = (Math.round(it / 50.0) * 50).toFloat()
                    },
                    onValueChangeFinished = {
                        onConfigChange(
                            config.copy(
                                pipeline =
                                    config.pipeline.copy(silenceThresholdMs = localSilence.toLong()),
                            ),
                        )
                    },
                    enabled = isReady,
                    valueRange = 100f..800f,
                    steps = 13,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = GruvboxGreenDark,
                            activeTrackColor = GruvboxGreenDark,
                            disabledThumbColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            disabledActiveTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                        ),
                )
            }

            // Voice Detection Tuning
            AdvancedSection(title = "Voice Detection Tuning") {
                Text(
                    text = "Adjust detection sensitivity. This sets the similarity threshold for matching wake-words and stop-words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Wakeword Sensitivity
                val sensitivity = config.voice.wakewordSensitivity?.toFloat() ?: 0.5f
                var localSensitivity by remember(sensitivity) { mutableStateOf(sensitivity) }

                Text(
                    text = "Wakeword Sensitivity: ${(localSensitivity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GruvboxFg0,
                )
                Slider(
                    value = localSensitivity,
                    onValueChange = { localSensitivity = (Math.round(it * 10f) / 10f) },
                    onValueChangeFinished = {
                        onConfigChange(
                            config.copy(
                                voice =
                                    config.voice.copy(wakewordSensitivity = localSensitivity.toDouble()),
                            ),
                        )
                    },
                    enabled = isReady,
                    valueRange = 0.1f..0.9f,
                    steps = 7,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = GruvboxGreenDark,
                            activeTrackColor = GruvboxGreenDark,
                            disabledThumbColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            disabledActiveTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                        ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stop Word Sensitivity
                val stopSensitivity = config.voice.stopWakewordSensitivity?.toFloat() ?: 0.5f
                var localStopSensitivity by remember(stopSensitivity) { mutableStateOf(stopSensitivity) }

                Text(
                    text = "Stop Word Sensitivity: ${(localStopSensitivity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GruvboxFg0,
                )
                Slider(
                    value = localStopSensitivity,
                    onValueChange = { localStopSensitivity = (Math.round(it * 10f) / 10f) },
                    onValueChangeFinished = {
                        onConfigChange(
                            config.copy(
                                voice =
                                    config.voice.copy(stopWakewordSensitivity = localStopSensitivity.toDouble()),
                            ),
                        )
                    },
                    enabled = isReady,
                    valueRange = 0.1f..0.9f,
                    steps = 7,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = GruvboxGreenDark,
                            activeTrackColor = GruvboxGreenDark,
                            disabledThumbColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            disabledActiveTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                        ),
                )
            }

            // System-wide Input
            AdvancedSection(title = "System-wide Input") {
                Text(
                    text = "Automatically type transcription into other applications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val inputEnabled = config.systemInput.enabled
                val typeGhost = config.systemInput.typeGhost

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable System Input",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Text(
                            text = "Type text into the focused window",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = inputEnabled,
                        onCheckedChange = {
                            onConfigChange(
                                config.copy(
                                    systemInput =
                                        config.systemInput.copy(enabled = it),
                                ),
                            )
                        },
                        enabled = isReady,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Type Ghost Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Type Ghost Output",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Text(
                            text = "Show real-time typing (replaces by backspacing)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = typeGhost,
                        onCheckedChange = {
                            onConfigChange(
                                config.copy(
                                    systemInput =
                                        config.systemInput.copy(typeGhost = it),
                                ),
                            )
                        },
                        enabled = isReady && inputEnabled,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            ),
                    )
                }
            }

            // Interface Settings
            AdvancedSection(title = "Notifications") {
                val notifications = config.ui.notifications

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Desktop Notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Text(
                            text = "Show alerts when the engine is triggered or stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = notifications,
                        onCheckedChange = {
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui.copy(
                                            notifications = it,
                                        ),
                                ),
                            )
                        },
                        enabled = isReady,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                                disabledCheckedThumbColor = GruvboxGreenDark.copy(alpha = 0.5f),
                                disabledCheckedTrackColor = GruvboxGreenDark.copy(alpha = 0.25f),
                            ),
                    )
                }
            }

            AdvancedSection(title = "Activation Detection Indicator") {
                Text(
                    text = "This floating status window is accessible via the system tray icon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val iconOnly = config.ui.activationDetection.iconOnly
                val overlayMode = config.ui.activationDetection.overlayMode

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
                                        config.ui.copy(
                                            activationDetection =
                                                config.ui.activationDetection.copy(iconOnly = it),
                                        ),
                                ),
                            )
                        },
                        enabled = isReady,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                                disabledCheckedThumbColor = GruvboxGreenDark.copy(alpha = 0.5f),
                                disabledCheckedTrackColor = GruvboxGreenDark.copy(alpha = 0.25f),
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
                                        config.ui.copy(
                                            activationDetection =
                                                config.ui.activationDetection.copy(overlayMode = it),
                                        ),
                                ),
                            )
                        },
                        enabled = isReady,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                                disabledCheckedThumbColor = GruvboxGreenDark.copy(alpha = 0.5f),
                                disabledCheckedTrackColor = GruvboxGreenDark.copy(alpha = 0.25f),
                            ),
                    )
                }
            }

            AdvancedSection(title = "Transcription Window") {
                Text(
                    text = "Controls for the dedicated transcription flow window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val alwaysOnTop = config.ui.transcriptionWindow.alwaysOnTop

                // Always on Top Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always on Top",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GruvboxFg0,
                        )
                        Text(
                            text = "Keep window above all other applications",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxFg0.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = alwaysOnTop,
                        onCheckedChange = {
                            onConfigChange(
                                config.copy(
                                    ui =
                                        config.ui.copy(
                                            transcriptionWindow =
                                                config.ui.transcriptionWindow.copy(alwaysOnTop = it),
                                        ),
                                ),
                            )
                        },
                        enabled = isReady,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = GruvboxGreenDark,
                                checkedTrackColor = GruvboxGreenDark.copy(alpha = 0.5f),
                            ),
                    )
                }
            }

            AdvancedSection(title = "Wake Word Models") {
                Text(
                    text = "Download additional wake word models from community repositories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                var showRepositoryBrowser by remember { mutableStateOf(false) }

                Button(
                    onClick = { showRepositoryBrowser = true },
                    enabled = isReady,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GruvboxBlueDark,
                            contentColor = GruvboxFg0,
                            disabledContainerColor = GruvboxBlueDark.copy(alpha = 0.5f),
                            disabledContentColor = GruvboxFg0.copy(alpha = 0.5f),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Browse Model Repositories", style = MaterialTheme.typography.bodySmall)
                }

                if (showRepositoryBrowser) {
                    ovh.devcraft.vogonpoet.ui.windows.ModelRepositoryBrowserWindow(
                        onCloseRequest = { showRepositoryBrowser = false },
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

@Composable
fun VramUsageBar(
    total: Double,
    baseline: Double,
    model: Double,
    deviceName: String? = null,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return

    val free = (total - model).coerceAtLeast(0.0)
    val modelOnly = (model - baseline).coerceAtLeast(0.0)

    // Normalize weights for the progress bar
    val baselineWeight = (baseline / total).toFloat().coerceIn(0.01f, 1f)
    val modelWeight = (modelOnly / total).toFloat().coerceIn(0.01f, 1f)
    val freeWeight = (free / total).toFloat().coerceIn(0.01f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        deviceName?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = GruvboxFg0.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VRAM Usage", style = MaterialTheme.typography.labelMedium, color = GruvboxFg0)
            Text("${model.format(1)} / ${total.format(1)} GB", style = MaterialTheme.typography.labelSmall, color = GruvboxFg0)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(GruvboxBg0),
        ) {
            if (baselineWeight > 0) {
                Box(Modifier.fillMaxHeight().weight(baselineWeight).background(GruvboxBlueDark))
            }
            if (modelWeight > 0) {
                Box(Modifier.fillMaxHeight().weight(modelWeight).background(GruvboxYellowDark))
            }
            if (freeWeight > 0) {
                Box(Modifier.fillMaxHeight().weight(freeWeight).background(GruvboxGray.copy(alpha = 0.2f)))
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem("System", GruvboxBlueDark)
            LegendItem("Babelfish", GruvboxYellowDark)
        }
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun LegendItem(
    label: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, shape = RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = GruvboxFg0.copy(alpha = 0.6f))
    }
}
