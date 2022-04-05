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
package com.salesforce.samples.mobilesynccompose.model.contacts

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.extensions.optStringOrNull
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import org.json.JSONObject

/**
 * An abstraction and runtime data model of a Contact Salesforce Standard Object.
 *
 * This is not represented as a data class because there is business logic that needs to be applied
 * to copy and de/serialization operations that do not work with data class semantics.
 *
 * Note how this is not a [SalesforceObject]. [SalesforceObject]s are _mutable_ which goes against
 * Jetpack Compose guidelines to make state objects immutable.
 */
data class ContactObject
@Throws(ContactValidationException::class) constructor(
    val firstName: String?,
    val lastName: String,
    val title: String?,
    val department: String?,
) : SObject {
    init {
        validateLastName(lastName)
    }

    override val objectType: String = Constants.CONTACT

    val fullName = formatFullName(firstName = firstName, lastName = lastName)

    override fun JSONObject.applyObjProperties() = this.apply {
        putOpt(KEY_FIRST_NAME, firstName)
        putOpt(KEY_LAST_NAME, lastName)
        putOpt(KEY_TITLE, title)
        putOpt(KEY_DEPARTMENT, department)
        putOpt(Constants.NAME, fullName)
    }

    companion object : SObjectDeserializerBase<ContactObject>(objectType = Constants.CONTACT) {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_TITLE = "Title"
        const val KEY_DEPARTMENT = "Department"

        @Throws(CoerceException::class)
        override fun buildModel(fromJson: JSONObject): ContactObject = try {
            ContactObject(
                firstName = fromJson.optStringOrNull(KEY_FIRST_NAME),
                lastName = fromJson.optString(KEY_LAST_NAME),
                title = fromJson.optStringOrNull(KEY_TITLE),
                department = fromJson.optStringOrNull(KEY_DEPARTMENT),
            )
        } catch (ex: ContactValidationException) {
            when (ex) {
                ContactValidationException.LastNameCannotBeBlank -> InvalidPropertyValue(
                    propertyKey = KEY_LAST_NAME,
                    allowedValuesDescription = "Contact Last Name cannot be blank",
                    offendingJsonString = fromJson.toString()
                )
            }.let { throw it } // exhaustive when
        }

        @Throws(ContactValidationException.LastNameCannotBeBlank::class)
        fun validateLastName(lastName: String?) {
            if (lastName.isNullOrBlank())
                throw ContactValidationException.LastNameCannotBeBlank
        }

        fun formatFullName(firstName: String?, lastName: String?) = buildString {
            if (firstName != null) append("$firstName ")
            if (lastName != null) append(lastName)
        }.trim()
    }
}

sealed class ContactValidationException(override val message: String?) : Exception() {
    object LastNameCannotBeBlank : ContactValidationException("Contact Last Name cannot be blank")
}
