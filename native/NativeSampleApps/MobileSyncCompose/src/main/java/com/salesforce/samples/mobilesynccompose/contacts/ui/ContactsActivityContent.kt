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
package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.state.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactDetailsUiMode.*
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.ListDetail
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.Single
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.createFirstNameVm
import com.salesforce.samples.mobilesynccompose.contacts.vm.createLastNameVm
import com.salesforce.samples.mobilesynccompose.contacts.vm.createTitleVm
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.components.DeleteConfirmationDialog
import com.salesforce.samples.mobilesynccompose.core.ui.components.DiscardChangesDialog
import com.salesforce.samples.mobilesynccompose.core.ui.components.UndeleteConfirmationDialog
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.TestOnly
import org.json.JSONObject

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
    layoutRestrictions: LayoutRestrictions,
    vm: ContactsActivityViewModel,
    onInspectDbClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    val uiState by vm.uiState.collectAsState() // this drives recomposition when the VM updates itself
    Log.d("ContactsActivityContent", "uiState = $uiState")

    when (layoutRestrictions.calculatePaneLayout()) {
        Single -> {
            SinglePaneScaffold(
                layoutRestrictions = layoutRestrictions,
                vm = vm,
                uiState = uiState,
                onInspectDbClick = onInspectDbClick,
                onLogoutClick = onLogoutClick,
                onSwitchUserClick = onSwitchUserClick,
                onSyncClick = onSyncClick
            )
        }

        ListDetail -> TODO("ListDetail pane layout not implemented")
    }
}

@Composable
private fun SinglePaneScaffold(
    layoutRestrictions: LayoutRestrictions,
    vm: ContactsActivityViewModel,
    uiState: ContactsActivityUiState,
    onInspectDbClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            SinglePaneTopAppBar(
                uiState = uiState,
                vm = vm,
                onInspectDbClick = onInspectDbClick,
                onLogoutClick = onLogoutClick,
                onSwitchUserClick = onSwitchUserClick,
                onSyncClick = onSyncClick,
            )
        },
        bottomBar = { SinglePaneBottomAppBar(uiState = uiState, vm = vm) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { SinglePaneFab(uiState = uiState, vm = vm) },
        isFloatingActionButtonDocked = true,
    ) { paddingVals ->
        val fixedPadding = paddingVals.fixForMainContent()

        /* For the purposes of a Single Pane layout, the null/non-null detailsState drives whether to
         * show the details pane as the full screen content. I.e. if the detailsState is null, show
         * the list content, otherwise show the details content. */
        if (uiState.detailsState != null) {
            when (uiState.detailsState.mode) {
                Creating,
                Editing -> ContactDetailsEditingContactSinglePane(
                    details = uiState.detailsState,
                    showLoading = uiState.isSyncing || uiState.detailsState.isSaving,
                    onDetailsUpdated = vm::onDetailsUpdated
                )
                Viewing -> ContactDetailsViewingContactSinglePane(
                    details = uiState.detailsState,
                    showLoading = uiState.isSyncing
                )
            }
        } else {
            ContactsListViewingModeSinglePane(
                modifier = Modifier.padding(paddingValues = fixedPadding),
                contacts = uiState.listState.contacts,
                showLoadingOverlay = uiState.isSyncing || uiState.listState.isSaving,
                listContactClick = vm::listContactClick,
                listDeleteClick = vm::listDeleteClick,
                listEditClick = vm::listEditClick,
                listUndeleteClick = vm::listUndeleteClick
            )
        }

        when (val dialog = uiState.dialogUiState) {
            is DeleteConfirmationDialogUiState -> DeleteConfirmationDialog(
                layoutRestrictions = layoutRestrictions,
                onCancel = dialog.onCancelDelete,
                onDelete = { dialog.onDeleteConfirm(dialog.contactToDelete.id) },
                objectLabel = dialog.contactToDelete.fullName
            )
            is DiscardChangesDialogUiState -> DiscardChangesDialog(
                layoutRestrictions = layoutRestrictions,
                discardChanges = dialog.onDiscardChanges,
                keepChanges = dialog.onKeepChanges
            )
            is UndeleteConfirmationDialogUiState -> UndeleteConfirmationDialog(
                layoutRestrictions = layoutRestrictions,
                onCancel = dialog.onCancelUndelete,
                onUndelete = { dialog.onUndeleteConfirm(dialog.contactToUndelete.id) },
                objectLabel = dialog.contactToUndelete.fullName
            )
            null -> {
                /* clear the dialog */
            }
        }
    }
}

