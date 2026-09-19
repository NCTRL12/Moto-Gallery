package com.nctrl.motogallery.data

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/**
 * Índice de texto en memoria. Cada elemento se convierte en una frase con todo
 * lo que se puede buscar (nombre, álbum, lugar, fecha en palabras y tipo), de
 * forma que una sola comparación cubre "playa", "marzo", "vídeo" o "2024".
 */
class SearchIndex private constructor(private val documents: List<Document>) {

    private data class Document(val item: MediaItem, val text: String)

    fun query(raw: String): List<MediaItem> {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return emptyList()
        return documents
            .filter { document -> tokens.all { document.text.contains(it) } }
            .map { it.item }
    }

    companion object {
        val EMPTY = SearchIndex(emptyList())

        fun build(items: List<MediaItem>, places: Map<String, String>): SearchIndex =
            SearchIndex(items.map { Document(it, describe(it, places[it.key])) })

        fun tokenize(raw: String): List<String> =
            normalize(raw).split(' ').filter { it.isNotBlank() }

        /** Minúsculas y sin acentos: "Málaga" y "malaga" buscan igual. */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
                .replace(ACCENTS, "")
                .replace('_', ' ')
                .replace('-', ' ')

        private fun describe(item: MediaItem, place: String?): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = item.dateTaken }
            val month = calendar.getDisplayName(
                Calendar.MONTH,
                Calendar.LONG_FORMAT,
                Locale.getDefault(),
            ).orEmpty()
            val weekday = calendar.getDisplayName(
                Calendar.DAY_OF_WEEK,
                Calendar.LONG_FORMAT,
                Locale.getDefault(),
            ).orEmpty()

            val parts = buildList {
                add(item.name)
                add(item.bucketName)
                place?.let { add(it) }
                add(month)
                add(weekday)
                add(calendar.get(Calendar.YEAR).toString())
                add(calendar.get(Calendar.DAY_OF_MONTH).toString())
                add(item.mimeType.substringAfterLast('/'))
                if (item.isVideo) add("video vídeo clip grabacion") else add("foto imagen")
                if (item.mimeType.endsWith("gif")) add("gif animacion")
                if (looksLikeScreenshot(item)) add("captura pantalla screenshot")
                if (place != null) add("ubicacion lugar sitio")
            }
            return normalize(parts.joinToString(" "))
        }

        private fun looksLikeScreenshot(item: MediaItem): Boolean {
            val text = normalize("${item.name} ${item.bucketName}")
            return text.contains("screenshot") || text.contains("captura")
        }

        private val ACCENTS = Regex("\\p{Mn}+")
    }
}

/** Sugerencias que se ofrecen antes de escribir nada en el buscador. */
data class SearchSuggestion(
    val label: String,
    val kind: Kind,
    val cover: MediaItem?,
) {
    enum class Kind { PLACE, ALBUM, YEAR, TYPE }
}
