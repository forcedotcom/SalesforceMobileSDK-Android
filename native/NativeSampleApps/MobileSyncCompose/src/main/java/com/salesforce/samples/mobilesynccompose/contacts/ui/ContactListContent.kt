package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

data class TempContactObject(val id: Int, val name: String, val title: String)

@Composable
fun ContactListContent(modifier: Modifier = Modifier, contacts: List<TempContactObject>) {
    LazyColumn(modifier = modifier) {
        items(items = contacts, key = { it.id }) {
            ContactCard(
                modifier = Modifier.padding(4.dp),
                startExpanded = false,
                isSynced = true,
                name = it.name,
                title = it.title
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
            ContactListContent(modifier = Modifier.padding(4.dp), contacts = contacts)
        }
    }
}
