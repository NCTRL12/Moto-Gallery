package com.nctrl.motogallery.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.CropAspect
import com.nctrl.motogallery.data.CropRect
import com.nctrl.motogallery.data.EditState
import com.nctrl.motogallery.data.ImageEditing
import com.nctrl.motogallery.data.PhotoFilter
import com.nctrl.motogallery.ui.EditorUiState
import com.nctrl.motogallery.ui.EditorViewModel
import com.nctrl.motogallery.ui.components.CircleIconButton
import com.nctrl.motogallery.ui.components.OneUiChip
import com.nctrl.motogallery.ui.theme.Motion

private enum class Tool { CROP, FILTERS, ADJUST }

/**
 * Editor de fotos: recortar, girar, filtros y ajustes de color. Lo editado se
 * guarda siempre como copia nueva; el original se queda intacto.
 */
@Composable
fun EditorScreen(
    uri: Uri,
    sourceName: String,
    enhance: Boolean,
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tool by remember { mutableStateOf(if (enhance) Tool.ADJUST else Tool.CROP) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(uri) { viewModel.load(uri, enhance) }

    fun close() {
        if (state.edit.hasChanges) confirmDiscard = true else onClose()
    }

    BackHandler { close() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        EditorTopBar(
            saving = state.saving,
            canSave = state.edit.hasChanges && state.preview != null,
            onClose = { close() },
            onSave = {
                viewModel.save(sourceName) { saved -> if (saved != null) onSaved() }
            },
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(color = Color.White)
                state.failed || state.preview == null -> Text(
                    text = stringResource(R.string.editor_open_failed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                else -> EditorPreview(
                    preview = requireNotNull(state.preview),
                    edit = state.edit,
                    showCrop = tool == Tool.CROP,
                    onCropChange = viewModel::setCrop,
                )
            }
        }

        EditorTools(
            tool = tool,
            state = state,
            onToolChange = { tool = it },
            viewModel = viewModel,
        )
    }

    if (state.saving) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    saving: Boolean,
    canSave: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        CircleIconButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.action_cancel),
            onClick = onClose,
            tint = Color.White,
            background = Color.White.copy(alpha = 0.14f),
        )
        Text(
            text = stringResource(R.string.action_edit),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )
        TextButton(onClick = onSave, enabled = canSave && !saving) {
            Text(
                text = stringResource(R.string.action_save),
                color = if (canSave && !saving) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.35f)
                },
            )
        }
    }
}

@Composable
private fun EditorPreview(
    preview: Bitmap,
    edit: EditState,
    showCrop: Boolean,
    onCropChange: (CropRect) -> Unit,
) {
    val colorFilter = remember(edit.filter, edit.brightness, edit.contrast, edit.saturation, edit.warmth) {
        ColorFilter.colorMatrix(ColorMatrix(ImageEditing.colorMatrix(edit).array))
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        // La imagen se encaja dentro del hueco disponible; el marco de recorte
        // se coloca justo encima, del mismo tamaño exacto.
        val imageRatio = preview.width.toFloat() / preview.height.toFloat()
        val boxRatio = maxWidth / maxHeight
        val width = if (imageRatio >= boxRatio) maxWidth else maxHeight * imageRatio
        val height = if (imageRatio >= boxRatio) maxWidth / imageRatio else maxHeight

        Box(modifier = Modifier.size(width, height)) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
            if (showCrop) {
                CropOverlay(
                    crop = edit.crop,
                    aspect = edit.aspect.ratio,
                    onCropChange = onCropChange,
                )
            }
        }
    }
}

@Composable
private fun EditorTools(
    tool: Tool,
    state: EditorUiState,
    onToolChange: (Tool) -> Unit,
    viewModel: EditorViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(vertical = 14.dp),
    ) {
        AnimatedContent(
            targetState = tool,
            transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) },
            label = "tools",
        ) { current ->
            when (current) {
                Tool.CROP -> CropTools(state, viewModel)
                Tool.FILTERS -> FilterTools(state, viewModel)
                Tool.ADJUST -> AdjustTools(state, viewModel)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        ) {
            OneUiChip(
                label = stringResource(R.string.editor_tab_crop),
                selected = tool == Tool.CROP,
                onClick = { onToolChange(Tool.CROP) },
            )
            OneUiChip(
                label = stringResource(R.string.editor_tab_filters),
                selected = tool == Tool.FILTERS,
                onClick = { onToolChange(Tool.FILTERS) },
            )
            OneUiChip(
                label = stringResource(R.string.editor_tab_adjust),
                selected = tool == Tool.ADJUST,
                onClick = { onToolChange(Tool.ADJUST) },
            )
        }
    }
}

@Composable
private fun CropTools(state: EditorUiState, viewModel: EditorViewModel) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            CircleIconButton(
                icon = Icons.Default.RotateRight,
                contentDescription = stringResource(R.string.action_rotate),
                onClick = viewModel::rotateRight,
            )
            CircleIconButton(
                icon = Icons.Default.Flip,
                contentDescription = stringResource(R.string.action_flip),
                onClick = viewModel::flipHorizontal,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(CropAspect.entries.toList()) { aspect ->
                OneUiChip(
                    label = aspect.label,
                    selected = state.edit.aspect == aspect,
                    onClick = { viewModel.setAspect(aspect) },
                )
            }
        }
    }
}

@Composable
private fun FilterTools(state: EditorUiState, viewModel: EditorViewModel) {
    val thumbnail = remember(state.preview) {
        state.preview?.let { source ->
            val side = 140
            val ratio = source.width.toFloat() / source.height.toFloat()
            val width = if (ratio >= 1f) side else (side * ratio).toInt().coerceAtLeast(1)
            val height = if (ratio >= 1f) (side / ratio).toInt().coerceAtLeast(1) else side
            runCatching { Bitmap.createScaledBitmap(source, width, height, true) }.getOrNull()
        }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(PhotoFilter.entries.toList()) { filter ->
            val selected = state.edit.filter == filter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clickable { viewModel.setFilter(filter) },
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(16.dp),
                        )
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = filter.label,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix(
                                    ImageEditing.colorMatrix(
                                        EditState(filter = filter)
                                    ).array
                                )
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AdjustTools(state: EditorUiState, viewModel: EditorViewModel) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        AdjustSlider(
            label = stringResource(R.string.adjust_brightness),
            value = state.edit.brightness,
            range = -1f..1f,
            onChange = viewModel::setBrightness,
        )
        AdjustSlider(
            label = stringResource(R.string.adjust_contrast),
            value = state.edit.contrast,
            range = 0.4f..1.8f,
            onChange = viewModel::setContrast,
        )
        AdjustSlider(
            label = stringResource(R.string.adjust_saturation),
            value = state.edit.saturation,
            range = 0f..2f,
            onChange = viewModel::setSaturation,
        )
        AdjustSlider(
            label = stringResource(R.string.adjust_warmth),
            value = state.edit.warmth,
            range = -1f..1f,
            onChange = viewModel::setWarmth,
        )
        TextButton(
            onClick = viewModel::resetAdjustments,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.action_reset))
        }
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
