package com.nctrl.motogallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.util.MediaActions
import com.nctrl.motogallery.util.formatDateTime

/** Visor a pantalla completa: deslizar para cambiar, pellizcar para ampliar. */
@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    favoriteKeys: Set<String>,
    onToggleFavorite: (MediaItem) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onBack: () -> Unit,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val current = items.getOrNull(pagerState.currentPage.coerceIn(0, items.lastIndex))

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            key = { index -> items[index].key },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            val isCurrent = page == pagerState.currentPage

            if (item.isVideo) {
                VideoPlayer(
                    uri = item.uri,
                    isCurrentPage = isCurrent,
                    showControls = chromeVisible,
                    onTap = { chromeVisible = !chromeVisible },
                )
            } else {
                ZoomableImage(
                    uri = item.uri,
                    isCurrentPage = isCurrent,
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomChanged = { if (isCurrent) zoomed = it },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current?.name.orEmpty(),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = formatDateTime(current?.dateTaken ?: 0L),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_with)) },
                            onClick = {
                                menuOpen = false
                                current?.let { MediaActions.openWith(context, it) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_use_as)) },
                            onClick = {
                                menuOpen = false
                                current?.let { MediaActions.useAs(context, it) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_info)) },
                            onClick = {
                                menuOpen = false
                                showDetails = true
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(vertical = 4.dp),
            ) {
                IconButton(onClick = { current?.let { MediaActions.share(context, listOf(it)) } }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { current?.let(onToggleFavorite) }) {
                    val isFavorite = current?.key in favoriteKeys
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                IconButton(onClick = { showDetails = true }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_info),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { current?.let { onDelete(listOf(it)) } }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showDetails && current != null) {
        DetailsSheet(item = current, onDismiss = { showDetails = false })
    }
}
