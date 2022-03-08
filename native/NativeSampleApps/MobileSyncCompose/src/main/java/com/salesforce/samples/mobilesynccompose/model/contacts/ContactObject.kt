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

import com.salesforce.androidsdk.mobilesync.model.SalesforceObject
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact.Companion.equals
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact.Companion.hashCode
import org.jetbrains.annotations.TestOnly
import org.json.JSONObject
import java.util.*

/**
 * An abstraction and runtime data model representation of a Contact Salesforce Standard Object.
 *
 * This is not represented as a data class because there is business logic that needs to be applied
 * to copy and de/serialization operations that do not work with data class semantics.
 *
 * Note how this is not a [SalesforceObject]. [SalesforceObject]s are _mutable_ which goes against
 * Jetpack Compose guidelines to make state objects immutable.
 */
class Contact private constructor(raw: JSONObject) {

    private val raw = JSONObject(raw.toString()) // new JSON obj ref to avoid mutation issues

    val id: String = this.raw.optString(Constants.ID)
    val firstName: String = this.raw.optString(KEY_FIRST_NAME)
    val lastName: String = this.raw.optString(KEY_LAST_NAME)
    val title: String = this.raw.optString(KEY_TITLE)
    val fullName: String = "$firstName $lastName".ifBlank { "" }
    val locallyCreated: Boolean = this.raw.optBoolean(LOCALLY_CREATED, false)
    val locallyDeleted: Boolean = this.raw.optBoolean(LOCALLY_DELETED, false)
    val locallyUpdated: Boolean = this.raw.optBoolean(LOCALLY_UPDATED, false)
    val local: Boolean = locallyCreated || locallyDeleted || locallyUpdated

    fun copy(
        firstName: String = this.firstName,
        lastName: String = this.lastName,
        title: String = this.title
    ) = copy(
        firstName = firstName,
        lastName = lastName,
        title = title,
        locallyUpdated = locallyUpdated || // preserve current locallyUpdated state
                firstName != this.firstName || lastName != this.lastName || title != this.title,
    )

    private fun copy(
        firstName: String = this.firstName,
        lastName: String = this.lastName,
        title: String = this.title,
        locallyCreated: Boolean = this.locallyCreated,
        locallyDeleted: Boolean = this.locallyDeleted,
        locallyUpdated: Boolean = this.locallyUpdated,
    ) = Contact(
        raw
            .putOpt(KEY_FIRST_NAME, firstName)
            .putOpt(KEY_LAST_NAME, lastName)
            .putOpt(KEY_TITLE, title)
            .putOpt(LOCALLY_CREATED, locallyCreated)
            .putOpt(LOCALLY_DELETED, locallyDeleted)
            .putOpt(LOCALLY_UPDATED, locallyUpdated)
            .putOpt(LOCAL, locallyCreated || locallyDeleted || locallyUpdated)
    )

    fun toJson(): JSONObject = JSONObject(raw.toString()).putOpt(Constants.NAME, fullName)

    /* This equals() method does not simply compare the raw JSONObject property because we are not
     * interested in all the extra properties that might be in that JSON. Equality for this model
     * representation means that only all publicly-exposed properties are equal. */
    override fun equals(other: Any?): Boolean = this === other || (
            other is Contact &&
                    other.id == this.id &&
                    other.firstName == this.firstName &&
                    other.lastName == this.lastName &&
                    other.title == this.title &&
                    other.locallyCreated == this.locallyCreated &&
                    other.locallyDeleted == this.locallyDeleted &&
                    other.locallyUpdated == this.locallyUpdated
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + locallyCreated.hashCode()
        result = 31 * result + locallyDeleted.hashCode()
        result = 31 * result + locallyUpdated.hashCode()
        return result
    }

    override fun toString(): String {
        return "Contact(id='$id', firstName='$firstName', lastName='$lastName', title='$title', fullName='$fullName', locallyCreated=$locallyCreated, locallyDeleted=$locallyDeleted, locallyUpdated=$locallyUpdated, local=$local)"
    }

    companion object {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_TITLE = "Title"

        /**
         * Extracts the required properties for this contact model from the input [JSONObject] and
         * creates a [Contact] model using these properties. Default/new values will be used if
         * required properties are missing. The provided [JSONObject] can be safely mutated after
         * this method returns without this [Contact] model being affected.
         *
         * @param json The input [JSONObject] to deserialize into a [Contact]
         * @return The newly created [Contact] model.
         */
        fun coerceFromJson(json: JSONObject): Contact {
            val rawCopy = JSONObject(json.toString())
            var locallyCreated = rawCopy.optBoolean(LOCALLY_CREATED, false)

            val id = rawCopy.optString(Constants.ID).ifEmpty {
                locallyCreated = true
                SyncTarget.createLocalId()
            }

            val locallyDeleted = rawCopy.optBoolean(LOCALLY_DELETED, false)
            val locallyUpdated = rawCopy.optBoolean(LOCALLY_UPDATED, false)

            val local = locallyCreated || locallyDeleted || locallyUpdated

            return Contact(
                rawCopy
                    .putOpt(Constants.ID, id)
                    .putOpt(LOCALLY_CREATED, locallyCreated)
                    .putOpt(LOCAL, local)
            )
        }

        /**
         * Creates a new [Contact] model object from the provided properties.
         *
         * @param firstName The contact's first name.
         * @param lastName The contact's last name.
         * @param title The contact's business title.
         * @return The newly-created [Contact] model object.
         */
        fun createNewLocal(
            firstName: String = "",
            lastName: String = "",
            title: String = ""
        ): Contact {
            val attributes = JSONObject()
                .put(Constants.TYPE.lowercase(Locale.US), Constants.CONTACT)

            return Contact(
                JSONObject()
                    .putOpt(Constants.ID, SyncTarget.createLocalId())
                    .putOpt(KEY_FIRST_NAME, firstName)
                    .putOpt(KEY_LAST_NAME, lastName)
                    .putOpt(KEY_TITLE, title)
                    .put(Constants.ATTRIBUTES, attributes)
                    .putOpt(LOCALLY_CREATED, true)
                    .putOpt(LOCALLY_DELETED, false)
                    .putOpt(LOCALLY_UPDATED, false)
                    .putOpt(LOCAL, true)
            )
        }

        @TestOnly
        internal fun mockContact(
            id: String,
            firstName: String,
            lastName: String,
            title: String,
            locallyCreated: Boolean,
            locallyDeleted: Boolean,
            locallyUpdated: Boolean,
            local: Boolean
        ) = coerceFromJson(
            JSONObject()
                .putOpt(Constants.ID, id)
                .putOpt(KEY_FIRST_NAME, firstName)
                .putOpt(KEY_LAST_NAME, lastName)
                .putOpt(KEY_TITLE, title)
                .putOpt(LOCALLY_CREATED, locallyCreated)
                .putOpt(LOCALLY_DELETED, locallyDeleted)
                .putOpt(LOCALLY_UPDATED, locallyUpdated)
                .putOpt(LOCAL, local)
        )
    }
}
