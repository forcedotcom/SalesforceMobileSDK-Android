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
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.components.OutlinedTextFieldWithHelp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

@Composable
fun ContactsListViewingModeSinglePane(
    modifier: Modifier = Modifier,
    contacts: List<Contact>,
    showLoadingOverlay: Boolean,
    listContactClick: (contactId: String) -> Unit,
    listDeleteClick: (contactId: String) -> Unit,
    listEditClick: (contactId: String) -> Unit,
    listUndeleteClick: (contactId: String) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(items = contacts, key = { it.id }) { contact ->
            ContactCard(
                modifier = Modifier.padding(4.dp),
                startExpanded = false,
                contact = contact,
                onCardClick = listContactClick,
                onDeleteClick = listDeleteClick,
                onUndeleteClick = listUndeleteClick,
                onEditClick = listEditClick,
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
        Contact.createNewLocal(
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
        Contact.createNewLocal(
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
    val contacts = emptyList<Contact>()

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
