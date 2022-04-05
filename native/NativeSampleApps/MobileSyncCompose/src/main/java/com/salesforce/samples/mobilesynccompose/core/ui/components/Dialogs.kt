/*
 * Copyright (c) 2022-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.mobilesynccompose.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.core.ui.state.*
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun ShowOrClearDialog(dialog: DialogUiState?) = when (dialog) {
    is DeleteConfirmationDialogUiState -> DeleteConfirmationDialog(
        onCancel = dialog.onCancelDelete,
        onDelete = { dialog.onDeleteConfirm(dialog.objIdToDelete) },
        objectLabel = dialog.objName
    )
    is DiscardChangesDialogUiState -> DiscardChangesDialog(
        discardChanges = dialog.onDiscardChanges,
        keepChanges = dialog.onKeepChanges
    )
    is UndeleteConfirmationDialogUiState -> UndeleteConfirmationDialog(
        onCancel = dialog.onCancelUndelete,
        onUndelete = { dialog.onUndeleteConfirm(dialog.objIdToUndelete) },
        objectLabel = dialog.objName
    )
    is ErrorDialogUiState -> ErrorDialog(
        onDismiss = dialog.onDismiss,
        message = dialog.message
    )
    null -> {
        /* clear the dialog */
    }
}

/**
 * [AlertDialog] which asks the user to confirm whether they want to discard unsaved changes. The
 * user's choice is provided by invocation of either [discardChanges] or [keepChanges].
 */
@Composable
fun DiscardChangesDialog(
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
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    objectLabel: String? = null
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
        text = {
            if (objectLabel == null || objectLabel.isBlank())
                Text(stringResource(id = body_delete_confirm))
            else
                Text(stringResource(id = body_delete_confirm_with_name, objectLabel))
        }
    )
}

/**
 * [AlertDialog] which asks the user to confirm whether they want to undelete an item. The user's
 * choice is provided by invocation of either [onUndelete] or [onCancel].
 */
@Composable
fun UndeleteConfirmationDialog(
    onCancel: () -> Unit,
    onUndelete: () -> Unit,
    objectLabel: String? = null
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
        text = {
            if (objectLabel == null || objectLabel.isBlank())
                Text(stringResource(id = body_delete_confirm))
            else
                Text(stringResource(id = body_undelete_confirm_with_name, objectLabel))
        }
    )
}

@Composable
fun ErrorDialog(
    onDismiss: () -> Unit,
    message: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = cta_ok))
            }
        },
        title = { Text(stringResource(id = label_error)) },
        text = { Text(message) }
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscardChangesPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiscardChangesDialog(
                discardChanges = {},
                keepChanges = {}
            )
        }
    }
}
