package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState.Loading
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState.ViewingList
import com.salesforce.samples.mobilesynccompose.contacts.state.NoContactSelected
import com.salesforce.samples.mobilesynccompose.contacts.state.ViewingContact
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.Single
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.SinglePaneContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane.SinglePaneContactsList
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiState
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ContactsActivityContent(
    layoutRestrictions: LayoutRestrictions,
    vm: ContactsActivityViewModel
) {
    val uiState by vm.uiState.collectAsState()
    val detailUiState = uiState.contactDetailsUiState
    val listUiState = uiState.contactsListUiState
//    val menuButtonContent: @Composable () -> Unit = {
//        ContactsActivityMenuButton(
//            onSyncClick = { vm.handleEvent(SyncClick) },
//            onSwitchUserClick = { vm.handleEvent(SwitchUserClick) },
//            onLogoutClick = { vm.handleEvent(LogoutClick) },
//            onInspectDbClick = { vm.handleEvent(InspectDbClick) }
//        )
//    }

    when (layoutRestrictions.calculatePaneLayout()) {
        Single -> {
            if (detailUiState !is NoContactSelected) {
                SinglePaneContactDetails(
                    uiState = detailUiState,
                    activityEventHandler = vm,
                    detailEventHandler = vm
                )
            } else {
                SinglePaneContactsList(
                    uiState = listUiState,
                    listEventHandler = vm,
                    activityEventHandler = vm
                )
            }
        }
    }

//    val topAppBarContent: @Composable RowScope.() -> Unit
//    val bottomAppBarContent: @Composable RowScope.() -> Unit
//    val fabContent: @Composable () -> Unit
//    val mainContent: @Composable (PaddingValues) -> Unit
//    val topAppBarContent = paneLayout.getTopAppBarContentForUiState(uiState, vm)
//    val bottomAppBarContent = paneLayout.getBottomAppBarContentForUiState(uiState, vm)
//    val fabContent: @Composable () -> Unit = when () {
//    }
//    val mainContent: @Composable (PaddingValues) -> Unit

    // NoContactSelected means we are viewing the list
//    if (detailUiState is NoContactSelected) {
//        topAppBarContent = listUiState.getSinglePaneTopAppBarContent(
//            layoutRestrictions,
//            onUpClick = { vm.handleEvent(ListNavUp) },
//            onSearchTermUpdate = { vm.handleEvent(SearchTermUpdated(newSearchTerm = it)) },
//            menuButtonContent = menuButtonContent
//        )
//
//        bottomAppBarContent = getBottomAppBarContentForContactList(
//            layoutRestrictions,
//            onSearchClick = { vm.handleEvent(SearchClick) }
//        )
//
//        fabContent = {
//            ContactsActivityFab.Add(onAddClick = { vm.handleEvent(ContactCreate) })
//        }
//
//        mainContent = { paddingVals ->
//            ContactListContent(
//                modifier = Modifier.padding(paddingValues = paddingVals.fixForMainContent()),
//                listUiState = listUiState,
//                onContactClick = { vm.handleEvent(ContactView(it)) },
//                onContactDeleteClick = { TODO("onContactDeleteClick") },
//                onContactEditClick = { TODO("onContactEditClick") }
//            )
//            BackHandler(enabled = listUiState is Search, onBack = { vm.handleEvent(NavBack) })
//        }
//    } else {
//        topAppBarContent = detailUiState.getSinglePaneTopAppBarContent(
//            layoutRestrictions,
//            onUpClick = { vm.handleEvent(ListNavUp) },
//            menuButtonContent = menuButtonContent
//        )

//        bottomAppBarContent = getBottomAppBarContentForContactDetail(
//            layoutRestrictions,
//            onDeleteClick = { detailUiState.onDetailDeleteClick(vm) }
//        )
//
//        fabContent = when (detailUiState) {
//            is EditingContact -> {
//                { ContactsActivityFab.Save(onSaveClick = { vm.handleEvent(SaveClick) }) }
//            }
//            NoContactSelected -> {
//                {}
//            }
//            is ViewingContact -> {
//                {
//                    ContactsActivityFab.Edit(
//                        onEditClick = { vm.handleEvent(ContactEdit(detailUiState.contact)) }
//                    )
//                }
//            }
//        }
//
//        mainContent = when (val detailState = uiState.contactDetailsUiState) {
//            is EditingContact -> {
//                { paddingVals ->
//                    ContactDetailContent.CompactEditMode(
//                        modifier = Modifier.padding(paddingVals.fixForMainContent()),
//                        delegate = vm,
//                        state = detailState,
//                    )
//                    BackHandler { vm.handleEvent(ListNavBack) }
//                }
//            }
//            is ViewingContact -> {
//                { paddingVals ->
//                    ContactDetailContent.CompactViewMode(
//                        modifier = Modifier.padding(paddingVals.fixForMainContent()),
//                        state = detailState
//                    )
//                    BackHandler {
//                        vm.handleEvent(ListNavBack)
//                    }
//                }
//            }
//            NoContactSelected -> {
//                { ContactDetailContent.Empty() }
//            }
//        }
//    }
//
//    Scaffold(
//        topBar = { TopAppBar(content = topAppBarContent) },
//        bottomBar = {
//            BottomAppBar(
//                cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50)),
//                content = bottomAppBarContent
//            )
//        },
//        floatingActionButtonPosition = FabPosition.Center,
//        floatingActionButton = fabContent,
//        isFloatingActionButtonDocked = true,
//        content = mainContent
//    )
}

