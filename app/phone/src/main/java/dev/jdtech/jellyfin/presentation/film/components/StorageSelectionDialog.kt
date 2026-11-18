package dev.jdtech.jellyfin.presentation.film.components

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.io.File

@Composable
fun StorageSelectionDialog(
    storageLocations: List<String>,
    onSelect: (storageIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        title = stringResource(CoreR.string.select_storage_location),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacings.default)
        ) {
            storageLocations.forEachIndexed { i, storageLocation ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(48.dp)
                            .clickable { onSelect(i) }
                            .padding(horizontal = MaterialTheme.spacings.default),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = storageLocation, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Human readable names, including the available space, for [StorageSelectionDialog]. */
fun getStorageLocationNames(context: Context, storageLocations: Array<out File>): List<String> =
    storageLocations.map { dir ->
        val locationStringRes =
            if (Environment.isExternalStorageRemovable(dir)) CoreR.string.external
            else CoreR.string.internal
        val locationString = context.getString(locationStringRes)

        val stat = StatFs(dir.path)
        val availableMegaBytes = stat.availableBytes.div(1000000)
        context.getString(CoreR.string.storage_name, locationString, availableMegaBytes)
    }

@Composable
@Preview
private fun StorageSelectionDialogPreview() {
    FindroidTheme {
        StorageSelectionDialog(
            storageLocations = listOf("Internal", "External"),
            onSelect = {},
            onDismiss = {},
        )
    }
}
