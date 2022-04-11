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
package com.salesforce.samples.mobilesynccompose.contacts.activity

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.*
import com.salesforce.samples.mobilesynccompose.core.ui.components.*
import com.salesforce.samples.mobilesynccompose.core.ui.state.*

/**
 * The main entry point for all Contacts Activity UI, similar in use-case to a traditional top-level
 * `contacts_activity_layout.xml`.
 *
 * This Composable does not handle configuration changes by itself, leaving it up to the owning
 * Activity to drive the [layoutRestrictions] recomposition; however, this _will_ automatically
 * recompose itself and its content when the [vm] UI state is updated.
 */
@Composable
fun ContactsActivityContent(
    vm: ContactsActivityViewModel,
    detailsVm: ContactDetailsViewModel,
    menuHandler: ContactsActivityMenuHandler,
) {
    // this drives recomposition when the VM updates itself:
    val detailsUiState by detailsVm.uiState.collectAsState()
    val uiState by vm.uiState.collectAsState()

    SinglePane(
        activityUiState = uiState,
        detailsUiState = detailsUiState,
        detailsUiEventHandler = detailsVm,
        menuHandler = menuHandler
    )

    uiState.dialogUiState?.RenderDialog(modifier = Modifier)
}

@Composable
private fun SinglePane(
    activityUiState: ContactsActivityUiState,
    detailsUiState: ContactDetailsUiState,
    detailsUiEventHandler: ContactDetailsUiEventHandler,
    menuHandler: ContactsActivityMenuHandler,
) {
    when (detailsUiState) {
        is ContactDetailsUiState.ViewingContactDetails -> ContactDetailsSinglePaneComponent(
            details = detailsUiState,
            componentUiEventHandler = detailsUiEventHandler,
            menuHandler = menuHandler
        )
        else -> TODO("Single pane list view")
    }
}

//@Composable
//private fun SinglePaneScaffold(
//    listState: ContactsActivityListUiState,
//    detailsUiState: ContactDetailsUiState2,
//    vm: ContactsActivityViewModel,
//    onInspectDbClick: () -> Unit,
//    onLogoutClick: () -> Unit,
//    onSwitchUserClick: () -> Unit,
//    onSyncClick: () -> Unit,
//) {
//    Scaffold(
//        topBar = {
//            if (detailsUiState is ContactDetailsUiState2.HasContact) {
//                SinglePaneTopAppBarDetails(
//                    detailsUiState = detailsUiState,
//                    detailsExitClick = { /*TODO*/ },
//                    onInspectDbClick = onInspectDbClick,
//                    onLogoutClick = onLogoutClick,
//                    onSwitchUserClick = onSwitchUserClick,
//                    onSyncClick = onSyncClick
//                )
//            } else {
//                SinglePaneTopAppBarList(
//                    listUiState = listState,
//                    onListExitSearchClick = vm::listExitSearchClick,
//                    onSearchTermUpdated = vm::onSearchTermUpdated,
//                    onInspectDbClick = onInspectDbClick,
//                    onLogoutClick = onLogoutClick,
//                    onSwitchUserClick = onSwitchUserClick,
//                    onSyncClick = onSyncClick
//                )
//            }
//        },
//        bottomBar = {
//            if (detailsUiState is)
//                SinglePaneBottomAppBar(uiState = uiState, vm = vm)
//        },
//        floatingActionButtonPosition = FabPosition.Center,
//        floatingActionButton = { SinglePaneFab(uiState = uiState, vm = vm) },
//        isFloatingActionButtonDocked = true,
//    ) { paddingVals ->
//        val fixedPadding = paddingVals.fixForMainContent()
//
//        /* For the purposes of a Single Pane layout, the null/non-null detailsState drives whether to
//         * show the details pane as the full screen content. I.e. if the detailsState is null, show
//         * the list content, otherwise show the details content. */
//        if (uiState.detailsState != null) {
//            when (uiState.detailsState.mode) {
//                Creating,
//                Editing -> ContactDetailsEditingContactSinglePane(
//                    details = uiState.detailsState,
//                    showLoading = uiState.isSyncing || uiState.detailsState.isSaving,
//                )
//                Viewing -> ContactDetailsContent(
//                    details = uiState.detailsState,
//                    showLoading = uiState.isSyncing
//                )
//            }
//        } else {
//            ContactsListViewingModeSinglePane(
//                modifier = Modifier.padding(paddingValues = fixedPadding),
//                contacts = uiState.listState.contacts,
//                showLoadingOverlay = uiState.isSyncing || uiState.listState.isSaving,
//                listContactClick = vm::listContactClick,
//                listDeleteClick = vm::listDeleteClick,
//                listEditClick = vm::listEditClick,
//                listUndeleteClick = vm::listUndeleteClick
//            )
//        }
//
//        when (val dialog = uiState.dialogUiState) {
//            is DeleteConfirmationDialogUiState -> DeleteConfirmationDialog(
//                onCancel = dialog.onCancelDelete,
//                onDelete = { dialog.onDeleteConfirm(dialog.objIdToDelete) },
//                objectLabel = dialog.objName
//            )
//            is DiscardChangesDialogUiState -> DiscardChangesDialog(
//                discardChanges = dialog.onDiscardChanges,
//                keepChanges = dialog.onKeepChanges
//            )
//            is UndeleteConfirmationDialogUiState -> UndeleteConfirmationDialog(
//                onCancel = dialog.onCancelUndelete,
//                onUndelete = { dialog.onUndeleteConfirm(dialog.objIdToUndelete) },
//                objectLabel = dialog.objName
//            )
//            is ErrorDialogUiState -> ErrorDialog(
//                onDismiss = dialog.onDismiss,
//                message = dialog.message
//            )
//            null -> {
//                /* clear the dialog */
//            }
//        }
//    }
//}

