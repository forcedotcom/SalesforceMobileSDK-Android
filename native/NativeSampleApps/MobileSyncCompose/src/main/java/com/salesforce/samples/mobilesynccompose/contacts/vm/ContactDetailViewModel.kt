package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailUiState.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface ContactDetailViewModel : ContactDetailsChangedHandler {
    val uiState: StateFlow<ContactDetailUiState>

    fun onBack()
    fun onDeleteClick()
    fun onEditClick()
    fun onSaveClick()
}

sealed interface ContactDetailUiState {
    data class ContactSelected(
        val curContactDetails: TempContactObject,
        val isInEditMode: Boolean,
        val nameVm: ContactDetailFieldViewModel,
        val titleVm: ContactDetailFieldViewModel,
    ) : ContactDetailUiState {
        val isModified: Boolean =
            curContactDetails.copy(
                name = nameVm.fieldValue,
                title = titleVm.fieldValue
            ) != curContactDetails
    }

    object NoContactSelected : ContactDetailUiState
}

class DefaultContactDetailViewModel(
    contactSelectionEvents: Flow<TempContactObject?>,
    parentScope: CoroutineScope,
    private val onBackDelegate: () -> Unit
) : ContactDetailViewModel {

    private val mutUiState: MutableStateFlow<ContactDetailUiState> =
        MutableStateFlow(NoContactSelected)

    override val uiState: StateFlow<ContactDetailUiState> get() = mutUiState

    init {
        parentScope.launch {
            contactSelectionEvents.collect {
                if (it == null) {
                    mutUiState.emit(NoContactSelected)
                } else {
                    mutUiState.emit(
                        ContactSelected(
                            curContactDetails = it,
                            isInEditMode = false,
                            nameVm = it.toNameVm(),
                            titleVm = it.toTitleVm()
                        )
                    )
                }
            }
        }
    }

    override fun onBack() {
        onBackDelegate()
    }

    override fun onDeleteClick() {
        TODO("onDeleteClick")
    }

    override fun onEditClick() {
        TODO("onEditClick")
    }

    override fun onSaveClick() {
        TODO("onSaveClick")
    }

    override fun onNameChanged(newValue: String) {
        TODO("onNameChanged")
    }

    override fun onTitleChanged(newValue: String) {
        TODO("onTitleChanged")
    }

}

private fun TempContactObject.toNameVm(): ContactDetailFieldViewModel {
    val isError = name.isBlank()
    val help = if (isError) R.string.help_cannot_be_blank else null

    return ContactDetailFieldViewModel(
        fieldValue = name,
        isInErrorState = isError,
        helperRes = help
    )
}

private fun TempContactObject.toTitleVm(): ContactDetailFieldViewModel =
    ContactDetailFieldViewModel(
        fieldValue = title,
        isInErrorState = false, // cannot be in error state
        helperRes = null
    )