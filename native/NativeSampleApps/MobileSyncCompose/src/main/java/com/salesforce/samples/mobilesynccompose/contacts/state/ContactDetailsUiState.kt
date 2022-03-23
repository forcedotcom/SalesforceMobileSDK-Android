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
package com.salesforce.samples.mobilesynccompose.contacts.state

import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactObjectFieldChangeHandler
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId
import com.salesforce.samples.mobilesynccompose.core.vm.TextFieldViewModel
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

data class ContactDetailsUiState(
    val mode: ContactDetailsUiMode,
    val contactObj: ContactObject,
    val fieldValueChangeHandler: ContactObjectFieldChangeHandler,

    // transient state properties all have default values to make things less verbose:
    val isSaving: Boolean = false,
    val shouldScrollToErrorField: Boolean = false
) {
    val firstNameVm = contactObj.createFirstNameVm(fieldValueChangeHandler::onFirstNameChange)
    val lastNameVm = contactObj.createLastNameVm(fieldValueChangeHandler::onLastNameChange)
    val titleVm = contactObj.createTitleVm(fieldValueChangeHandler::onTitleChange)
    val departmentVm = contactObj.createDepartmentVm(fieldValueChangeHandler::onDepartmentChange)

    val fieldToScrollTo: TextFieldViewModel? by lazy { fieldsInErrorState.firstOrNull() }

    val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm,
        departmentVm
    )

    val fieldsInErrorState: List<TextFieldViewModel> by lazy {
        vmList.filter { it.isInErrorState }
    }

    val hasFieldsInErrorState: Boolean by lazy {
        fieldsInErrorState.isNotEmpty()
    }
}

enum class ContactDetailsUiMode {
    Creating,
    Editing,
    Viewing
}

fun ContactObject.toContactDetailsUiState(
    mode: ContactDetailsUiMode,
    fieldValueChangeHandler: ContactObjectFieldChangeHandler,
    isSaving: Boolean = false,
    shouldScrollToErrorField: Boolean = false,
) = ContactDetailsUiState(
    mode = mode,
    contactObj = this,
    fieldValueChangeHandler = fieldValueChangeHandler,
    isSaving = isSaving,
    shouldScrollToErrorField = shouldScrollToErrorField,
)

fun ContactObject.createFirstNameVm(
    onFieldValueChanged: (id: SObjectId, newValue: String?) -> Unit
) = TextFieldViewModel(
    fieldValue = firstName,
    isInErrorState = false,
    canBeEdited = true,
    labelRes = R.string.label_contact_first_name,
    helperRes = null,
    placeholderRes = R.string.label_contact_first_name,
    onFieldValueChange = { onFieldValueChanged(this.id, it.ifBlank { null }) }
)

fun ContactObject.createLastNameVm(
    onFieldValueChanged: (id: SObjectId, newValue: String?) -> Unit
): TextFieldViewModel {

    val isError = lastName.isNullOrBlank()
    val help = if (isError) R.string.help_cannot_be_blank else null

    return TextFieldViewModel(
        fieldValue = lastName,
        isInErrorState = isError,
        canBeEdited = true,
        labelRes = R.string.label_contact_last_name,
        helperRes = help,
        placeholderRes = R.string.label_contact_last_name,
        onFieldValueChange = { onFieldValueChanged(this.id, it.ifBlank { null }) }
    )
}

fun ContactObject.createTitleVm(
    onFieldValueChanged: (id: SObjectId, newValue: String?) -> Unit
) = TextFieldViewModel(
    fieldValue = title,
    isInErrorState = false, // cannot be in error state
    canBeEdited = true,
    labelRes = R.string.label_contact_title,
    helperRes = null,
    placeholderRes = R.string.label_contact_title,
    onFieldValueChange = { onFieldValueChanged(this.id, it.ifBlank { null }) }
)

fun ContactObject.createDepartmentVm(
    onFieldValueChanged: (id: SObjectId, newValue: String?) -> Unit
) = TextFieldViewModel(
    fieldValue = department,
    isInErrorState = false,
    canBeEdited = true,
    labelRes = R.string.label_contact_department,
    helperRes = null,
    placeholderRes = R.string.label_contact_department,
    onFieldValueChange = { onFieldValueChanged(this.id, it.ifBlank { null }) }
)

