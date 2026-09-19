package com.nctrl.motogallery.data

import android.content.Context

/**
 * Guarda los favoritos como URIs en SharedPreferences. Es suficiente para un
 * único dispositivo y evita añadir una base de datos a la app.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun save(keys: Set<String>) {
        // Copia defensiva: SharedPreferences no debe recibir el mismo Set mutable.
        prefs.edit().putStringSet(KEY, HashSet(keys)).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
