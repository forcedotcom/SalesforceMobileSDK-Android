package com.salesforce.samples.mobilesynccompose.model.contacts

import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import com.salesforce.androidsdk.mobilesync.util.Constants
import org.json.JSONObject
import java.util.*

class Contact private constructor(raw: JSONObject) {

    private val raw = JSONObject(raw.toString()) // new JSON obj ref to avoid mutation issues

    val id: String = this.raw.optString(Constants.ID)
    val firstName: String = this.raw.optString(KEY_FIRST_NAME)
    val lastName: String = this.raw.optString(KEY_LAST_NAME)
    val title: String = this.raw.optString(KEY_TITLE)
    val fullName: String = "$firstName $lastName".ifBlank { "" }
    private val locallyCreated: Boolean = this.raw.optBoolean(LOCALLY_CREATED, false)
    private val locallyDeleted: Boolean = this.raw.optBoolean(LOCALLY_DELETED, false)
    private val locallyUpdated: Boolean = this.raw.optBoolean(LOCALLY_UPDATED, false)
    val local: Boolean = locallyCreated || locallyDeleted || locallyUpdated

    fun copy(
        firstName: String = this.firstName,
        lastName: String = this.lastName,
        title: String = this.title
    ) = copy(
        firstName = firstName,
        lastName = lastName,
        title = title,
        locallyUpdated = firstName != this.firstName || lastName != this.lastName || title != this.title,
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

    fun markForDeletion() = copy(locallyDeleted = true)
    fun toJson(): JSONObject = JSONObject(raw.toString()).putOpt(Constants.NAME, fullName)

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
    }
}
