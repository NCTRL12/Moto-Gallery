package com.nctrl.motogallery.ui

import android.app.Activity
import android.content.IntentSender
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

/**
 * Encapsula el borrado: en Android 11+ el sistema muestra su propio diálogo de
 * confirmación, así que hay que lanzar un IntentSender y esperar el resultado.
 */
@Composable
fun rememberDeleteAction(onDeleted: (List<MediaItem>) -> Unit): (List<MediaItem>) -> Unit {
    val context = LocalContext.current
    val deletedMessage = stringResource(R.string.deleted_toast)
    val failedMessage = stringResource(R.string.delete_failed)
    var pending by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onDeleted(pending)
            Toast.makeText(context, deletedMessage, Toast.LENGTH_SHORT).show()
        }
        pending = emptyList()
    }

    return remember(launcher) {
        { items: List<MediaItem> ->
            if (items.isNotEmpty()) {
                pending = items
                when (val outcome = MediaActions.delete(context, items)) {
                    is DeleteOutcome.Deleted -> {
                        onDeleted(items)
                        pending = emptyList()
                        Toast.makeText(context, deletedMessage, Toast.LENGTH_SHORT).show()
                    }

                    is DeleteOutcome.NeedsConfirmation -> launcher.launch(
                        buildRequest(outcome.request)
                    )

                    is DeleteOutcome.Failed -> {
                        pending = emptyList()
                        Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

private fun buildRequest(sender: IntentSender): IntentSenderRequest =
    IntentSenderRequest.Builder(sender).build()
