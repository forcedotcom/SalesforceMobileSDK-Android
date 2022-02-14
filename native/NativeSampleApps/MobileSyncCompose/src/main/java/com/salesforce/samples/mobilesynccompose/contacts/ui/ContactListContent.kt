package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

@Composable
fun ContactListContent(
    modifier: Modifier = Modifier,
    listUiState: ContactsListUiState,
    onContactClick: (Contact) -> Unit,
    onContactDeleteClick: (Contact) -> Unit,
    onContactEditClick: (Contact) -> Unit,
) {
    if (listUiState is ContactsListUiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", style = MaterialTheme.typography.h3) // TODO use string res
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(items = listUiState.contacts, key = { it.id }) { contact ->
                ContactCard(
                    modifier = Modifier.padding(4.dp),
                    startExpanded = false,
                    contact = contact,
                    onCardClick = onContactClick,
                    onDeleteClick = onContactDeleteClick,
                    onEditClick = onContactEditClick,
                )
            }
        }
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
            ContactListContent(
                modifier = Modifier.padding(4.dp),
                listUiState = ContactsListUiState.ViewingList(contacts = contacts),
//                contacts = contacts,
                onContactClick = { Log.d("ContactListContentPreview", "Clicked: $it") },
                onContactDeleteClick = {
                    Log.d(
                        "ContactListContentPreview",
                        "Delete Clicked: $it"
                    )
                },
                onContactEditClick = { Log.d("ContactListContentPreview", "Edit Clicked: $it") }
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListLoadingPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactListContent(
                modifier = Modifier.padding(4.dp),
                listUiState = ContactsListUiState.Loading,
//                contacts = contacts,
                onContactClick = { Log.d("ContactListContentPreview", "Clicked: $it") },
                onContactDeleteClick = {
                    Log.d(
                        "ContactListContentPreview",
                        "Delete Clicked: $it"
                    )
                },
                onContactEditClick = { Log.d("ContactListContentPreview", "Edit Clicked: $it") }
            )
        }
    }
}
