package com.salesforce.samples.mobilesynccompose.contacts.ui

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
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.ViewContactList
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailUiState
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailUiState.ContactSelected
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailUiState.NoContactSelected
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactListUiState
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactListUiState.*
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField

@Composable
fun ContactActivityContent(
    layoutRestrictions: LayoutRestrictions,
    vm: ContactActivityViewModel
) {
    val activityUiState by vm.uiState.collectAsState()
    val detailUiState by vm.detailVm.uiState.collectAsState()
    val listUiState by vm.listVm.uiState.collectAsState()
    val topAppBarContent = vm.getTopAppBarContentForState(
        activityState = activityUiState,
        detailState = detailUiState,
        listState = listUiState
    )
    val bottomAppBarContent = vm.getBottomAppBarContentForState(activityUiState)

    Scaffold(
        topBar = { TopAppBar(content = topAppBarContent) },
        bottomBar = {
            BottomAppBar(
                cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50)),
                content = bottomAppBarContent
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            when (activityUiState) {
                ViewContactDetails -> {
                    detailUiState.let { state ->
                        if (state is ContactSelected) {
                            if (state.isInEditMode)
                                ContactActivityFab.Save(onSaveClick = vm.detailVm::onSaveClick)
                            else
                                ContactActivityFab.Edit(onEditClick = vm.detailVm::onEditClick)
                        }
                    }
                }
                ViewContactList -> {
                    ContactActivityFab.Add(onAddClick = { TODO("onAddClick") })
                }
            }
        },
        isFloatingActionButtonDocked = true
    ) { paddingVals ->
        val fixedPadding = PaddingValues(
            start = paddingVals.calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
            top = paddingVals.calculateTopPadding() + 4.dp,
            end = paddingVals.calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
            bottom = paddingVals.calculateBottomPadding() + 4.dp
        )

        when (activityUiState) {
            ViewContactDetails -> {
                when (@Suppress("UnnecessaryVariable") val detailState = detailUiState) {
                    is ContactSelected -> ContactDetailContent.Compact(
                        modifier = Modifier.padding(fixedPadding),
                        nameVm = detailState.nameVm,
                        titleVm = detailState.titleVm,
                        contactDetailsChangedHandler = vm.detailVm,
                        isEditMode = detailState.isInEditMode
                    )
                    NoContactSelected -> ContactDetailContent.Empty()
                }
            }

            ViewContactList -> ContactListContent(
                modifier = Modifier.padding(fixedPadding),
                contacts = listUiState.contacts,
                onContactClick = vm.listVm::onContactSelected,
                onContactDeleteClick = { TODO("onContactDeleteClick") },
                onContactEditClick = { TODO("onContactEditClick") }
            )
        }
    }
}

private fun ContactActivityViewModel.getTopAppBarContentForState(
    activityState: ContactActivityState,
    detailState: ContactDetailUiState,
    listState: ContactListUiState
): @Composable RowScope.() -> Unit = {
    when (activityState) {
        ViewContactDetails -> {
            IconButton(onClick = detailVm::onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = content_desc_back)
                )
            }
            when (detailState) {
                NoContactSelected -> Text("", modifier = Modifier.weight(1f))
                is ContactSelected -> Text(
                    detailState.nameVm.fieldValue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ViewContactList -> {
            when (listState) {
                is Search -> {
                    IconButton(onClick = listVm::exitSearchMode) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = content_desc_back)
                        )
                    }
                    ToggleableEditTextField(
                        modifier = Modifier.weight(1f),
                        fieldValue = listState.searchTerm,
                        isEditEnabled = true,
                        onValueChange = listVm::onSearchTermUpdate
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
        }
    }

    ContactsActivityMenuButton(
        onSyncClick = this@getTopAppBarContentForState::sync,
        onSwitchUserClick = this@getTopAppBarContentForState::switchUser,
        onLogoutClick = this@getTopAppBarContentForState::logout,
        onInspectDbClick = this@getTopAppBarContentForState::inspectDb
    )
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

private fun ContactActivityViewModel.getBottomAppBarContentForState(
    uiState: ContactActivityState
): @Composable RowScope.() -> Unit = {
    Spacer(modifier = Modifier.weight(1f))
    when (uiState) {
        ViewContactDetails -> {
            IconButton(onClick = detailVm::onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(id = cta_delete))
            }
        }
        ViewContactList -> {
            IconButton(onClick = listVm::enterSearchMode) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(id = content_desc_search)
                )
            }
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
