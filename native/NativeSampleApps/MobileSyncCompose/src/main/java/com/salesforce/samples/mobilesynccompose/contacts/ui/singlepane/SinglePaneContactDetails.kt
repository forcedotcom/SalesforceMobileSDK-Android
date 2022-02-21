package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailsCoreEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailsDiscardChangesEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactEditModeEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactViewModeEventHandler
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
        handler: ContactDetailsDiscardChangesEventHandler,
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
                    isEditEnabled = false,
                    isError = fieldVm.isInErrorState,
                    onValueChange = { }, // Not editable in this mode
                    label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                    help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                    placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
                )
            }

            if (details.showDiscardChanges) {
                DiscardChangesDialog(handler = handler)
            }
        }
    }

    @Composable
    fun EditingContact(
        modifier: Modifier = Modifier,
        details: ContactDetailsUiState,
        handler: ContactEditModeEventHandler,
        discardChangesHandler: ContactDetailsDiscardChangesEventHandler,
        isSaving: Boolean
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
                    onValueChange = { handler.onDetailsUpdated(fieldVm.onFieldValueChange(it)) },
                    label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                    help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                    placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
                )
            }
        }
        when {
            isSaving -> {
                LoadingOverlay()
            }

            details.showDiscardChanges -> {
                DiscardChangesDialog(handler = discardChangesHandler)
            }
        }
    }

    object ScaffoldContent {
        @Composable
        fun RowScope.TopAppBar(label: String, handler: ContactDetailsCoreEventHandler) {
            IconButton(onClick = handler::detailsExitClick) {
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
        }

        @Composable
        fun RowScope.BottomAppBar(handler: ContactDetailsCoreEventHandler) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = handler::detailsDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(id = cta_delete)
                )
            }
        }

        @Composable
        fun EditModeFab(handler: ContactEditModeEventHandler) {
            FloatingActionButton(onClick = handler::saveClick) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(id = cta_save)
                )
            }
        }

        @Composable
        fun ViewModeFab(handler: ContactViewModeEventHandler) {
            FloatingActionButton(onClick = handler::detailsEditClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(id = cta_edit)
                )
            }
        }
    }
}

@Composable
private fun DiscardChangesDialog(handler: ContactDetailsDiscardChangesEventHandler) {
    AlertDialog(
        onDismissRequest = handler::continueEditing,
        confirmButton = {
            TextButton(onClick = handler::discardChanges) {
                Text(stringResource(id = cta_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = handler::continueEditing) {
                Text(stringResource(id = cta_continue_editing))
            }
        },
        title = { Text(stringResource(id = label_discard_changes)) },
        text = { Text(stringResource(id = body_discard_changes)) }
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
                handler = object : ContactDetailsDiscardChangesEventHandler {
                    override fun discardChanges() {
                        TODO("Not yet implemented")
                    }

                    override fun continueEditing() {
                        TODO("Not yet implemented")
                    }
                }
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
                handler = object : ContactEditModeEventHandler {
                    override fun onDetailsUpdated(newContact: Contact) {
                        TODO("Not yet implemented")
                    }

                    override fun saveClick() {
                        TODO("Not yet implemented")
                    }

                    override fun detailsDeleteClick() {
                        TODO("Not yet implemented")
                    }

                    override fun detailsExitClick() {
                        TODO("Not yet implemented")
                    }
                },
                discardChangesHandler = object : ContactDetailsDiscardChangesEventHandler {
                    override fun discardChanges() {
                        TODO("Not yet implemented")
                    }

                    override fun continueEditing() {
                        TODO("Not yet implemented")
                    }

                },
                isSaving = false
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DiscardChangesPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiscardChangesDialog(handler = object : ContactDetailsDiscardChangesEventHandler {
                override fun discardChanges() {
                    TODO("Not yet implemented")
                }

                override fun continueEditing() {
                    TODO("Not yet implemented")
                }
            })
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
                handler = object : ContactEditModeEventHandler {
                    override fun onDetailsUpdated(newContact: Contact) {
                        TODO("Not yet implemented")
                    }

                    override fun saveClick() {
                        TODO("Not yet implemented")
                    }

                    override fun detailsDeleteClick() {
                        TODO("Not yet implemented")
                    }

                    override fun detailsExitClick() {
                        TODO("Not yet implemented")
                    }
                },
                discardChangesHandler = object : ContactDetailsDiscardChangesEventHandler {
                    override fun discardChanges() {
                        TODO("Not yet implemented")
                    }

                    override fun continueEditing() {
                        TODO("Not yet implemented")
                    }
                },
                isSaving = true
            )
        }
    }
}
