package com.nctrl.motogallery.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.util.formatDateTime
import com.nctrl.motogallery.util.formatDuration
import com.nctrl.motogallery.util.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(item: MediaItem, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            DetailRow(stringResource(R.string.details_name), item.name)
            DetailRow(stringResource(R.string.details_date), formatDateTime(item.dateTaken))
            DetailRow(stringResource(R.string.details_size), formatSize(item.size))
            if (item.width > 0 && item.height > 0) {
                DetailRow(stringResource(R.string.details_dimensions), "${item.width} × ${item.height}")
            }
            if (item.isVideo) {
                DetailRow(stringResource(R.string.details_duration), formatDuration(item.durationMs))
            }
            DetailRow(stringResource(R.string.details_type), item.mimeType)
            DetailRow(stringResource(R.string.details_path), item.bucketName)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
