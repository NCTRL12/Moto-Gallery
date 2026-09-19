package com.nctrl.motogallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.ui.components.CircleIconButton
import com.nctrl.motogallery.ui.theme.Motion
import com.nctrl.motogallery.util.DateGrouping
import com.nctrl.motogallery.util.MediaActions

/**
 * Pantalla de rejilla con la cabecera grande de One UI, que se encoge al hacer
 * scroll, más modo selección por pulsación larga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGridScreen(
    title: String,
    items: List<MediaItem>,
    favoriteKeys: Set<String>,
    loading: Boolean,
    emptyMessage: String,
    bottomPadding: Dp,
    onOpen: (Int) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    partialAccess: Boolean = false,
    onSelectMorePhotos: () -> Unit = {},
    grouping: DateGrouping = DateGrouping.DAY,
    header: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var selectedKeys by remember(items) { mutableStateOf(emptySet<String>()) }
    val selectionActive = selectedKeys.isNotEmpty()
    val selectedItems = remember(selectedKeys, items) { items.filter { it.key in selectedKeys } }

    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 3
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    BackHandler(enabled = selectionActive) { selectedKeys = emptySet() }

    val gridHeader: (@Composable () -> Unit)? = if (header == null) {
        null
    } else {
        {
            Column {
                header()
                if (partialAccess) PartialAccessBanner(onSelectMore = onSelectMorePhotos)
                if (items.isEmpty() && !loading) {
                    EmptyMessage(
                        message = emptyMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = selectionActive,
                transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) },
                label = "topBar",
            ) { selecting ->
                if (selecting) {
                    SelectionBar(
                        count = selectedKeys.size,
                        onClear = { selectedKeys = emptySet() },
                        onShare = { MediaActions.share(context, selectedItems) },
                        onFavorite = {
                            selectedItems.forEach(onToggleFavorite)
                            selectedKeys = emptySet()
                        },
                        onDelete = {
                            onDelete(selectedItems)
                            selectedKeys = emptySet()
                        },
                    )
                } else {
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineLarge,
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
                                Box(modifier = Modifier.padding(start = 8.dp)) {
                                    CircleIconButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.action_back),
                                        onClick = onBack,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (onSearch != null) {
                                Box(modifier = Modifier.padding(end = 10.dp)) {
                                    CircleIconButton(
                                        icon = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.action_search),
                                        onClick = onSearch,
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                        ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && items.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                items.isEmpty() && header == null -> EmptyMessage(
                    message = emptyMessage,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> GalleryGrid(
                    items = items,
                    favoriteKeys = favoriteKeys,
                    selectedKeys = selectedKeys,
                    columns = columns,
                    contentPadding = PaddingValues(
                        start = 6.dp,
                        end = 6.dp,
                        top = innerPadding.calculateTopPadding(),
                        bottom = bottomPadding + 24.dp,
                    ),
                    onClick = { item ->
                        if (selectionActive) {
                            selectedKeys = selectedKeys.toggle(item.key)
                        } else {
                            onOpen(items.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
                        }
                    },
                    onLongClick = { item -> selectedKeys = selectedKeys.toggle(item.key) },
                    grouping = grouping,
                    header = gridHeader,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selection_count, count),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                CircleIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    onClick = onClear,
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 10.dp),
            ) {
                CircleIconButton(
                    icon = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share),
                    onClick = onShare,
                )
                CircleIconButton(
                    icon = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.action_favorite),
                    onClick = onFavorite,
                )
                CircleIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = onDelete,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun EmptyMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(32.dp),
    )
}

@Composable
private fun PartialAccessBanner(onSelectMore: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_partial),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectMore) {
            Text(stringResource(R.string.permission_select_more))
        }
    }
}

private fun Set<String>.toggle(key: String): Set<String> =
    if (key in this) this - key else this + key
