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
package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.R.string.ok
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.drawable.ic_help
import com.salesforce.samples.mobilesynccompose.R.drawable.ic_undo
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsUiMode
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsUiState
import com.salesforce.samples.mobilesynccompose.contacts.state.toContactDetailsUiState
import com.salesforce.samples.mobilesynccompose.contacts.ui.mockLocallyDeletedContact
import com.salesforce.samples.mobilesynccompose.contacts.vm.*
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.isLocallyDeleted
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.components.OutlinedTextFieldWithHelp
import com.salesforce.samples.mobilesynccompose.core.ui.safeStringResource
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
fun ContactDetailsViewingContactSinglePane(
    modifier: Modifier = Modifier,
    details: ContactDetailsUiState,
    showLoading: Boolean = false
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .verticalScroll(state = scrollState)
    ) {
        if (details.contactObj.localStatus.isLocallyDeleted) {
            LocallyDeletedRow()
        }

        details.vmList.forEach { fieldVm ->
            OutlinedTextFieldWithHelp(
                fieldValue = fieldVm.fieldValue,
                isEditEnabled = false,
                isError = fieldVm.isInErrorState,
                onValueChange = { }, // Not editable in this mode
                label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
            )
        }
    }

    if (showLoading) {
        LoadingOverlay()
    }
}

@Composable
fun ContactDetailsEditingContactSinglePane(
    modifier: Modifier = Modifier,
    details: ContactDetailsUiState,
    showLoading: Boolean,
    onDetailsUpdated: (newContact: ContactObject) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .verticalScroll(state = scrollState)
    ) {
        details.vmList.forEach { fieldVm ->
            OutlinedTextFieldWithHelp(
                fieldValue = fieldVm.fieldValue,
                isEditEnabled = fieldVm.canBeEdited,
                isError = fieldVm.isInErrorState,
                onValueChange = {
                    val newValue = it.ifBlank { null }
                    onDetailsUpdated(fieldVm.onFieldValueChange(newValue))
                },
                label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
            )
        }
    }
    when {
        showLoading -> {
            LoadingOverlay()
        }
    }
}

@Composable
fun RowScope.ContactDetailsTopAppBarSinglePane(
    label: String,
    syncIconContent: @Composable () -> Unit,
    detailsExitClick: () -> Unit
) {
    IconButton(onClick = detailsExitClick) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = stringResource(id = content_desc_back)
        )
    }

    Text(
        label,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    syncIconContent()
}

@Composable
fun RowScope.ContactDetailsBottomAppBarSinglePane(
    showDelete: Boolean,
    detailsDeleteClick: () -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))
    if (showDelete) {
        IconButton(onClick = detailsDeleteClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(id = cta_delete)
            )
        }
    }
}

@Composable
fun ContactDetailsDeletedModeFabSinglePane(detailsUndeleteClick: () -> Unit) {
    FloatingActionButton(onClick = detailsUndeleteClick) {
        Icon(
            painter = painterResource(id = ic_undo),
            contentDescription = stringResource(id = cta_undelete)
        )
    }
}

@Composable
fun ContactDetailsEditModeFabSinglePane(detailsSaveClick: () -> Unit) {
    FloatingActionButton(onClick = detailsSaveClick) {
        Icon(
            Icons.Default.Check,
            contentDescription = stringResource(id = cta_save)
        )
    }
}

@Composable
fun ContactDetailsViewModeFabSinglePane(detailsEditClick: () -> Unit) {
    FloatingActionButton(onClick = detailsEditClick) {
        Icon(
            Icons.Default.Edit,
            contentDescription = stringResource(id = cta_edit)
        )
    }
}

@Composable
private fun LocallyDeletedRow() {
    var infoIsExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { infoIsExpanded = true }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.error) {
            Text(stringResource(id = label_locally_deleted))
            Icon(
                painterResource(id = ic_help),
                contentDescription = stringResource(id = content_desc_help),
                modifier = Modifier
                    .size(32.dp)
                    .padding(8.dp)
            )
        }
    }
    if (infoIsExpanded) {
        LocallyDeletedInfoDialog(onDismiss = { infoIsExpanded = false })
    }
}

@Composable
private fun LocallyDeletedInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = ok)) }
        },
        text = { Text(stringResource(id = body_locally_deleted_info)) }
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailViewModePreview() {
    val contact = ContactObject.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ContactDetailsViewingContactSinglePane(
                details = contact.toContactDetailsUiState(
                    ContactDetailsUiMode.Viewing
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEditModePreview() {
    val origContact = ContactObject.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    val editedContact = origContact.copy(
        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
    )

    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ContactDetailsEditingContactSinglePane(
                details = ContactDetailsUiState(
                    contactObj = editedContact,
                    mode = ContactDetailsUiMode.Editing
                ),
                showLoading = false,
                onDetailsUpdated = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEditModeSavingPreview() {
    val origContact = ContactObject.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    val editedContact = origContact.copy(
        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
    )

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactDetailsEditingContactSinglePane(
                details = ContactDetailsUiState(
                    contactObj = editedContact,
                    mode = ContactDetailsUiMode.Editing,
                    isSaving = true
                ),
                showLoading = true,
                onDetailsUpdated = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LocallyDeletedPreview() {
    val contact = mockLocallyDeletedContact()
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactDetailsViewingContactSinglePane(
                details = contact.toContactDetailsUiState(mode = ContactDetailsUiMode.Viewing),
            )
        }
    }
}
