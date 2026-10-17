package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R

/** LogsScreen — the desktop LogsWindow as a screen (core log tail). */
@Composable
fun LogsScreen() {
    val logs by ForoughBus.logs.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to the newest line like the desktop log window.
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        if (logs.isEmpty()) {
            Text(
                stringResource(R.string.logs_empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                items(logs.size) { index ->
                    Text(
                        logs[index],
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