//@Composable
//private fun SinglePaneTopAppBarDetails(
//    detailsUiState: ContactDetailsUiState2.HasContact,
//    detailsExitClick: () -> Unit,
//    onInspectDbClick: () -> Unit,
//    onLogoutClick: () -> Unit,
//    onSwitchUserClick: () -> Unit,
//    onSyncClick: () -> Unit,
//) {
//    TopAppBar {
//        ContactDetailsTopAppBarSinglePane(
//            label = detailsUiState.personalInfoFields.fullName,
//            syncIconContent = {
//                SyncImage(contactObjLocalStatus = detailsUiState.contactObjLocalStatus)
//            },
//            detailsExitClick = detailsExitClick
//        )
//
//        ContactsActivityMenuButton(
//            onInspectDbClick = onInspectDbClick,
//            onLogoutClick = onLogoutClick,
//            onSwitchUserClick = onSwitchUserClick,
//            onSyncClick = onSyncClick
//        )
//    }
//}

//@Composable
//private fun SinglePaneTopAppBarList(
//    listUiState: ContactsActivityListUiState,
//    onListExitSearchClick: () -> Unit,
//    onSearchTermUpdated: (String) -> Unit,
//    onInspectDbClick: () -> Unit,
//    onLogoutClick: () -> Unit,
//    onSwitchUserClick: () -> Unit,
//    onSyncClick: () -> Unit,
//) {
//    TopAppBar {
//        if (listUiState.searchTerm != null) {
//            ContactsListTopAppBarSearchModeSinglePane(
//                searchTerm = listUiState.searchTerm,
//                listExitSearchClick = onListExitSearchClick,
//                onSearchTermUpdated = onSearchTermUpdated
//            )
//        } else {
//            ContactsListTopAppBarSinglePane()
//        }
//
//        ContactsActivityMenuButton(
//            onInspectDbClick = onInspectDbClick,
//            onLogoutClick = onLogoutClick,
//            onSwitchUserClick = onSwitchUserClick,
//            onSyncClick = onSyncClick
//        )
//    }
//}

//@Composable
//private fun SinglePaneBottomAppBarDetails(
//    uiState: ContactDetailsUiState2,
//    deleteClick: () -> Unit
//) {
//    BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))) {
//        when (uiState) {
//            is ContactDetailsUiState2.HasContact -> ContactDetailsBottomAppBarSinglePane(
//                showDelete = !uiState.contactObjLocalStatus.isLocallyDeleted,
//                detailsDeleteClick = deleteClick
//            )
//
//            // No content
//            ContactDetailsUiState2.InitialLoad -> {}
//            ContactDetailsUiState2.NoContactSelected -> {}
//        }
//    }
//}

//@Composable
//private fun SinglePaneBottomAppBarList(
//    uiState: ContactsActivityListUiState,
//    vm: ContactsActivityViewModel
//) {
//    BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))) {
//        if (uiState.searchTerm != null)
//            ContactsListBottomAppBarSearchSinglePane()
//        else
//            ContactsListBottomAppBarSinglePane(listSearchClick = vm::listSearchClick)
//    }
//}

