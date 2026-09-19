package com.nctrl.motogallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.data.SearchSuggestion
import com.nctrl.motogallery.ui.SearchState
import com.nctrl.motogallery.ui.components.CircleIconButton
import com.nctrl.motogallery.ui.theme.Motion
import com.nctrl.motogallery.ui.theme.PillShape

/**
 * Buscador a pantalla completa. Mientras no hay texto muestra sugerencias
 * (lugares, álbumes, años y tipos); al escribir, la rejilla de resultados.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    favoriteKeys: Set<String>,
    bottomPadding: Dp,
    onQueryChange: (String) -> Unit,
    onOpen: (Int) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler { onBack() }

    // El teclado sale solo: si entras a buscar, es para escribir.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                background = MaterialTheme.colorScheme.background,
            )

            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                trailingIcon = {
                    AnimatedVisibility(
                        visible = state.query.isNotEmpty(),
                        enter = fadeIn(Motion.quick()),
                        exit = fadeOut(Motion.quick()),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onQueryChange("") }
                                .padding(4.dp),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                shape = PillShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        if (state.query.isBlank()) {
            SuggestionList(
                suggestions = state.suggestions,
                bottomPadding = bottomPadding,
                onPick = onQueryChange,
            )
        } else {
            SearchResults(
                state = state,
                favoriteKeys = favoriteKeys,
                bottomPadding = bottomPadding,
                onOpen = onOpen,
                onDelete = onDelete,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    favoriteKeys: Set<String>,
    bottomPadding: Dp,
    onOpen: (Int) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
) {
    if (state.results.isEmpty() && !state.searching) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.search_empty, state.query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(32.dp),
            )
        }
        return
    }

    Column {
        Text(
            text = stringResource(R.string.search_results, state.results.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        GalleryGrid(
            items = state.results,
            favoriteKeys = favoriteKeys,
            selectedKeys = emptySet(),
            columns = 3,
            contentPadding = PaddingValues(
                start = 6.dp,
                end = 6.dp,
                bottom = bottomPadding + 24.dp,
            ),
            onClick = { item ->
                onOpen(state.results.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
            },
            onLongClick = { onToggleFavorite(it) },
            groupByDay = false,
        )
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<SearchSuggestion>,
    bottomPadding: Dp,
    onPick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = bottomPadding + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.search_suggestions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(suggestions, key = { "${it.kind}-${it.label}" }) { suggestion ->
            SuggestionRow(suggestion = suggestion, onClick = { onPick(suggestion.label) })
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: SearchSuggestion, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (suggestion.cover != null) {
                AsyncImage(
                    model = suggestion.cover.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f),
                )
            } else {
                Icon(
                    imageVector = when (suggestion.kind) {
                        SearchSuggestion.Kind.PLACE -> Icons.Default.Place
                        SearchSuggestion.Kind.ALBUM -> Icons.Default.PhotoLibrary
                        SearchSuggestion.Kind.YEAR -> Icons.Default.CalendarMonth
                        SearchSuggestion.Kind.TYPE -> Icons.Default.Videocam
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = suggestion.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (suggestion.kind) {
                    SearchSuggestion.Kind.PLACE -> "Lugar"
                    SearchSuggestion.Kind.ALBUM -> "Álbum"
                    SearchSuggestion.Kind.YEAR -> "Año"
                    SearchSuggestion.Kind.TYPE -> "Tipo"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Separador fino para listas, al gusto One UI. */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
