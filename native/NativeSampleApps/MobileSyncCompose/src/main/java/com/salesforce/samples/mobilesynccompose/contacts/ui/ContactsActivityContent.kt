package com.salesforce.samples.mobilesynccompose.contacts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsListUiState.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.DetailComponentUiEvents.SaveClick
import com.salesforce.samples.mobilesynccompose.contacts.vm.ListComponentUiEvents.SearchClick
import com.salesforce.samples.mobilesynccompose.contacts.vm.ListComponentUiEvents.SearchTermUpdated
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
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
    val menuButtonContent: @Composable () -> Unit = {
        ContactsActivityMenuButton(
            onSyncClick = { vm.handleEvent(SyncClick) },
            onSwitchUserClick = { vm.handleEvent(SwitchUserClick) },
            onLogoutClick = { vm.handleEvent(LogoutClick) },
            onInspectDbClick = { vm.handleEvent(InspectDbClick) }
        )
    }

    val topAppBarContent: @Composable RowScope.() -> Unit
    val bottomAppBarContent: @Composable RowScope.() -> Unit
    val fabContent: @Composable () -> Unit
    val mainContent: @Composable (PaddingValues) -> Unit

    // NoContactSelected means we are viewing the list
    if (detailUiState is NoContactSelected) {
        topAppBarContent = listUiState.getTopAppBarContent(
            layoutRestrictions,
            onUpClick = { vm.handleEvent(NavUp) },
            onSearchTermUpdate = { vm.handleEvent(SearchTermUpdated(newSearchTerm = it)) },
            menuButtonContent = menuButtonContent
        )

        bottomAppBarContent = getBottomAppBarContentForContactList(
            layoutRestrictions,
            onSearchClick = { vm.handleEvent(SearchClick) }
        )

        fabContent = {
            ContactActivityFab.Add(onAddClick = { vm.handleEvent(ContactCreate) })
        }

        mainContent = { paddingVals ->
            ContactListContent(
                modifier = Modifier.padding(paddingValues = paddingVals.fixForMainContent()),
                listUiState = listUiState,
//                contacts = listUiState.contacts,
                onContactClick = { vm.handleEvent(ContactView(it)) },
                onContactDeleteClick = { TODO("onContactDeleteClick") },
                onContactEditClick = { TODO("onContactEditClick") }
            )
//            BackHandler(enabled = listUiState is Search, onBack = { vm.handleEvent(NavBack) })
        }
    } else {
        topAppBarContent = detailUiState.getTopAppBarContent(
            layoutRestrictions,
            onUpClick = { vm.handleEvent(NavUp) },
            menuButtonContent = menuButtonContent
        )

        bottomAppBarContent = getBottomAppBarContentForContactDetail(
            layoutRestrictions,
            onDeleteClick = { detailUiState.onDetailDeleteClick(vm) }
        )

        fabContent = when (detailUiState) {
            is EditingContact -> {
                { ContactActivityFab.Save(onSaveClick = { vm.handleEvent(SaveClick) }) }
            }
            NoContactSelected -> {
                {}
            }
            is ViewingContact -> {
                {
                    ContactActivityFab.Edit(
                        onEditClick = { vm.handleEvent(ContactEdit(detailUiState.contact)) }
                    )
                }
            }
        }

        mainContent = when (val detailState = uiState.contactDetailsUiState) {
            is EditingContact -> {
                { paddingVals ->
                    ContactDetailContent.CompactEditMode(
                        modifier = Modifier.padding(paddingVals.fixForMainContent()),
                        delegate = vm,
                        state = detailState,
                    )
                    BackHandler { vm.handleEvent(NavBack) }
                }
            }
            is ViewingContact -> {
                { paddingVals ->
                    ContactDetailContent.CompactViewMode(
                        modifier = Modifier.padding(paddingVals.fixForMainContent()),
                        state = detailState
                    )
                    BackHandler {
                        vm.handleEvent(NavBack)
                    }
                }
            }
            NoContactSelected -> {
                { ContactDetailContent.Empty() }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(content = topAppBarContent) },
        bottomBar = {
            BottomAppBar(
                cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50)),
                content = bottomAppBarContent
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = fabContent,
        isFloatingActionButtonDocked = true,
        content = mainContent
    )
}

private fun ContactDetailUiState.onDetailDeleteClick(handler: ContactActivityEventHandler) {
    when (this) {
        is EditingContact -> handler.handleEvent(ContactDelete(this.originalContact))
        NoContactSelected -> {
            /* no-op */
        }
        is ViewingContact -> handler.handleEvent(ContactDelete(this.contact))
    }
}

@Composable
private fun PaddingValues.fixForMainContent() = PaddingValues(
    start = calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
    top = calculateTopPadding() + 4.dp,
    end = calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
    bottom = calculateBottomPadding() + 4.dp
)

