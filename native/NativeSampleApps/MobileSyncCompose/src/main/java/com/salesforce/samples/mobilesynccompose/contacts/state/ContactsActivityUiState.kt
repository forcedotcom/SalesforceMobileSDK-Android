package com.salesforce.samples.mobilesynccompose.contacts.state

data class ContactsActivityUiState(
    val listState: ContactsActivityListUiState,
    val detailsState: ContactDetailsUiState?,
    val isSyncing: Boolean,
    val dialogUiState: ContactsActivityDialogUiState?
)
