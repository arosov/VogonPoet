package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.components.MessageInspector
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme

@Composable
fun ProtocolLogWindow(
    viewModel: MainViewModel,
    onCloseRequest: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()

    Window(
        onCloseRequest = onCloseRequest,
        title = "VogonPoet - Protocol Log",
    ) {
        MaterialTheme(
            colorScheme = GruvboxDarkColorScheme,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                MessageInspector(
                    messages = messages,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
