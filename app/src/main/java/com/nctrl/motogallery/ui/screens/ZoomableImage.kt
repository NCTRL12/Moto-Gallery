package com.nctrl.motogallery.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.max

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ZOOM_THRESHOLD = 1.01f

/**
 * Foto a pantalla completa con pellizco para ampliar, arrastre y doble toque.
 *
 * Con un solo dedo y sin zoom el gesto NO se consume: así el deslizamiento
 * llega al carrusel y se pasa de foto. En cuanto hay dos dedos o la imagen
 * está ampliada, el gesto pasa a ser de la imagen.
 */
@Composable
fun ZoomableImage(
    uri: Uri,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }

    // Al cambiar de foto se vuelve al tamaño original.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    LaunchedEffect(scale) { onZoomChanged(scale > ZOOM_THRESHOLD) }

    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxX = { current: Float -> max(0f, (constraints.maxWidth * (current - 1f)) / 2f) }
        val maxY = { current: Float -> max(0f, (constraints.maxHeight * (current - 1f)) / 2f) }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(uri) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            if (scale > ZOOM_THRESHOLD) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = DOUBLE_TAP_SCALE
                                // Centra el punto tocado dentro de la ampliación.
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                offsetX = ((centerX - tap.x) * (DOUBLE_TAP_SCALE - 1f))
                                    .coerceIn(-maxX(DOUBLE_TAP_SCALE), maxX(DOUBLE_TAP_SCALE))
                                offsetY = ((centerY - tap.y) * (DOUBLE_TAP_SCALE - 1f))
                                    .coerceIn(-maxY(DOUBLE_TAP_SCALE), maxY(DOUBLE_TAP_SCALE))
                            }
                        },
                    )
                }
                .pointerInput(uri) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.count { it.pressed }
                            val isPinch = pointers > 1
                            val isZoomed = scale > ZOOM_THRESHOLD

                            if (isPinch || isZoomed) {
                                val next = (scale * event.calculateZoom())
                                    .coerceIn(MIN_SCALE, MAX_SCALE)
                                val pan = event.calculatePan()
                                scale = next

                                if (next <= ZOOM_THRESHOLD) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX = (offsetX + pan.x)
                                        .coerceIn(-maxX(next), maxX(next))
                                    offsetY = (offsetY + pan.y)
                                        .coerceIn(-maxY(next), maxY(next))
                                }

                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        )
    }
}
