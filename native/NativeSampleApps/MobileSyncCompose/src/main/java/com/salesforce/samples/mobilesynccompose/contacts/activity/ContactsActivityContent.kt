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

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsUiEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsUiState
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsViewModel
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ui.ContactDetailsContent
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ui.ContactDetailsContentSinglePane
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ui.ContactDetailsTopBarContentExpanded
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ui.toPreviewViewingContactDetails
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListDataOpHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListItemClickHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListViewModel
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ui.ContactsListContent
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ui.ContactsListSinglePaneComponent
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.state.*
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The main entry point for all Contacts Activity UI.
 */
@Composable
fun ContactsActivityContent(
    activityVm: ContactsActivityViewModel,
    menuHandler: ContactsActivityMenuHandler,
    windowSizeClasses: WindowSizeClasses
) {
    val detailsUiState by activityVm.detailsVm.uiState.collectAsState()
    val listUiState by activityVm.listVm.uiState.collectAsState()
    val activityUiState by activityVm.uiState.collectAsState()

    when (windowSizeClasses.toContactsActivityContentLayout()) {
        ContactsActivityContentLayout.SinglePane -> SinglePane(
            detailsUiState = detailsUiState,
            detailsUiEventHandler = activityVm.detailsVm,
            listUiState = listUiState,
            listItemClickHandler = activityVm.listVm,
            listDataOpHandler = activityVm.listVm,
            onSearchTermUpdated = activityVm.listVm::onSearchTermUpdated,
            menuHandler = menuHandler
        )
        ContactsActivityContentLayout.ListDetail -> ListDetail(
            detailsUiState = detailsUiState,
            detailsUiEventHandler = activityVm.detailsVm,
            listUiState = listUiState,
            listItemClickHandler = activityVm.listVm,
            listDataOpHandler = activityVm.listVm,
            onSearchTermUpdated = activityVm.listVm::onSearchTermUpdated,
            menuHandler = menuHandler,
            windowSizeClasses = windowSizeClasses
        )
    }

    activityUiState.dialogUiState?.RenderDialog(modifier = Modifier)
}

@Composable
private fun SinglePane(
    detailsUiState: ContactDetailsUiState,
    detailsUiEventHandler: ContactDetailsUiEventHandler,
    listUiState: ContactsListUiState,
    listItemClickHandler: ContactsListItemClickHandler,
    listDataOpHandler: ContactsListDataOpHandler,
    onSearchTermUpdated: (newSearchTerm: String) -> Unit,
    menuHandler: ContactsActivityMenuHandler,
) {
    // In single pane mode, if the user is viewing contact details then only show the details component;
    // else show the list component
    when (detailsUiState) {
        is ContactDetailsUiState.ViewingContactDetails -> ContactDetailsContentSinglePane(
            details = detailsUiState,
            componentUiEventHandler = detailsUiEventHandler,
            menuHandler = menuHandler
        )
        else -> ContactsListSinglePaneComponent(
            uiState = listUiState,
            listItemClickHandler = listItemClickHandler,
            dataOpHandler = listDataOpHandler,
            onSearchTermUpdated = onSearchTermUpdated,
            menuHandler = menuHandler
        )
    }
}

