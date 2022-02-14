package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactListContent
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.ui.fixForMainContent
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

@Composable
fun SinglePaneContactsList(
    uiState: ContactsListUiState,
    listEventHandler: ContactsListEventHandler,
    activityEventHandler: ContactsActivityEventHandler
) {
    Scaffold(
        topBar = {
            TopAppBarContent(
                uiState = uiState,
                listEventHandler = listEventHandler,
                activityEventHandler = activityEventHandler
            )
        },
        bottomBar = { BottomAppBarContent(listEventHandler = listEventHandler) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { FabContent(activityEventHandler = activityEventHandler) },
        isFloatingActionButtonDocked = true,
    ) { paddingVals ->
        ContactListContent(
            modifier = Modifier.padding(paddingValues = paddingVals.fixForMainContent()),
            listUiState = uiState,
            onContactClick = { activityEventHandler.handleEvent(ContactView(it)) },
            onContactDeleteClick = { activityEventHandler.handleEvent(ContactDelete(it)) },
            onContactEditClick = { activityEventHandler.handleEvent(ContactEdit(it)) }
        )
    }
}

@Composable
private fun TopAppBarContent(
    uiState: ContactsListUiState,
    listEventHandler: ContactsListEventHandler,
    activityEventHandler: ContactsActivityEventHandler
) {
    TopAppBar {
        var handleBack = false
        when (uiState) {
            is ContactsListUiState.Searching -> {
                IconButton(onClick = { listEventHandler.handleEvent(ListNavUp) }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.content_desc_back)
                    )
                }
                ToggleableEditTextField(
                    modifier = Modifier.weight(1f),
                    fieldValue = uiState.searchTerm,
                    isError = false, // cannot be in error state
                    isEditEnabled = true,
                    onValueChange = { listEventHandler.handleEvent(SearchTermUpdated(newSearchTerm = it)) }
                )
                handleBack = true
            }

            ContactsListUiState.Loading,
            is ContactsListUiState.ViewingList -> {
                Text(
                    stringResource(id = R.string.label_contacts),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ContactsActivityMenuButton(handler = activityEventHandler)

        BackHandler(enabled = handleBack) { listEventHandler.handleEvent(ListNavBack) }
    }
}

@Composable
private fun BottomAppBarContent(listEventHandler: ContactsListEventHandler) {
    BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))) {
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { listEventHandler.handleEvent(SearchClick) }) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.content_desc_search)
            )
        }
    }
}

@Composable
private fun FabContent(activityEventHandler: ContactsActivityEventHandler) {
    FloatingActionButton(onClick = { activityEventHandler.handleEvent(ContactCreate) }) {
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(id = R.string.content_desc_add_contact)
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SinglePaneContactsListPreview() {
    val uiState = ContactsListUiState.ViewingList(
        contacts = (1..100).map {
            Contact.createNewLocal(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it"
            )
        }
    )
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactsList(uiState = uiState, listEventHandler = {}, activityEventHandler = {})
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SinglePaneContactsListSearchPreview() {
    val uiState = ContactsListUiState.Searching(
        contacts = (2..100).step(2).map {
            Contact.createNewLocal(
                firstName = "First $it",
                lastName = "Last $it",
                title = "Title $it"
            )
        },
        searchTerm = "evens only"
    )
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactsList(uiState = uiState, listEventHandler = {}, activityEventHandler = {})
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SinglePaneContactsListLoadingPreview() {
    val uiState = ContactsListUiState.Loading
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactsList(uiState = uiState, listEventHandler = {}, activityEventHandler = {})
    }
}
