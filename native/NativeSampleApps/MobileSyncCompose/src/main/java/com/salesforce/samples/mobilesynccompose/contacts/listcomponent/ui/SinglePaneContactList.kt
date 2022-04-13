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
package com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuHandler
import com.salesforce.samples.mobilesynccompose.contacts.activity.PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListDataOpHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListItemClickHandler
import com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.LocalStatus
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactRecord

@Composable
fun ContactsListSinglePaneComponent(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    uiState: ContactsListUiState,
    listItemClickHandler: ContactsListItemClickHandler,
    dataOpHandler: ContactsListDataOpHandler,
    onSearchTermUpdated: (newSearchTerm: String) -> Unit,
    menuHandler: ContactsActivityMenuHandler
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar {
                ContactsListTopAppBarSinglePane()
                ContactsActivityMenuButton(menuHandler = menuHandler)
            }
        },
        bottomBar = {
            BottomAppBar(
                cutoutShape = MaterialTheme.shapes.small.copy(all = CornerSize(percent = 50))
            ) {}
        },
        floatingActionButton = { ContactsListFabSinglePane(listCreateClick = listItemClickHandler::createClick) },
        floatingActionButtonPosition = FabPosition.Center,
        isFloatingActionButtonDocked = true,
    ) {
        ContactsListContent(
            modifier = Modifier
                .padding(it)
                .then(contentModifier),
            uiState = uiState,
            listItemClickHandler = listItemClickHandler,
            dataOpHandler = dataOpHandler,
            onSearchTermUpdated = onSearchTermUpdated
        )
    }
}

@Composable
private fun RowScope.ContactsListTopAppBarSinglePane() {
    Text(
        stringResource(id = label_contacts),
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ContactsListFabSinglePane(listCreateClick: () -> Unit) {
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
                uiState = ContactsListUiState(
                    contacts = contacts,
                    curSelectedContactId = null,
                    isDoingInitialLoad = false,
                ),
                listItemClickHandler = PREVIEW_LIST_ITEM_CLICK_HANDLER,
                dataOpHandler = PREVIEW_LIST_DATA_OP_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                onSearchTermUpdated = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactListSyncingAndSearchingPreview() {
    val curSearchTerm = "9"
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
        .filter {
            it.sObject.fullName.contains(curSearchTerm)
        }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListSinglePaneComponent(
                modifier = Modifier.padding(4.dp),
                uiState = ContactsListUiState(
                    contacts = contacts,
                    curSelectedContactId = null,
                    isDoingInitialLoad = false,
                    curSearchTerm = curSearchTerm
                ),
                listItemClickHandler = PREVIEW_LIST_ITEM_CLICK_HANDLER,
                dataOpHandler = PREVIEW_LIST_DATA_OP_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                onSearchTermUpdated = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactListLoadingPreview() {
    val contacts = emptyList<ContactRecord>()

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListSinglePaneComponent(
                modifier = Modifier.padding(4.dp),
                uiState = ContactsListUiState(
                    contacts = contacts,
                    curSelectedContactId = null,
                    isDoingInitialLoad = true,
                ),
                listItemClickHandler = PREVIEW_LIST_ITEM_CLICK_HANDLER,
                dataOpHandler = PREVIEW_LIST_DATA_OP_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
                onSearchTermUpdated = {}
            )
        }
    }
}

private val PREVIEW_LIST_ITEM_CLICK_HANDLER = object : ContactsListItemClickHandler {
    override fun contactClick(contactId: String) {}
    override fun createClick() {}
    override fun editClick(contactId: String) {}
}

private val PREVIEW_LIST_DATA_OP_HANDLER = object : ContactsListDataOpHandler {
    override fun deleteClick(contactId: String) {}
    override fun undeleteClick(contactId: String) {}
}
