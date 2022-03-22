/*
 * Copyright (c) 2022-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.isLocallyDeleted
import com.salesforce.samples.mobilesynccompose.core.ui.components.ExpandoButton
import com.salesforce.samples.mobilesynccompose.core.ui.theme.ALPHA_DISABLED
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
/* TODO How to put in profile pic?  Glide lib? */
fun ContactCard(
    modifier: Modifier = Modifier,
    contact: ContactObject,
    onCardClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onUndeleteClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    startExpanded: Boolean = false,
    elevation: Dp = 2.dp,
) {
    var showDropDownMenu by rememberSaveable { mutableStateOf(false) }
    val alpha = if (contact.localStatus.isLocallyDeleted) ALPHA_DISABLED else 1f

    CompositionLocalProvider(LocalContentAlpha provides alpha) {
        Card(
            modifier = Modifier
                .animateContentSize()
                .then(modifier)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onCardClick(contact.serverId) },
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
    contact: ContactObject,
    startExpanded: Boolean,
) {
    var isExpanded by rememberSaveable { mutableStateOf(startExpanded) }
    Column(modifier = modifier.padding(8.dp)) {
        Row {
            // TODO There is a bug where long-pressing on the name will both select the text and open the menu.
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    contact.fullName ?: "",
                    style = MaterialTheme.typography.body1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SyncImage(contactObjLocalStatus = contact.localStatus)

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
                    Text(contact.title ?: "", style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

@Composable
private fun ContactDropdownMenu(
    showDropDownMenu: Boolean,
    contact: ContactObject,
    onDismissMenu: () -> Unit,
    onDeleteClick: (String) -> Unit,
    onUndeleteClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
) {
    DropdownMenu(expanded = showDropDownMenu, onDismissRequest = onDismissMenu) {
        if (contact.localStatus.isLocallyDeleted) {
            DropdownMenuItem(onClick = { onDismissMenu(); onUndeleteClick(contact.serverId) }) {
                Text(stringResource(id = R.string.cta_undelete))
            }
        } else {
            DropdownMenuItem(onClick = { onDismissMenu(); onDeleteClick(contact.serverId) }) {
                Text(stringResource(id = R.string.cta_delete))
            }
        }
        DropdownMenuItem(onClick = { onDismissMenu(); onEditClick(contact.serverId) }) {
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
                    contact = ContactObject.createNewLocal(
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
