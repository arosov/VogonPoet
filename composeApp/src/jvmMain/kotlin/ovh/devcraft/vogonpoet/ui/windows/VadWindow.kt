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

    // Determine window properties based on mode
    // Skinny window: 60dp width for icon-only, 200dp for full mode
    val windowState =
        if (iconOnly) {
            WindowState(width = 60.dp, height = 60.dp)
        } else {
            WindowState(width = 200.dp, height = 200.dp)
        }

    Window(
        onCloseRequest = onCloseRequest,
        title = "VogonPoet",
        state = windowState,
        undecorated = overlayMode || iconOnly, // Undecorated in both overlay and icon-only modes
        alwaysOnTop = overlayMode,
        focusable = !overlayMode && !iconOnly,
        transparent = overlayMode || iconOnly,
    ) {
        MaterialTheme(
            colorScheme = GruvboxDarkColorScheme,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (overlayMode || iconOnly) GruvboxBg1.copy(alpha = 0.0f) else GruvboxBg1,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (vadState) {
                        VadState.Idle -> {
                            if (iconOnly) {
                                // Icon only mode - tiny microphone
                                Text(
                                    text = "🎤",
                                    fontSize = 32.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.alpha(0.4f),
                                )
                            } else {
                                // Full mode with text - compact
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    Text(
                                        text = "🎤",
                                        fontSize = 48.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Idle",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GruvboxFg0.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        VadState.Listening -> {
                            if (iconOnly) {
                                // Icon only mode - glowing microphone, compact
                                Box(
                                    modifier =
                                        Modifier
                                            .size(48.dp)
                                            .background(
                                                color = GruvboxGreenLight.copy(alpha = 0.4f),
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "🎤",
                                        fontSize = 32.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                // Full mode with text - compact
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(8.dp),
                                ) {
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Listening",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GruvboxGreenLight,
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
