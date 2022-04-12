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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.*
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListSinglePaneComponent
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListUiEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListViewModel
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.state.SObjectUiSyncState
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The main entry point for all Contacts Activity UI, similar in use-case to a traditional top-level
 * `contacts_activity_layout.xml`.
 */
@Composable
fun ContactsActivityContent(
    vm: ContactsActivityViewModel,
    detailsVm: ContactDetailsViewModel,
    listVm: ContactsListViewModel,
    menuHandler: ContactsActivityMenuHandler,
) {
    // this drives recomposition when the VM updates itself:
    val detailsUiState by detailsVm.uiState.collectAsState()
    val listUiState by listVm.uiState.collectAsState()
    val uiState by vm.uiState.collectAsState()

    SinglePane(
        activityUiState = uiState,
        detailsUiState = detailsUiState,
        detailsUiEventHandler = detailsVm,
        listUiState = listUiState,
        listUiEventHandler = listVm,
        menuHandler = menuHandler
    )

    uiState.dialogUiState?.RenderDialog(modifier = Modifier)
}

@Composable
private fun SinglePane(
    activityUiState: ContactsActivityUiState,
    detailsUiState: ContactDetailsUiState,
    detailsUiEventHandler: ContactDetailsUiEventHandler,
    listUiState: ContactsListUiState,
    listUiEventHandler: ContactsListUiEventHandler,
    menuHandler: ContactsActivityMenuHandler,
) {
    when (detailsUiState) {
        is ContactDetailsUiState.ViewingContactDetails -> ContactDetailsSinglePaneComponent(
            details = detailsUiState,
            componentUiEventHandler = detailsUiEventHandler,
            menuHandler = menuHandler
        )
        else -> ContactsListSinglePaneComponent(
            uiState = listUiState,
            uiEventHandler = listUiEventHandler
        )
    }


    activityUiState.dialogUiState?.RenderDialog(modifier = Modifier)
}

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
fun SyncImage(modifier: Modifier = Modifier, uiState: SObjectUiSyncState) {
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SinglePaneListPreview() {
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
        uiState = ContactsListUiState.ViewingList(
            contacts = contacts,
            curSelectedContactId = null,
            showLoadingOverlay = false
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
                vm = vm,
                detailsVm = detailsVm,
                listVm = listVm,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
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
        uiState = ContactsListUiState.ViewingList(
            contacts = contacts,
            curSelectedContactId = selectedContact.id,
            showLoadingOverlay = false
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
                vm = vm,
                detailsVm = vm.detailsVm,
                listVm = vm.listVm,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
            )
        }
    }
}

private class PreviewDetailsVm(uiState: ContactDetailsUiState) : ContactDetailsViewModel {
    override val uiState: StateFlow<ContactDetailsUiState> = MutableStateFlow(uiState)
    val uiStateValue get() = this.uiState.value

    override suspend fun clearContactObj() {}
    override suspend fun setContact(recordId: String, startWithEditingEnabled: Boolean) {}
    override fun onFirstNameChange(newFirstName: String) {}
    override fun onLastNameChange(newLastName: String) {}
    override fun onTitleChange(newTitle: String) {}
    override fun onDepartmentChange(newDepartment: String) {}
    override fun createClick() {}
    override fun deleteClick() {}
    override fun undeleteClick() {}
    override fun editClick() {}
    override fun exitClick() {}
    override fun saveClick() {}
}

private class PreviewListVm(uiState: ContactsListUiState) : ContactsListViewModel {
    override val uiState: StateFlow<ContactsListUiState> = MutableStateFlow(uiState)
    val uiStateValue get() = this.uiState.value

    override fun contactClick(contactId: String) {}
    override fun createClick() {}
    override fun deleteClick(contactId: String) {}
    override fun editClick(contactId: String) {}
    override fun undeleteClick(contactId: String) {}
    override fun searchClick() {}
    override fun exitSearchClick() {}
    override fun onSearchTermUpdated(newSearchTerm: String) {}

}

private class PreviewActivityVm(
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
