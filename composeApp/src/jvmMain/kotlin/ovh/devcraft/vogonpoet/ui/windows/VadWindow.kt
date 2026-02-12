package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.VogonLogger
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGray
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGreenLight

@Composable
fun VadWindow(
    viewModel: MainViewModel,
    onCloseRequest: () -> Unit,
) {
    val vadState by viewModel.vadState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val config by viewModel.draftConfig.collectAsState()

    // Get activation detection settings
    val iconOnly = config?.ui?.activation_detection?.icon_only ?: false
    val overlayMode = config?.ui?.activation_detection?.overlay_mode ?: false

    // Check if backend is ready
    val isReady = connectionState is ConnectionState.Connected

    // Remember window state across property changes to preserve position
    val windowState =
        remember {
            val width = if (iconOnly) 85.dp else 170.dp
            val height = if (iconOnly) 85.dp else 200.dp
            WindowState(width = width, height = height)
        }

    // Enforce exact window size (1px tolerance)
    LaunchedEffect(windowState.size, iconOnly) {
        val currentSize = windowState.size
        val targetWidth = if (iconOnly) 85.dp else 170.dp
        val targetHeight = if (iconOnly) 85.dp else 200.dp

        // Check if size is more than 1px off from target
        val widthDiff = kotlin.math.abs(currentSize.width.value - targetWidth.value)
        val heightDiff = kotlin.math.abs(currentSize.height.value - targetHeight.value)

        if (widthDiff > 1f || heightDiff > 1f) {
            VogonLogger.i(
                "[VAD Window] Size clamping: ${currentSize.width.value.toInt()}x${currentSize.height.value.toInt()} -> ${targetWidth.value.toInt()}x${targetHeight.value.toInt()}",
            )
            windowState.size =
                androidx.compose.ui.unit
                    .DpSize(targetWidth, targetHeight)
        }
    }

    key(overlayMode) {
        Window(
            onCloseRequest = onCloseRequest,
            title = "VogonPoet - Activation Detection",
            state = windowState,
            // Overlay mode properties
            undecorated = overlayMode,
            alwaysOnTop = overlayMode,
            focusable = !overlayMode,
            transparent = overlayMode,
        ) {
            MaterialTheme(
                colorScheme = GruvboxDarkColorScheme,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (overlayMode) GruvboxBg1.copy(alpha = 0.8f) else GruvboxBg1,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Content with reduced alpha when not ready
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .alpha(if (isReady) 1f else 0.3f),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (vadState) {
                                VadState.Idle -> {
                                    if (iconOnly) {
                                        // Icon only mode - just the microphone
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(80.dp)
                                                    .background(
                                                        color = androidx.compose.ui.graphics.Color.Transparent,
                                                        shape = androidx.compose.foundation.shape.CircleShape,
                                                    ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "🎤",
                                                fontSize = 48.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.alpha(0.5f),
                                            )
                                        }
                                    } else {
                                        // Full mode with text
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(120.dp)
                                                        .background(
                                                            color = androidx.compose.ui.graphics.Color.Transparent,
                                                            shape = androidx.compose.foundation.shape.CircleShape,
                                                        ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "🎤",
                                                    fontSize = 64.sp,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Idle",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = GruvboxFg0.copy(alpha = 0.6f),
                                                textAlign = TextAlign.Center,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Waiting for activation...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = GruvboxFg0.copy(alpha = 0.5f),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }

                                VadState.Listening -> {
                                    if (iconOnly) {
                                        // Icon only mode - glowing microphone
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(80.dp)
                                                    .background(
                                                        color = GruvboxGreenLight.copy(alpha = 0.3f),
                                                        shape = androidx.compose.foundation.shape.CircleShape,
                                                    ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "🎤",
                                                fontSize = 48.sp,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    } else {
                                        // Full mode with text
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(120.dp)
                                                        .background(
                                                            color = GruvboxGreenLight.copy(alpha = 0.3f),
                                                            shape = androidx.compose.foundation.shape.CircleShape,
                                                        ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "🎤",
                                                    fontSize = 64.sp,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Listening",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = GruvboxGreenLight,
                                                textAlign = TextAlign.Center,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Speech detected",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = GruvboxFg0.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Overlay when backend is not ready
                        if (!isReady) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(GruvboxBg1.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (iconOnly) {
                                    Text(
                                        text = "⏳",
                                        fontSize = 32.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = "⏳",
                                            fontSize = 48.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Initializing...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GruvboxGray,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
