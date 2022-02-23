package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.R.string.ok
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.drawable.ic_help
import com.salesforce.samples.mobilesynccompose.R.drawable.ic_undo
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.ui.SyncImage
import com.salesforce.samples.mobilesynccompose.contacts.ui.mockLocallyDeletedContact
import com.salesforce.samples.mobilesynccompose.contacts.vm.*
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
import com.salesforce.samples.mobilesynccompose.core.ui.safeStringResource
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

object SinglePaneContactDetails {
    @Composable
    fun ViewingContact(
        modifier: Modifier = Modifier,
        details: ContactDetailsUiState,
        showLoading: Boolean = false
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .padding(horizontal = 8.dp)
                .verticalScroll(state = scrollState)
        ) {
            if (details.origContact.locallyDeleted) {
                var infoIsExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { infoIsExpanded = true }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.error) {
                        Text(stringResource(id = label_locally_deleted))
                        Icon(
                            painterResource(id = ic_help),
                            contentDescription = stringResource(id = content_desc_help),
                            modifier = Modifier
                                .size(32.dp)
                                .padding(8.dp)
                        )
                    }
                }
                if (infoIsExpanded) {
                    LocallyDeletedInfoDialog(onDismiss = { infoIsExpanded = false })
                }
            }
            details.vmList.forEach { fieldVm ->
                ToggleableEditTextField(
                    fieldValue = fieldVm.fieldValue,
                    isEditEnabled = false,
                    isError = fieldVm.isInErrorState,
                    onValueChange = { }, // Not editable in this mode
                    label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                    help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                    placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
                )
            }
        }

        if (showLoading) {
            LoadingOverlay()
        }
    }

    @Composable
    fun EditingContact(
        modifier: Modifier = Modifier,
        details: ContactDetailsUiState,
        showLoading: Boolean,
        onDetailsUpdated: (newContact: Contact) -> Unit
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .padding(horizontal = 8.dp)
                .verticalScroll(state = scrollState)
        ) {
            details.vmList.forEach { fieldVm ->
                ToggleableEditTextField(
                    fieldValue = fieldVm.fieldValue,
                    isEditEnabled = fieldVm.canBeEdited,
                    isError = fieldVm.isInErrorState,
                    onValueChange = { onDetailsUpdated(fieldVm.onFieldValueChange(it)) },
                    label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                    help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                    placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
                )
            }
        }
        when {
            showLoading -> {
                LoadingOverlay()
            }
        }
    }

    object ScaffoldContent {
        @Composable
        fun RowScope.TopAppBar(
            label: String,
            syncIconContent: @Composable () -> Unit,
            detailsExitClick: () -> Unit
        ) {
            IconButton(onClick = detailsExitClick) {
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
        fun RowScope.BottomAppBar(showDelete: Boolean, detailsDeleteClick: () -> Unit) {
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
        fun DeletedModeFab(detailsUndeleteClick: () -> Unit) {
            FloatingActionButton(onClick = detailsUndeleteClick) {
                Icon(
                    painter = painterResource(id = ic_undo),
                    contentDescription = stringResource(id = cta_undelete)
                )
            }
        }

        @Composable
        fun EditModeFab(detailsSaveClick: () -> Unit) {
            FloatingActionButton(onClick = detailsSaveClick) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(id = cta_save)
                )
            }
        }

        @Composable
        fun ViewModeFab(detailsEditClick: () -> Unit) {
            FloatingActionButton(onClick = detailsEditClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(id = cta_edit)
                )
            }
        }
    }
}

@Composable
private fun LocallyDeletedInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = ok)) }
        },
        text = { Text(stringResource(id = body_locally_deleted_info)) }
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailViewModePreview() {
    val contact = Contact.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SinglePaneContactDetails.ViewingContact(
                details = contact.toContactDetailsUiState(
                    ContactDetailsUiMode.Viewing
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEditModePreview() {
    val origContact = Contact.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    val editedContact = origContact.copy(
        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
    )

    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SinglePaneContactDetails.EditingContact(
                details = ContactDetailsUiState(
                    origContact = origContact,
                    firstNameVm = editedContact.createFirstNameVm(),
                    lastNameVm = editedContact.createLastNameVm(),
                    titleVm = editedContact.createTitleVm(),
                    mode = ContactDetailsUiMode.Editing
                ),
                showLoading = false,
                onDetailsUpdated = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEditModeSavingPreview() {
    val origContact = Contact.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    val editedContact = origContact.copy(
        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
    )

    SalesforceMobileSDKAndroidTheme {
        Surface {
            SinglePaneContactDetails.EditingContact(
                details = ContactDetailsUiState(
                    origContact = origContact,
                    firstNameVm = editedContact.createFirstNameVm(),
                    lastNameVm = editedContact.createLastNameVm(),
                    titleVm = editedContact.createTitleVm(),
                    mode = ContactDetailsUiMode.Editing,
                    isSaving = true
                ),
                showLoading = true,
                onDetailsUpdated = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LocallyDeletedPreview() {
    val contact = mockLocallyDeletedContact()
    SalesforceMobileSDKAndroidTheme {
        Surface {
            SinglePaneContactDetails.ViewingContact(
                details = contact.toContactDetailsUiState(mode = ContactDetailsUiMode.Viewing),
            )
        }
    }
}
