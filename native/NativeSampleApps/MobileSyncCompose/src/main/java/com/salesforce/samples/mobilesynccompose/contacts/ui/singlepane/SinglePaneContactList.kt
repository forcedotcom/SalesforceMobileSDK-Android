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
package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactCard
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.components.OutlinedTextFieldWithHelp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
fun ContactsListViewingModeSinglePane(
    modifier: Modifier = Modifier,
    contacts: List<ContactObject>,
    showLoadingOverlay: Boolean,
    listContactClick: (id: SObjectId) -> Unit,
    listDeleteClick: (id: SObjectId) -> Unit,
    listEditClick: (id: SObjectId) -> Unit,
    listUndeleteClick: (id: SObjectId) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(items = contacts, key = { it.id.localId ?: it.id.primaryKey }) { contact ->
            ContactCard(
                modifier = Modifier.padding(4.dp),
                startExpanded = false,
                contact = contact,
                onCardClick = { listContactClick(contact.id) },
                onDeleteClick = { listDeleteClick(contact.id) },
                onUndeleteClick = { listUndeleteClick(contact.id) },
                onEditClick = { listEditClick(contact.id) },
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
private fun ContactListContentPreview() {
    val contacts = (0..100).map {
        ContactObject.createNewLocal(
            firstName = "Contact",
            lastName = "$it",
            title = "Title $it",
        )
    }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListViewingModeSinglePane(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
                showLoadingOverlay = false,
                listContactClick = {},
                listDeleteClick = {},
                listEditClick = {},
                listUndeleteClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListSyncingPreview() {
    val contacts = (0..100).map {
        ContactObject.createNewLocal(
            firstName = "Contact",
            lastName = "$it",
            title = "Title $it",
        )
    }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListViewingModeSinglePane(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
                showLoadingOverlay = true,
                listContactClick = {},
                listDeleteClick = {},
                listEditClick = {},
                listUndeleteClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListLoadingPreview() {
    val contacts = emptyList<ContactObject>()

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactsListViewingModeSinglePane(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
                showLoadingOverlay = true,
                listContactClick = {},
                listDeleteClick = {},
                listEditClick = {},
                listUndeleteClick = {}
            )
        }
    }
}
