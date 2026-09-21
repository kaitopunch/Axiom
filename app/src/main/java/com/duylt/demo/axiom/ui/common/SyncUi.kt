package com.duylt.demo.axiom.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.axiom.state.AxiomError
import com.axiom.state.SyncResult
import com.axiom.state.SyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The four things a screen wants to know about a [SyncState], as Material 3 chip parameters. */
data class SyncChipStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
fun SyncState.chipStyle(): SyncChipStyle {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        // Idle is "loading", not "done": it is the state between process start and onCreate's sync() (README §3.1).
        is SyncState.Idle -> SyncChipStyle("Idle", Icons.Outlined.HourglassEmpty, scheme.surfaceVariant, scheme.onSurfaceVariant)
        is SyncState.Scheduled -> SyncChipStyle("Scheduled", Icons.Outlined.Schedule, scheme.secondaryContainer, scheme.onSecondaryContainer)
        is SyncState.Running -> SyncChipStyle("Running #${attempt + 1}", Icons.Outlined.Sync, scheme.primaryContainer, scheme.onPrimaryContainer)
        is SyncState.Success -> SyncChipStyle("Success", Icons.Outlined.CheckCircle, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        is SyncState.Failed -> SyncChipStyle("Failed", Icons.Outlined.ErrorOutline, scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
fun SyncStateChip(
    state: SyncState,
    modifier: Modifier = Modifier,
) {
    val style = state.chipStyle()
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(style.label) },
        leadingIcon = { Icon(style.icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        colors =
            AssistChipDefaults.assistChipColors(
                disabledContainerColor = style.container,
                disabledLabelColor = style.content,
                disabledLeadingIconContentColor = style.content,
            ),
        border = null,
    )
}

/** An error the screen can act on. What Axiom classified it as is part of the message — that is the demo. */
@Composable
fun SyncErrorCard(
    error: AxiomError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = error.describeKind(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(error.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) {
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Retry (sync)") }
            }
        }
    }
}

fun AxiomError.describeKind(): String {
    val code = code?.let { " $it" } ?: ""
    val retry = if (isTransient) "transient → retried" else "not transient → no retry"
    return "${kind.name}$code · $retry"
}

/** One line for a snackbar: what `Axiom.run()` came back with. */
fun SyncResult.describe(): String =
    when (this) {
        is SyncResult.Success -> "Success — store written at ${formatClock(syncedAt)}"
        is SyncResult.Unchanged -> "Unchanged — fetched, same payload as stored (SHA-256), nothing written"
        is SyncResult.Skipped -> "Skipped — store still fresh (last sync ${relativeTime(lastSyncedAt)}), no network call"
        is SyncResult.Failure -> "Failure — ${error.describeKind()}: ${error.message}"
    }

private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

fun formatClock(millis: Long): String = clockFormat.format(Date(millis))

fun relativeTime(
    millis: Long?,
    now: Long = System.currentTimeMillis(),
): String {
    millis ?: return "never"
    val seconds = ((now - millis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s ago"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ago"
    }
}
