package com.nctrl.motogallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Zona recortada, en proporción (0..1) sobre la imagen ya girada. */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0.001f && top <= 0.001f &&
        right >= 0.999f && bottom >= 0.999f

    companion object {
        val Full = CropRect()
    }
}

/** Proporciones fijas del recorte. */
enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Libre", null),
    SQUARE("1:1", 1f),
    PORTRAIT("3:4", 3f / 4f),
    LANDSCAPE("4:3", 4f / 3f),
    WIDE("16:9", 16f / 9f),
}

/** Filtros de color predefinidos. */
enum class PhotoFilter(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vívido"),
    WARM("Cálido"),
    COOL("Frío"),
    MONO("B/N"),
    SEPIA("Sepia"),
    FADE("Desvaído");

    fun matrix(): ColorMatrix? = when (this) {
        ORIGINAL -> null
        VIVID -> ColorMatrix().apply { setSaturation(1.45f) }
        WARM -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 6f,
                0f, 1.02f, 0f, 0f, 2f,
                0f, 0f, 0.90f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        COOL -> ColorMatrix(
            floatArrayOf(
                0.90f, 0f, 0f, 0f, 0f,
                0f, 1.00f, 0f, 0f, 2f,
                0f, 0f, 1.14f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        MONO -> ColorMatrix().apply { setSaturation(0f) }
        SEPIA -> ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.10f, 0f, 0f, 0f, 22f,
                        0f, 1.00f, 0f, 0f, 8f,
                        0f, 0f, 0.82f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }

        // Negros levantados y menos contraste, como una foto antigua.
        FADE -> ColorMatrix(
            floatArrayOf(
                0.86f, 0f, 0f, 0f, 26f,
                0f, 0.86f, 0f, 0f, 24f,
                0f, 0f, 0.86f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
}

/** Todo lo que el usuario ha tocado en el editor. */
data class EditState(
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val crop: CropRect = CropRect.Full,
    val aspect: CropAspect = CropAspect.FREE,
    val filter: PhotoFilter = PhotoFilter.ORIGINAL,
    /** -1..1 */
    val brightness: Float = 0f,
    /** 0,4..1,8 (1 = sin cambios) */
    val contrast: Float = 1f,
    /** 0..2 (1 = sin cambios) */
    val saturation: Float = 1f,
    /** -1..1, hacia ámbar o hacia azul */
    val warmth: Float = 0f,
) {
    val hasChanges: Boolean
        get() = rotation != 0 || flipHorizontal || !crop.isFull ||
            filter != PhotoFilter.ORIGINAL || brightness != 0f ||
            contrast != 1f || saturation != 1f || warmth != 0f
}

object ImageEditing {

    /** Matriz final: ajustes primero, filtro encima. */
    fun colorMatrix(state: EditState): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.postConcat(ColorMatrix().apply { setSaturation(state.saturation) })
        matrix.postConcat(contrastAndBrightness(state.contrast, state.brightness))
        if (state.warmth != 0f) matrix.postConcat(warmth(state.warmth))
        state.filter.matrix()?.let { matrix.postConcat(it) }
        return matrix
    }

    private fun contrastAndBrightness(contrast: Float, brightness: Float): ColorMatrix {
        // El contraste pivota sobre el gris medio para no desplazar la imagen.
        val translate = (1f - contrast) * 127.5f + brightness * 90f
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun warmth(amount: Float): ColorMatrix = ColorMatrix(
        floatArrayOf(
            1f + amount * 0.18f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - amount * 0.18f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    /**
     * Versión reducida para la vista previa: mover 50 megapíxeles en cada
     * deslizamiento sería inviable.
     */
    suspend fun loadPreview(context: Context, uri: Uri, maxSize: Int = 1600): Bitmap? =
        withContext(Dispatchers.IO) {
            val bounds = decodeBounds(context, uri) ?: return@withContext null
            val sample = sampleSizeFor(bounds.first, bounds.second, maxSize)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null
            applyExifOrientation(context, uri, bitmap)
        }

    /**
     * Aplica todo sobre la imagen a resolución completa y guarda una copia
     * nueva: el original no se toca nunca.
     */
    suspend fun export(
        context: Context,
        uri: Uri,
        sourceName: String,
        state: EditState,
    ): Uri? = withContext(Dispatchers.IO) {
        val source = decodeForExport(context, uri) ?: return@withContext null
        val oriented = applyExifOrientation(context, uri, source) ?: return@withContext null

        val transformed = transform(oriented, state) ?: return@withContext null
        if (transformed !== oriented) oriented.recycle()

        val output = Bitmap.createBitmap(
            transformed.width,
            transformed.height,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(output).drawBitmap(
            transformed,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix(state))
            },
        )
        transformed.recycle()

        val saved = save(context, output, sourceName)
        output.recycle()
        saved
    }

    /** Gira, voltea y recorta, en ese orden. */
    private fun transform(source: Bitmap, state: EditState): Bitmap? {
        val rotated = if (state.rotation != 0 || state.flipHorizontal) {
            val matrix = Matrix().apply {
                if (state.flipHorizontal) postScale(-1f, 1f)
                if (state.rotation != 0) postRotate(state.rotation.toFloat())
            }
            runCatching {
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            }.getOrNull() ?: return null
        } else {
            source
        }

        if (state.crop.isFull) return rotated

        val x = (state.crop.left * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
        val y = (state.crop.top * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
        val width = (state.crop.width * rotated.width).roundToInt()
            .coerceIn(1, rotated.width - x)
        val height = (state.crop.height * rotated.height).roundToInt()
            .coerceIn(1, rotated.height - y)

        val cropped = runCatching {
            Bitmap.createBitmap(rotated, x, y, width, height)
        }.getOrNull() ?: return rotated

        if (cropped !== rotated && rotated !== source) rotated.recycle()
        return cropped
    }

    private fun decodeBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxSize) sample *= 2
        return sample
    }

    /**
     * Intenta resolución completa y va reduciendo si no cabe en memoria: más
     * vale guardar algo algo más pequeño que cerrar la app de golpe.
     */
    private fun decodeForExport(context: Context, uri: Uri): Bitmap? {
        var sample = 1
        repeat(4) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) return bitmap
            sample *= 2
        }
        return null
    }

    /** La cámara guarda la orientación en el EXIF, no en los píxeles. */
    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap? {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun save(context: Context, bitmap: Bitmap, sourceName: String): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${sourceName.substringBeforeLast('.')}_editada_$stamp.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, bitmap, name)
        } else {
            saveToPublicDirectory(context, bitmap, name)
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, name: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
            ?: return null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
            } ?: false
        }.getOrDefault(false)

        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(context: Context, bitmap: Bitmap, name: String): Uri? {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            FOLDER,
        )
        if (!directory.exists() && !directory.mkdirs()) return null

        val file = File(directory, name)
        val written = runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.isSuccess
        if (!written) return null

        // Sin esto la foto nueva no aparece en la galería hasta reiniciar.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
        }
        return runCatching {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    private const val FOLDER = "Galeria"
    private const val QUALITY = 95
}