private fun ContactDetailUiState.getTopAppBarContent(
    layoutRestrictions: LayoutRestrictions,
    onUpClick: () -> Unit,
    menuButtonContent: @Composable () -> Unit
): @Composable RowScope.() -> Unit = {
    IconButton(onClick = onUpClick) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = stringResource(id = content_desc_back)
        )
    }

    val label = with(this@getTopAppBarContent) {
        when (this) {
            is EditingContact -> {
                "${firstNameVm.fieldValue} ${lastNameVm.fieldValue}"
            }

            NoContactSelected -> stringResource(id = label_contact_details)

            is ViewingContact -> {
                "${firstNameVm.fieldValue} ${lastNameVm.fieldValue}"
            }
        }
    }

    Text(label, modifier = Modifier.weight(1f))

    menuButtonContent()
}

private fun getBottomAppBarContentForContactDetail(
    layoutRestrictions: LayoutRestrictions,
    onDeleteClick: () -> Unit,
): @Composable RowScope.() -> Unit = {
    Spacer(modifier = Modifier.weight(1f))
    IconButton(onClick = onDeleteClick) {
        Icon(Icons.Default.Delete, contentDescription = stringResource(id = cta_delete))
    }
}

private fun ContactsListUiState.getTopAppBarContent(
    layoutRestrictions: LayoutRestrictions,
    onUpClick: () -> Unit,
    onSearchTermUpdate: (String) -> Unit,
    menuButtonContent: @Composable () -> Unit,
): @Composable RowScope.() -> Unit = {
    when (this@getTopAppBarContent) {
        is Search -> {
            IconButton(onClick = onUpClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = content_desc_back)
                )
            }
            ToggleableEditTextField(
                modifier = Modifier.weight(1f),
                fieldValue = searchTerm,
                isError = false, // cannot be in error state
                isEditEnabled = true,
                onValueChange = onSearchTermUpdate
            )
        }

        Loading,
        is ViewList -> {
            Text(
                stringResource(id = label_contacts),
                modifier = Modifier.weight(1f)
            )
        }
    }

    menuButtonContent()
}

private fun getBottomAppBarContentForContactList(
    layoutRestrictions: LayoutRestrictions,
    onSearchClick: () -> Unit
): @Composable RowScope.() -> Unit = {
    Spacer(modifier = Modifier.weight(1f))
    IconButton(onClick = onSearchClick) {
        Icon(
            Icons.Default.Search,
            contentDescription = stringResource(id = content_desc_search)
        )
    }
}

@Composable
private fun ContactsActivityMenuButton(
    onSyncClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onInspectDbClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { menuExpanded = !menuExpanded }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(id = content_desc_menu)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(onClick = onSyncClick) { Text(stringResource(id = cta_sync)) }
            DropdownMenuItem(onClick = onSwitchUserClick) { Text(stringResource(id = cta_switch_user)) }
            DropdownMenuItem(onClick = onLogoutClick) { Text(stringResource(id = cta_logout)) }
            DropdownMenuItem(onClick = onInspectDbClick) { Text(stringResource(id = cta_inspect_db)) }
        }
    }
}

private object ContactActivityFab {
    @Composable
    fun Add(onAddClick: () -> Unit) {
        FloatingActionButton(onClick = onAddClick) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(id = content_desc_add_contact)
            )
        }
    }

    @Composable
    fun Edit(onEditClick: () -> Unit) {
        FloatingActionButton(onClick = onEditClick) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(id = cta_edit)
            )
        }
    }

    @Composable
    fun Save(onSaveClick: () -> Unit) {
        FloatingActionButton(onClick = onSaveClick) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(id = cta_save)
            )
        }
    }
}

//@Composable
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//private fun ListPreview() {
//    val contacts = (1..100).map {
//        ContactObject(
//            id = it.toString(),
//            firstName = "First",
//            lastName = "Last $it",
//            title = "Title",
//            isLocallyCreated = false,
//            isLocallyDeleted = false,
//            isLocallyUpdated = false
//        )
//    }
//    val vm = object : PreviewContactsActivityViewModel() {}.apply {
//        mutUiState.value = ContactActivityUiState(
//            contactDetailsUiState = NoContactSelected,
//            contactsListUiState = ViewList(contacts)
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

//@Composable
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//private fun DetailsPreview() {
//    val contact = ContactObject(
//        id = "1",
//        firstName = "First",
//        lastName = "Last",
//        title = "Title",
//        isLocallyCreated = false,
//        isLocallyDeleted = false,
//        isLocallyUpdated = false
//    )
//
//    val vm = object : PreviewContactsActivityViewModel() {}.apply {
//        mutUiState.value = ContactActivityUiState(
//            contactDetailsUiState = contact.toViewContactUiState(),
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

private abstract class PreviewContactsActivityViewModel : ContactsActivityViewModel {
    val mutUiState = MutableStateFlow(
        ContactActivityUiState(
            contactDetailsUiState = NoContactSelected,
            contactsListUiState = Loading
        )
    )
    override val uiState: StateFlow<ContactActivityUiState> get() = mutUiState
    override val inspectDbClickEvents: ReceiveChannel<Unit>
        get() = Channel()

    override fun handleEvent(event: ContactsActivityUiEvents) {
        /* no-op */
    }

    override fun handleEvent(event: ListComponentUiEvents) {
        /* no-op */
    }

    override fun handleEvent(event: DetailComponentUiEvents) {
        /* no-op */
    }
}
