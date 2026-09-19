package com.nctrl.motogallery.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.util.dayKey
import com.nctrl.motogallery.util.formatDayHeader
import com.nctrl.motogallery.util.formatDuration

/**
 * Rejilla de miniaturas agrupadas por día. La usan las pestañas de fotos,
 * favoritos y el detalle de un álbum.
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
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (groupByDay) {
            val groups = items.groupBy { dayKey(it.dateTaken) }
            groups.forEach { (day, dayItems) ->
                item(key = "header-$day", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = formatDayHeader(dayItems.first().dateTaken),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
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
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSelected) 8.dp else 0.dp),
        )

        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
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
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(14.dp),
            )
        }

        if (selectionActive) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f)),
            )
        }
    }
}