@Composable
private fun SinglePaneTopAppBar(
    uiState: ContactsActivityUiState,
    vm: ContactsActivityViewModel,
    onInspectDbClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    TopAppBar {
        when {
            uiState.detailsState != null -> {
                uiState.detailsState.let {
                    ContactDetailsTopAppBarSinglePane(
                        label = "${it.firstNameVm.fieldValue} ${it.lastNameVm.fieldValue}",
                        detailsExitClick = vm::detailsExitClick,
                        syncIconContent = { SyncImage(contact = uiState.detailsState.updatedContact) }
                    )
                }
            }

            uiState.listState.searchTerm != null -> {
                ContactsListTopAppBarSearchModeSinglePane(
                    searchTerm = uiState.listState.searchTerm,
                    listExitSearchClick = vm::listExitSearchClick,
                    onSearchTermUpdated = vm::onSearchTermUpdated
                )
            }

            else -> {
                ContactsListTopAppBarSinglePane()
            }
        }

        ContactsActivityMenuButton(
            onInspectDbClick = onInspectDbClick,
            onLogoutClick = onLogoutClick,
            onSwitchUserClick = onSwitchUserClick,
            onSyncClick = onSyncClick
        )
    }
}

@Composable
private fun SinglePaneBottomAppBar(
    uiState: ContactsActivityUiState,
    vm: ContactsActivityViewModel
) {
    BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))) {
        when {
            uiState.detailsState != null -> ContactDetailsBottomAppBarSinglePane(
                showDelete = !uiState.detailsState.origContact.locallyDeleted,
                detailsDeleteClick = vm::detailsDeleteClick
            )

            uiState.listState.searchTerm != null -> ContactsListBottomAppBarSearchSinglePane()
            else -> ContactsListBottomAppBarSinglePane(listSearchClick = vm::listSearchClick)
        }
    }
}

@Composable
private fun SinglePaneFab(uiState: ContactsActivityUiState, vm: ContactsActivityViewModel) {
    if (uiState.detailsState != null)
        if (uiState.detailsState.origContact.locallyDeleted) {
            ContactDetailsDeletedModeFabSinglePane(detailsUndeleteClick = vm::detailsUndeleteClick)
        } else {
            when (uiState.detailsState.mode) {
                Creating,
                Editing -> ContactDetailsEditModeFabSinglePane(detailsSaveClick = vm::detailsSaveClick)
                Viewing -> ContactDetailsViewModeFabSinglePane(detailsEditClick = vm::detailsEditClick)
            }
        }
    else
        ContactsListFabSinglePane(vm::listCreateClick)
}

/**
 * Determines which high-level layout to use using the [LayoutRestrictions].
 *
 * We are locking the layout to Single Pane at this time until we can develop for flexible UI.
 */
private fun LayoutRestrictions.calculatePaneLayout(): PaneLayout = Single

enum class PaneLayout {
    Single,
    ListDetail
    // There may be other options, TBD when flexible UI development begins.
}

@Composable
fun PaddingValues.fixForMainContent() = PaddingValues(
    start = calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
    top = calculateTopPadding() + 4.dp,
    end = calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
    bottom = calculateBottomPadding() + 4.dp
)

@Composable
fun ContactsActivityMenuButton(
    onInspectDbClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
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
            DropdownMenuItem(onClick = { dismissMenu(); onSyncClick() }) {
                Text(stringResource(id = cta_sync))
            }

            DropdownMenuItem(onClick = { dismissMenu(); onSwitchUserClick() }) {
                Text(stringResource(id = cta_switch_user))
            }

            DropdownMenuItem(onClick = { dismissMenu(); onLogoutClick() }) {
                Text(stringResource(id = cta_logout))
            }

            DropdownMenuItem(onClick = { dismissMenu(); onInspectDbClick() }) {
                Text(stringResource(id = cta_inspect_db))
            }
        }
    }
}

