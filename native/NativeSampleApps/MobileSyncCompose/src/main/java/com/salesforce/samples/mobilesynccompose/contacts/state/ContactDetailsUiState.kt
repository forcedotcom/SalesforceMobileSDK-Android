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

import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailFieldViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.createFirstNameVm
import com.salesforce.samples.mobilesynccompose.contacts.vm.createLastNameVm
import com.salesforce.samples.mobilesynccompose.contacts.vm.createTitleVm
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

data class ContactDetailsUiState(
    val mode: ContactDetailsUiMode,
    val origContact: Contact,
    val firstNameVm: ContactDetailFieldViewModel,
    val lastNameVm: ContactDetailFieldViewModel,
    val titleVm: ContactDetailFieldViewModel,

    // transient state properties all have default values to make things less verbose:
    val isSaving: Boolean = false,
    val fieldToScrollTo: ContactDetailFieldViewModel? = null
) {
    val vmList = listOf(
        firstNameVm,
        lastNameVm,
        titleVm
    )

    val contactId = origContact.id

    val updatedContact: Contact by lazy {
        origContact.copy(
            firstName = firstNameVm.fieldValue,
            lastName = lastNameVm.fieldValue,
            title = titleVm.fieldValue,
        )
    }

    val isModified: Boolean by lazy {
        updatedContact != origContact
    }

    val fieldsInErrorState: List<ContactDetailFieldViewModel> by lazy {
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

fun Contact.toContactDetailsUiState(
    mode: ContactDetailsUiMode,
    isSaving: Boolean = false,
    fieldToScrollTo: ContactDetailFieldViewModel? = null
) = ContactDetailsUiState(
    mode = mode,
    origContact = this,
    firstNameVm = createFirstNameVm(),
    lastNameVm = createLastNameVm(),
    titleVm = createTitleVm(),
    isSaving = isSaving,
    fieldToScrollTo = fieldToScrollTo,
)
