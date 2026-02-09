package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import ovh.devcraft.vogonpoet.domain.model.VadState
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGreenLight

@Composable
fun VadWindow(
    viewModel: MainViewModel,
    onCloseRequest: () -> Unit,
) {
    val vadState by viewModel.vadState.collectAsState()

    Window(
        onCloseRequest = onCloseRequest,
        title = "VogonPoet - Activation Detection",
    ) {
        MaterialTheme(
            colorScheme = GruvboxDarkColorScheme,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = GruvboxBg1,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (vadState) {
                        VadState.Idle -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "🎤",
                                    fontSize = 64.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Idle",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = GruvboxFg0.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Waiting for activation...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GruvboxFg0.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        VadState.Listening -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(120.dp)
                                            .background(
                                                color = GruvboxGreenLight.copy(alpha = 0.3f),
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "🎤",
                                        fontSize = 64.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Listening",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = GruvboxGreenLight,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Speech detected - processing...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GruvboxFg0.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
