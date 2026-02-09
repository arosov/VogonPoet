package ovh.devcraft.vogonpoet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState

@Composable
fun ApplicationScope.VogonPoetTray(
    connectionState: ConnectionState,
    vadState: VadState,
    icon: Painter,
    onExit: () -> Unit,
    onReconnect: () -> Unit,
    onRestartEngine: () -> Unit,
) {
    val trayState = rememberTrayState()
    val tooltip =
        when (connectionState) {
            is ConnectionState.Disconnected -> "VogonPoet: Disconnected"
            is ConnectionState.Connecting -> "VogonPoet: Connecting..."
            is ConnectionState.Bootstrapping -> "VogonPoet: Setting Up..."
            is ConnectionState.Connected -> if (vadState == VadState.Listening) "VogonPoet: Listening" else "VogonPoet: Connected"
            is ConnectionState.Error -> "VogonPoet: Connection Error"
        }

    Tray(
        state = trayState,
        icon = icon,
        tooltip = tooltip,
        menu = {
            Item("Reconnect", onClick = onReconnect)
            Item("Restart Engine", onClick = onRestartEngine)
            Item("Exit", onClick = onExit)
        },
    )
}
