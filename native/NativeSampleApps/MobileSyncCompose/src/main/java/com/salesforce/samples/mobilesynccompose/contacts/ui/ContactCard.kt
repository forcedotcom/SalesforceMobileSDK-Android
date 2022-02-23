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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.salesforce.samples.mobilesynccompose.core.ui.theme.ALPHA_DISABLED
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

@Composable
/* TODO How to put in profile pic?  Glide lib? */
fun ContactCard(
    modifier: Modifier = Modifier,
    contact: Contact,
    onCardClick: (Contact) -> Unit,
    onDeleteClick: (Contact) -> Unit,
    onUndeleteClick: (Contact) -> Unit,
    onEditClick: (Contact) -> Unit,
    startExpanded: Boolean = false,
    elevation: Dp = 2.dp,
) {
    var showDropDownMenu by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(startExpanded) }
    val alpha = if (contact.locallyDeleted) ALPHA_DISABLED else 1f

    CompositionLocalProvider(LocalContentAlpha provides alpha) {
        Card(
            modifier = Modifier
                .animateContentSize()
                .then(modifier)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onCardClick(contact) },
                        onLongPress = {
                            android.util.Log.d("ContactListContent", "Long Click: $contact")
                            showDropDownMenu = true
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
                    SyncImage(contact = contact)
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
                    if (contact.locallyDeleted) {
                        DropdownMenuItem(
                            onClick = {
                                showDropDownMenu = false
                                onUndeleteClick(contact)
                            }
                        ) {
                            Text(stringResource(id = R.string.cta_undelete))
                        }
                    } else {
                        DropdownMenuItem(
                            onClick = {
                                showDropDownMenu = false
                                onDeleteClick(contact)
                            }
                        ) {
                            Text(stringResource(id = R.string.cta_delete))
                        }
                    }
                    DropdownMenuItem(
                        onClick = {
                            showDropDownMenu = false
                            onEditClick(contact)
                        }
                    ) {
                        Text(stringResource(id = R.string.cta_edit))
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncImage(contact: Contact) {
    if (contact.locallyDeleted) {
        Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(id = R.string.content_desc_item_deleted_locally),
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
        )
    } else {
        Image(
            painter = painterResource(
                id = if (contact.local)
                    R.drawable.sync_local
                else
                    R.drawable.sync_save
            ),
            contentDescription = stringResource(
                id = if (contact.local)
                    R.string.content_desc_item_saved_locally
                else
                    R.string.content_desc_item_synced
            ),
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PreviewContactListItem() {
    SalesforceMobileSDKAndroidTheme {
        Surface {
            Column {
                ContactCard(
                    modifier = Modifier.padding(8.dp),
                    startExpanded = true,
                    contact = mockSyncedContact(),
                    onCardClick = {},
                    onDeleteClick = {},
                    onUndeleteClick = {},
                    onEditClick = {},
                )
                ContactCard(
                    modifier = Modifier.padding(8.dp),
                    startExpanded = true,
                    contact = Contact.createNewLocal(
                        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
                        lastName = "Last Last Last Last Last Last Last Last Last Last Last",
                        title = "Title",
                    ),
                    onCardClick = {},
                    onDeleteClick = {},
                    onUndeleteClick = {},
                    onEditClick = {},
                )
                ContactCard(
                    modifier = Modifier.padding(8.dp),
                    contact = mockLocallyDeletedContact(),
                    onCardClick = {},
                    onDeleteClick = {},
                    onUndeleteClick = {},
                    onEditClick = {},
                    startExpanded = true
                )
            }
        }
    }
}
