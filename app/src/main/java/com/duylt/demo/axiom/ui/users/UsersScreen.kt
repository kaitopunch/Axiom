package com.duylt.demo.axiom.ui.users

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.axiom.paging.DefaultListPagingConfig
import com.axiom.state.SyncState
import com.duylt.demo.axiom.data.record.UserRecord
import com.duylt.demo.axiom.ui.common.SyncErrorCard
import com.duylt.demo.axiom.ui.common.SyncStateChip
import com.duylt.demo.axiom.ui.common.relativeTime
import org.koin.androidx.compose.koinViewModel

/**
 * Paging 3 over the store: `asPagingData()` in the repository, `collectAsLazyPagingItems()` here.
 * The list is in memory, so pages load instantly — the point is that a re-sync while scrolled to row
 * 150 keeps the position (one Pager, invalidated around the anchor) rather than snapping to the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(viewModel: UsersViewModel = koinViewModel()) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val users = viewModel.users.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                actions = {
                    SyncStateChip(header.state)
                    IconButton(onClick = viewModel::sync) { Icon(Icons.Outlined.Sync, contentDescription = "Sync") }
                    IconButton(onClick = viewModel::clearAndSync) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear and sync") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = header.count?.let { "$it users in store · ${users.itemCount} loaded into pages" } ?: "Store is empty (data() → null)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "pageSize ${DefaultListPagingConfig.pageSize} · prefetch ${DefaultListPagingConfig.prefetchDistance} · last sync ${relativeTime(header.state.lastSyncedAt)} · staleAfter = default",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            (header.state as? SyncState.Failed)?.let { failed ->
                item { SyncErrorCard(failed.error, modifier = Modifier.padding(horizontal = 16.dp), onRetry = viewModel::sync) }
            }
            if (header.count == null && header.state !is SyncState.Failed) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            items(
                count = users.itemCount,
                key = users.itemKey { it.id },
            ) { index ->
                users[index]?.let { UserRow(index + 1, it) }
            }
        }
    }
}

@Composable
private fun UserRow(
    position: Int,
    user: UserRecord,
) {
    ListItem(
        headlineContent = { Text(user.fullName) },
        supportingContent = {
            Text(
                text = listOf(user.jobTitle, user.company).filter { it.isNotBlank() }.joinToString(" @ ").ifBlank { user.email },
                maxLines = 1,
            )
        },
        leadingContent = {
            // Straight from the URL: the users task has no image transform, in contrast to products.
            AsyncImage(
                model = user.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("#$position", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(2.dp))
                Text("${user.age}y", style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}
