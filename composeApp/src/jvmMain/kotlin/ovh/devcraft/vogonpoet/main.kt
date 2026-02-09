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
        var showVadWindow by remember { mutableStateOf(true) } // TEST: Show on start
        var showProtocolLog by remember { mutableStateOf(false) }

        // Settings Window State - narrower and taller
        val settingsWindowState =
            rememberWindowState(
                width = 420.dp,
                height = 800.dp,
            )

        // Settings Window (Main Configuration)
        if (showSettings) {
            Window(
                onCloseRequest = { showSettings = false },
                title = "VogonPoet - Settings",
                state = settingsWindowState,
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
