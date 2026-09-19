package com.nctrl.motogallery.data

import android.content.Context
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.math.round

/**
 * Descubre dónde se tomó cada foto y lo traduce a un nombre de sitio.
 *
 * La coordenada sale del propio archivo (EXIF en fotos, metadatos en vídeos) y
 * el nombre lo pone el Geocoder del sistema, que va por un servicio de Android:
 * la app sigue sin pedir permiso de internet. Todo queda cacheado en disco,
 * así que el trabajo pesado solo ocurre la primera vez.
 */
class LocationIndexer(private val context: Context) {

    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)
    private val geocoder: Geocoder? =
        if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null

    /** item.key -> nombre del sitio (cadena vacía si se comprobó y no tenía GPS). */
    private val itemPlaces = HashMap<String, String>()

    /** celda geográfica -> nombre, para no geocodificar dos veces el mismo sitio. */
    private val cellNames = HashMap<String, String>()

    init {
        readInto(KEY_ITEMS, itemPlaces)
        readInto(KEY_CELLS, cellNames)
    }

    fun cached(): Map<String, String> = itemPlaces.filterValues { it.isNotEmpty() }

    /**
     * Recorre los elementos que aún no estén cacheados y va publicando
     * resultados parciales para que la pantalla se rellene mientras trabaja.
     */
    suspend fun index(
        items: List<MediaItem>,
        onProgress: suspend (Map<String, String>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val pending = items.filter { it.key !in itemPlaces }
        if (pending.isEmpty()) return@withContext

        var sinceLastEmit = 0
        var dirty = false

        // Las fotos primero: son mucho más baratas de abrir que los vídeos.
        for (item in pending.sortedBy { it.isVideo }) {
            if (!currentCoroutineContext().isActive) break

            val coordinates = readCoordinates(item)
            val place = coordinates?.let { nameFor(it.first, it.second) }.orEmpty()
            itemPlaces[item.key] = place
            dirty = true
            sinceLastEmit++

            // Refrescar cada pocos hallazgos: la UI se va poblando sin parpadear.
            if (place.isNotEmpty() && sinceLastEmit >= EMIT_EVERY) {
                sinceLastEmit = 0
                onProgress(cached())
            }
        }

        if (dirty) {
            persist()
            onProgress(cached())
        }
    }

    private fun readCoordinates(item: MediaItem): Pair<Double, Double>? =
        if (item.isVideo) readVideoCoordinates(item) else readImageCoordinates(item)

    private fun readImageCoordinates(item: MediaItem): Pair<Double, Double>? = runCatching {
        // En Android 10+ el sistema borra el GPS salvo que se pida el original.
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.setRequireOriginal(item.uri)
        } else {
            item.uri
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).latLong?.let { it[0] to it[1] }
        }
    }.getOrNull()

    private fun readVideoCoordinates(item: MediaItem): Pair<Double, Double>? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            parseIso6709(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
        } finally {
            retriever.release()
        }
    }.getOrNull()

    /** Los vídeos guardan la posición como "+37.4220-122.0840/". */
    private fun parseIso6709(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank()) return null
        val match = ISO_6709.find(raw) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        return latitude to longitude
    }

    private fun nameFor(latitude: Double, longitude: Double): String {
        val cell = cellKey(latitude, longitude)
        cellNames[cell]?.let { return it }

        val resolved = runCatching {
            @Suppress("DEPRECATION")
            geocoder?.getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    address.locality
                        ?: address.subAdminArea
                        ?: address.adminArea
                        ?: address.countryName
                }
        }.getOrNull().orEmpty()

        // Un fallo del geocoder (sin cobertura, por ejemplo) no se cachea:
        // así se reintenta en el próximo arranque.
        if (resolved.isNotBlank()) cellNames[cell] = resolved
        return resolved
    }

    /** Dos decimales ≈ 1 km: fotos del mismo sitio comparten celda. */
    private fun cellKey(latitude: Double, longitude: Double): String {
        val lat = round(latitude * 100) / 100
        val lon = round(longitude * 100) / 100
        return "$lat,$lon"
    }

    private fun readInto(key: String, target: MutableMap<String, String>) {
        val raw = prefs.getString(key, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { k -> target[k] = json.optString(k) }
        }
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_ITEMS, JSONObject(itemPlaces as Map<*, *>).toString())
            .putString(KEY_CELLS, JSONObject(cellNames as Map<*, *>).toString())
            .apply()
    }

    private companion object {
        const val KEY_ITEMS = "item_places"
        const val KEY_CELLS = "cell_names"
        const val EMIT_EVERY = 12
        val ISO_6709 = Regex("""([+-]\d+\.?\d*)([+-]\d+\.?\d*)""")
    }
}

/** Grupo de fotos hechas en el mismo sitio. */
data class Place(
    val name: String,
    val cover: MediaItem,
    val count: Int,
)
