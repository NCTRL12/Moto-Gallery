package com.nctrl.motogallery.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nctrl.motogallery.data.CropAspect
import com.nctrl.motogallery.data.CropRect
import com.nctrl.motogallery.data.EditState
import com.nctrl.motogallery.data.ImageEditing
import com.nctrl.motogallery.data.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** Imagen reducida, ya girada, sobre la que se dibuja la vista previa. */
    val preview: Bitmap? = null,
    val edit: EditState = EditState(),
    val failed: Boolean = false,
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Retoque suave: un poco de luz, contraste y color. */
        val AUTO_ENHANCE = EditState(
            brightness = 0.07f,
            contrast = 1.12f,
            saturation = 1.18f,
            warmth = 0.08f,
        )
    }

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Vista previa sin girar; de aquí se derivan las rotaciones. */
    private var original: Bitmap? = null
    private var loadedUri: Uri? = null

    fun load(uri: Uri, enhance: Boolean = false) {
        if (loadedUri == uri) return
        loadedUri = uri

        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            val bitmap = ImageEditing.loadPreview(getApplication(), uri)
            original = bitmap
            _state.update {
                it.copy(
                    loading = false,
                    preview = bitmap,
                    failed = bitmap == null,
                    // "Mejorar" entra con los ajustes ya puestos, listos para
                    // retocar o guardar directamente.
                    edit = if (enhance) AUTO_ENHANCE else it.edit,
                )
            }
        }
    }

    fun rotateRight() = changeOrientation(rotationDelta = 90)

    fun flipHorizontal() = changeOrientation(flip = true)

    /**
     * La vista previa se regenera ya girada para que las coordenadas del
     * recorte siempre se refieran a lo que se está viendo.
     */
    private fun changeOrientation(rotationDelta: Int = 0, flip: Boolean = false) {
        val source = original ?: return

        val current = _state.value.edit
        val rotation = (current.rotation + rotationDelta + 360) % 360
        val flipHorizontal = if (flip) !current.flipHorizontal else current.flipHorizontal

        viewModelScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                orient(source, rotation, flipHorizontal)
            }
            _state.update {
                it.copy(
                    preview = rotated,
                    // Girar cambia la forma del lienzo: el recorte deja de valer.
                    edit = it.edit.copy(
                        rotation = rotation,
                        flipHorizontal = flipHorizontal,
                        crop = CropRect.Full,
                        aspect = CropAspect.FREE,
                    ),
                )
            }
        }
    }

    private fun orient(source: Bitmap, rotation: Int, flip: Boolean): Bitmap {
        if (rotation == 0 && !flip) return source
        val matrix = Matrix().apply {
            if (flip) postScale(-1f, 1f)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrDefault(source)
    }

    fun setCrop(crop: CropRect) = _state.update { it.copy(edit = it.edit.copy(crop = crop)) }

    fun setAspect(aspect: CropAspect) {
        val preview = _state.value.preview ?: return
        val crop = aspect.ratio?.let { centeredCrop(it, preview.width, preview.height) }
            ?: CropRect.Full
        _state.update { it.copy(edit = it.edit.copy(aspect = aspect, crop = crop)) }
    }

    /** Mayor rectángulo centrado con la proporción pedida. */
    private fun centeredCrop(ratio: Float, width: Int, height: Int): CropRect {
        val imageRatio = width.toFloat() / height.toFloat()
        return if (ratio >= imageRatio) {
            val normalizedHeight = imageRatio / ratio
            val margin = (1f - normalizedHeight) / 2f
            CropRect(0f, margin, 1f, 1f - margin)
        } else {
            val normalizedWidth = ratio / imageRatio
            val margin = (1f - normalizedWidth) / 2f
            CropRect(margin, 0f, 1f - margin, 1f)
        }
    }

    fun setFilter(filter: PhotoFilter) =
        _state.update { it.copy(edit = it.edit.copy(filter = filter)) }

    fun setBrightness(value: Float) =
        _state.update { it.copy(edit = it.edit.copy(brightness = value)) }

    fun setContrast(value: Float) =
        _state.update { it.copy(edit = it.edit.copy(contrast = value)) }

    fun setSaturation(value: Float) =
        _state.update { it.copy(edit = it.edit.copy(saturation = value)) }

    fun setWarmth(value: Float) =
        _state.update { it.copy(edit = it.edit.copy(warmth = value)) }

    /** Deja los ajustes de color como estaban, sin tocar giro ni recorte. */
    fun resetAdjustments() = _state.update {
        it.copy(
            edit = it.edit.copy(
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                warmth = 0f,
                filter = PhotoFilter.ORIGINAL,
            )
        )
    }

    fun save(sourceName: String, onFinished: (Uri?) -> Unit) {
        val uri = loadedUri ?: return onFinished(null)

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = ImageEditing.export(getApplication(), uri, sourceName, _state.value.edit)
            _state.update { it.copy(saving = false) }
            onFinished(result)
        }
    }
}
