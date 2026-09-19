package com.nctrl.motogallery.util

import java.text.DateFormat
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

/** Cabecera de sección del grid: "12 de marzo de 2025". */
fun formatDayHeader(millis: Long): String {
    if (millis <= 0) return "—"
    return DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis))
}

/** Todos los elementos del mismo día (hora local) comparten esta clave. */
fun dayKey(millis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
}

/** Días que faltan para que Android borre solo un elemento de la papelera. */
fun daysUntil(epochSeconds: Long): Int {
    if (epochSeconds <= 0) return 0
    val remaining = epochSeconds * 1000L - System.currentTimeMillis()
    return if (remaining <= 0) 0 else TimeUnit.MILLISECONDS.toDays(remaining).toInt() + 1
}
