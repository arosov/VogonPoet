package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0

/**
 * Window for browsing and downloading models from remote repositories.
 *
 * @param onCloseRequest Callback when the window is closed
 */
@Composable
fun ModelRepositoryBrowserWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(width = 600.dp, height = 500.dp)

    Window(
        onCloseRequest = onCloseRequest,
        title = "Model Repository Browser",
        state = windowState,
    ) {
        MaterialTheme(
            colorScheme = GruvboxDarkColorScheme,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = GruvboxBg1,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Model Repository Browser\n(Implementation in progress)",
                        color = GruvboxFg0,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
