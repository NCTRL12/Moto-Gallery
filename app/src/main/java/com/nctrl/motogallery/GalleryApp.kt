package com.nctrl.motogallery

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class GalleryApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            // Miniaturas de vídeo (primer fotograma).
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbnails"))
                .maxSizeBytes(120L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                // En equipos antiguos el bitmap RGB_565 gasta la mitad de memoria.
                allowRgb565(true)
            }
        }
        .build()
}
