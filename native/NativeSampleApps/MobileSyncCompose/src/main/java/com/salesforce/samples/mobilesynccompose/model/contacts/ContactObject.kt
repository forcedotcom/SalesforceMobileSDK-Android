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

data class ContactObject(
    val firstName: String?,
    val lastName: String?,
    val title: String?,
    val department: String?,
    val accountId: String?,
    override val serverId: String,
    override val localId: String?,
    override val localStatus: LocalStatus,
    private val elt: ReadOnlyJson
) : So {

    val fullName =
        if (firstName == null && this.lastName == null) null
        else buildString {
            if (firstName != null) append("$firstName ")
            if (lastName != null) append(lastName)
        }.trim()

    override fun buildUpdatedElt(): JSONObject = elt.buildMutableCopy().apply {
        putOpt(KEY_FIRST_NAME, firstName)
        putOpt(KEY_LAST_NAME, lastName)
        putOpt(KEY_TITLE, title)
        putOpt(KEY_DEPARTMENT, department)
        putOpt(Constants.NAME, fullName)
        putOpt(KEY_ACCOUNT_ID, accountId)
    }

    override val hasUnsavedChanges: Boolean by lazy {
        with(elt) {
            optStringOrNull(KEY_FIRST_NAME) != firstName ||
                    optStringOrNull(KEY_LAST_NAME) != lastName ||
                    optStringOrNull(KEY_TITLE) != title ||
                    optStringOrNull(KEY_DEPARTMENT) != department ||
                    optStringOrNull(KEY_ACCOUNT_ID) != accountId
        }
    }

    companion object : SalesforceObjectDeserializer<ContactObject> {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_TITLE = "Title"
        const val KEY_DEPARTMENT = "Department"
        const val KEY_ACCOUNT_ID = "AccountId"
        private const val OBJECT_TYPE = Constants.CONTACT

        @Throws(CoerceException::class)
        override fun coerceFromJsonOrThrow(json: JSONObject): ContactObject {
            val elt = ReadOnlyJson(json)

            ReadOnlySoHelper.requireSoType(elt, OBJECT_TYPE)

            return ContactObject(
                serverId = ReadOnlySoHelper.getServerIdOrThrow(elt),
                localId = ReadOnlySoHelper.getLocalId(elt),
                localStatus = ReadOnlySoHelper.getLocalStatus(elt),
                elt = elt,
                firstName = elt.optStringOrNull(KEY_FIRST_NAME),
                lastName = elt.optStringOrNull(KEY_LAST_NAME),
                title = elt.optStringOrNull(KEY_TITLE),
                department = elt.optStringOrNull(KEY_DEPARTMENT),
                accountId = elt.optStringOrNull(KEY_ACCOUNT_ID),
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
                putOpt(KEY_ACCOUNT_ID, associatedAccountId)
            }.let {
                coerceFromJsonOrThrow(it)
            }
    }
}

/**
 * An abstraction and runtime data model of a Contact Salesforce Standard Object.
 *
 * This is not represented as a data class because there is business logic that needs to be applied
 * to copy and de/serialization operations that do not work with data class semantics.
 *
 * Note how this is not a [SalesforceObject]. [SalesforceObject]s are _mutable_ which goes against
 * Jetpack Compose guidelines to make state objects immutable.
 */
//class Contact
//@Throws(CoerceException::class) private constructor(
//    val firstName: String?,
//    val lastName: String?,
//    val title: String?,
//    val department: String?,
//    val accountId: String?,
//    startingSoupElt: JSONObject
//) : SalesforceObjectContainer(startingSoupElt, requiredObjectType = Constants.CONTACT) {
//
//    @Throws(CoerceException::class)
//    constructor(eltCopy: JsonCopy) : this(
//        firstName = eltCopy.value.optStringOrNull(KEY_FIRST_NAME),
//        lastName = eltCopy.value.optStringOrNull(KEY_LAST_NAME),
//        title = eltCopy.value.optStringOrNull(KEY_TITLE),
//        department = eltCopy.value.optStringOrNull(KEY_DEPARTMENT),
//        accountId = eltCopy.value.optStringOrNull(KEY_ACCOUNT_ID),
//        eltCopy.value
//    )
//
//    val fullName =
//        if (firstName == null && this.lastName == null) null
//        else buildString {
//            if (this@Contact.firstName != null) append("${this@Contact.firstName} ")
//            if (this@Contact.lastName != null) append(this@Contact.lastName)
//        }.trim()
//
//    override val updatedJson: JSONObject by lazy {
//        super.updatedJson.apply {
//            putOpt(KEY_FIRST_NAME, firstName)
//            putOpt(KEY_LAST_NAME, lastName)
//            putOpt(KEY_TITLE, title)
//            putOpt(KEY_DEPARTMENT, department)
//            putOpt(Constants.NAME, fullName)
//            putOpt(KEY_ACCOUNT_ID, accountId)
//        }
//    }
//
//    override val isModifiedInMemory: Boolean =
//        this.startingSoupElt.optStringOrNull(KEY_FIRST_NAME) != firstName ||
//                this.startingSoupElt.optStringOrNull(KEY_LAST_NAME) != lastName ||
//                this.startingSoupElt.optStringOrNull(KEY_TITLE) != title ||
//                this.startingSoupElt.optStringOrNull(KEY_DEPARTMENT) != department ||
//                this.startingSoupElt.optStringOrNull(KEY_ACCOUNT_ID) != accountId
//
//    override fun JSONObject.isModified(): Boolean {
//        TODO("Not yet implemented")
//    }
//
//    fun copy(
//        firstName: String? = this.firstName,
//        lastName: String? = this.lastName,
//        title: String? = this.title,
//        department: String? = this.department,
//    ) = Contact(
//        firstName = firstName,
//        lastName = lastName,
//        title = title,
//        department = department,
//        accountId = accountId,
//        startingSoupElt = startingSoupElt
//    )
//
//    override fun toString(): String {
//        return "Contact(firstName=$firstName, lastName=$lastName, title=$title, department=$department, accountId=$accountId, fullName=$fullName) ${super.toString()}"
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        if (!super.equals(other)) return false
//
//        other as Contact
//
//        if (firstName != other.firstName) return false
//        if (lastName != other.lastName) return false
//        if (title != other.title) return false
//        if (department != other.department) return false
//        if (accountId != other.accountId) return false
//        if (fullName != other.fullName) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = super.hashCode()
//        result = 31 * result + (firstName?.hashCode() ?: 0)
//        result = 31 * result + (lastName?.hashCode() ?: 0)
//        result = 31 * result + (title?.hashCode() ?: 0)
//        result = 31 * result + (department?.hashCode() ?: 0)
//        result = 31 * result + (accountId?.hashCode() ?: 0)
//        result = 31 * result + (fullName?.hashCode() ?: 0)
//        return result
//    }
//
//    companion object : SalesforceObjectDeserializerBase<Contact>(objType = Constants.CONTACT) {
//        const val KEY_FIRST_NAME = "FirstName"
//        const val KEY_LAST_NAME = "LastName"
//        const val KEY_TITLE = "Title"
//        const val KEY_DEPARTMENT = "Department"
//        const val KEY_ACCOUNT_ID = "AccountId"
//
//        override fun createModelInstance(verifiedJson: JSONObject) =
//            Contact(
//                firstName = verifiedJson.optStringOrNull(KEY_FIRST_NAME),
//                lastName = verifiedJson.optStringOrNull(KEY_LAST_NAME),
//                title = verifiedJson.optStringOrNull(KEY_TITLE),
//                department = verifiedJson.optStringOrNull(KEY_DEPARTMENT),
//                accountId = verifiedJson.optStringOrNull(KEY_ACCOUNT_ID),
//                startingSoupElt = verifiedJson
//            )
//
//        /**
//         * Creates a new [Contact] model object from the provided properties.
//         *
//         * @param firstName The contact's first name.
//         * @param lastName The contact's last name.
//         * @param title The contact's business title.
//         * @param department The contact's department.
//         * @param associatedAccountId (Optional) the ID of the Salesforce Standard Object Account this contact is associated with.
//         * @return The newly-created [Contact] model object.
//         */
//        fun createNewLocal(
//            firstName: String? = null,
//            lastName: String? = null,
//            title: String? = null,
//            department: String? = null,
//            associatedAccountId: String? = null
//        ) = Contact(
//            firstName = firstName,
//            lastName = lastName,
//            title = title,
//            department = department,
//            accountId = associatedAccountId,
//            startingSoupElt = createNewSoupEltBase()
//                .putOpt(KEY_ACCOUNT_ID, associatedAccountId)
//                .putOpt(KEY_FIRST_NAME, firstName)
//                .putOpt(KEY_LAST_NAME, lastName)
//                .putOpt(KEY_TITLE, title)
//                .putOpt(KEY_DEPARTMENT, department)
//        )
//    }
//}
