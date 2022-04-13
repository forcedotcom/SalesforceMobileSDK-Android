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
package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.salesforce.samples.mobilesynccompose.R.drawable.ic_undo
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.activity.ContactsActivityMenuHandler
import com.salesforce.samples.mobilesynccompose.contacts.activity.PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER
import com.salesforce.samples.mobilesynccompose.contacts.activity.SyncImage
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsField
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsUiEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.core.ui.state.SObjectUiSyncState
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject
import org.jetbrains.annotations.TestOnly

@Composable
fun ContactDetailsContentSinglePane(
    details: ContactDetailsUiState,
    componentUiEventHandler: ContactDetailsUiEventHandler,
    menuHandler: ContactsActivityMenuHandler,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier
) {
    val contactDetailsUi = details as? ContactDetailsUiState.ViewingContactDetails
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar {
                ContactDetailsTopAppBarSinglePane(
                    label = contactDetailsUi?.fullName ?: "",
                    syncIconContent = {
                        contactDetailsUi?.let {
                            SyncImage(uiState = contactDetailsUi.uiSyncState)
                        }
                    },
                    onUpClick =
                    if (contactDetailsUi?.isEditingEnabled == true)
                        componentUiEventHandler::exitEditClick
                    else
                        componentUiEventHandler::deselectContact
                )

                ContactsActivityMenuButton(menuHandler = menuHandler)
            }
        },

        bottomBar = {
            BottomAppBar(cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))) {
                if (contactDetailsUi != null) {
                    ContactDetailsBottomAppBarSinglePane(
                        showDelete = contactDetailsUi.uiSyncState != SObjectUiSyncState.Deleted,
                        detailsDeleteClick = componentUiEventHandler::deleteClick
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ContactDetailsFab(uiState = details, handler = componentUiEventHandler)
        },
        isFloatingActionButtonDocked = true
    ) { paddingValues ->
        ContactDetailsContent(
            modifier = Modifier
                .padding(paddingValues)
                .then(contentModifier),
            details = details
        )
    }
}


@Composable
private fun RowScope.ContactDetailsTopAppBarSinglePane(
    label: String,
    syncIconContent: @Composable () -> Unit,
    onUpClick: () -> Unit
) {
    IconButton(onClick = onUpClick) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = stringResource(id = content_desc_back)
        )
    }

    Text(
        label,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    syncIconContent()
}

@Composable
private fun RowScope.ContactDetailsBottomAppBarSinglePane(
    showDelete: Boolean,
    detailsDeleteClick: () -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))
    if (showDelete) {
        IconButton(onClick = detailsDeleteClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(id = cta_delete)
            )
        }
    }
}

@Composable
private fun ContactDetailsFab(
    modifier: Modifier = Modifier,
    uiState: ContactDetailsUiState,
    handler: ContactDetailsUiEventHandler
) {
    when (uiState) {
        is ContactDetailsUiState.ViewingContactDetails -> {
            when {
                uiState.uiSyncState == SObjectUiSyncState.Deleted ->
                    FloatingActionButton(
                        onClick = handler::undeleteClick,
                        modifier = modifier
                    ) {
                        Icon(
                            painter = painterResource(id = ic_undo),
                            contentDescription = stringResource(id = cta_undelete)
                        )
                    }

                uiState.isEditingEnabled ->
                    FloatingActionButton(
                        onClick = handler::saveClick,
                        modifier = modifier
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(id = cta_save)
                        )
                    }

                else ->
                    FloatingActionButton(
                        onClick = handler::editClick,
                        modifier = modifier
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(id = cta_edit)
                        )
                    }
            }
        }
        is ContactDetailsUiState.NoContactSelected -> FloatingActionButton(
            onClick = handler::createClick,
            modifier = modifier
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(id = content_desc_add_contact)
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailViewModePreview() {
    val contact = ContactObject(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle",
        department = "DepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartmentDepartment"
    )

    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ContactDetailsContentSinglePane(
                details = contact.toPreviewViewingContactDetails(),
                componentUiEventHandler = PREVIEW_CONTACT_DETAILS_UI_HANDLER,
                menuHandler = PREVIEW_CONTACTS_ACTIVITY_MENU_HANDLER,
            )
        }
    }
}

@TestOnly
fun ContactObject.toPreviewViewingContactDetails(
    uiSyncState: SObjectUiSyncState = SObjectUiSyncState.Updated,
    isEditingEnabled: Boolean = false,
    dataOperationIsActive: Boolean = false,
    shouldScrollToErrorField: Boolean = false,
    curDialogUiState: DialogUiState? = null
) = ContactDetailsUiState.ViewingContactDetails(
    firstNameField = ContactDetailsField.FirstName(
        fieldValue = firstName,
        onValueChange = {}
    ),
    lastNameField = ContactDetailsField.LastName(
        fieldValue = lastName,
        onValueChange = {}
    ),
    titleField = ContactDetailsField.Title(
        fieldValue = title,
        onValueChange = {}
    ),
    departmentField = ContactDetailsField.Department(
        fieldValue = department,
        onValueChange = {}
    ),
    uiSyncState = uiSyncState,
    isEditingEnabled = isEditingEnabled,
    dataOperationIsActive = dataOperationIsActive,
    shouldScrollToErrorField = shouldScrollToErrorField,
    curDialogUiState = curDialogUiState
)

val PREVIEW_CONTACT_DETAILS_UI_HANDLER = object : ContactDetailsUiEventHandler {
    override fun createClick() {}
    override fun deleteClick() {}
    override fun undeleteClick() {}
    override fun deselectContact() {}
    override fun editClick() {}
    override fun exitEditClick() {}
    override fun saveClick() {}
}
