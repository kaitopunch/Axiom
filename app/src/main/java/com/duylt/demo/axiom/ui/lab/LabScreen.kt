package com.duylt.demo.axiom.ui.lab

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.axiom.state.SyncState
import com.duylt.demo.axiom.BuildConfig
import com.duylt.demo.axiom.data.DemoClients
import com.duylt.demo.axiom.data.DemoSettings
import com.duylt.demo.axiom.log.LogLine
import com.duylt.demo.axiom.ui.common.SyncStateChip
import com.duylt.demo.axiom.ui.common.describeKind
import com.duylt.demo.axiom.ui.common.formatClock
import com.duylt.demo.axiom.ui.common.relativeTime
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Everything Axiom exposes about itself, on one screen: the config it was built with, every task with
 * its state and the six verbs (`sync` / `run` / `cancel` / `clear` / `read`, plus `syncAll`), the
 * WorkManager requests behind them, one API call through the Koin-provided service, and the SDK's log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(viewModel: LabViewModel = koinViewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val work by viewModel.work.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val now = rememberNow()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    viewModel.peek?.let { peek -> PeekDialog(peek, onDismiss = viewModel::dismissPeek) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lab") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ConfigCard(onSyncAll = viewModel::syncAll, onCancelAll = viewModel::cancelAll, onClearAll = viewModel::clearAll) }

            item { SectionTitle("Tasks (${tasks.size})") }
            items(tasks, key = { it.spec.key }) { card ->
                TaskCardView(
                    card = card,
                    now = now,
                    onSync = { viewModel.sync(card.spec.key) },
                    onRun = { viewModel.run(card.spec.key) },
                    onCancel = { viewModel.cancel(card.spec.key) },
                    onClear = { viewModel.clear(card.spec.key) },
                    onPeek = { viewModel.peek(card.spec) },
                )
            }

            item { SectionTitle("WorkManager (tag \"axiom\")") }
            item { WorkCard(work, now) }

            item { SectionTitle("Koin") }
            item { KoinCard(result = viewModel.apiCallResult, onCall = viewModel::callApiThroughKoin) }

            item { SectionTitle("Axiom log (${logs.size})") }
            item { LogCard(logs, onClear = viewModel::clearLogs) }
        }
    }
}

/** A clock that ticks once a second, so "12s ago" keeps counting while the screen is open. */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ConfigCard(
    onSyncAll: () -> Unit,
    onCancelAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Axiom.init { … }", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            KeyValue("isDebug", "${DemoSettings.IS_DEBUG}  (app build: ${if (BuildConfig.DEBUG) "debug" else "release"})")
            KeyValue("defaultStaleAfter", DemoSettings.DEFAULT_STALE_AFTER.toString())
            KeyValue("client \"${DemoClients.API}\"", BuildConfig.DUMMYJSON_BASE_URL)
            KeyValue("client \"${DemoClients.ASSETS}\"", "no baseUrl — callFactory() only")
            KeyValue("client \"${DemoClients.BROKEN}\"", DemoClients.BROKEN_BASE_URL)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSyncAll) { Text("syncAll()") }
                OutlinedButton(onClick = onCancelAll) { Text("cancelAll()") }
                OutlinedButton(onClick = onClearAll) { Text("clearAll()") }
            }
        }
    }
}

@Composable
private fun KeyValue(
    key: String,
    value: String,
) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TaskCardView(
    card: TaskCard,
    now: Long,
    onSync: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onPeek: () -> Unit,
) {
    val spec = card.spec
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = spec.type.shortName(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SyncStateChip(card.state)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    buildString {
                        append("staleAfter ")
                        append(spec.staleAfter?.toString() ?: "default")
                        append(" · periodic ")
                        append(spec.periodic?.toString() ?: "—")
                        append(" · retry ${spec.retry.maxRetries}× from ${spec.retry.initialBackoff}")
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "last success ${relativeTime(card.state.lastSyncedAt, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (card.state as? SyncState.Failed)?.let { failed ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${failed.error.describeKind()}\n${failed.error.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSync) { Text("sync") }
                TextButton(onClick = onRun) { Text("run") }
                TextButton(onClick = onCancel) { Text("cancel") }
                TextButton(onClick = onClear) { Text("clear") }
                TextButton(onClick = onPeek) { Text("read") }
            }
        }
    }
}

@Composable
private fun WorkCard(
    rows: List<WorkRow>,
    now: Long,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (rows.isEmpty()) {
                Text("No Axiom work enqueued yet.", style = MaterialTheme.typography.bodySmall)
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (row.periodic) "${row.key} (periodic)" else row.key,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text =
                                buildString {
                                    append("attempt ${row.runAttemptCount}")
                                    row.nextScheduleAt?.let { at ->
                                        if (at > now) append(" · next in ${(at - now) / 1000}s (${formatClock(at)})")
                                    }
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(row.state.name, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun KoinCard(
    result: String?,
    onCall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "axiomApi<DummyJsonApi>(client = \"${DemoClients.API}\") registers the Retrofit service as a Koin single; this ViewModel takes it by constructor and calls it outside any task.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onCall) { Text("api.getCategories()") }
            result?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun LogCard(
    lines: List<LogLine>,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AxiomLogRecorder → logcat tag \"Axiom\" + this buffer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("clear") }
            }
            Column(Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState())) {
                lines.asReversed().forEach { line ->
                    Text(
                        text = "${formatClock(line.at)} ${line.level.name.first()} ${line.text}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color =
                            when (line.level) {
                                com.axiom.log.AxiomLogger.Level.ERROR -> MaterialTheme.colorScheme.error
                                com.axiom.log.AxiomLogger.Level.WARN -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeekDialog(
    peek: Peek,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("read(\"${peek.key}\")") },
        text = {
            Column {
                Text(
                    text = if (peek.json == null) "null — nothing stored for this key" else "${peek.bytes} bytes of JSON",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                peek.json?.let { json ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (json.length > PEEK_PREVIEW_CHARS) json.take(PEEK_PREVIEW_CHARS) + "\n…" else json,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
    )
}

private const val PEEK_PREVIEW_CHARS = 1_500

/** `java.util.List<com.duylt.demo.axiom.data.record.UserRecord>` → `List<UserRecord>`. */
private fun Type.shortName(): String =
    when (this) {
        is ParameterizedType -> "${(rawType as Class<*>).simpleName}<${actualTypeArguments.joinToString { it.shortName() }}>"
        is Class<*> -> simpleName
        else -> toString()
    }
