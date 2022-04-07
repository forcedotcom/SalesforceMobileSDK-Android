package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent

import com.salesforce.samples.mobilesynccompose.core.ui.state.DialogUiState
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

sealed interface ContactDetailsUiState {
    val curDialogUiState: DialogUiState?
    val dataOperationIsActive: Boolean

    data class ViewingContactDetails(
        val firstNameField: ContactDetailsField.FirstName,
        val lastNameField: ContactDetailsField.LastName,
        val titleField: ContactDetailsField.Title,
        val departmentField: ContactDetailsField.Department,

        val isEditingEnabled: Boolean,
        override val dataOperationIsActive: Boolean,
        val shouldScrollToErrorField: Boolean,
        override val curDialogUiState: DialogUiState?
    ) : ContactDetailsUiState {
        val fullName = ContactObject.formatFullName(
            firstName = firstNameField.fieldValue,
            lastName = lastNameField.fieldValue
        )
    }

    data class NoContactSelected(
        override val dataOperationIsActive: Boolean,
        override val curDialogUiState: DialogUiState?
    ) : ContactDetailsUiState
}

fun ContactDetailsUiState.copy(
    dataOperationIsActive: Boolean = this.dataOperationIsActive,
    curDialogUiState: DialogUiState? = this.curDialogUiState
) = when (this) {
        is ContactDetailsUiState.NoContactSelected -> this.copy(
            dataOperationIsActive = dataOperationIsActive,
            curDialogUiState = curDialogUiState
        )
        is ContactDetailsUiState.ViewingContactDetails -> this.copy(
            dataOperationIsActive = dataOperationIsActive,
            curDialogUiState = curDialogUiState
        )
    }
