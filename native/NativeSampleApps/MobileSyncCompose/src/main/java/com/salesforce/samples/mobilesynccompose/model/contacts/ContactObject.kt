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
import com.salesforce.samples.mobilesynccompose.core.ReadOnlyJson
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
    val lastName: String?,
    val title: String?,
    val department: String?,
    override val id: SObjectId,
    override val localStatus: LocalStatus,
    override val originalElt: ReadOnlyJson
) : SObject {

    val fullName =
        if (firstName == null && this.lastName == null) null
        else buildString {
            if (firstName != null) append("$firstName ")
            if (lastName != null) append(lastName)
        }.trim()

    override fun buildUpdatedElt(): JSONObject = originalElt.buildMutableCopy().apply {
        putOpt(KEY_FIRST_NAME, firstName)
        putOpt(KEY_LAST_NAME, lastName)
        putOpt(KEY_TITLE, title)
        putOpt(KEY_DEPARTMENT, department)
        putOpt(Constants.NAME, fullName)
    }

    override val curPropertiesAreModifiedFromOriginal: Boolean by lazy {
        with(originalElt) {
            optStringOrNull(KEY_FIRST_NAME) != firstName ||
                    optStringOrNull(KEY_LAST_NAME) != lastName ||
                    optStringOrNull(KEY_TITLE) != title ||
                    optStringOrNull(KEY_DEPARTMENT) != department
        }
    }

    companion object : SalesforceObjectDeserializer<ContactObject> {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_TITLE = "Title"
        const val KEY_DEPARTMENT = "Department"
        private const val OBJECT_TYPE = Constants.CONTACT

        @Throws(CoerceException::class)
        override fun coerceFromJsonOrThrow(json: JSONObject): ContactObject {
            val elt = ReadOnlyJson(json)

            ReadOnlySoHelper.requireSoType(elt, OBJECT_TYPE)

            val serverId = ReadOnlySoHelper.getServerIdOrThrow(elt)
            val localId = ReadOnlySoHelper.getLocalId(elt)

            return ContactObject(
                id = SObjectId(primaryKey = serverId, localId = localId),
                localStatus = ReadOnlySoHelper.getLocalStatus(elt),
                originalElt = elt,
                firstName = elt.optStringOrNull(KEY_FIRST_NAME),
                lastName = elt.optStringOrNull(KEY_LAST_NAME),
                title = elt.optStringOrNull(KEY_TITLE),
                department = elt.optStringOrNull(KEY_DEPARTMENT),
            )
        }

        /**
         * Creates a new [Contact] model object from the provided properties.
         *
         * @param firstName The contact's first name.
         * @param lastName The contact's last name.
         * @param title The contact's business title.
         * @param department The contact's department.
         * @param associatedAccountId (Optional) the ID of the Salesforce Standard Object Account this contact is associated with.
         * @return The newly-created [Contact] model object.
         */
        fun createNewLocal(
            firstName: String? = null,
            lastName: String? = null,
            title: String? = null,
            department: String? = null,
            associatedAccountId: String? = null
        ) = createNewSoupEltBase(forObjType = OBJECT_TYPE)
            .apply {
                putOpt(KEY_FIRST_NAME, firstName)
                putOpt(KEY_LAST_NAME, lastName)
                putOpt(KEY_TITLE, title)
                putOpt(KEY_DEPARTMENT, department)
            }.let {
                coerceFromJsonOrThrow(it)
            }
    }
}