//@Composable
//private fun SinglePaneFab(uiState: ContactsActivityUiState, vm: ContactsActivityViewModel) {
//    if (uiState.detailsState != null)
//        if (uiState.detailsState.contactObj.localStatus.isLocallyDeleted) {
//            ContactDetailsDeletedModeFabSinglePane(detailsUndeleteClick = vm::detailsUndeleteClick)
//        } else {
//            when (uiState.detailsState.mode) {
//                Creating,
//                Editing -> ContactDetailsEditModeFabSinglePane(detailsSaveClick = vm::detailsSaveClick)
//                Viewing -> ContactDetailsViewModeFabSinglePane(detailsEditClick = vm::detailsEditClick)
//            }
//        }
//    else
//        ContactsListFabSinglePane(vm::listCreateClick)
//}

@Composable
fun PaddingValues.fixForMainContent() = PaddingValues(
    start = calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
    top = calculateTopPadding() + 4.dp,
    end = calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
    bottom = calculateBottomPadding() + 4.dp
)

@Composable
fun ContactsActivityMenuButton(menuHandler: ContactsActivityMenuHandler) {
    var menuExpanded by remember { mutableStateOf(false) }
    fun dismissMenu() {
        menuExpanded = false
    }

    IconButton(onClick = { menuExpanded = !menuExpanded }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(id = content_desc_menu)
        )

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(onClick = { dismissMenu(); menuHandler.onSyncClick() }) {
                Text(stringResource(id = cta_sync))
            }

            DropdownMenuItem(onClick = { dismissMenu(); menuHandler.onSwitchUserClick() }) {
                Text(stringResource(id = cta_switch_user))
            }

            DropdownMenuItem(onClick = { dismissMenu(); menuHandler.onLogoutClick() }) {
                Text(stringResource(id = cta_logout))
            }

            DropdownMenuItem(onClick = { dismissMenu(); menuHandler.onInspectDbClick() }) {
                Text(stringResource(id = cta_inspect_db))
            }
        }
    }
}

@Composable
fun SyncImage( modifier: Modifier = Modifier, uiState: SObjectUiSyncState) {
    when (uiState) {
        SObjectUiSyncState.NotSaved -> TODO("New Icon")

        SObjectUiSyncState.Deleted -> Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(id = content_desc_item_deleted_locally),
            modifier = modifier
        )

        SObjectUiSyncState.Updated -> Image(
            painter = painterResource(id = R.drawable.sync_local),
            contentDescription = stringResource(id = content_desc_item_saved_locally),
            modifier = modifier
        )

        SObjectUiSyncState.Synced -> Image(
            painter = painterResource(id = R.drawable.sync_save),
            contentDescription = stringResource(id = content_desc_item_synced),
            modifier = modifier
        )
    }
}

//@Composable
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//private fun ListPreview() {
//    val contacts = (1..100).map {
//        ContactObject.createNewLocal(
//            firstName = "First",
//            lastName = "Last $it",
//            title = "Title",
//        )
//    }
//
//    val vm = PreviewContactsActivityViewModel(
//        ContactsActivityUiState(
//            listState = ContactsActivityListUiState(
//                contacts = contacts,
//                searchTerm = null,
//            ),
//            detailsState = null,
//            isSyncing = false,
//            dialogUiState = null,
//        )
//    )
//
//    SalesforceMobileSDKAndroidTheme {
//        ContactsActivityContent(
//            layoutRestrictions = LayoutRestrictions(
//                WindowSizeRestrictions(
//                    WindowSizeClass.Compact,
//                    WindowSizeClass.Medium
//                )
//            ),
//            vm = vm,
//            onInspectDbClick = {},
//            onLogoutClick = {},
//            onSwitchUserClick = {},
//            onSyncClick = {}
//        )
//    }
//}
//
//@Composable
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//private fun DetailsPreview() {
//    val contact = mockLocallyDeletedContact()
//
//    SalesforceMobileSDKAndroidTheme {
//        ContactsActivityContent(
//            layoutRestrictions = LayoutRestrictions(
//                WindowSizeRestrictions(
//                    WindowSizeClass.Compact,
//                    WindowSizeClass.Medium
//                )
//            ),
//            vm = PreviewContactsActivityViewModel(
//                ContactsActivityUiState(
//                    listState = ContactsActivityListUiState(
//                        contacts = listOf(contact),
//                        searchTerm = null
//                    ),
//                    detailsState = ContactDetailsUiState(
//                        mode = Viewing,
//                        fieldValueChangeHandler = PREVIEW_CONTACT_FIELD_CHANGE_HANDLER,
//                        contactObj = contact
//                    ),
//                    isSyncing = false,
//                    dialogUiState = null,
//                )
//            ),
//            onInspectDbClick = {},
//            onLogoutClick = {},
//            onSwitchUserClick = {},
//            onSyncClick = {}
//        )
//    }
//}

