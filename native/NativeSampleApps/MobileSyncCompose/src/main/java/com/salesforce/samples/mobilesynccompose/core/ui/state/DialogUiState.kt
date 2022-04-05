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
package com.salesforce.samples.mobilesynccompose.core.ui.state

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectCombinedId

interface DialogUiState {
    @Composable
    fun RenderDialog(modifier: Modifier = Modifier)
}

data class DeleteConfirmationDialogUiState(
    val objIdToDelete: SObjectCombinedId,
    val objName: String?,
    val onCancelDelete: () -> Unit,
    val onDeleteConfirm: (objId: SObjectCombinedId) -> Unit,
) : DialogUiState {
    @Composable
    override fun RenderDialog(modifier: Modifier) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            confirmButton = {
                TextButton(onClick = { onDeleteConfirm(objIdToDelete) }) {
                    Text(stringResource(id = cta_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
            title = { Text(stringResource(id = label_delete_confirm)) },
            text = {
                if (objName.isNullOrBlank())
                    Text(stringResource(id = body_delete_confirm))
                else
                    Text(stringResource(id = body_delete_confirm_with_name, objName))
            },
            modifier = modifier
        )
    }
}

data class DiscardChangesDialogUiState(
    val onDiscardChanges: () -> Unit,
    val onKeepChanges: () -> Unit,
) : DialogUiState {
    @Composable
    override fun RenderDialog(modifier: Modifier) {
        AlertDialog(
            onDismissRequest = onKeepChanges,
            confirmButton = {
                TextButton(onClick = onDiscardChanges) {
                    Text(stringResource(id = cta_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepChanges) {
                    Text(stringResource(id = cta_continue_editing))
                }
            },
            title = { Text(stringResource(id = label_discard_changes)) },
            text = { Text(stringResource(id = body_discard_changes)) },
            modifier = modifier
        )
    }
}

data class UndeleteConfirmationDialogUiState(
    val objIdToUndelete: SObjectCombinedId,
    val objName: String?,
    val onCancelUndelete: () -> Unit,
    val onUndeleteConfirm: (objId: SObjectCombinedId) -> Unit,
) : DialogUiState {
    @Composable
    override fun RenderDialog(modifier: Modifier) {
        AlertDialog(
            onDismissRequest = onCancelUndelete,
            confirmButton = {
                TextButton(onClick = { onUndeleteConfirm(objIdToUndelete) }) {
                    Text(stringResource(id = cta_undelete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelUndelete) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
            title = { Text(stringResource(id = label_undelete_confirm)) },
            text = {
                if (objName.isNullOrBlank())
                    Text(stringResource(id = body_delete_confirm))
                else
                    Text(stringResource(id = body_undelete_confirm_with_name, objName))
            },
            modifier = modifier
        )
    }
}

data class ErrorDialogUiState(
    val onDismiss: () -> Unit,
    val message: String
) : DialogUiState {
    @Composable
    override fun RenderDialog(modifier: Modifier) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = cta_ok))
                }
            },
            title = { Text(stringResource(id = label_error)) },
            text = { Text(message) },
            modifier = modifier
        )
    }
}
