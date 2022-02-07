package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
fun ContactListContent(
    modifier: Modifier = Modifier,
    contacts: List<ContactObject>,
    onContactClick: (ContactObject) -> Unit,
    onContactDeleteClick: (ContactObject) -> Unit,
    onContactEditClick: (ContactObject) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(items = contacts, key = { it.objectId }) { contact ->
            ContactCard(
                modifier = Modifier.padding(4.dp),
                startExpanded = false,
                isSynced = true,
                contact = contact,
                onCardClick = onContactClick,
                onDeleteClick = onContactDeleteClick,
                onEditClick = onContactEditClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListContentPreview() {
    val contacts = (0..100).map {
        ContactObject(
            id = it.toString(),
            firstName = "Contact",
            lastName = "$it",
            title = "Title $it"
        )
    }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactListContent(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
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
