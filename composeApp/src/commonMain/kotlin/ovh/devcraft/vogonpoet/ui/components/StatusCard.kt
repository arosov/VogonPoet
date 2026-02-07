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

@Composable
fun StatusCard(
    connectionState: ConnectionState,
    vadState: VadState,
    modifier: Modifier = Modifier
) {
    val baseColor = when (connectionState) {
        is ConnectionState.Disconnected -> Color.Gray
        is ConnectionState.Connecting -> Color.Blue
        is ConnectionState.Connected -> if (vadState == VadState.Listening) Color.Green else Color(0xFF4CAF50) // MD3 Green
        is ConnectionState.Error -> Color(0xFFB71C1C) // MD3 Red 900
    }

    val color by animateColorAsState(baseColor)

    // Pulsing animation for Listening state
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by if (connectionState is ConnectionState.Connected && vadState == VadState.Listening) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = alpha)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (connectionState) {
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Connected -> if (vadState == VadState.Listening) "Listening" else "Connected"
                    is ConnectionState.Error -> "Connection Error"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            
            if (connectionState is ConnectionState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
