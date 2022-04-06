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
package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import android.R.string.ok
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuHandler
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.activity.SyncImage
import com.salesforce.samples.mobilesynccompose.core.extensions.takeIfInstance
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.components.OutlinedTextFieldWithHelp
import com.salesforce.samples.mobilesynccompose.core.ui.components.ShowOrClearDialog
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.core.vm.EditableTextFieldUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
fun ContactDetailsSinglePaneComponent(
    details: ContactDetailsUiState,
    componentUiEventHandler: ContactDetailsUiEventHandler,
    menuHandler: ContactsActivityMenuHandler,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier
) {
    val contactDetailsUi = details.takeIfInstance<ContactDetailsUiState.ViewingContactDetails>()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar {
                ContactDetailsTopAppBarSinglePane(
                    label = contactDetailsUi?.fullName ?: "",
                    syncIconContent = {
                        contactDetailsUi?.let {
                            SyncImage(contactObjLocalStatus = it.contactObjLocalStatus)
                        }
                    },
                    detailsExitClick = componentUiEventHandler::exitClick
                )

                ContactsActivityMenuButton(menuHandler = menuHandler)
            }
        },

        bottomBar = {
            BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))) {
                if (contactDetailsUi != null) {
                    ContactDetailsBottomAppBarSinglePane(
                        showDelete = !contactDetailsUi.contactObjLocalStatus.isLocallyDeleted,
                        detailsDeleteClick = componentUiEventHandler::deleteClick
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ContactDetailsFab(uiState = details, handler = componentUiEventHandler)
        },
        isFloatingActionButtonDocked = true
    ) { paddingValues ->
        ContactDetailsContent(
            modifier = Modifier
                .padding(paddingValues)
                .then(contentModifier),
            details = details
        )
    }
}

@Composable
fun ContactDetailsContent(
    details: ContactDetailsUiState,
    modifier: Modifier = Modifier,
) {
    when (details) {
        is ContactDetailsUiState.ViewingContactDetails -> ContactDetailsWithContact(
            modifier = modifier,
            details = details
        )
        is ContactDetailsUiState.InitialLoad -> LoadingOverlay()
        is ContactDetailsUiState.NoContactSelected -> {}
    }
}

@Composable
private fun ContactDetailsWithContact(
    modifier: Modifier = Modifier,
    details: ContactDetailsUiState.ViewingContactDetails
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .verticalScroll(state = scrollState)
    ) {
        if (details.contactObjLocalStatus.isLocallyDeleted) {
            LocallyDeletedRow()
        }

        details.firstNameField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
        details.lastNameField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
        details.titleField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
        details.departmentField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
    }

    if (details.dataOperationIsActive) {
        LoadingOverlay()
    }

    ShowOrClearDialog(details.curDialogUiState)
}

@Composable
private fun EditableTextFieldUiState.OutlinedTextFieldWithHelp(isEditingEnabled: Boolean) {
    OutlinedTextFieldWithHelp(
        fieldValue = fieldValue,
        isEditEnabled = isEditingEnabled && fieldIsEnabled,
        isError = isInErrorState,
        onValueChange = onValueChange,
        label = { labelRes?.let { Text(stringResource(id = it)) } },
        help = { helperRes?.let { Text(stringResource(id = it)) } },
        placeholder = { placeholderRes?.let { Text(stringResource(id = it)) } }
    )
}

//@Composable
//fun ContactDetailsEditingContactSinglePane(
//    modifier: Modifier = Modifier,
//    details: ContactDetailsUiState2,
//    showLoading: Boolean,
//) {
//    val scrollState = rememberScrollState()
//
//    Column(
//        modifier = modifier
//            .padding(horizontal = 8.dp)
//            .verticalScroll(state = scrollState)
//    ) {
//        details.vmList.forEach { fieldVm ->
//            OutlinedTextFieldWithHelp(
//                fieldValue = fieldVm.fieldValue,
//                isEditEnabled = fieldVm.canBeEdited,
//                isError = fieldVm.isInErrorState,
//                onValueChange = fieldVm.onFieldValueChange,
//                label = { Text(safeStringResource(id = fieldVm.labelRes)) },
//                help = { Text(safeStringResource(id = fieldVm.helperRes)) },
//                placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
//            )
//        }
//    }
//    when {
//        showLoading -> {
//            LoadingOverlay()
//        }
//    }
//}

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
fun ContactDetailsFab(
    modifier: Modifier = Modifier,
    uiState: ContactDetailsUiState,
    handler: ContactDetailsUiEventHandler
) {
    when (uiState) {
        is ContactDetailsUiState.ViewingContactDetails -> {
            when {
                uiState.contactObjLocalStatus.isLocallyDeleted ->
                    FloatingActionButton(
                        onClick = handler::undeleteClick,
                        modifier = modifier
                    ) {
                        Icon(
                            painter = painterResource(id = ic_undo),
                            contentDescription = stringResource(id = cta_undelete)
                        )
                    }

                uiState.isEditingEnabled ->
                    FloatingActionButton(
                        onClick = handler::saveClick,
                        modifier = modifier
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(id = cta_save)
                        )
                    }

                else ->
                    FloatingActionButton(
                        onClick = handler::editClick,
                        modifier = modifier
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(id = cta_edit)
                        )
                    }
            }
        }
        is ContactDetailsUiState.InitialLoad -> {}
        is ContactDetailsUiState.NoContactSelected -> FloatingActionButton(
            onClick = handler::createClick,
            modifier = modifier
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(id = content_desc_add_contact)
            )
        }
    }
}

