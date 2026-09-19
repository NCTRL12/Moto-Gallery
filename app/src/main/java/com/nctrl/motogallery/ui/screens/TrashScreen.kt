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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.ui.components.CircleIconButton
import com.nctrl.motogallery.ui.components.OneUiChip
import com.nctrl.motogallery.ui.theme.Motion

/**
 * Papelera del sistema: se puede mirar, restaurar lo que haga falta y vaciarla.
 * Android la borra sola a los 30 días.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    items: List<MediaItem>,
    supported: Boolean,
    bottomPadding: Dp,
    onOpen: (Int) -> Unit,
    onRestore: (List<MediaItem>) -> Unit,
    onDeleteForever: (List<MediaItem>) -> Unit,
    onBack: () -> Unit,
) {
    var selectedKeys by remember(items) { mutableStateOf(emptySet<String>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val selectionActive = selectedKeys.isNotEmpty()
    val selectedItems = remember(selectedKeys, items) { items.filter { it.key in selectedKeys } }
    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 3

    BackHandler { if (selectionActive) selectedKeys = emptySet() else onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = selectionActive,
                transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) },
                label = "trashBar",
            ) { selecting ->
                TopAppBar(
                    title = {
                        Text(
                            text = if (selecting) {
                                stringResource(R.string.selection_count, selectedKeys.size)
                            } else {
                                stringResource(R.string.tab_trash)
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            CircleIconButton(
                                icon = if (selecting) {
                                    Icons.Default.Close
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = stringResource(R.string.action_back),
                                onClick = {
                                    if (selecting) selectedKeys = emptySet() else onBack()
                                },
                            )
                        }
                    },
                    actions = {
                        if (selecting) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 10.dp),
                            ) {
                                CircleIconButton(
                                    icon = Icons.Default.Restore,
                                    contentDescription = stringResource(R.string.action_restore),
                                    onClick = {
                                        onRestore(selectedItems)
                                        selectedKeys = emptySet()
                                    },
                                )
                                CircleIconButton(
                                    icon = Icons.Default.DeleteForever,
                                    contentDescription =
                                        stringResource(R.string.action_delete_forever),
                                    onClick = {
                                        onDeleteForever(selectedItems)
                                        selectedKeys = emptySet()
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding() + 72.dp)
                        .padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = if (supported) {
                            stringResource(R.string.trash_empty)
                        } else {
                            stringResource(R.string.trash_unsupported)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                GalleryGrid(
                    items = items,
                    favoriteKeys = emptySet(),
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
                            selectedKeys = selectedKeys.toggleKey(item.key)
                        } else {
                            onOpen(items.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
                        }
                    },
                    onLongClick = { item -> selectedKeys = selectedKeys.toggleKey(item.key) },
                    groupByDay = false,
                    header = {
                        TrashHeader(
                            count = items.size,
                            onEmpty = { confirmEmpty = true },
                        )
                    },
                )
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash_confirm_title)) },
            text = { Text(stringResource(R.string.empty_trash_confirm_body, items.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    onDeleteForever(items)
                }) {
                    Text(stringResource(R.string.action_empty_trash))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TrashHeader(count: Int, onEmpty: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.trash_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            OneUiChip(
                label = stringResource(R.string.action_empty_trash),
                selected = false,
                onClick = onEmpty,
                icon = Icons.Default.DeleteForever,
            )
        }
    }
}

private fun Set<String>.toggleKey(key: String): Set<String> =
    if (key in this) this - key else this + key
