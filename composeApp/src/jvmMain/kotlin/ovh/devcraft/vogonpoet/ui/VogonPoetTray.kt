package ovh.devcraft.vogonpoet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import vogonpoet.composeapp.generated.resources.Res
import vogonpoet.composeapp.generated.resources.tray_disconnected
import vogonpoet.composeapp.generated.resources.tray_error
import vogonpoet.composeapp.generated.resources.tray_idle
import vogonpoet.composeapp.generated.resources.tray_listening

@OptIn(ExperimentalResourceApi::class)
@Composable
fun ApplicationScope.VogonPoetTray(
    connectionState: ConnectionState,
    vadState: VadState,
    listeningText: String = "Listening...",
    defaultIcon: Painter,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVadWindow: () -> Unit,
    onOpenTranscriptionWindow: () -> Unit,
    onOpenProtocolLog: () -> Unit,
) {
    val trayState = rememberTrayState()

    // Load all icons using painterResource
    val idleIcon = painterResource(Res.drawable.tray_idle)
    val listeningIcon = painterResource(Res.drawable.tray_listening)
    val disconnectedIcon = painterResource(Res.drawable.tray_disconnected)
    val errorIcon = painterResource(Res.drawable.tray_error)

    // Track current icon based on state
    var currentIcon by remember { mutableStateOf(defaultIcon) }

    // Update icon when state changes
    LaunchedEffect(connectionState, vadState) {
        currentIcon =
            when (connectionState) {
                is ConnectionState.Disconnected -> {
                    disconnectedIcon
                }

                is ConnectionState.Connecting -> {
                    idleIcon
                }

                is ConnectionState.BabelfishRestarting -> {
                    idleIcon
                }

                is ConnectionState.Bootstrapping -> {
                    idleIcon
                }

                is ConnectionState.Connected -> {
                    if (vadState == VadState.Listening) listeningIcon else idleIcon
                }

                is ConnectionState.Error -> {
                    errorIcon
                }
            }
    }

    val tooltip =
        when (connectionState) {
            is ConnectionState.Disconnected -> "VogonPoet: Starting..."
            is ConnectionState.Connecting -> "VogonPoet: Starting..."
            is ConnectionState.BabelfishRestarting -> "VogonPoet: Starting..."
            is ConnectionState.Bootstrapping -> "VogonPoet: Bootstrap"
            is ConnectionState.Connected -> if (vadState == VadState.Listening) "VogonPoet: $listeningText" else "VogonPoet: Ready"
            is ConnectionState.Error -> "VogonPoet: Connection Error"
        }

    var isMenuOpen by remember { mutableStateOf(false) }

    Tray(
        state = trayState,
        icon = currentIcon,
        tooltip = if (isMenuOpen) null else tooltip,
        onAction = {
            // onAction is often called when the tray icon is clicked (especially on macOS/Windows)
        },
        menu = {
            Item("Settings", onClick = {
                isMenuOpen = false
                onOpenSettings()
            })
            Item("Activation Detection", onClick = {
                isMenuOpen = false
                onOpenVadWindow()
            })
            Item("Transcription", onClick = {
                isMenuOpen = false
                onOpenTranscriptionWindow()
            })
            Item("Protocol Log", onClick = {
                isMenuOpen = false
                onOpenProtocolLog()
            })
            Item("Exit", onClick = {
                isMenuOpen = false
                onExit()
            })
        },
    )
}
