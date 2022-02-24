package com.salesforce.samples.mobilesynccompose.contacts.state

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactsActivityDialogUiState

data class DeleteConfirmationDialogUiState(
    val contactToDelete: Contact,
    val onCancelDelete: () -> Unit,
    val onDeleteConfirm: (contactId: String) -> Unit,
) : ContactsActivityDialogUiState

data class DiscardChangesDialogUiState(
    val onDiscardChanges: () -> Unit,
    val onKeepChanges: () -> Unit,
) : ContactsActivityDialogUiState

data class UndeleteConfirmationDialogUiState(
    val contactToUndelete: Contact,
    val onCancelUndelete: () -> Unit,
    val onUndeleteConfirm: (contactId: String) -> Unit,
) : ContactsActivityDialogUiState
