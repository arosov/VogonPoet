package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.ui.theme.*

@Composable
fun StatusCard(
    connectionState: ConnectionState,
    vadState: VadState,
    modifier: Modifier = Modifier,
) {
    val baseColor =
        when (connectionState) {
            is ConnectionState.Disconnected -> GruvboxGray
            is ConnectionState.Connecting -> GruvboxBlueDark
            is ConnectionState.Bootstrapping -> GruvboxYellowDark
            is ConnectionState.Connected -> if (vadState == VadState.Listening) GruvboxGreenLight else GruvboxGreenDark
            is ConnectionState.Error -> GruvboxRedDark
        }

    val color by animateColorAsState(baseColor)

    // Pulsing animation for Listening state
    val infiniteTransition = rememberInfiniteTransition()
    val animatedAlpha =
        if (connectionState is ConnectionState.Connected && vadState == VadState.Listening) {
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
                .fillMaxWidth()
                .padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = color.copy(alpha = alpha),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    when (connectionState) {
                        is ConnectionState.Disconnected -> "Disconnected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.Bootstrapping -> "Setting Up..."
                        is ConnectionState.Connected -> if (vadState == VadState.Listening) "Listening" else "Ready"
                        is ConnectionState.Error -> "Connection Error"
                    },
                style = MaterialTheme.typography.headlineMedium,
                color = GruvboxFg0,
            )

            if (connectionState is ConnectionState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = GruvboxFg1.copy(alpha = 0.9f),
                )
            }

            if (connectionState is ConnectionState.Bootstrapping) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionState.message,
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
