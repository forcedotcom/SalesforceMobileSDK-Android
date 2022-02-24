package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    onCardClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onUndeleteClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    startExpanded: Boolean = false,
    elevation: Dp = 2.dp,
) {
    var showDropDownMenu by rememberSaveable { mutableStateOf(false) }
    val alpha = if (contact.locallyDeleted) ALPHA_DISABLED else 1f

    CompositionLocalProvider(LocalContentAlpha provides alpha) {
        Card(
            modifier = Modifier
                .animateContentSize()
                .then(modifier)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onCardClick(contact.id) },
                        onLongPress = { showDropDownMenu = true }
                    )
                },
            elevation = elevation
        ) {
            ContactCardInnerContent(
                contact = contact,
                startExpanded = startExpanded
            )
            ContactDropdownMenu(
                showDropDownMenu = showDropDownMenu,
                contact = contact,
                onDismissMenu = { showDropDownMenu = false },
                onDeleteClick = onDeleteClick,
                onUndeleteClick = onUndeleteClick,
                onEditClick = onEditClick
            )
        }
    }
}

@Composable
private fun ContactCardInnerContent(
    modifier: Modifier = Modifier,
    contact: Contact,
    startExpanded: Boolean,
) {
    var isExpanded by rememberSaveable { mutableStateOf(startExpanded) }
    Column(modifier = modifier.padding(8.dp)) {
        Row {
            // TODO There is a bug where long-pressing on the name will both select the text and open the menu.
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
                onExpandoChangeListener = { isExpanded = it }
            )
        }
        if (isExpanded) {
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Row {
                SelectionContainer {
                    Text(contact.title, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

@Composable
private fun ContactDropdownMenu(
    showDropDownMenu: Boolean,
    contact: Contact,
    onDismissMenu: () -> Unit,
    onDeleteClick: (String) -> Unit,
    onUndeleteClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
) {
    DropdownMenu(expanded = showDropDownMenu, onDismissRequest = onDismissMenu) {
        if (contact.locallyDeleted) {
            DropdownMenuItem(onClick = { onDismissMenu(); onUndeleteClick(contact.id) }) {
                Text(stringResource(id = R.string.cta_undelete))
            }
        } else {
            DropdownMenuItem(onClick = { onDismissMenu(); onDeleteClick(contact.id) }) {
                Text(stringResource(id = R.string.cta_delete))
            }
        }
        DropdownMenuItem(onClick = { onDismissMenu(); onEditClick(contact.id) }) {
            Text(stringResource(id = R.string.cta_edit))
        }
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