//@Composable
//fun ContactDetailsDeletedModeFabSinglePane(detailsUndeleteClick: () -> Unit) {
//    FloatingActionButton(onClick = detailsUndeleteClick) {
//        Icon(
//            painter = painterResource(id = ic_undo),
//            contentDescription = stringResource(id = cta_undelete)
//        )
//    }
//}
//
//@Composable
//fun ContactDetailsEditModeFabSinglePane(detailsSaveClick: () -> Unit) {
//    FloatingActionButton(onClick = detailsSaveClick) {
//        Icon(
//            Icons.Default.Check,
//            contentDescription = stringResource(id = cta_save)
//        )
//    }
//}
//
//@Composable
//fun ContactDetailsViewModeFabSinglePane(detailsEditClick: () -> Unit) {
//    FloatingActionButton(onClick = detailsEditClick) {
//        Icon(
//            Icons.Default.Edit,
//            contentDescription = stringResource(id = cta_edit)
//        )
//    }
//}

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
    val contact = ContactObject(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle",
        department = "DepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartment"
    )

    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ContactDetailsSinglePaneComponent(
                details = ContactDetailsUiState.ViewingContactDetails(
                    firstNameField = ContactDetailsField.FirstName(
                        fieldValue = contact.firstName,
                        onValueChange = {}
                    ),
                    lastNameField = ContactDetailsField.LastName(
                        fieldValue = contact.lastName,
                        onValueChange = {}
                    ),
                    titleField = ContactDetailsField.Title(
                        fieldValue = contact.title,
                        onValueChange = {}
                    ),
                    departmentField = ContactDetailsField.Department(
                        fieldValue = contact.department,
                        onValueChange = {}
                    ),
                    contactObjLocalStatus = LocalStatus.LocallyCreated,
                    isEditingEnabled = false
                ),
                componentUiEventHandler = PREVIEW_CONTACT_DETAILS_UI_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
            )
        }
    }
}

//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//@Composable
//private fun ContactDetailEditModePreview() {
//    val origContact = ContactObject(
//        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
//        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
//        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle",
//        department = "DepartmentDepartmentDepartmentDepartmentDepartmentDepartment"
//    )
//
//    SalesforceMobileSDKAndroidTheme {
//        Surface(modifier = Modifier.fillMaxSize()) {
//            ContactDetailsSinglePaneContent(
//                details = ContactDetailsUiState(
//                    contactObj = editedContact,
//                    mode = ContactDetailsUiMode.Editing,
//                    fieldValueChangeHandler = PREVIEW_CONTACT_FIELD_CHANGE_HANDLER
//                ),
//                showLoading = false,
//            )
//        }
//    }
//}
//
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//@Composable
//private fun ContactDetailEditModeSavingPreview() {
//    val origContact = ContactObject.createNewLocal(
//        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
//        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
//        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
//    )
//    val editedContact = origContact.copy(
//        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
//        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
//    )
//
//    SalesforceMobileSDKAndroidTheme {
//        Surface {
//            ContactDetailsEditingContactSinglePane(
//                details = ContactDetailsUiState(
//                    contactObj = editedContact,
//                    mode = ContactDetailsUiMode.Editing,
//                    fieldValueChangeHandler = PREVIEW_CONTACT_FIELD_CHANGE_HANDLER,
//                    isSaving = true
//                ),
//                showLoading = true,
//            )
//        }
//    }
//}
//
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//@Composable
//private fun LocallyDeletedPreview() {
//    val contact = mockLocallyDeletedContact()
//    SalesforceMobileSDKAndroidTheme {
//        Surface {
//            ContactDetailsSinglePaneContent(
//                details = contact.toContactDetailsUiState(
//                    mode = ContactDetailsUiMode.Viewing,
//                    fieldValueChangeHandler = PREVIEW_CONTACT_FIELD_CHANGE_HANDLER
//                ),
//            )
//        }
//    }
//}
//

val PREVIEW_CONTACT_DETAILS_UI_HANDLER = object : ContactDetailsUiEventHandler {
    override fun createClick() {}
    override fun deleteClick() {}
    override fun undeleteClick() {}
    override fun editClick() {}
    override fun exitClick() {}
    override fun saveClick() {}

}
val PREVIEW_CONTACT_FIELD_CHANGE_HANDLER = object : ContactDetailsFieldChangeHandler {
    override fun onFirstNameChange(newFirstName: String) {}
    override fun onLastNameChange(newLastName: String) {}
    override fun onTitleChange(newTitle: String) {}
    override fun onDepartmentChange(newDepartment: String) {}
}

val PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER = object : ContactsActivityMenuHandler {
    override fun onInspectDbClick() {}
    override fun onLogoutClick() {}
    override fun onSwitchUserClick() {}
    override fun onSyncClick() {}
}
