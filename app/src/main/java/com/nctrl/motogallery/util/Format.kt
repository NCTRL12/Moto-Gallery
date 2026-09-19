package com.nctrl.motogallery.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun formatDateTime(millis: Long): String {
    if (millis <= 0) return "—"
    val format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
    return format.format(Date(millis))
}

/** Cómo se reparten las fotos en secciones dentro de la rejilla. */
enum class DateGrouping { DAY, MONTH, YEAR }

/** Clave de sección según la agrupación elegida. */
fun groupKey(millis: Long, grouping: DateGrouping): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    val year = calendar.get(Calendar.YEAR)
    return when (grouping) {
        DateGrouping.DAY -> year * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
        DateGrouping.MONTH -> year * 100L + calendar.get(Calendar.MONTH)
        DateGrouping.YEAR -> year.toLong()
    }
}

/** Título de la sección: "12 de marzo de 2025", "Marzo de 2025" o "2025". */
fun groupHeader(millis: Long, grouping: DateGrouping): String {
    if (millis <= 0) return "—"
    val date = Date(millis)
    return when (grouping) {
        DateGrouping.DAY -> DateFormat.getDateInstance(DateFormat.LONG).format(date)
        DateGrouping.MONTH -> localized("MMMM y", date).replaceFirstChar { it.uppercase() }
        DateGrouping.YEAR -> localized("y", date)
    }
}

/** Usa el orden de fecha propio del idioma del teléfono. */
private fun localized(skeleton: String, date: Date): String {
    val locale = Locale.getDefault()
    val pattern = runCatching {
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    }.getOrNull() ?: skeleton
    return runCatching { SimpleDateFormat(pattern, locale).format(date) }
        .getOrDefault(SimpleDateFormat(skeleton, locale).format(date))
}

/** Días que faltan para que Android borre solo un elemento de la papelera. */
fun daysUntil(epochSeconds: Long): Int {
    if (epochSeconds <= 0) return 0
    val remaining = epochSeconds * 1000L - System.currentTimeMillis()
    return if (remaining <= 0) 0 else TimeUnit.MILLISECONDS.toDays(remaining).toInt() + 1
}
