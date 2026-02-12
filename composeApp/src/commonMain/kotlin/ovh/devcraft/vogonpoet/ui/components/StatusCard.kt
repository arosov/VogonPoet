package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.EngineMode
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.ui.theme.*

@Composable
fun OutlinedStatusText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = GruvboxFg0,
) {
    // Use smooth blur shadow for anti-aliased outline effect
    Text(
        text = text,
        style =
            style.copy(
                shadow =
                    androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset =
                            androidx.compose.ui.geometry
                                .Offset(0f, 0f),
                        blurRadius = 6f,
                    ),
            ),
        color = color,
        modifier = modifier,
    )
}

@Composable
fun StatusCard(
    connectionState: ConnectionState,
    vadState: VadState,
    engineMode: EngineMode = EngineMode.Wakeword,
    transcribingText: String = "Transcribing...",
    modifier: Modifier = Modifier,
    config: Babelfish? = null,
    displayedEvent: String? = null,
) {
    val baseColor =
        when (connectionState) {
            is ConnectionState.Disconnected -> {
                GruvboxBlueDark
            }

            is ConnectionState.Connecting -> {
                GruvboxBlueDark
            }

            is ConnectionState.BabelfishRestarting -> {
                GruvboxBlueDark
            }

            is ConnectionState.Bootstrapping -> {
                GruvboxBlueDark
            }

            is ConnectionState.Connected -> {
                if (vadState == VadState.Listening ||
                    displayedEvent != null
                ) {
                    GruvboxGreenLight
                } else {
                    GruvboxGreenDarker
                }
            }

            is ConnectionState.Error -> {
                GruvboxRedDark
            }
        }

    val color by animateColorAsState(baseColor)

    // Pulsing animation for active states
    val infiniteTransition = rememberInfiniteTransition()
    val animatedAlpha =
        if ((connectionState is ConnectionState.Connected && (vadState == VadState.Listening || displayedEvent != null)) ||
            connectionState is ConnectionState.Bootstrapping
        ) {
            infiniteTransition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        } else {
            null
        }

    val alpha = animatedAlpha?.value ?: 1f

    Card(
        modifier =
            modifier
                .fillMaxWidth(0.85f)
                .height(120.dp)
                .padding(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = color.copy(alpha = alpha),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OutlinedStatusText(
                modifier = Modifier,
                text =
                    when (connectionState) {
                        is ConnectionState.Disconnected -> {
                            "Bootstrap"
                        }

                        is ConnectionState.Connecting -> {
                            "Starting..."
                        }

                        is ConnectionState.BabelfishRestarting -> {
                            "Starting..."
                        }

                        is ConnectionState.Bootstrapping -> {
                            "Starting..."
                        }

                        is ConnectionState.Connected -> {
                            val substatus =
                                displayedEvent ?: when (vadState) {
                                    VadState.Listening -> {
                                        null
                                    }

                                    VadState.Idle -> {
                                        when (engineMode) {
                                            EngineMode.Wakeword -> "Idle"
                                            EngineMode.Active -> "Listening"
                                        }
                                    }
                                }
                            if (substatus != null) {
                                "Ready - $substatus"
                            } else if (vadState == VadState.Listening) {
                                transcribingText
                            } else {
                                "Ready"
                            }
                        }

                        is ConnectionState.Error -> {
                            "Connection Error"
                        }
                    },
                style = MaterialTheme.typography.headlineMedium,
            )

            if (connectionState is ConnectionState.Connected && vadState != VadState.Listening) {
                val deviceName = config?.hardware?.active_device_name ?: config?.hardware?.active_device
                deviceName?.let { device ->
                    OutlinedStatusText(
                        text = "Running on ${device.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GruvboxFg0.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (connectionState is ConnectionState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxFg1.copy(alpha = 0.9f),
                )
            }

            if (connectionState is ConnectionState.Bootstrapping ||
                connectionState is ConnectionState.BabelfishRestarting ||
                connectionState is ConnectionState.Connecting
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        when (connectionState) {
                            is ConnectionState.Bootstrapping -> connectionState.message
                            is ConnectionState.Connecting -> "Connecting to server..."
                            else -> "Waiting for server to initialize..."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxFg1.copy(alpha = 0.9f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = GruvboxFg1,
                    trackColor = GruvboxBg0.copy(alpha = 0.3f),
                )
            }
        }
    }
}
