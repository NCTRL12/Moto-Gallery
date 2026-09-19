package com.nctrl.motogallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.util.MediaActions

/**
 * Pantalla de rejilla reutilizable con barra superior, modo selección
 * (pulsación larga) y estados de carga/vacío.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGridScreen(
    title: String,
    items: List<MediaItem>,
    favoriteKeys: Set<String>,
    loading: Boolean,
    emptyMessage: String,
    partialAccess: Boolean,
    scaffoldPadding: PaddingValues,
    onSelectMorePhotos: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var selectedKeys by remember(items) { mutableStateOf(emptySet<String>()) }
    val selectionActive = selectedKeys.isNotEmpty()
    val selectedItems = remember(selectedKeys, items) {
        items.filter { it.key in selectedKeys }
    }

    // En horizontal caben más columnas; en vertical, 3 como cualquier galería.
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 3

    BackHandler(enabled = selectionActive) { selectedKeys = emptySet() }

    Scaffold(
        topBar = {
            if (selectionActive) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.selection_count, selectedKeys.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedKeys = emptySet() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { MediaActions.share(context, selectedItems) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                            )
                        }
                        IconButton(onClick = {
                            selectedItems.forEach(onToggleFavorite)
                            selectedKeys = emptySet()
                        }) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = stringResource(R.string.action_favorite),
                            )
                        }
                        IconButton(onClick = {
                            onDelete(selectedItems)
                            selectedKeys = emptySet()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (items.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.items_count, items.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && items.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                items.isEmpty() -> {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                }

                else -> {
                    GalleryGrid(
                        items = items,
                        favoriteKeys = favoriteKeys,
                        selectedKeys = selectedKeys,
                        columns = columns,
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() +
                                if (partialAccess) 56.dp else 0.dp,
                            bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
                        ),
                        onClick = { item ->
                            if (selectionActive) {
                                selectedKeys = selectedKeys.toggle(item.key)
                            } else {
                                onOpen(items.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
                            }
                        },
                        onLongClick = { item -> selectedKeys = selectedKeys.toggle(item.key) },
                    )
                }
            }

            if (partialAccess && !selectionActive) {
                PartialAccessBanner(
                    onSelectMore = onSelectMorePhotos,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding()),
                )
            }
        }
    }
}

@Composable
private fun PartialAccessBanner(onSelectMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_partial),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectMore) {
            Text(stringResource(R.string.permission_select_more))
        }
    }
}

private fun Set<String>.toggle(key: String): Set<String> =
    if (key in this) this - key else this + key
