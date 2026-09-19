package com.nctrl.motogallery.util

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nctrl.motogallery.data.MediaItem

/** Resultado de intentar borrar: o se borró ya, o hace falta que el usuario confirme. */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome
    data class NeedsConfirmation(val request: IntentSender) : DeleteOutcome
    data class Failed(val error: Throwable) : DeleteOutcome
}

object MediaActions {

    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.first().mimeType
                putExtra(Intent.EXTRA_STREAM, items.first().uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType(items)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startChooser(context, intent, "Compartir")
    }

    fun openWith(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(context, intent, "Abrir con")
    }

    fun useAs(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("mimeType", item.mimeType)
        }
        startChooser(context, intent, "Usar como")
    }

    /**
     * Borra los elementos. A partir de Android 11 el sistema pide confirmación
     * con su propio diálogo, así que devolvemos el IntentSender para lanzarlo.
     */
    fun delete(context: Context, items: List<MediaItem>): DeleteOutcome {
        if (items.isEmpty()) return DeleteOutcome.Deleted
        val uris = items.map { it.uri }
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return DeleteOutcome.NeedsConfirmation(
                MediaStore.createDeleteRequest(resolver, uris).intentSender
            )
        }

        return try {
            uris.forEach { resolver.delete(it, null, null) }
            DeleteOutcome.Deleted
        } catch (security: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                security is RecoverableSecurityException
            ) {
                DeleteOutcome.NeedsConfirmation(security.userAction.actionIntent.intentSender)
            } else {
                DeleteOutcome.Failed(security)
            }
        } catch (error: Exception) {
            DeleteOutcome.Failed(error)
        }
    }

    private fun startChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ContextCompat.startActivity(context, chooser, null)
        } catch (_: ActivityNotFoundException) {
            // No hay ninguna app que acepte la acción; no hay nada que hacer.
        }
    }

    private fun commonMimeType(items: List<MediaItem>): String {
        val prefixes = items.map { it.mimeType.substringBefore('/') }.toSet()
        return if (prefixes.size == 1) "${prefixes.first()}/*" else "*/*"
    }

    /** Uri "cruda" para pasar a otras apps sin exponer rutas de archivo. */
    fun contentUri(item: MediaItem): Uri = item.uri
}
