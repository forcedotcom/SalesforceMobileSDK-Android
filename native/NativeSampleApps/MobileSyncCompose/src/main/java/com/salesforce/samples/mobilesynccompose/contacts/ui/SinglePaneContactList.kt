package com.salesforce.samples.mobilesynccompose.contacts.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListCoreEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsSearchEventHandler
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

object SinglePaneContactsList {
    @Composable
    fun ViewingContactsList(
        modifier: Modifier = Modifier,
        contacts: List<Contact>,
        isSyncing: Boolean,
        handler: ContactsListCoreEventHandler
    ) {
        LazyColumn(modifier = modifier) {
            items(items = contacts, key = { it.id }) { contact ->
                ContactCard(
                    modifier = Modifier.padding(4.dp),
                    startExpanded = false,
                    contact = contact,
                    onCardClick = handler::listContactClick,
                    onDeleteClick = handler::listDeleteClick,
                    onEditClick = handler::listEditClick,
                )
            }
        }
    }

    object ScaffoldContent {
        @Composable
        fun RowScope.TopAppBarSearchMode(
            searchTerm: String,
            handler: ContactsSearchEventHandler
        ) {
            IconButton(onClick = { handler.exitSearch() }) {
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
                onValueChange = { handler.searchTermUpdated(it) }
            )
        }

        @Composable
        fun RowScope.TopAppBar() {
            Text(
                stringResource(id = label_contacts),
                modifier = Modifier.weight(1f)
            )
        }

        @Composable
        fun RowScope.BottomAppBarSearch() {
            // no content
        }

        @Composable
        fun RowScope.BottomAppBar(handler: ContactsListEventHandler) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = handler::searchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(id = content_desc_search)
                )
            }
        }

        @Composable
        fun Fab(handler: ContactsListCoreEventHandler) {
            FloatingActionButton(onClick = handler::listCreateClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(id = content_desc_add_contact)
                )
            }
        }
    }
}

//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//@Composable
//private fun ContactListContentPreview() {
//    val contacts = (0..100).map {
//        Contact.createNewLocal(
//            firstName = "Contact",
//            lastName = "$it",
//            title = "Title $it",
//        )
//    }
//
//    SalesforceMobileSDKAndroidTheme {
//        Surface {
//            ContactListContent(
//                modifier = Modifier.padding(4.dp),
//                listUiState = ContactsListUiState.ViewingList(contacts = contacts),
////                contacts = contacts,
//                onContactClick = { Log.d("ContactListContentPreview", "Clicked: $it") },
//                onContactDeleteClick = {
//                    Log.d(
//                        "ContactListContentPreview",
//                        "Delete Clicked: $it"
//                    )
//                },
//                onContactEditClick = { Log.d("ContactListContentPreview", "Edit Clicked: $it") }
//            )
//        }
//    }
//}
//
//@Preview(showBackground = true)
//@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
//@Composable
//private fun ContactListLoadingPreview() {
//    SalesforceMobileSDKAndroidTheme {
//        Surface {
//            ContactListContent(
//                modifier = Modifier.padding(4.dp),
//                listUiState = ContactsListUiState.Loading,
////                contacts = contacts,
//                onContactClick = { Log.d("ContactListContentPreview", "Clicked: $it") },
//                onContactDeleteClick = {
//                    Log.d(
//                        "ContactListContentPreview",
//                        "Delete Clicked: $it"
//                    )
//                },
//                onContactEditClick = { Log.d("ContactListContentPreview", "Edit Clicked: $it") }
//            )
//        }
//    }
//}
