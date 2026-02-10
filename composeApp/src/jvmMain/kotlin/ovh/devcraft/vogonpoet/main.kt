package ovh.devcraft.vogonpoet

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.painterResource
import ovh.devcraft.vogonpoet.infrastructure.BackendManager
import ovh.devcraft.vogonpoet.infrastructure.KwBabelfishClient
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.VogonPoetTray
import ovh.devcraft.vogonpoet.ui.windows.ProtocolLogWindow
import ovh.devcraft.vogonpoet.ui.windows.VadWindow
import vogonpoet.composeapp.generated.resources.Res
import vogonpoet.composeapp.generated.resources.compose_multiplatform

fun main() {
    BackendManager.startBackend()
    application {
        val applicationScope = remember { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
        val babelfishClient = remember { KwBabelfishClient(scope = applicationScope) }
        val viewModel = remember { MainViewModel(babelfishClient) }

        val connectionState by viewModel.connectionState.collectAsState()
        val vadState by viewModel.vadState.collectAsState()
        val icon = painterResource(Res.drawable.compose_multiplatform)

        var showSettings by remember { mutableStateOf(true) }
        var showVadWindow by remember { mutableStateOf(false) }
        var showProtocolLog by remember { mutableStateOf(false) }

        // Settings Window State - compact height to fit content
        // Add 15dp to account for title bar on Linux (window size includes decorations)
        val settingsWindowState =
            rememberWindowState(
                width = 630.dp,
                height = 670.dp,
            )

        // Enforce window size constraints - reset to bounds if resized outside
        LaunchedEffect(settingsWindowState.size) {
            val width = settingsWindowState.size.width
            val height = settingsWindowState.size.height
            val minWidth = 629.dp
            val maxWidth = 731.dp
            val minHeight = 669.dp
            val maxHeight = 671.dp

            println("[Window Resize] Settings window: ${width.value.toInt()}dp x ${height.value.toInt()}dp")

            // Clamp size to min/max bounds if it somehow got resized
            if (width < minWidth || width > maxWidth || height < minHeight || height > maxHeight) {
                println("[Window Constraint] Size out of bounds, clamping...")
                settingsWindowState.size =
                    androidx.compose.ui.unit.DpSize(
                        width = width.coerceIn(minWidth, maxWidth),
                        height = height.coerceIn(minHeight, maxHeight),
                    )
            }
        }

        // Settings Window (Main Configuration)
        if (showSettings) {
            Window(
                onCloseRequest = { showSettings = false },
                title = "VogonPoet - Settings",
                state = settingsWindowState,
                resizable = false,
            ) {
                App(viewModel)
            }
        }

        // VAD Window (Activation Detection)
        if (showVadWindow) {
            VadWindow(
                viewModel = viewModel,
                onCloseRequest = { showVadWindow = false },
            )
        }

        // Protocol Log Window
        if (showProtocolLog) {
            ProtocolLogWindow(
                viewModel = viewModel,
                onCloseRequest = { showProtocolLog = false },
            )
        }

        VogonPoetTray(
            connectionState = connectionState,
            vadState = vadState,
            icon = icon,
            onExit = ::exitApplication,
            onOpenSettings = { showSettings = true },
            onOpenVadWindow = { showVadWindow = true },
            onOpenProtocolLog = { showProtocolLog = true },
        )
    }
}
