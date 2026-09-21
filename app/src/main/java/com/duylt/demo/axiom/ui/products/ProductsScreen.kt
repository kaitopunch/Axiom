package com.duylt.demo.axiom.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.axiom.state.SyncState
import com.duylt.demo.axiom.data.DemoSettings
import com.duylt.demo.axiom.data.record.ProductRecord
import com.duylt.demo.axiom.ui.common.SyncErrorCard
import com.duylt.demo.axiom.ui.common.SyncStateChip
import com.duylt.demo.axiom.ui.common.relativeTime
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.Locale

/**
 * The core loop on one screen: rows from `axiom.data(spec)`, a chip from `axiom.state(key)`, three
 * ways to ask for a sync — `sync()` (WorkManager), pull-to-refresh (`run()`, result in a snackbar),
 * and Clear + Sync (the way past the freshness window) — and a per-row local/remote badge that shows
 * the transform's `publish()` hand-over as the thumbnails land in the cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(viewModel: ProductsViewModel = koinViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products") },
                actions = { SyncStateChip(ui.state, Modifier.padding(end = 12.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SummaryCard(
                        ui = ui,
                        onSync = viewModel::sync,
                        onClearAndSync = viewModel::clearAndSync,
                    )
                }
                val failed = ui.state as? SyncState.Failed
                if (failed != null) {
                    item { SyncErrorCard(failed.error, onRetry = viewModel::sync) }
                }
                if (ui.store == null) {
                    item { NeverSynced(ui.state) }
                }
                items(ui.products, key = { it.id }) { product ->
                    ProductRow(product)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    ui: ProductsUiState,
    onSync: () -> Unit,
    onClearAndSync: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            val store = ui.store
            Text(
                text =
                    if (store == null) {
                        "Store is empty (data() → null)"
                    } else {
                        "${store.products.size} of ${store.total} products · ${ui.cachedCount}/${store.products.size} thumbnails local"
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Last sync ${relativeTime(ui.state.lastSyncedAt)} · staleAfter ${DemoSettings.PRODUCTS_STALE_AFTER} · pull down = run()",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (store != null && ui.cachedCount < store.products.size && ui.state.isInFlight) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ui.cachedCount.toFloat() / store.products.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSync) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sync")
                }
                OutlinedButton(onClick = onClearAndSync) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear + Sync")
                }
            }
        }
    }
}

@Composable
private fun NeverSynced(state: SyncState) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state !is SyncState.Failed) CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state is SyncState.Failed) "Nothing stored yet" else "Waiting for the first successful sync…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductRow(product: ProductRecord) {
    val local = product.thumbLocal
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // A cached file when the transform has one, the remote URL before that: what `publish()` buys.
            AsyncImage(
                model = local?.let(::File) ?: product.thumbUrl,
                contentDescription = product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = listOfNotNull(product.brand, product.category).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$${"%.2f".format(Locale.US, product.price)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(" ${"%.1f".format(Locale.US, product.rating)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (local != null) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue,
                contentDescription = if (local != null) "thumbnail cached locally" else "thumbnail from remote URL",
                tint = if (local != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
