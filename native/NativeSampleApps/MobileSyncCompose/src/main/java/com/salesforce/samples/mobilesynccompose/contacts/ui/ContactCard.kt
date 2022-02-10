package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.core.ui.components.ExpandoButton
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

@Composable
/* TODO How to put in profile pic?  Glide lib? */
fun ContactCard(
    modifier: Modifier = Modifier,
    contact: Contact,
    isSynced: Boolean,
    onCardClick: (Contact) -> Unit,
    onDeleteClick: (Contact) -> Unit,
    onEditClick: (Contact) -> Unit,
    startExpanded: Boolean = false,
    elevation: Dp = 2.dp,
) {
    var showDropDownMenu by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(startExpanded) }

    Card(
        modifier = Modifier
            .animateContentSize()
            .then(modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onCardClick(contact) },
                    onLongPress = {
                        android.util.Log.d("ContactListContent", "Long Click: $contact")
                        showDropDownMenu = !showDropDownMenu
                    }
                )
            },
        elevation = elevation
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.fullName,
                        style = MaterialTheme.typography.body1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    startsExpanded = isExpanded,
                    isEnabled = true,
                ) { isExpanded = it }
            }
            if (isExpanded) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Row {
                    SelectionContainer {
                        Text(contact.title, style = MaterialTheme.typography.body2)
                    }
                }
            }
            DropdownMenu(
                expanded = showDropDownMenu,
                onDismissRequest = { showDropDownMenu = false }
            ) {
                DropdownMenuItem(onClick = { onDeleteClick(contact) }) {
                    Text(stringResource(id = R.string.cta_delete))
                }
                DropdownMenuItem(onClick = { onEditClick(contact) }) {
                    Text(stringResource(id = R.string.cta_edit))
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
                contact = Contact(
                    id = "1",
                    firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
                    lastName = "Last Last Last Last Last Last Last Last Last Last Last",
                    title = "Title",
                    locallyCreated = false,
                    locallyDeleted = false,
                    locallyUpdated = false
                ),
                onCardClick = {},
                onDeleteClick = {},
                onEditClick = {},
            )
        }
    }
}
