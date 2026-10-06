package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.devcraft.vogonpoet.domain.model.UtteranceTelemetry
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg0
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg2
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGray
import ovh.devcraft.vogonpoet.ui.theme.GruvboxYellowDark

/**
 * Collapsible three-section telemetry panel for the ghost pass of the
 * current utterance:
 *
 * 1. raw ghost increments (one row per window transcription, kept for the
 *    whole utterance, gray italic),
 * 2. the on-progress composed ghost — committed words in normal
 *    foreground, live tail gray italic,
 * 3. the final transcript (bold) with the server timing strip.
 *
 * Collapse state is session-only: the panel opens at every startup and
 * stays open until the user collapses it.
 */
@Composable
fun TranscriptTelemetryCard(
    telemetry: UtteranceTelemetry,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(0.85f).padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = GruvboxBg1),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isExpanded) "▾" else "▸",
                    color = GruvboxYellowDark,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ghost pass — current utterance",
                    color = GruvboxFg2,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("1 · Ghost increments (raw window transcriptions)")
                    if (telemetry.increments.isEmpty()) {
                        if (telemetry.finalText == null) {
                            WaitingForVoice()
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            telemetry.increments.forEach { increment ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "👻 t=%+dms".format(increment.arrivalOffsetMs),
                                        color = GruvboxGray,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = increment.raw,
                                        color = GruvboxGray,
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    SectionTitle("2 · On-progress ghost (composed, live)")
                    Text(
                        text =
                            buildAnnotatedString {
                                if (telemetry.committed.isNotBlank()) {
                                    withStyle(style = SpanStyle(color = GruvboxFg0)) {
                                        append(telemetry.committed)
                                    }
                                }
                                if (telemetry.live.isNotBlank()) {
                                    if (telemetry.committed.isNotBlank()) {
                                        append(" ")
                                    }
                                    withStyle(
                                        style =
                                            SpanStyle(
                                                color = GruvboxGray,
                                                fontStyle = FontStyle.Italic,
                                            ),
                                    ) {
                                        append(telemetry.live)
                                    }
                                }
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionTitle("3 · Final")
                    val finalText = telemetry.finalText
                    if (finalText != null) {
                        Text(
                            text = finalText,
                            color = GruvboxFg0,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val strip =
                            buildString {
                                append("⏱")
                                telemetry.firstGhostDelayMs?.let { append(" first ghost %.0fms".format(it)) }
                                telemetry.finalComputeMs?.let { append(" · final pass %.0fms".format(it)) }
                                telemetry.utteranceDurationMs?.let { append(" · duration %.2fs".format(it)) }
                            }
                        Text(
                            text = strip,
                            color = GruvboxFg2,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "final pass pending…",
                            color = GruvboxGray,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = GruvboxFg2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun WaitingForVoice() {
    Text(
        text = "waiting for voice…",
        color = GruvboxGray,
        fontSize = 13.sp,
        fontStyle = FontStyle.Italic,
    )
}