private fun LayoutRestrictions.calculatePaneLayout(): PaneLayout = Single

private enum class PaneLayout {
    Single,
    // ListDetail
    // ...
}

@Composable
fun PaddingValues.fixForMainContent() = PaddingValues(
    start = calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
    top = calculateTopPadding() + 4.dp,
    end = calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
    bottom = calculateBottomPadding() + 4.dp
)

@Composable
fun ContactsActivityMenuButton(handler: ContactsActivityEventHandler) {
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
                    handler.handleEvent(SyncClick)
                }
            ) {
                Text(stringResource(id = cta_sync))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    handler.handleEvent(SwitchUserClick)
                }
            ) {
                Text(stringResource(id = cta_switch_user))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    handler.handleEvent(LogoutClick)
                }
            ) {
                Text(stringResource(id = cta_logout))
            }

            DropdownMenuItem(
                onClick = {
                    menuExpanded = false
                    handler.handleEvent(InspectDbClick)
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
    val vm = object : PreviewContactsActivityViewModel() {}.apply {
        mutUiState.value = ContactsActivityUiState(
            contactDetailsUiState = NoContactSelected,
            contactsListUiState = ViewingList(contacts)
        )
    }
    SalesforceMobileSDKAndroidTheme {
        ContactsActivityContent(
            layoutRestrictions = LayoutRestrictions(
                WindowSizeRestrictions(
                    WindowSizeClass.Compact,
                    WindowSizeClass.Medium
                )
            ),
            vm = vm
        )
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun DetailsPreview() {
    val contact = Contact.createNewLocal(
        firstName = "First",
        lastName = "Last",
        title = "Title",
    )

    val vm = object : PreviewContactsActivityViewModel() {}.apply {
        mutUiState.value = ContactsActivityUiState(
            contactDetailsUiState = ViewingContact(contact),
            contactsListUiState = Loading
        )
    }

    SalesforceMobileSDKAndroidTheme {
        ContactsActivityContent(
            layoutRestrictions = LayoutRestrictions(
                WindowSizeRestrictions(
                    WindowSizeClass.Compact,
                    WindowSizeClass.Medium
                )
            ),
            vm = vm
        )
    }
}

private abstract class PreviewContactsActivityViewModel : ContactsActivityViewModel {
    val mutUiState = MutableStateFlow(
        ContactsActivityUiState(
            contactDetailsUiState = NoContactSelected,
            contactsListUiState = Loading
        )
    )
    override val uiState: StateFlow<ContactsActivityUiState> get() = mutUiState
    override val inspectDbClickEvents: ReceiveChannel<Unit>
        get() = Channel()
    override val logoutClickEvents: ReceiveChannel<Unit>
        get() = Channel()

    override fun handleEvent(event: ContactsActivityUiEvents) {
        /* no-op */
    }

    override fun handleEvent(event: ContactsListUiEvents) {
        /* no-op */
    }

    override fun handleEvent(event: ContactDetailUiEvents) {
        /* no-op */
    }
}
