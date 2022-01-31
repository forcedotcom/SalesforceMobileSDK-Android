package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

data class TempContactObject(val id: Int, val name: String, val title: String)

@Composable
fun ContactListContent(
    modifier: Modifier = Modifier,
    contacts: List<TempContactObject>,
    onContactClick: (TempContactObject) -> Unit,
    onContactDeleteClick: (TempContactObject) -> Unit,
    onContactEditClick: (TempContactObject) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(items = contacts, key = { it.id }) { contact ->
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
        TempContactObject(id = it, name = "Contact Name $it", title = "Title $it")
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
