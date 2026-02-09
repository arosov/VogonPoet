package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGreenLight

@Composable
fun VadWindow(
    viewModel: MainViewModel,
    onCloseRequest: () -> Unit,
) {
    val vadState by viewModel.vadState.collectAsState()
    val config by viewModel.config.collectAsState()

    // Get activation detection settings
    val iconOnly = config?.ui?.activation_detection?.icon_only ?: false
    val overlayMode = config?.ui?.activation_detection?.overlay_mode ?: false

    // Determine window properties based on overlay mode
    val windowState =
        if (iconOnly) {
            // Small window for icon-only mode
            WindowState(width = 120.dp, height = 120.dp)
        } else {
            // Default size for full mode
            WindowState(width = 300.dp, height = 300.dp)
        }

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
                    when (vadState) {
                        VadState.Idle -> {
                            if (iconOnly) {
                                // Icon only mode - just the microphone
                                Text(
                                    text = "🎤",
                                    fontSize = 48.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.alpha(0.5f),
                                )
                            } else {
                                // Full mode with text
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "🎤",
                                        fontSize = 64.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Idle",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = GruvboxFg0.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Listening",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = GruvboxGreenLight,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Speech detected - processing...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GruvboxFg0.copy(alpha = 0.7f),
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
