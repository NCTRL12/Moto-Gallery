package com.nctrl.motogallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.ui.components.CircleIconButton
import com.nctrl.motogallery.ui.theme.Motion
import com.nctrl.motogallery.ui.theme.PillShape
import com.nctrl.motogallery.util.MediaActions
import kotlinx.coroutines.launch

/**
 * Visor a pantalla completa al estilo One UI: fondo neutro, botones flotantes
 * en píldora, tira de miniaturas para saltar de una foto a otra y barra de
 * acciones abajo. Un toque en la imagen esconde todo.
 */
@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    favoriteKeys: Set<String>,
    place: (MediaItem) -> String?,
    onToggleFavorite: (MediaItem) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onBack: () -> Unit,
    onEdit: ((MediaItem) -> Unit)? = null,
    onEnhance: ((MediaItem) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val filmstripState = rememberLazyListState()

    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val page = pagerState.currentPage.coerceIn(0, items.lastIndex)
    val current = items.getOrNull(page)
    val isFavorite = current?.key in favoriteKeys

    // La tira de abajo sigue a la foto que se está viendo.
    LaunchedEffect(page) {
        filmstripState.animateScrollToItem(index = page, scrollOffset = -220)
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            key = { index -> items[index].key },
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val item = items[index]
            val isCurrent = index == page

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
            enter = fadeIn(Motion.quick()) + slideInVertically(Motion.offset()) { -it / 2 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.offset()) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                FloatingPill {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                        background = Color.Transparent,
                    )
                }

                Box(modifier = Modifier.weight(1f))

                FloatingPill {
                    CircleIconButton(
                        icon = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_info),
                        onClick = { showDetails = true },
                        background = Color.Transparent,
                    )
                    Box {
                        CircleIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = null,
                            onClick = { menuOpen = true },
                            background = Color.Transparent,
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
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
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(Motion.quick()) + slideInVertically(Motion.offset()) { it },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.offset()) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                if (items.size > 1) {
                    Filmstrip(
                        items = items,
                        currentPage = page,
                        state = filmstripState,
                        onSelect = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    )
                }

                FloatingPill(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalPadding = 6.dp,
                ) {
                    CircleIconButton(
                        icon = if (isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = stringResource(R.string.action_favorite),
                        onClick = { current?.let(onToggleFavorite) },
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        background = Color.Transparent,
                    )

                    val editable = current?.isVideo == false
                    if (onEdit != null && editable) {
                        CircleIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            onClick = { current?.let(onEdit) },
                            background = Color.Transparent,
                        )
                    }
                    if (onEnhance != null && editable) {
                        CircleIconButton(
                            icon = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.action_enhance),
                            onClick = { current?.let(onEnhance) },
                            background = Color.Transparent,
                        )
                    }

                    CircleIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_share),
                        onClick = { current?.let { MediaActions.share(context, listOf(it)) } },
                        background = Color.Transparent,
                    )
                    CircleIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        onClick = { current?.let { onDelete(listOf(it)) } },
                        background = Color.Transparent,
                    )
                }
            }
        }
    }

    if (showDetails && current != null) {
        DetailsSheet(
            item = current,
            place = place(current),
            onDismiss = { showDetails = false },
        )
    }
}

/** Contenedor flotante con forma de píldora, como los de One UI. */
@Composable
private fun FloatingPill(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
        ) {
            content()
        }
    }
}

/** Tira de miniaturas: marca la actual y permite saltar a cualquier otra. */
@Composable
private fun Filmstrip(
    items: List<MediaItem>,
    currentPage: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val selected = index == currentPage
            val height by animateDpAsState(
                targetValue = if (selected) 62.dp else 48.dp,
                animationSpec = Motion.soft(),
                label = "stripHeight",
            )
            val width by animateDpAsState(
                targetValue = if (selected) 46.dp else 40.dp,
                animationSpec = Motion.soft(),
                label = "stripWidth",
            )
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.94f,
                animationSpec = Motion.bouncy(),
                label = "stripScale",
            )

            Box(
                modifier = Modifier
                    .scale(scale)
                    .width(width)
                    .height(height)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(index) },
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(12.dp),
                    )
                }
            }
        }
    }
}
