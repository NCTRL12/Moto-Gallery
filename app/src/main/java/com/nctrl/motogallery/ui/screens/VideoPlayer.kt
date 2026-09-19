package com.nctrl.motogallery.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Reproductor para la página de vídeo del visor. Solo la página visible
 * mantiene un ExoPlayer vivo; al salir se libera.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    isCurrentPage: Boolean,
    showControls: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
        }
    }

    LaunchedEffect(isCurrentPage) {
        // Al deslizar a otro elemento el vídeo se pausa y vuelve al inicio.
        if (isCurrentPage) {
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
            player.seekTo(0)
        }
    }

    DisposableEffect(uri) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                setPlayer(player)
                setUseController(true)
                setControllerAutoShow(false)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setBackgroundColor(android.graphics.Color.BLACK)
                setOnClickListener { onTap() }
            }
        },
        update = { view ->
            if (showControls) view.showController() else view.hideController()
        },
        onRelease = { view -> view.setPlayer(null) },
        modifier = modifier.fillMaxSize(),
    )
}
