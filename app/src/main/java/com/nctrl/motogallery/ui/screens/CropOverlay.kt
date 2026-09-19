package com.nctrl.motogallery.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.nctrl.motogallery.data.CropRect

private enum class Handle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

private const val MIN_SIZE = 0.12f
private const val TOUCH_SLOP_PX = 56f

/**
 * Marco de recorte sobre la imagen: se arrastran las esquinas para ajustar y
 * el centro para mover. Las coordenadas van en proporción (0..1), así que
 * valen igual para la vista previa y para la foto a resolución completa.
 */
@Composable
fun CropOverlay(
    crop: CropRect,
    /** Proporción ancho/alto a respetar, o null para recorte libre. */
    aspect: Float?,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // El gesto lee siempre el recorte más reciente sin reiniciarse a cada cambio.
    val currentCrop by rememberUpdatedState(crop)
    val currentAspect by rememberUpdatedState(aspect)
    val onChange by rememberUpdatedState(onCropChange)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var handle = Handle.NONE
                detectDragGestures(
                    onDragStart = { offset ->
                        handle = handleAt(offset, currentCrop, size.width.toFloat(), size.height.toFloat())
                    },
                    onDragEnd = { handle = Handle.NONE },
                    onDragCancel = { handle = Handle.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (handle != Handle.NONE) {
                            onChange(
                                applyDrag(
                                    crop = currentCrop,
                                    handle = handle,
                                    deltaX = dragAmount.x / size.width,
                                    deltaY = dragAmount.y / size.height,
                                    aspect = currentAspect,
                                    viewWidth = size.width.toFloat(),
                                    viewHeight = size.height.toFloat(),
                                )
                            )
                        }
                    },
                )
            }
    ) {
        val left = crop.left * size.width
        val top = crop.top * size.height
        val right = crop.right * size.width
        val bottom = crop.bottom * size.height

        // Oscurecer lo que queda fuera del recorte.
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(
            shade,
            topLeft = Offset(right, top),
            size = Size(size.width - right, bottom - top),
        )

        // Marco y regla de los tercios.
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2f),
        )
        val thirdsColor = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = left + (right - left) * i / 3f
            val y = top + (bottom - top) * i / 3f
            drawLine(thirdsColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
            drawLine(thirdsColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        }

        // Escuadras en las esquinas, para que se vean agarrables.
        val arm = 28f
        val thickness = 6f
        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(right, top) to Pair(-arm, arm),
            Offset(left, bottom) to Pair(arm, -arm),
            Offset(right, bottom) to Pair(-arm, -arm),
        ).forEach { (corner, direction) ->
            drawLine(
                Color.White,
                corner,
                Offset(corner.x + direction.first, corner.y),
                strokeWidth = thickness,
            )
            drawLine(
                Color.White,
                corner,
                Offset(corner.x, corner.y + direction.second),
                strokeWidth = thickness,
            )
        }
    }
}

private fun handleAt(offset: Offset, crop: CropRect, width: Float, height: Float): Handle {
    val left = crop.left * width
    val top = crop.top * height
    val right = crop.right * width
    val bottom = crop.bottom * height

    fun near(x: Float, y: Float) =
        kotlin.math.abs(offset.x - x) < TOUCH_SLOP_PX && kotlin.math.abs(offset.y - y) < TOUCH_SLOP_PX

    return when {
        near(left, top) -> Handle.TOP_LEFT
        near(right, top) -> Handle.TOP_RIGHT
        near(left, bottom) -> Handle.BOTTOM_LEFT
        near(right, bottom) -> Handle.BOTTOM_RIGHT
        offset.x in left..right && offset.y in top..bottom -> Handle.MOVE
        else -> Handle.NONE
    }
}

private fun applyDrag(
    crop: CropRect,
    handle: Handle,
    deltaX: Float,
    deltaY: Float,
    aspect: Float?,
    viewWidth: Float,
    viewHeight: Float,
): CropRect {
    if (handle == Handle.MOVE) {
        val dx = deltaX.coerceIn(-crop.left, 1f - crop.right)
        val dy = deltaY.coerceIn(-crop.top, 1f - crop.bottom)
        return crop.copy(
            left = crop.left + dx,
            right = crop.right + dx,
            top = crop.top + dy,
            bottom = crop.bottom + dy,
        )
    }

    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom

    when (handle) {
        Handle.TOP_LEFT -> {
            left = (left + deltaX).coerceIn(0f, right - MIN_SIZE)
            top = (top + deltaY).coerceIn(0f, bottom - MIN_SIZE)
        }

        Handle.TOP_RIGHT -> {
            right = (right + deltaX).coerceIn(left + MIN_SIZE, 1f)
            top = (top + deltaY).coerceIn(0f, bottom - MIN_SIZE)
        }

        Handle.BOTTOM_LEFT -> {
            left = (left + deltaX).coerceIn(0f, right - MIN_SIZE)
            bottom = (bottom + deltaY).coerceIn(top + MIN_SIZE, 1f)
        }

        Handle.BOTTOM_RIGHT -> {
            right = (right + deltaX).coerceIn(left + MIN_SIZE, 1f)
            bottom = (bottom + deltaY).coerceIn(top + MIN_SIZE, 1f)
        }

        else -> Unit
    }

    if (aspect == null) return CropRect(left, top, right, bottom)

    // Con proporción fija, el alto se deduce del ancho y se ancla en la
    // esquina contraria a la que se está arrastrando.
    val normalizedRatio = aspect * viewHeight / viewWidth
    val width = right - left
    var height = width / normalizedRatio

    if (height > 1f) {
        height = 1f
    }

    return when (handle) {
        Handle.TOP_LEFT -> {
            val newTop = (bottom - height).coerceAtLeast(0f)
            CropRect(left, newTop, right, bottom)
        }

        Handle.TOP_RIGHT -> {
            val newTop = (bottom - height).coerceAtLeast(0f)
            CropRect(left, newTop, right, bottom)
        }

        Handle.BOTTOM_LEFT -> {
            val newBottom = (top + height).coerceAtMost(1f)
            CropRect(left, top, right, newBottom)
        }

        else -> {
            val newBottom = (top + height).coerceAtMost(1f)
            CropRect(left, top, right, newBottom)
        }
    }
}
