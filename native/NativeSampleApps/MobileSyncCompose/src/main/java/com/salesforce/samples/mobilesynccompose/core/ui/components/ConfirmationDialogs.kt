package com.salesforce.samples.mobilesynccompose.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

/**
 * [AlertDialog] which asks the user to confirm whether they want to discard unsaved changes. The
 * user's choice is provided by invocation of either [discardChanges] or [keepChanges].
 */
@Composable
fun DiscardChangesDialog(
    layoutRestrictions: LayoutRestrictions, // TODO use fold restrictions to place the dialog
    discardChanges: () -> Unit,
    keepChanges: () -> Unit
) {
    AlertDialog(
        onDismissRequest = keepChanges,
        confirmButton = {
            TextButton(onClick = discardChanges) {
                Text(stringResource(id = cta_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = keepChanges) {
                Text(stringResource(id = cta_continue_editing))
            }
        },
        title = { Text(stringResource(id = label_discard_changes)) },
        text = { Text(stringResource(id = body_discard_changes)) }
    )
}

/**
 * [AlertDialog] which asks the user to confirm whether they want to delete an item. The user's
 * choice is provided by invocation of either [onDelete] or [onCancel].
 */
@Composable
fun DeleteConfirmationDialog(
    layoutRestrictions: LayoutRestrictions, // TODO use fold restrictions to place the dialog
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    objectLabel: String = ""
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(id = cta_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        title = { Text(stringResource(id = label_delete_confirm)) },
        text = { Text(stringResource(id = body_delete_confirm, objectLabel)) }
    )
}

/**
 * [AlertDialog] which asks the user to confirm whether they want to undelete an item. The user's
 * choice is provided by invocation of either [onUndelete] or [onCancel].
 */
@Composable
fun UndeleteConfirmationDialog(
    layoutRestrictions: LayoutRestrictions, // TODO use fold restrictions to place the dialog
    onCancel: () -> Unit,
    onUndelete: () -> Unit,
    objectLabel: String = ""
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onUndelete) {
                Text(stringResource(id = cta_undelete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        title = { Text(stringResource(id = label_undelete_confirm)) },
        text = { Text(stringResource(id = body_undelete_confirm, objectLabel)) }
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscardChangesPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiscardChangesDialog(
                layoutRestrictions = LayoutRestrictions(
                    WindowSizeRestrictions(
                        horiz = WindowSizeClass.Compact,
                        vert = WindowSizeClass.Medium
                    )
                ),
                discardChanges = {},
                keepChanges = {}
            )
        }
    }
}
