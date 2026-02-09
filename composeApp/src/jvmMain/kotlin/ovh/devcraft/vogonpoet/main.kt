package ovh.devcraft.vogonpoet

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.painterResource
import ovh.devcraft.vogonpoet.infrastructure.BackendManager
import ovh.devcraft.vogonpoet.infrastructure.KwBabelfishClient
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.VogonPoetTray
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

        VogonPoetTray(
            connectionState = connectionState,
            vadState = vadState,
            icon = icon,
            onExit = ::exitApplication,
            onReconnect = { viewModel.reconnect() },
            onRestartEngine = { viewModel.restartBackend() },
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "VogonPoet",
        ) {
            App(viewModel)
        }
    }
}
