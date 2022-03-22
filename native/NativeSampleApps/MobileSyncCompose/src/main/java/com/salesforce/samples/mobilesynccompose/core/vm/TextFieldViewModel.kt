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
package com.salesforce.samples.mobilesynccompose.core.vm

import androidx.annotation.StringRes
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.So

/**
 * A ViewModel for a single text field for a Salesforce Object (e.g. the "first name" field of a Contact).
 * It holds the entire state of the text field and handles content change events, encapsulating
 * business logic for creating updated Salesforce Objects when the corresponding field value changes.
 */
data class TextFieldViewModel<T : So>(
    val fieldValue: String?,
    val isInErrorState: Boolean,
    val canBeEdited: Boolean,
    @StringRes val labelRes: Int?,
    @StringRes val helperRes: Int?,
    @StringRes val placeholderRes: Int?,
    val onFieldValueChange: (newValue: String?) -> T,
    val maxLines: UInt = 1u
)

// TODO there is a flaw in this system of capturing the Contact reference in this callback. Using a
//  stale object can lead to inconsistent state and at bare minimum we should change this interface
//  to emit the contact ID and which field changed. This means more concretely typing the fields so
//  that the activity VM knows which field changed.
//fun Contact.createFirstNameVm(): ContactDetailFieldViewModel =
//    ContactDetailFieldViewModel(
//        fieldValue = firstName,
//        isInErrorState = false,
//        canBeEdited = true,
//        labelRes = label_contact_first_name,
//        helperRes = null,
//        placeholderRes = label_contact_first_name,
//        onFieldValueChange = { newValue -> this.copy(firstName = newValue) }
//    )
//
//fun Contact.createLastNameVm(): ContactDetailFieldViewModel {
//    val isError = lastName.isNullOrBlank()
//    val help = if (isError) help_cannot_be_blank else null
//
//    return ContactDetailFieldViewModel(
//        fieldValue = lastName,
//        isInErrorState = isError,
//        canBeEdited = true,
//        labelRes = label_contact_last_name,
//        helperRes = help,
//        placeholderRes = label_contact_last_name,
//        onFieldValueChange = { newValue -> this.copy(lastName = newValue) }
//    )
//}
//
//fun Contact.createTitleVm(): ContactDetailFieldViewModel =
//    ContactDetailFieldViewModel(
//        fieldValue = title,
//        isInErrorState = false, // cannot be in error state
//        canBeEdited = true,
//        labelRes = label_contact_title,
//        helperRes = null,
//        placeholderRes = label_contact_title,
//        onFieldValueChange = { newValue -> this.copy(title = newValue) }
//    )
