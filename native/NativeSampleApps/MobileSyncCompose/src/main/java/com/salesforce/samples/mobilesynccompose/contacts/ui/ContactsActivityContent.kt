package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.ListDetail
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.Single
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.SinglePaneContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.SinglePaneContactsList
import com.salesforce.samples.mobilesynccompose.contacts.vm.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailsUiMode.*
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

@Composable
fun ContactsActivityContent(
    layoutRestrictions: LayoutRestrictions,
    vm: ContactsActivityViewModel,
    onInspectDbClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    val uiState by vm.uiState.collectAsState()
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
        if (uiState.detailsState != null) {
            when (uiState.detailsState.mode) {
                Creating,
                Editing -> SinglePaneContactDetails.EditingContact(
                    details = uiState.detailsState,
                    isSaving = uiState.detailsState.isSaving,
                    detailsContinueEditing = vm::detailsContinueEditing,
                    detailsDiscardChanges = vm::detailsDiscardChanges,
                    onDetailsUpdated = vm::onDetailsUpdated
                )
                LocallyDeleted,
                Viewing -> SinglePaneContactDetails.ViewingContact(
                    details = uiState.detailsState,
                    detailsContinueEditing = vm::detailsContinueEditing,
                    detailsDiscardChanges = vm::detailsDiscardChanges,
                )
            }
        } else {
            SinglePaneContactsList.ViewingContactsList(
                modifier = Modifier.padding(paddingValues = fixedPadding),
                contacts = uiState.listState.contacts,
                isSyncing = uiState.isSyncing,
                listContactClick = vm::listContactClick,
                listDeleteClick = vm::listDeleteClick,
                listEditClick = vm::listEditClick,
                listUndeleteClick = vm::listUndeleteClick
            )
        }

        when (val dialog = uiState.dialog) {
            is DeleteConfirmation -> DeleteConfirmationDialog(
                layoutRestrictions = layoutRestrictions,
                onCancel = dialog.onCancelDelete,
                onDelete = { dialog.onDeleteConfirm(dialog.contactToDelete) },
                objectLabel = dialog.contactToDelete.fullName
            )
            is DiscardChanges -> DiscardChangesDialog(
                layoutRestrictions = layoutRestrictions,
                discardChanges = dialog.onDiscardChanges,
                keepChanges = dialog.onKeepChanges
            )
            is UndeleteConfirmation -> UndeleteConfirmationDialog(
                layoutRestrictions = layoutRestrictions,
                onCancel = dialog.onCancelUndelete,
                onUndelete = { dialog.onUndeleteConfirm(dialog.contactToUndelete) },
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
                with(SinglePaneContactDetails.ScaffoldContent) {
                    uiState.detailsState.let {
                        TopAppBar(
                            label = "${it.firstNameVm.fieldValue} ${it.lastNameVm.fieldValue}",
                            detailsExitClick = vm::detailsExitClick
                        )
                    }
                }
            }

            uiState.listState.searchTerm != null -> {
                with(SinglePaneContactsList.ScaffoldContent) {
                    TopAppBarSearchMode(
                        searchTerm = uiState.listState.searchTerm,
                        listExitSearchClick = vm::listExitSearchClick,
                        onSearchTermUpdated = vm::onSearchTermUpdated
                    )
                }
            }

            else -> {
                with(SinglePaneContactsList.ScaffoldContent) { TopAppBar() }
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
            uiState.detailsState != null -> with(SinglePaneContactDetails.ScaffoldContent) {
                BottomAppBar(
                    mode = uiState.detailsState.mode,
                    detailsDeleteClick = vm::detailsDeleteClick
                )
            }
            uiState.listState.searchTerm != null -> with(SinglePaneContactsList.ScaffoldContent) {
                BottomAppBarSearch()
            }
            else -> with(SinglePaneContactsList.ScaffoldContent) {
                BottomAppBar(listSearchClick = vm::listSearchClick)
            }
        }
    }
}

@Composable
private fun SinglePaneFab(uiState: ContactsActivityUiState, vm: ContactsActivityViewModel) {
    if (uiState.detailsState != null)
        with(SinglePaneContactDetails.ScaffoldContent) {
            when (uiState.detailsState.mode) {
                Creating,
                Editing -> EditModeFab(detailsSaveClick = vm::detailsSaveClick)
                LocallyDeleted -> DeletedModeFab(detailsUndeleteClick = vm::detailsUndeleteClick)
                Viewing -> ViewModeFab(detailsEditClick = vm::detailsEditClick)
            }
        }
    else
        SinglePaneContactsList.ScaffoldContent.Fab(vm::listCreateClick)
}

private fun LayoutRestrictions.calculatePaneLayout(): PaneLayout = Single

enum class PaneLayout {
    Single,
    ListDetail
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

    IconButton(onClick = { menuExpanded = !menuExpanded }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(id = content_desc_menu)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    onSyncClick()
                }
            ) {
                Text(stringResource(id = cta_sync))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    onSwitchUserClick()
                }
            ) {
                Text(stringResource(id = cta_switch_user))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    onLogoutClick()
                }
            ) {
                Text(stringResource(id = cta_logout))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    onInspectDbClick()
                }
            ) {
                Text(stringResource(id = cta_inspect_db))
            }
        }
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
            dialog = null
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
                        mode = LocallyDeleted,
                        origContact = contact,
                        firstNameVm = contact.createFirstNameVm(),
                        lastNameVm = contact.createLastNameVm(),
                        titleVm = contact.createTitleVm()
                    ),
                    isSyncing = false,
                    dialog = null
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

    override fun listContactClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun listCreateClick() {
        TODO("Not yet implemented")
    }

    override fun listDeleteClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun listEditClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun listUndeleteClick(contact: Contact) {
        TODO("Not yet implemented")
    }

    override fun detailsDiscardChanges() {
        TODO("Not yet implemented")
    }

    override fun detailsContinueEditing() {
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
