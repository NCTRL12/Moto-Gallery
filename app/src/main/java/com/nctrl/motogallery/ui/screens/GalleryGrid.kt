package com.nctrl.motogallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.ui.theme.Motion
import com.nctrl.motogallery.util.DateGrouping
import com.nctrl.motogallery.util.groupHeader
import com.nctrl.motogallery.util.groupKey
import com.nctrl.motogallery.util.formatDuration

/**
 * Rejilla de miniaturas agrupada por día, con animación de entrada y de
 * recolocación. La usan fotos, favoritos, álbumes, lugares y resultados.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGrid(
    items: List<MediaItem>,
    favoriteKeys: Set<String>,
    selectedKeys: Set<String>,
    columns: Int,
    contentPadding: PaddingValues,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    groupByDay: Boolean = true,
    grouping: DateGrouping = DateGrouping.DAY,
    header: (@Composable () -> Unit)? = null,
) {
    // Agrupar miles de fotos en cada recomposición provoca tirones al
    // seleccionar o marcar favoritos: se calcula una vez por lista.
    val groups = remember(items, groupByDay, grouping) {
        if (groupByDay) items.groupBy { groupKey(it.dateTaken, grouping) } else emptyMap()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }

        if (groupByDay) {
            groups.forEach { (day, dayItems) ->
                item(key = "day-$day", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = groupHeader(dayItems.first().dateTaken, grouping),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(start = 6.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(dayItems, key = { it.key }) { item ->
                    MediaThumbnail(
                        item = item,
                        isFavorite = item.key in favoriteKeys,
                        isSelected = item.key in selectedKeys,
                        selectionActive = selectedKeys.isNotEmpty(),
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = Motion.quick(),
                            placementSpec = Motion.offset(),
                        ),
                    )
                }
            }
        } else {
            items(items, key = { it.key }) { item ->
                MediaThumbnail(
                    item = item,
                    isFavorite = item.key in favoriteKeys,
                    isSelected = item.key in selectedKeys,
                    selectionActive = selectedKeys.isNotEmpty(),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = Motion.quick(),
                        placementSpec = Motion.offset(),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumbnail(
    item: MediaItem,
    isFavorite: Boolean,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Al pulsar se hunde un poco; al seleccionar se encoge y deja ver el borde.
    val scale by animateFloatAsState(
        targetValue = when {
            isSelected -> 0.86f
            pressed -> 0.94f
            else -> 1f
        },
        animationSpec = Motion.bouncy(),
        label = "thumbScale",
    )
    val corner by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 6.dp,
        animationSpec = Motion.soft(),
        label = "thumbCorner",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .scale(Scale.FILL)
                .crossfade(180)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .size(13.dp),
            )
        }

        AnimatedVisibility(
            visible = selectionActive,
            enter = fadeIn(Motion.quick()) + scaleIn(Motion.bouncy(), initialScale = 0.6f),
            exit = fadeOut(Motion.quick()) + scaleOut(Motion.quick(), targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Black.copy(alpha = 0.35f)
                        }
                    ),
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
