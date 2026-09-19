package com.nctrl.motogallery.data

import android.net.Uri

/** Una foto o un vídeo del almacenamiento del teléfono. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    /** Duración en milisegundos; 0 para las fotos. */
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    /** Solo en la papelera: segundos epoch en los que Android lo borrará solo. */
    val expiresAt: Long = 0,
) {
    val isVideo: Boolean get() = durationMs > 0 || mimeType.startsWith("video/")

    /** Clave estable para recordar favoritos aunque cambie el id del MediaStore. */
    val key: String get() = uri.toString()
}

/** Carpeta de origen (DCIM/Camera, WhatsApp, Descargas...). */
data class Album(
    val id: Long,
    val name: String,
    val cover: MediaItem,
    val count: Int,
)
