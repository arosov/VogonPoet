package ovh.devcraft.vogonpoet.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ovh.devcraft.vogonpoet.domain.model.ConnectionState
import ovh.devcraft.vogonpoet.domain.model.TranscriptionState
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxDarkColorScheme
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGray

@Composable
fun TranscriptionWindow(
    viewModel: MainViewModel,
    onCloseRequest: () -> Unit,
) {
    val transcription by viewModel.transcription.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val config by viewModel.config.collectAsState()

    val alwaysOnTop = config?.ui?.transcriptionWindow?.alwaysOnTop ?: true
    val isReady = connectionState is ConnectionState.Connected

    val windowState = rememberWindowState(width = 400.dp, height = 300.dp)
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when new text arrives
    LaunchedEffect(transcription) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "VogonPoet - Transcription",
        state = windowState,
        alwaysOnTop = alwaysOnTop,
    ) {
        MaterialTheme(
            colorScheme = GruvboxDarkColorScheme,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = GruvboxBg1,
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val annotatedText =
                            buildAnnotatedString {
                                // Finalized sentences
                                transcription.finalizedText.forEach { sentence ->
                                    withStyle(style = SpanStyle(color = GruvboxFg0)) {
                                        append(sentence)
                                        append(" ")
                                    }
                                }

                                // Ghost text
                                if (transcription.ghostText.isNotBlank()) {
                                    withStyle(
                                        style =
                                            SpanStyle(
                                                color = GruvboxGray,
                                                fontStyle = FontStyle.Italic,
                                            ),
                                    ) {
                                        append(transcription.ghostText)
                                    }
                                }
                            }

                        if (annotatedText.isEmpty() && !isReady) {
                            Text(
                                text = "Waiting for connection...",
                                color = GruvboxGray,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        } else if (annotatedText.isEmpty()) {
                            Text(
                                text = "Ready to transcribe...",
                                color = GruvboxGray,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        } else {
                            Text(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