@Composable
fun SyncImage(contact: Contact) {
    if (contact.locallyDeleted) {
        Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(id = content_desc_item_deleted_locally),
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
        )
    } else {
        Image(
            painter = painterResource(
                id = if (contact.local)
                    R.drawable.sync_local
                else
                    R.drawable.sync_save
            ),
            contentDescription = stringResource(
                id = if (contact.local)
                    content_desc_item_saved_locally
                else
                    content_desc_item_synced
            ),
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
        )
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun ListPreview() {
    val contacts = (1..100).map {
        Contact.createNewLocal(
            firstName = "First",
            lastName = "Last $it",
            title = "Title",
        )
    }

    val vm = PreviewContactsActivityViewModel(
        ContactsActivityUiState(
            listState = ContactsActivityListUiState(
                contacts = contacts,
                searchTerm = null,
            ),
            detailsState = null,
            isSyncing = false,
            dialogUiState = null
        )
    )

    SalesforceMobileSDKAndroidTheme {
        ContactsActivityContent(
            layoutRestrictions = LayoutRestrictions(
                WindowSizeRestrictions(
                    WindowSizeClass.Compact,
                    WindowSizeClass.Medium
                )
            ),
            vm = vm,
            onInspectDbClick = {},
            onLogoutClick = {},
            onSwitchUserClick = {},
            onSyncClick = {}
        )
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun DetailsPreview() {
    val contact = mockLocallyDeletedContact()

    SalesforceMobileSDKAndroidTheme {
        ContactsActivityContent(
            layoutRestrictions = LayoutRestrictions(
                WindowSizeRestrictions(
                    WindowSizeClass.Compact,
                    WindowSizeClass.Medium
                )
            ),
            vm = PreviewContactsActivityViewModel(
                ContactsActivityUiState(
                    listState = ContactsActivityListUiState(
                        contacts = listOf(contact),
                        searchTerm = null
                    ),
                    detailsState = ContactDetailsUiState(
                        mode = Viewing,
                        origContact = contact,
                        firstNameVm = contact.createFirstNameVm(),
                        lastNameVm = contact.createLastNameVm(),
                        titleVm = contact.createTitleVm()
                    ),
                    isSyncing = false,
                    dialogUiState = null
                )
            ),
            onInspectDbClick = {},
            onLogoutClick = {},
            onSwitchUserClick = {},
            onSyncClick = {}
        )
    }
}

private class PreviewContactsActivityViewModel(state: ContactsActivityUiState) :
    ContactsActivityViewModel {

    override val uiState: StateFlow<ContactsActivityUiState> = MutableStateFlow(state)

    override fun sync(syncDownOnly: Boolean) {
        TODO("Not yet implemented")
    }

    override fun listSearchClick() {
        TODO("Not yet implemented")
    }

    override fun listContactClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun listCreateClick() {
        TODO("Not yet implemented")
    }

    override fun listDeleteClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun listUndeleteClick(contactId: String) {
        TODO("Not yet implemented")
    }

    override fun listExitSearchClick() {
        TODO("Not yet implemented")
    }

    override fun onSearchTermUpdated(newSearchTerm: String) {
        TODO("Not yet implemented")
    }

    override fun detailsDeleteClick() {
        TODO("Not yet implemented")
    }

    override fun detailsExitClick() {
        TODO("Not yet implemented")
    }

    override fun onDetailsUpdated(newContact: Contact) {
        TODO("Not yet implemented")
    }

    override fun detailsSaveClick() {
        TODO("Not yet implemented")
    }

    override fun detailsUndeleteClick() {
        TODO("Not yet implemented")
    }

    override fun detailsEditClick() {
        TODO("Not yet implemented")
    }
}

@TestOnly
internal fun mockSyncedContact(): Contact = Contact.coerceFromJson(
    JSONObject()
        .putOpt(Constants.ID, "ID")
        .putOpt(Contact.KEY_FIRST_NAME, "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirstFirst")
        .putOpt(Contact.KEY_LAST_NAME, "Last Last Last Last Last Last Last Last Last Last Last")
        .putOpt(Contact.KEY_TITLE, "Title")
        .putOpt(SyncTarget.LOCALLY_CREATED, false)
        .putOpt(SyncTarget.LOCALLY_DELETED, false)
        .putOpt(SyncTarget.LOCALLY_UPDATED, false)
        .putOpt(SyncTarget.LOCAL, false)
)

@TestOnly
internal fun mockLocallyDeletedContact(): Contact = Contact.coerceFromJson(
    JSONObject()
        .putOpt(Constants.ID, "ID")
        .putOpt(Contact.KEY_FIRST_NAME, "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirstFirst")
        .putOpt(Contact.KEY_LAST_NAME, "Last Last Last Last Last Last Last Last Last Last Last")
        .putOpt(Contact.KEY_TITLE, "Title")
        .putOpt(SyncTarget.LOCALLY_CREATED, false)
        .putOpt(SyncTarget.LOCALLY_DELETED, true)
        .putOpt(SyncTarget.LOCALLY_UPDATED, false)
        .putOpt(SyncTarget.LOCAL, true)
)
