package com.nctrl.motogallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee las fotos y vídeos del teléfono con el MediaStore. No copia ni mueve
 * nada: la app solo consulta el índice que ya mantiene Android.
 */
class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = ArrayList<MediaItem>(512)
        items += query(imagesUri(), isVideo = false)
        items += query(videosUri(), isVideo = true)
        items.sortByDescending { it.dateTaken }
        items
    }

    private fun imagesUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun videosUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun query(collection: Uri, isVideo: Boolean): List<MediaItem> {
        // Los nombres de columna van como literales porque las constantes de
        // MediaStore.MediaColumns para bucket/duración solo existen en API 29+.
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(COLUMN_DATE_TAKEN)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(COLUMN_BUCKET_ID)
            add(COLUMN_BUCKET_NAME)
            if (isVideo) add(COLUMN_DURATION)
        }.toTypedArray()

        val result = ArrayList<MediaItem>()
        val cursor = runCatching {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
        }.getOrNull() ?: return result

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modifiedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val takenCol = c.getColumnIndex(COLUMN_DATE_TAKEN)
            val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val bucketIdCol = c.getColumnIndex(COLUMN_BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(COLUMN_BUCKET_NAME)
            val durationCol = c.getColumnIndex(COLUMN_DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = takenCol.takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) } ?: 0L
                val modifiedSeconds = modifiedCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
                val mime = mimeCol.takeIf { it >= 0 }?.let { c.getString(it) }
                    ?: if (isVideo) "video/*" else "image/*"

                result += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = nameCol.takeIf { it >= 0 }?.let { c.getString(it) } ?: "$id",
                    mimeType = mime,
                    dateTaken = if (taken > 0) taken else modifiedSeconds * 1000L,
                    size = sizeCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                    width = widthCol.takeIf { it >= 0 }?.let { c.getInt(it) } ?: 0,
                    height = heightCol.takeIf { it >= 0 }?.let { c.getInt(it) } ?: 0,
                    durationMs = durationCol.takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) } ?: 0L,
                    bucketId = bucketIdCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                    bucketName = bucketNameCol.takeIf { it >= 0 }?.let { c.getString(it) }
                        ?: UNKNOWN_ALBUM,
                )
            }
        }
        return result
    }

    /** Se dispara cuando el usuario saca una foto o borra algo desde otra app. */
    fun observeChanges(onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        resolver.registerContentObserver(imagesUri(), true, observer)
        resolver.registerContentObserver(videosUri(), true, observer)
        return observer
    }

    fun stopObserving(observer: ContentObserver) {
        runCatching { resolver.unregisterContentObserver(observer) }
    }

    companion object {
        const val UNKNOWN_ALBUM = "Otros"
        private const val COLUMN_BUCKET_ID = "bucket_id"
        private const val COLUMN_BUCKET_NAME = "bucket_display_name"
        private const val COLUMN_DATE_TAKEN = "datetaken"
        private const val COLUMN_DURATION = "duration"
    }
}

/** Agrupa los elementos por carpeta para la pestaña de álbumes. */
fun List<MediaItem>.toAlbums(): List<Album> =
    groupBy { it.bucketId to it.bucketName }
        .map { (key, items) ->
            Album(
                id = key.first,
                name = key.second.ifBlank { MediaRepository.UNKNOWN_ALBUM },
                cover = items.first(),
                count = items.size,
            )
        }
        .sortedWith(compareByDescending<Album> { it.cover.dateTaken }.thenBy { it.name })
