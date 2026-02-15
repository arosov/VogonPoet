package ovh.devcraft.vogonpoet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.infrastructure.UpdateInfo
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
    updateInfo: UpdateInfo? = null,
    onOpenDownload: () -> Unit = {},
) {
    val trayState = rememberTrayState()

    val idleIcon = painterResource(Res.drawable.tray_idle)
    val listeningIcon = painterResource(Res.drawable.tray_listening)
    val disconnectedIcon = painterResource(Res.drawable.tray_disconnected)
    val errorIcon = painterResource(Res.drawable.tray_error)

    var currentIcon by remember { mutableStateOf(defaultIcon) }

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

    var notificationShown by remember { mutableStateOf(false) }

    LaunchedEffect(updateInfo) {
        if (updateInfo != null && !notificationShown) {
            notificationShown = true
            try {
                trayState.sendNotification(
                    notification =
                        Notification(
                            title = "VogonPoet Update Available",
                            message = "Version ${updateInfo.latestVersion} is available. Click to download.",
                        ),
                )
            } catch (e: Exception) {
                // Notification may not be supported on all platforms
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
            if (updateInfo != null) {
                Item("Download Update (${updateInfo.latestVersion})", onClick = {
                    isMenuOpen = false
                    onOpenDownload()
                })
                Separator()
            }
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
