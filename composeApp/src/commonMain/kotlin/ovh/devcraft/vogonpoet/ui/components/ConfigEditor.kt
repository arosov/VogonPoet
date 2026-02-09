package ovh.devcraft.vogonpoet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import ovh.devcraft.vogonpoet.ui.theme.GruvboxBg1

@Composable
fun ConfigEditor(
    config: Babelfish?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GruvboxBg1),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (config == null) {
                Text("No configuration loaded.", color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn {
                    item {
                        ConfigSection("Hardware", config.hardware.toString())
                        ConfigSection("Pipeline", config.pipeline.toString())
                        ConfigSection("Voice", config.voice.toString())
                        ConfigSection("UI", config.ui.toString())
                        ConfigSection("Server", config.server.toString())
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigSection(
    title: String,
    content: String,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
