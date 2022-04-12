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
package com.salesforce.samples.mobilesynccompose.contacts.listcomponent

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.ContactCard
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuHandler
import com.salesforce.samples.mobilesynccompose.contacts.activity.PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.toUiSyncState
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.components.OutlinedTextFieldWithHelp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
fun ContactsListSinglePaneComponent(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    uiState: ContactsListUiState,
    uiEventHandler: ContactsListUiEventHandler,
    menuHandler: ContactsActivityMenuHandler
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar {
                when (uiState) {
                    is ContactsListUiState.Searching -> ContactsListTopAppBarSearchModeSinglePane(
                        searchTerm = uiState.curSearchTerm,
                        listExitSearchClick = uiEventHandler::exitSearchClick,
                        onSearchTermUpdated = uiEventHandler::onSearchTermUpdated
                    )
                    is ContactsListUiState.ViewingList -> ContactsListTopAppBarSinglePane()
                }

                ContactsActivityMenuButton(menuHandler = menuHandler)
            }
        },
        bottomBar = {
            BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(all = CornerSize(percent = 50))) {
                when (uiState) {
                    is ContactsListUiState.Searching -> ContactsListBottomAppBarSearchSinglePane()
                    is ContactsListUiState.ViewingList -> ContactsListBottomAppBarSinglePane(
                        listSearchClick = uiEventHandler::searchClick
                    )
                }
            }
        },
        floatingActionButton = { ContactsListFabSinglePane(listCreateClick = uiEventHandler::createClick) },
        floatingActionButtonPosition = FabPosition.Center,
        isFloatingActionButtonDocked = true,
    ) {
        ContactsListContent(
            modifier = Modifier
                .padding(it)
                .then(contentModifier),
            contactRecords = uiState.contacts,
            showLoadingOverlay = uiState.showLoadingOverlay,
            listContactClick = uiEventHandler::contactClick,
            listDeleteClick = uiEventHandler::deleteClick,
            listEditClick = uiEventHandler::editClick,
            listUndeleteClick = uiEventHandler::undeleteClick
        )
    }
}

@Composable
fun ContactsListContent(
    modifier: Modifier = Modifier,
    contactRecords: List<SObjectRecord<ContactObject>>,
    showLoadingOverlay: Boolean,
    listContactClick: (id: String) -> Unit,
    listDeleteClick: (id: String) -> Unit,
    listEditClick: (id: String) -> Unit,
    listUndeleteClick: (id: String) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(items = contactRecords, key = { it.id }) { record ->
            ContactCard(
                modifier = Modifier.padding(4.dp),
                startExpanded = false,
                model = record.sObject,
                syncState = record.localStatus.toUiSyncState(),
                onCardClick = { listContactClick(record.id) },
                onDeleteClick = { listDeleteClick(record.id) },
                onUndeleteClick = { listUndeleteClick(record.id) },
                onEditClick = { listEditClick(record.id) },
            )
        }
    }

    if (showLoadingOverlay) {
        LoadingOverlay()
    }
}

@Composable
fun RowScope.ContactsListTopAppBarSearchModeSinglePane(
    searchTerm: String,
    listExitSearchClick: () -> Unit,
    onSearchTermUpdated: (newSearchTerm: String) -> Unit
) {
    IconButton(onClick = listExitSearchClick) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = stringResource(id = content_desc_back)
        )
    }
    OutlinedTextFieldWithHelp(
        modifier = Modifier.weight(1f),
        fieldValue = searchTerm,
        isError = false, // cannot be in error state
        isEditEnabled = true,
        onValueChange = onSearchTermUpdated
    )
}

@Composable
fun RowScope.ContactsListTopAppBarSinglePane() {
    Text(
        stringResource(id = label_contacts),
        modifier = Modifier.weight(1f)
    )
}

@Composable
fun ContactsListBottomAppBarSearchSinglePane() {
    // no content
}

@Composable
fun RowScope.ContactsListBottomAppBarSinglePane(listSearchClick: () -> Unit) {
    Spacer(modifier = Modifier.weight(1f))
    IconButton(onClick = listSearchClick) {
        Icon(
            Icons.Default.Search,
            contentDescription = stringResource(id = content_desc_search)
        )
    }
}

@Composable
fun ContactsListFabSinglePane(listCreateClick: () -> Unit) {
    FloatingActionButton(onClick = listCreateClick) {
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(id = content_desc_add_contact)
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactsListSinglePaneComponentPreview() {
    val contacts = (0..100)
        .map { it.toString() }
        .map {
            SObjectRecord(
                id = it,
                localStatus = LocalStatus.LocallyCreated,
                sObject = ContactObject(
                    firstName = "Contact",
                    lastName = it,
                    title = "Title $it",
                    department = "Department $it"
                )
            )
        }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListSinglePaneComponent(
                modifier = Modifier.padding(4.dp),
                uiState = ContactsListUiState.ViewingList(
                    contacts = contacts,
                    curSelectedContactId = null,
                    showLoadingOverlay = false
                ),
                uiEventHandler = PREVIEW_LIST_UI_EVENT_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactListSyncingPreview() {
    val contacts = (0..100)
        .map { it.toString() }
        .map {
            SObjectRecord(
                id = it,
                localStatus = LocalStatus.LocallyCreated,
                sObject = ContactObject(
                    firstName = "Contact",
                    lastName = it,
                    title = "Title $it",
                    department = "Department $it"
                )
            )
        }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListSinglePaneComponent(
                modifier = Modifier.padding(4.dp),
                uiState = ContactsListUiState.ViewingList(
                    contacts = contacts,
                    curSelectedContactId = null,
                    showLoadingOverlay = true
                ),
                uiEventHandler = PREVIEW_LIST_UI_EVENT_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactListLoadingPreview() {
    val contacts = emptyList<SObjectRecord<ContactObject>>()

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListSinglePaneComponent(
                modifier = Modifier.padding(4.dp),
                uiState = ContactsListUiState.ViewingList(
                    contacts = emptyList(),
                    curSelectedContactId = null,
                    showLoadingOverlay = true
                ),
                uiEventHandler = PREVIEW_LIST_UI_EVENT_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
            )
        }
    }
}

private val PREVIEW_LIST_UI_EVENT_HANDLER = object : ContactsListUiEventHandler {
    override fun contactClick(contactId: String) {}
    override fun createClick() {}
    override fun deleteClick(contactId: String) {}
    override fun editClick(contactId: String) {}
    override fun undeleteClick(contactId: String) {}
    override fun searchClick() {}
    override fun exitSearchClick() {}
    override fun onSearchTermUpdated(newSearchTerm: String) {}
}
