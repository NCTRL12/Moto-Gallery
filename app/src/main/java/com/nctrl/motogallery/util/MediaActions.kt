package com.nctrl.motogallery.util

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nctrl.motogallery.data.MediaItem

/** Qué hacer con los elementos seleccionados. */
enum class TrashOperation { TRASH, RESTORE, DELETE }

/** Resultado de la operación: o se hizo ya, o hace falta que el usuario confirme. */
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
     * Manda a la papelera, restaura o borra para siempre. Desde Android 11 el
     * sistema enseña su propio diálogo, así que se devuelve el IntentSender
     * para que lo lance la pantalla.
     */
    fun perform(
        context: Context,
        items: List<MediaItem>,
        operation: TrashOperation,
    ): DeleteOutcome {
        if (items.isEmpty()) return DeleteOutcome.Deleted
        val uris = items.map { it.uri }
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = when (operation) {
                TrashOperation.TRASH -> MediaStore.createTrashRequest(resolver, uris, true)
                TrashOperation.RESTORE -> MediaStore.createTrashRequest(resolver, uris, false)
                TrashOperation.DELETE -> MediaStore.createDeleteRequest(resolver, uris)
            }
            return DeleteOutcome.NeedsConfirmation(request.intentSender)
        }

        // Sin papelera del sistema (Android 10 o anterior) borrar es definitivo.
        if (operation == TrashOperation.RESTORE) return DeleteOutcome.Deleted

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
}
