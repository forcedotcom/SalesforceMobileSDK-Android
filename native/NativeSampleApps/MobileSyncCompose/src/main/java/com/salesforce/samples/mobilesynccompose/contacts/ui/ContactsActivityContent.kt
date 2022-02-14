package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents.DetailNavUp
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents.SaveClick
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.state.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState.*
import com.salesforce.samples.mobilesynccompose.contacts.ui.PaneLayout.Single
import com.salesforce.samples.mobilesynccompose.contacts.vm.*
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
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
    val menuButtonContent: @Composable () -> Unit = {
        ContactsActivityMenuButton(
            onSyncClick = { vm.handleEvent(SyncClick) },
            onSwitchUserClick = { vm.handleEvent(SwitchUserClick) },
            onLogoutClick = { vm.handleEvent(LogoutClick) },
            onInspectDbClick = { vm.handleEvent(InspectDbClick) }
        )
    }

    val paneLayout = layoutRestrictions.calculatePaneLayout()

//    val topAppBarContent: @Composable RowScope.() -> Unit
//    val bottomAppBarContent: @Composable RowScope.() -> Unit
//    val fabContent: @Composable () -> Unit
//    val mainContent: @Composable (PaddingValues) -> Unit
    val topAppBarContent = paneLayout.getTopAppBarContentForUiState(uiState, vm)
    val bottomAppBarContent = paneLayout.getBottomAppBarContentForUiState(uiState, vm)
    val fabContent: @Composable () -> Unit = when () {
    }
    val mainContent: @Composable (PaddingValues) -> Unit

    // NoContactSelected means we are viewing the list
    if (detailUiState is NoContactSelected) {
//        topAppBarContent = listUiState.getSinglePaneTopAppBarContent(
//            layoutRestrictions,
//            onUpClick = { vm.handleEvent(ListNavUp) },
//            onSearchTermUpdate = { vm.handleEvent(SearchTermUpdated(newSearchTerm = it)) },
//            menuButtonContent = menuButtonContent
//        )

        bottomAppBarContent = getBottomAppBarContentForContactList(
            layoutRestrictions,
            onSearchClick = { vm.handleEvent(SearchClick) }
        )

        fabContent = {
            ContactsActivityFab.Add(onAddClick = { vm.handleEvent(ContactCreate) })
        }

        mainContent = { paddingVals ->
            ContactListContent(
                modifier = Modifier.padding(paddingValues = paddingVals.fixForMainContent()),
                listUiState = listUiState,
                onContactClick = { vm.handleEvent(ContactView(it)) },
                onContactDeleteClick = { TODO("onContactDeleteClick") },
                onContactEditClick = { TODO("onContactEditClick") }
            )
//            BackHandler(enabled = listUiState is Search, onBack = { vm.handleEvent(NavBack) })
        }
    } else {
//        topAppBarContent = detailUiState.getSinglePaneTopAppBarContent(
//            layoutRestrictions,
//            onUpClick = { vm.handleEvent(ListNavUp) },
//            menuButtonContent = menuButtonContent
//        )

        bottomAppBarContent = getBottomAppBarContentForContactDetail(
            layoutRestrictions,
            onDeleteClick = { detailUiState.onDetailDeleteClick(vm) }
        )

        fabContent = when (detailUiState) {
            is EditingContact -> {
                { ContactsActivityFab.Save(onSaveClick = { vm.handleEvent(SaveClick) }) }
            }
            NoContactSelected -> {
                {}
            }
            is ViewingContact -> {
                {
                    ContactsActivityFab.Edit(
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
                    BackHandler { vm.handleEvent(ListNavBack) }
                }
            }
            is ViewingContact -> {
                { paddingVals ->
                    ContactDetailContent.CompactViewMode(
                        modifier = Modifier.padding(paddingVals.fixForMainContent()),
                        state = detailState
                    )
                    BackHandler {
                        vm.handleEvent(ListNavBack)
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

private fun LayoutRestrictions.calculatePaneLayout(): PaneLayout = Single

private enum class PaneLayout {
    Single,
    // ListDetail
    // ...
}

private fun PaneLayout.getTopAppBarContentForUiState(
    uiState: ContactsActivityUiState,
    vm: ContactsActivityViewModel
): @Composable RowScope.() -> Unit =
    when (this) {
        Single -> {
            if (uiState.contactDetailsUiState is NoContactSelected) {
                // Single pane layout and no contact selected means we are viewing list pane:
                with(ContactsListAppBarContent.SinglePane) { uiState.contactsListUiState.topBar(vm) }
            } else {
                with(ContactDetailAppBarContent.SinglePane) {
                    uiState.contactDetailsUiState.topBar(vm)
                }
            }
        }
    }

private fun PaneLayout.getBottomAppBarContentForUiState(
    uiState: ContactsActivityUiState,
    vm: ContactsActivityViewModel
): @Composable RowScope.() -> Unit =
    when (this) {
        Single -> {
            if (uiState.contactDetailsUiState is NoContactSelected) {
                with(ContactsListAppBarContent.SinglePane) {
                    uiState.contactsListUiState.bottomBar(eventHandler = vm)
                }
            } else {
                with(ContactDetailAppBarContent.SinglePane) {
                    uiState.contactDetailsUiState.bottomBar(eventHandler = vm)
                }
            }
        }
    }

private object ContactsListAppBarContent {

    object SinglePane {

        // TODO it would probably be better (but needs research) to not rebuild the lambda every time upstream changes
        fun ContactsListUiState.topBar(eventHandler: ContactsListEventHandler): @Composable RowScope.() -> Unit =
            {
                var handleBack = false
                when (this@topBar) {
                    is Searching -> {
                        IconButton(onClick = { eventHandler.handleEvent(ListNavUp) }) {
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
                            onValueChange = {
                                eventHandler.handleEvent(
                                    SearchTermUpdated(
                                        newSearchTerm = it
                                    )
                                )
                            }
                        )
                        handleBack = true
                    }

                    Loading,
                    is ViewingList -> {
                        Text(
                            stringResource(id = label_contacts),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

//    menuButtonContent()
                BackHandler(enabled = handleBack) {
                    eventHandler.handleEvent(
                        ListNavUp
                    )
                }
            }

        fun ContactsListUiState.bottomBar(eventHandler: ContactsListEventHandler): @Composable RowScope.() -> Unit =
            {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { eventHandler.handleEvent(SearchClick) }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(id = content_desc_search)
                    )
                }
            }
    }
}

private object ContactDetailAppBarContent {

    object SinglePane {

        fun ContactDetailUiState.topBar(eventHandler: ContactDetailEventHandler): @Composable RowScope.() -> Unit =
            {
                IconButton(onClick = { eventHandler.handleEvent(DetailNavUp) }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = content_desc_back)
                    )
                }

                val label = with(this@topBar) {
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

//    menuButtonContent()
            }

        fun ContactDetailUiState.bottomBar(eventHandler: ContactsActivityEventHandler): @Composable RowScope.() -> Unit =
            {
                val onDelete = when (this@bottomBar) {
                    is EditingContact -> {
                        { eventHandler.handleEvent(ContactDelete(this@bottomBar.originalContact)) }
                    }
                    NoContactSelected -> {
                        { /* no-op */ }
                    }
                    is ViewingContact -> {
                        { eventHandler.handleEvent(ContactDelete(this@bottomBar.contact)) }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = cta_delete))
                }
            }
    }
}

//private fun ContactDetailUiState.onDetailDeleteClick(handler: ContactActivityEventHandler) {
//    when (this) {
//        is EditingContact -> handler.handleEvent(ContactDelete(this.originalContact))
//        NoContactSelected -> {
//            /* no-op */
//        }
//        is ViewingContact -> handler.handleEvent(ContactDelete(this.contact))
//    }
//}

@Composable
private fun PaddingValues.fixForMainContent() = PaddingValues(
    start = calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
    top = calculateTopPadding() + 4.dp,
    end = calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
    bottom = calculateBottomPadding() + 4.dp
)

//private fun getBottomAppBarContentForContactDetail(
//    layoutRestrictions: LayoutRestrictions,
//    onDeleteClick: () -> Unit,
//): @Composable RowScope.() -> Unit = {
//    Spacer(modifier = Modifier.weight(1f))
//    IconButton(onClick = onDeleteClick) {
//        Icon(Icons.Default.Delete, contentDescription = stringResource(id = cta_delete))
//    }
//}

//private fun getBottomAppBarContentForContactList(
//    layoutRestrictions: LayoutRestrictions,
//    onSearchClick: () -> Unit
//): @Composable RowScope.() -> Unit = {
//    Spacer(modifier = Modifier.weight(1f))
//    IconButton(onClick = onSearchClick) {
//        Icon(
//            Icons.Default.Search,
//            contentDescription = stringResource(id = content_desc_search)
//        )
//    }
//}

@Composable
fun ContactsActivityMenuButton(handler: ContactsActivityEventHandler) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = { menuExpanded = !menuExpanded }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(id = content_desc_menu)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(onClick = { handler.handleEvent(SyncClick) }) {
                Text(stringResource(id = cta_sync))
            }
            DropdownMenuItem(onClick = { handler.handleEvent(SwitchUserClick) }) {
                Text(stringResource(id = cta_switch_user))
            }
            DropdownMenuItem(onClick = { handler.handleEvent(LogoutClick) }) {
                Text(stringResource(id = cta_logout))
            }
            DropdownMenuItem(onClick = { handler.handleEvent(InspectDbClick) }) {
                Text(stringResource(id = cta_inspect_db))
            }
        }
    }
}

//private fun PaneLayout.getFabContentForUiState(
//    uiState: ContactsActivityUiState,
//    vm: ContactsActivityViewModel
//): @Composable () -> Unit =
//    when (this) {
//        Single -> {
//            when ()
//        }
//    }

private object ContactsActivityFab {
    object SinglePane {
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
