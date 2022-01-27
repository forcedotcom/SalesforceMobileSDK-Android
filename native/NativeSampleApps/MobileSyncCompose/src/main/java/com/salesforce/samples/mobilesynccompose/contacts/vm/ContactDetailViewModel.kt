package com.salesforce.samples.mobilesynccompose.contacts.vm

import kotlinx.coroutines.flow.StateFlow

interface ContactDetailViewModel {
    val viewState: StateFlow<ContactDetailViewModelState>
}

data class ContactDetailViewModelState(
    val isInEditMode: Boolean,

    val nameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel
)