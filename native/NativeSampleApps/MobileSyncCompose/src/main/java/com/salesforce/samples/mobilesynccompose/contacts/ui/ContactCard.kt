package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.core.ui.components.ExpandoButton
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
/* TODO How to put in profile pic?  Glide lib? */
fun ContactCard(
    modifier: Modifier = Modifier,
    startExpanded: Boolean,
    isSynced: Boolean,
    name: String,
    title: String,
) {
    var isExpanded by remember { mutableStateOf(startExpanded) }

    Card(modifier = modifier.animateContentSize()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Image(
                    painter = painterResource(
                        id = if (isSynced)
                            R.drawable.sync_save
                        else
                            R.drawable.sync_local
                    ),
                    contentDescription = stringResource(
                        id = if (isSynced)
                            R.string.content_desc_item_synced
                        else
                            R.string.content_desc_item_saved_locally
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                )
                ExpandoButton(
                    startsExpanded = startExpanded,
                    isEnabled = true,
                ) { isExpanded = it }
            }
            if (isExpanded) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Row {
                    Text(title, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PreviewContactListItem() {
    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactCard(
                modifier = Modifier.padding(8.dp),
                startExpanded = true,
                isSynced = false,
                name = "FirstFirstFirstFirstFirstFirst Middleee Last Last Last Last Last Last Last Last",
                title = "Title"
            )
        }
    }
}