@Composable
private fun ListDetail(
    detailsUiState: ContactDetailsUiState,
    detailsUiEventHandler: ContactDetailsUiEventHandler,
    listUiState: ContactsListUiState,
    listItemClickHandler: ContactsListItemClickHandler,
    listDataOpHandler: ContactsListDataOpHandler,
    onSearchTermUpdated: (newSearchTerm: String) -> Unit,
    menuHandler: ContactsActivityMenuHandler,
    windowSizeClasses: WindowSizeClasses
) {
    Scaffold(
        topBar = {
            TopAppBar {
                when (detailsUiState) {
                    is ContactDetailsUiState.NoContactSelected -> Text(stringResource(id = label_contacts))
                    is ContactDetailsUiState.ViewingContactDetails -> Text(detailsUiState.fullName)
                }

                Spacer(modifier = Modifier.weight(1f))

                ContactDetailsTopBarContentExpanded(
                    detailsUiState = detailsUiState,
                    eventHandler = detailsUiEventHandler
                )
            }
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                ContactsActivityMenuButton(menuHandler = menuHandler)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = detailsUiEventHandler::createClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(id = content_desc_add_contact)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        isFloatingActionButtonDocked = false
    ) {
        val evenSplit = windowSizeClasses.horiz != WindowSizeClass.Expanded

        Row(modifier = Modifier.fillMaxSize()) {
            val listModifier: Modifier
            val detailModifier: Modifier

            if (evenSplit) {
                listModifier = Modifier.weight(0.5f)
                detailModifier = Modifier.weight(0.5f)
            } else {
                listModifier = Modifier.width((WINDOW_SIZE_COMPACT_CUTOFF_DP / 2).dp)
                detailModifier = Modifier.weight(1f)
            }

            Column(modifier = listModifier) {
                ContactsListContent(
                    modifier = Modifier.fillMaxSize(),
                    uiState = listUiState,
                    listItemClickHandler = listItemClickHandler,
                    dataOpHandler = listDataOpHandler,
                    onSearchTermUpdated = onSearchTermUpdated
                )
            }

            Column(modifier = detailModifier) {
                ListDetailContactDetailsContent(
                    detailsUiState = detailsUiState,
                    onExitClick = detailsUiEventHandler::exitEditClick
                )
            }
        }
    }
}

@Composable
private fun ListDetailContactDetailsContent(
    detailsUiState: ContactDetailsUiState,
    onExitClick: () -> Unit
) {
    val isEditing = detailsUiState is ContactDetailsUiState.ViewingContactDetails
            && detailsUiState.isEditingEnabled

    val topPadding by animateIntAsState(targetValue = if (isEditing) 64 else 0)

    Box(modifier = Modifier.fillMaxSize()) {
        ContactDetailsContent(
            details = detailsUiState,
            modifier = Modifier
                .animateContentSize()
                .fillMaxSize()
                .padding(top = topPadding.dp)
        )

        AnimatedVisibility(
            visible = isEditing,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd)
        ) {
            // This is similar to FloatingActionButton but constrained to the size of an icon button:
            Surface(
                shape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50)),
                elevation = 4.dp,
            ) {
                IconButton(onClick = onExitClick) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(id = content_desc_cancel_edit)
                    )
                }
            }
        }
    }
}

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
fun SyncImage(modifier: Modifier = Modifier, uiState: SObjectUiSyncState) {
    when (uiState) {
        SObjectUiSyncState.NotSaved -> Icon(
            Icons.Default.Star,
            contentDescription = stringResource(id = content_desc_not_saved)
        )

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

private enum class ContactsActivityContentLayout {
    SinglePane,
    ListDetail
}

private fun WindowSizeClasses.toContactsActivityContentLayout() = when (horiz) {
    WindowSizeClass.Compact -> ContactsActivityContentLayout.SinglePane
    WindowSizeClass.Medium,
    WindowSizeClass.Expanded -> ContactsActivityContentLayout.ListDetail
}

@Preview(showBackground = true)
@Composable
private fun SinglePaneListPreview() {
    // TODO these previews have a lot of duplicated code and could probably be simplified a lot
    val contacts = (1..100).map { it.toString() }.map {
        SObjectRecord(
            id = it,
            localStatus = LocalStatus.MatchesUpstream,
            sObject = ContactObject(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it",
                department = "Department $it"
            )
        )
    }

    val detailsVm = PreviewDetailsVm(
        uiState = ContactDetailsUiState.NoContactSelected(
            dataOperationIsActive = false,
            curDialogUiState = null
        )
    )

    val listVm = PreviewListVm(
        uiState = ContactsListUiState(
            contacts = contacts,
            curSelectedContactId = null,
            isDoingInitialLoad = false,
        )
    )

    val vm = PreviewActivityVm(
        activityState = ContactsActivityUiState(
            isSyncing = false,
            dialogUiState = null
        ),
        detailsState = detailsVm.uiStateValue,
        listState = listVm.uiStateValue
    )

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsActivityContent(
                activityVm = vm,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                windowSizeClasses = WindowSizeClasses(
                    horiz = WindowSizeClass.Compact,
                    vert = WindowSizeClass.Expanded
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SinglePaneDetailsPreview() {
    val contacts = (1..100).map { it.toString() }.map {
        SObjectRecord(
            id = it,
            localStatus = LocalStatus.MatchesUpstream,
            sObject = ContactObject(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it",
                department = "Department $it"
            )
        )
    }

    val selectedContact = contacts[3]

    val detailsVm = PreviewDetailsVm(
        uiState = selectedContact.sObject.toPreviewViewingContactDetails()
    )

    val listVm = PreviewListVm(
        uiState = ContactsListUiState(
            contacts = contacts,
            curSelectedContactId = selectedContact.id,
            isDoingInitialLoad = false
        )
    )

    val vm = PreviewActivityVm(
        activityState = ContactsActivityUiState(
            isSyncing = false,
            dialogUiState = null
        ),
        detailsState = detailsVm.uiStateValue,
        listState = listVm.uiStateValue
    )

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsActivityContent(
                activityVm = vm,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                windowSizeClasses = WindowSizeClasses(
                    horiz = WindowSizeClass.Compact,
                    vert = WindowSizeClass.Expanded
                )
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = WINDOW_SIZE_COMPACT_CUTOFF_DP,
    heightDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP
)
@Composable
private fun ListDetailMediumPreview() {
    val contacts = (1..100).map { it.toString() }.map {
        SObjectRecord(
            id = it,
            localStatus = LocalStatus.MatchesUpstream,
            sObject = ContactObject(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it",
                department = "Department $it"
            )
        )
    }

    val selectedContact = contacts[3]

    val detailsVm = PreviewDetailsVm(
        uiState = selectedContact.sObject.toPreviewViewingContactDetails()
    )

    val listVm = PreviewListVm(
        uiState = ContactsListUiState(
            contacts = contacts,
            curSelectedContactId = selectedContact.id,
            isDoingInitialLoad = false
        )
    )
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ListDetail(
                detailsUiState = detailsVm.uiStateValue,
                detailsUiEventHandler = detailsVm,
                listUiState = listVm.uiStateValue,
                listItemClickHandler = listVm,
                listDataOpHandler = listVm,
                onSearchTermUpdated = listVm::onSearchTermUpdated,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                WindowSizeClasses(horiz = WindowSizeClass.Medium, vert = WindowSizeClass.Expanded)
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = WINDOW_SIZE_COMPACT_CUTOFF_DP,
    heightDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP,
    uiMode = UI_MODE_NIGHT_YES
)
@Composable
private fun ListDetailEditingPreview() {
    val contacts = (1..100).map { it.toString() }.map {
        SObjectRecord(
            id = it,
            localStatus = LocalStatus.MatchesUpstream,
            sObject = ContactObject(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it",
                department = "Department $it"
            )
        )
    }

    val selectedContact = contacts[3]

    val detailsVm = PreviewDetailsVm(
        uiState = selectedContact.sObject.toPreviewViewingContactDetails(isEditingEnabled = true)
    )

    val listVm = PreviewListVm(
        uiState = ContactsListUiState(
            contacts = contacts,
            curSelectedContactId = selectedContact.id,
            isDoingInitialLoad = false
        )
    )
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ListDetail(
                detailsUiState = detailsVm.uiStateValue,
                detailsUiEventHandler = detailsVm,
                listUiState = listVm.uiStateValue,
                listItemClickHandler = listVm,
                listDataOpHandler = listVm,
                onSearchTermUpdated = listVm::onSearchTermUpdated,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                WindowSizeClasses(horiz = WindowSizeClass.Medium, vert = WindowSizeClass.Expanded)
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = WINDOW_SIZE_COMPACT_CUTOFF_DP,
    heightDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP
)
@Composable
private fun ListDetailNoContactPreview() {
    val curSearchTerm = "9"
    val contacts = (1..100).map { it.toString() }.map {
        SObjectRecord(
            id = it,
            localStatus = LocalStatus.MatchesUpstream,
            sObject = ContactObject(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it",
                department = "Department $it"
            )
        )
    }.filter { it.sObject.fullName.contains(curSearchTerm) }

    val detailsVm = PreviewDetailsVm(
        uiState = ContactDetailsUiState.NoContactSelected(
            dataOperationIsActive = false,
            curDialogUiState = null
        )
    )

    val listVm = PreviewListVm(
        uiState = ContactsListUiState(
            contacts = contacts,
            curSelectedContactId = null,
            isDoingInitialLoad = false,
            curSearchTerm = curSearchTerm
        )
    )
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ListDetail(
                detailsUiState = detailsVm.uiStateValue,
                detailsUiEventHandler = detailsVm,
                listUiState = listVm.uiStateValue,
                listItemClickHandler = listVm,
                listDataOpHandler = listVm,
                onSearchTermUpdated = listVm::onSearchTermUpdated,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                WindowSizeClasses(horiz = WindowSizeClass.Medium, vert = WindowSizeClass.Expanded)
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP,
    heightDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP
)
@Preview(
    showBackground = true,
    widthDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP,
    heightDp = WINDOW_SIZE_MEDIUM_CUTOFF_DP,
    uiMode = UI_MODE_NIGHT_YES
)
@Composable
private fun ListDetailExpandedPreview() {
    val contacts = (1..100).map { it.toString() }.map {
        SObjectRecord(
            id = it,
            localStatus = LocalStatus.MatchesUpstream,
            sObject = ContactObject(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it",
                department = "Department $it"
            )
        )
    }

    val selectedContact = contacts[3]

    val detailsVm = PreviewDetailsVm(
        uiState = selectedContact.sObject.toPreviewViewingContactDetails()
    )

    val listVm = PreviewListVm(
        uiState = ContactsListUiState(
            contacts = contacts,
            curSelectedContactId = selectedContact.id,
            isDoingInitialLoad = false
        )
    )
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ListDetail(
                detailsUiState = detailsVm.uiStateValue,
                detailsUiEventHandler = detailsVm,
                listUiState = listVm.uiStateValue,
                listItemClickHandler = listVm,
                listDataOpHandler = listVm,
                onSearchTermUpdated = listVm::onSearchTermUpdated,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                WindowSizeClasses(horiz = WindowSizeClass.Expanded, vert = WindowSizeClass.Expanded)
            )
        }
    }
}

class PreviewDetailsVm(uiState: ContactDetailsUiState) : ContactDetailsViewModel {
    override val uiState: StateFlow<ContactDetailsUiState> = MutableStateFlow(uiState)
    override suspend fun setContactOrThrow(recordId: String?, isEditing: Boolean) {}
    override suspend fun discardChangesAndSetContactOrThrow(
        recordId: String?,
        isEditing: Boolean
    ) {
    }

    val uiStateValue get() = this.uiState.value

    override fun onFirstNameChange(newFirstName: String) {}
    override fun onLastNameChange(newLastName: String) {}
    override fun onTitleChange(newTitle: String) {}
    override fun onDepartmentChange(newDepartment: String) {}
    override fun createClick() {}
    override fun deleteClick() {}
    override fun undeleteClick() {}
    override fun deselectContact() {}
    override fun editClick() {}
    override fun exitEditClick() {}
    override fun saveClick() {}
}

class PreviewListVm(uiState: ContactsListUiState) : ContactsListViewModel {
    override val uiState: StateFlow<ContactsListUiState> = MutableStateFlow(uiState)
    val uiStateValue get() = this.uiState.value

    override fun contactClick(contactId: String) {}
    override fun createClick() {}
    override fun deleteClick(contactId: String) {}
    override fun editClick(contactId: String) {}
    override fun undeleteClick(contactId: String) {}
    override fun onSearchTermUpdated(newSearchTerm: String) {}

    override fun setSelectedContact(id: String?) {}
    override fun setSearchTerm(newSearchTerm: String) {}
}

class PreviewActivityVm(
    activityState: ContactsActivityUiState,
    detailsState: ContactDetailsUiState,
    listState: ContactsListUiState
) : ContactsActivityViewModel {
    override val uiState: StateFlow<ContactsActivityUiState> = MutableStateFlow(activityState)
    val uiStateValue get() = uiState.value
    override val detailsVm: ContactDetailsViewModel = PreviewDetailsVm(detailsState)
    override val listVm: ContactsListViewModel = PreviewListVm(listState)

    override fun sync(syncDownOnly: Boolean) {}
}

val PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER = object : ContactsActivityMenuHandler {
    override fun onInspectDbClick() {}
    override fun onLogoutClick() {}
    override fun onSwitchUserClick() {}
    override fun onSyncClick() {}
}
