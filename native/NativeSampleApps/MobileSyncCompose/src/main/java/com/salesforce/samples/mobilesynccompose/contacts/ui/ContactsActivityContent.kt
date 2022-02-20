package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityMenuEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.ListDetail
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.Single
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.SinglePaneContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.SinglePaneContactsList
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailsUiMode.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiState
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ContactsActivityContent(
    layoutRestrictions: LayoutRestrictions,
    menuEventHandler: ContactsActivityMenuEventHandler,
    vm: ContactsActivityViewModel
) {
    val uiState by vm.uiState.collectAsState()

    when (layoutRestrictions.calculatePaneLayout()) {
        Single -> {
            SinglePaneScaffold(
                layoutRestrictions = layoutRestrictions,
                menuEventHandler = menuEventHandler,
                vm = vm,
                uiState = uiState
            )
        }

        ListDetail -> TODO("ListDetail pane layout not implemented")
    }
}

@Composable
private fun SinglePaneScaffold(
    layoutRestrictions: LayoutRestrictions,
    menuEventHandler: ContactsActivityMenuEventHandler,
    vm: ContactsActivityViewModel,
    uiState: ContactsActivityUiState
) {
    Scaffold(
        topBar = {
            SinglePaneTopAppBar(
                uiState = uiState,
                vm = vm,
                menuEventHandler = menuEventHandler
            )
        },
        bottomBar = { SinglePaneBottomAppBar(uiState = uiState, vm = vm) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { SinglePaneFab(uiState = uiState, vm = vm) },
        isFloatingActionButtonDocked = true,
    ) { paddingVals ->
        val fixedPadding = paddingVals.fixForMainContent()
        if (uiState.detailsState != null)
            when (uiState.detailsState.mode) {
                Creating,
                Editing -> SinglePaneContactDetails.EditingContact(
                    details = uiState.detailsState,
                    isSaving = uiState.detailsState.isSaving,
                    handler = vm,
                )
                Viewing -> SinglePaneContactDetails.ViewingContact(
                    details = uiState.detailsState
                )
            }
        else
            SinglePaneContactsList.ViewingContactsList(
                modifier = Modifier.padding(paddingValues = fixedPadding),
                contacts = uiState.contacts,
                isSyncing = uiState.isSyncing,
                handler = vm
            )
    }
}

@Composable
private fun SinglePaneTopAppBar(
    uiState: ContactsActivityUiState,
    vm: ContactsActivityViewModel,
    menuEventHandler: ContactsActivityMenuEventHandler
) {
    TopAppBar {
        when {
            uiState.detailsState != null -> {
                with(SinglePaneContactDetails.ScaffoldContent) {
                    uiState.detailsState.let {
                        TopAppBar(
                            label = "${it.firstNameVm.fieldValue} ${it.lastNameVm.fieldValue}",
                            handler = vm
                        )
                    }
                }
            }

            uiState.searchTerm != null -> {
                with(SinglePaneContactsList.ScaffoldContent) {
                    TopAppBarSearchMode(searchTerm = uiState.searchTerm, handler = vm)
                }
            }

            else -> {
                with(SinglePaneContactsList.ScaffoldContent) { TopAppBar() }
            }
        }

        ContactsActivityMenuButton(handler = menuEventHandler)
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
                BottomAppBar(vm)
            }
            uiState.searchTerm != null -> with(SinglePaneContactsList.ScaffoldContent) {
                BottomAppBarSearch()
            }
            else -> with(SinglePaneContactsList.ScaffoldContent) {
                BottomAppBar(handler = vm)
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
                Editing -> EditModeFab(handler = vm)
                Viewing -> ViewModeFab(handler = vm)
            }
        }
    else
        SinglePaneContactsList.ScaffoldContent.Fab(handler = vm)
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
fun ContactsActivityMenuButton(handler: ContactsActivityMenuEventHandler) {
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
                    handler.syncClicked()
                }
            ) {
                Text(stringResource(id = cta_sync))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    handler.switchUserClicked()
                }
            ) {
                Text(stringResource(id = cta_switch_user))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    handler.logoutClicked()
                }
            ) {
                Text(stringResource(id = cta_logout))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    handler.inspectDbClicked()
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
            contacts = contacts,
            detailsState = null,
            searchTerm = null,
            isSyncing = false,
            showDiscardChanges = false
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
            menuEventHandler = PreviewMenuEventHandler,
            vm = vm
        )
    }
}

//@Composable
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//private fun DetailsPreview() {
//    val contact = Contact.createNewLocal(
//        firstName = "First",
//        lastName = "Last",
//        title = "Title",
//    )
//
//    val vm = object : PreviewContactsActivityViewModel() {}.apply {
//        mutUiState.value = ContactsActivityUiState(
//            contactDetailsUiState = ViewingContact(contact),
//            contactsListUiState = Loading
//        )
//    }
//
//    SalesforceMobileSDKAndroidTheme {
//        ContactsActivityContent(
//            layoutRestrictions = LayoutRestrictions(
//                WindowSizeRestrictions(
//                    WindowSizeClass.Compact,
//                    WindowSizeClass.Medium
//                )
//            ),
//            vm = vm
//        )
//    }
//}
//
private class PreviewContactsActivityViewModel(state: ContactsActivityUiState) :
    ContactsActivityViewModel {

    override val uiState: StateFlow<ContactsActivityUiState> = MutableStateFlow(state)

    override fun sync(syncDownOnly: Boolean) {
        TODO("Not yet implemented")
    }

    override fun searchClick() {
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

    override fun exitSearch() {
        TODO("Not yet implemented")
    }

    override fun searchTermUpdated(newSearchTerm: String) {
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

    override fun saveClick() {
        TODO("Not yet implemented")
    }

    override fun detailsEditClick() {
        TODO("Not yet implemented")
    }
}

private object PreviewMenuEventHandler : ContactsActivityMenuEventHandler {
    override fun inspectDbClicked() {
        TODO("Not yet implemented")
    }

    override fun logoutClicked() {
        TODO("Not yet implemented")
    }

    override fun switchUserClicked() {
        TODO("Not yet implemented")
    }

    override fun syncClicked() {
        TODO("Not yet implemented")
    }

}
