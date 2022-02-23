package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

sealed interface ContactsActivityDialog

data class DeleteConfirmation(
    val contactToDelete: Contact,
    val onCancelDelete: () -> Unit,
    val onDeleteConfirm: (contactId: String) -> Unit,
) : ContactsActivityDialog

data class DiscardChanges(
    val onDiscardChanges: () -> Unit,
    val onKeepChanges: () -> Unit,
) : ContactsActivityDialog

data class UndeleteConfirmation(
    val contactToUndelete: Contact,
    val onCancelUndelete: () -> Unit,
    val onUndeleteConfirm: (contactId: String) -> Unit,
) : ContactsActivityDialog
