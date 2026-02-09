package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.devcraft.vogonpoet.domain.model.MessageDirection
import ovh.devcraft.vogonpoet.domain.model.ProtocolMessage
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1
import ovh.devcraft.vogonpoet.ui.theme.GruvboxFg2
import ovh.devcraft.vogonpoet.ui.theme.GruvboxGreen
import ovh.devcraft.vogonpoet.ui.theme.GruvboxPurple
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MessageInspector(
    messages: List<ProtocolMessage>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GruvboxBg1),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Protocol Log",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 500.dp) // Flexible height
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                reverseLayout = true,
            ) {
                // We want newest at the bottom, so we reverse the list for display if using reverseLayout=true?
                // Actually reverseLayout=true starts from bottom. So passed list index 0 is at bottom.
                // If we append messages, index 0 is oldest. We want newest at bottom.
                // So we should pass messages.reversed() to LazyColumn with reverseLayout=true?
                // Let's just use normal layout and auto-scroll later, or simply show newest at top.
                // Simpler: Show newest at top.
                items(messages.reversed()) { msg ->
                    MessageItem(msg)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: ProtocolMessage) {
    var expanded by remember { mutableStateOf(false) }
    val directionArrow = if (message.direction == MessageDirection.Sent) "⬆" else "⬇"
    val directionColor = if (message.direction == MessageDirection.Sent) GruvboxGreen else GruvboxPurple

    val time =
        Instant
            .ofEpochMilli(message.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = directionArrow,
                color = directionColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = GruvboxFg2,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.content.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
            )
        }
    }
}
