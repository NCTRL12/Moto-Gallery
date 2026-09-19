package com.nctrl.motogallery.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.util.DeleteOutcome
import com.nctrl.motogallery.util.MediaActions
import com.nctrl.motogallery.util.TrashOperation

/** Las tres operaciones destructivas, ya conectadas al diálogo del sistema. */
class MediaActionRunner internal constructor(
    val trash: (List<MediaItem>) -> Unit,
    val restore: (List<MediaItem>) -> Unit,
    val deleteForever: (List<MediaItem>) -> Unit,
)

/**
 * Encapsula mover a la papelera, restaurar y borrar del todo. Desde Android 11
 * el sistema pide confirmación con su propia ventana, así que hay que lanzar un
 * IntentSender y esperar el resultado antes de dar la operación por hecha.
 */
@Composable
fun rememberMediaActions(
    onChanged: (List<MediaItem>, TrashOperation) -> Unit,
): MediaActionRunner {
    val context = LocalContext.current
    val trashedMessage = stringResource(R.string.trashed_toast)
    val restoredMessage = stringResource(R.string.restored_toast)
    val deletedMessage = stringResource(R.string.deleted_toast)
    val failedMessage = stringResource(R.string.delete_failed)

    var pending by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var pendingOperation by remember { mutableStateOf(TrashOperation.TRASH) }

    fun messageFor(operation: TrashOperation) = when (operation) {
        TrashOperation.TRASH -> trashedMessage
        TrashOperation.RESTORE -> restoredMessage
        TrashOperation.DELETE -> deletedMessage
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onChanged(pending, pendingOperation)
            Toast.makeText(context, messageFor(pendingOperation), Toast.LENGTH_SHORT).show()
        }
        pending = emptyList()
    }

    return remember(launcher) {
        fun run(items: List<MediaItem>, operation: TrashOperation) {
            if (items.isEmpty()) return
            pending = items
            pendingOperation = operation

            when (val outcome = MediaActions.perform(context, items, operation)) {
                is DeleteOutcome.Deleted -> {
                    onChanged(items, operation)
                    pending = emptyList()
                    Toast.makeText(context, messageFor(operation), Toast.LENGTH_SHORT).show()
                }

                is DeleteOutcome.NeedsConfirmation -> launcher.launch(
                    IntentSenderRequest.Builder(outcome.request).build()
                )

                is DeleteOutcome.Failed -> {
                    pending = emptyList()
                    Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

        MediaActionRunner(
            trash = { run(it, TrashOperation.TRASH) },
            restore = { run(it, TrashOperation.RESTORE) },
            deleteForever = { run(it, TrashOperation.DELETE) },
        )
    }
}
