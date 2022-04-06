package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState

data class DiscardChangesOrUndeleteDialogUiState(
    val onDiscardChanges: () -> Unit,
    val onUndelete: () -> Unit,
) : DialogUiState {
    @Composable
    override fun RenderDialog(modifier: Modifier) {
        AlertDialog(
            onDismissRequest = { /* Disallow skipping by back button */ },
            buttons = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDiscardChanges) { Text(stringResource(id = R.string.cta_discard)) }
                    TextButton(onClick = onUndelete) { Text(stringResource(id = R.string.cta_undelete)) }
                }
            },
            // TODO use string res
            title = { Text("Deleted while editing") },
            text = { Text("This contact was deleted while you were editing it. You may undelete it and continue editing, or you may discard your unsaved changes.") },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            modifier = modifier
        )
    }
}
