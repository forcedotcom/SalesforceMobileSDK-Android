package com.salesforce.samples.mobilesynccompose.contacts.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityMenuEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.Single
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions

@Composable
fun ContactsActivityContent(
    layoutRestrictions: LayoutRestrictions,
    menuEventHandler: ContactsActivityMenuEventHandler,
    vm: ContactsActivityViewModel
) {
    val uiState by vm.uiState.collectAsState()
//    val detailUiState = uiState.contactDetailsUiState
//    val listUiState = uiState.contactsListUiState
//
//    when (layoutRestrictions.calculatePaneLayout()) {
//        Single -> {
//            if (detailUiState !is NoContactSelected) {
//                SinglePaneContactDetails(
//                    uiState = detailUiState,
//                    activityEventHandler = vm,
//                    sharedEventHandler = vm,
//                    detailEventHandler = vm
//                )
//            } else {
//                SinglePaneContactsList(
//                    uiState = listUiState,
//                    listEventHandler = vm,
//                    sharedEventHandler = vm,
//                    activityEventHandler = vm
//                )
//            }
//        }
//
//        ListDetail -> TODO("ListDetail pane layout not implemented")
//    }
}

private fun LayoutRestrictions.calculatePaneLayout(): PaneLayout = Single

private enum class PaneLayout {
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

//@Composable
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//private fun ListPreview() {
//    val contacts = (1..100).map {
//        Contact.createNewLocal(
//            firstName = "First",
//            lastName = "Last $it",
//            title = "Title",
//        )
//    }
//    val vm = object : PreviewContactsActivityViewModel() {}.apply {
//        mutUiState.value = ContactsActivityUiState(
//            contactDetailsUiState = NoContactSelected,
//            contactsListUiState = ViewingList(contacts)
//        )
//    }
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
//private abstract class PreviewContactsActivityViewModel : ContactsActivityViewModel {
//    val mutUiState = MutableStateFlow(
//        ContactsActivityUiState(
//            contactDetailsUiState = NoContactSelected,
//            contactsListUiState = Loading
//        )
//    )
//    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState
//    override val inspectDbClickEvents: ReceiveChannel<Unit>
//        get() = Channel()
//    override val logoutClickEvents: ReceiveChannel<Unit>
//        get() = Channel()
//
//    override fun handleEvent(event: ContactsActivityUiEvents) {
//        /* no-op */
//    }
//
//    override fun handleEvent(event: ContactsActivitySharedUiEvents) {
//        /* no-op */
//    }
//
//    override fun handleEvent(event: ContactsListUiEvents) {
//        /* no-op */
//    }
//
//    override fun handleEvent(event: ContactDetailUiEvents) {
//        /* no-op */
//    }
//}