//private class PreviewContactsActivityViewModel(state: ContactsActivityUiState) :
//    ContactsActivityViewModel,
//    ContactObjectFieldChangeHandler by PREVIEW_CONTACT_FIELD_CHANGE_HANDLER {
//
//    override val uiState: StateFlow<ContactsActivityUiState> = MutableStateFlow(state)
//
//    override fun sync(syncDownOnly: Boolean) {
//        throw NotImplementedError("sync")
//    }
//
//    override fun listSearchClick() {
//        throw NotImplementedError("listSearchClick")
//    }
//
//    override fun listContactClick(id: SObjectId) {
//        throw NotImplementedError("listContactClick")
//    }
//
//    override fun listCreateClick() {
//        throw NotImplementedError("listCreateClick")
//    }
//
//    override fun listDeleteClick(contactId: SObjectId) {
//        throw NotImplementedError("listDeleteClick")
//    }
//
//    override fun listEditClick(contactId: SObjectId) {
//        throw NotImplementedError("listEditClick")
//    }
//
//    override fun listUndeleteClick(contactId: SObjectId) {
//        throw NotImplementedError("listUndeleteClick")
//    }
//
//    override fun listExitSearchClick() {
//        throw NotImplementedError("listExitSearchClick")
//    }
//
//    override fun onSearchTermUpdated(newSearchTerm: String) {
//        throw NotImplementedError("onSearchTermUpdated")
//    }
//
//    override fun detailsDeleteClick() {
//        throw NotImplementedError("detailsDeleteClick")
//    }
//
//    override fun detailsExitClick() {
//        throw NotImplementedError("detailsExitClick")
//    }
//
//    override fun detailsSaveClick() {
//        throw NotImplementedError("detailsSaveClick")
//    }
//
//    override fun detailsUndeleteClick() {
//        throw NotImplementedError("detailsUndeleteClick")
//    }
//
//    override fun detailsEditClick() {
//        throw NotImplementedError("detailsEditClick")
//    }
//}

//@TestOnly
//internal fun mockSyncedContact() = ContactObject.coerceFromJsonOrThrow(
//    JSONObject()
//        .putOpt(Constants.ID, "ID")
//        .putOpt(
//            ContactObject.KEY_FIRST_NAME,
//            "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirstFirst"
//        )
//        .putOpt(
//            ContactObject.KEY_LAST_NAME,
//            "Last Last Last Last Last Last Last Last Last Last Last"
//        )
//        .putOpt(ContactObject.KEY_TITLE, "Title")
//        .putOpt(SyncTarget.LOCALLY_CREATED, false)
//        .putOpt(SyncTarget.LOCALLY_DELETED, false)
//        .putOpt(SyncTarget.LOCALLY_UPDATED, false)
//        .putOpt(SyncTarget.LOCAL, false)
//)
//
//@TestOnly
//internal fun mockLocallyDeletedContact() = ContactObject.coerceFromJsonOrThrow(
//    JSONObject()
//        .putOpt(Constants.ID, "ID")
//        .putOpt(
//            ContactObject.KEY_FIRST_NAME,
//            "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirstFirst"
//        )
//        .putOpt(
//            ContactObject.KEY_LAST_NAME,
//            "Last Last Last Last Last Last Last Last Last Last Last"
//        )
//        .putOpt(ContactObject.KEY_TITLE, "Title")
//        .putOpt(SyncTarget.LOCALLY_CREATED, false)
//        .putOpt(SyncTarget.LOCALLY_DELETED, true)
//        .putOpt(SyncTarget.LOCALLY_UPDATED, false)
//        .putOpt(SyncTarget.LOCAL, true)
//)
