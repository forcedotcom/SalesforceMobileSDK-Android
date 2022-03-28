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
import com.salesforce.samples.mobilesynccompose.core.data.ReadOnlyJson
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
data class ContactObject(
    val firstName: String?,
    val lastName: String,
    val title: String?,
    val department: String?,
) : SObjectModel {

    val fullName = buildString {
        if (firstName != null) append("$firstName ")
        append(lastName)
    }.trim()

    override fun JSONObject.applyMemoryModelProperties() = this.apply {
        putOpt(KEY_FIRST_NAME, firstName)
        putOpt(KEY_LAST_NAME, lastName)
        putOpt(KEY_TITLE, title)
        putOpt(KEY_DEPARTMENT, department)
        putOpt(Constants.NAME, fullName)
    }

    companion object : SObjectDeserializer<ContactObject> {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_TITLE = "Title"
        const val KEY_DEPARTMENT = "Department"
        private const val OBJECT_TYPE = Constants.CONTACT

        @Throws(CoerceException::class)
        override fun coerceFromJsonOrThrow(json: ReadOnlyJson): SObjectRecord<ContactObject> {
            ReadOnlySoHelper.requireSoType(json, OBJECT_TYPE)

            val primaryKey = ReadOnlySoHelper.getPrimaryKeyOrThrow(json)
            val localId = ReadOnlySoHelper.getLocalId(json)
            val lastName = json.getRequiredStringOrThrow(KEY_LAST_NAME, valueCanBeBlank = false)

            val model = ContactObject(
                firstName = json.optStringOrNull(KEY_FIRST_NAME),
                lastName = lastName,
                title = json.optStringOrNull(KEY_TITLE),
                department = json.optStringOrNull(KEY_DEPARTMENT),
            )

            return SObjectRecord(
                primaryKey = primaryKey,
                localId = localId,
                localStatus = json.coerceToLocalStatus(),
                model = model
            )
        }

        override val objectType: String = OBJECT_TYPE
    }
}
