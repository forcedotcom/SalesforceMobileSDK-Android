package com.salesforce.samples.mobilesynccompose.model.contacts

import com.salesforce.androidsdk.mobilesync.model.SalesforceObject
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import org.json.JSONObject

class ContactObject(raw: JSONObject) : SalesforceObject(raw) {
    constructor(
        id: String,
        firstName: String,
        middleName: String?,
        lastName: String,
        title: String
    ) : this(
        JSONObject().apply {
            putOpt(Constants.ID, id)
            putOpt(KEY_FIRST_NAME, firstName)
            putOpt(KEY_MIDDLE_NAME, middleName)
            putOpt(KEY_LAST_NAME, lastName)
            putOpt(KEY_TITLE, title)
            putOpt(Constants.NAME, "$firstName $middleName $lastName")
        }
    )

    val firstName: String = raw.optString(KEY_FIRST_NAME)
    val middleName: String = raw.optString(KEY_MIDDLE_NAME)
    val lastName: String = raw.optString(KEY_LAST_NAME)
    val title: String = raw.optString(KEY_TITLE)

    val isLocallyCreated: Boolean = raw.optBoolean(SyncTarget.LOCALLY_CREATED)
    val isLocallyDeleted: Boolean = raw.optBoolean(SyncTarget.LOCALLY_DELETED)
    val isLocallyUpdated: Boolean = raw.optBoolean(SyncTarget.LOCALLY_UPDATED)

    fun copy(
        id: String = this.objectId,
        firstName: String = this.firstName,
        middleName: String? = this.middleName,
        lastName: String = this.lastName,
        title: String = this.title
    ): ContactObject = ContactObject(
        id,
        firstName,
        middleName,
        lastName,
        title
    )

    companion object {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_MIDDLE_NAME = "MiddleName"
        const val KEY_TITLE = "Title"
    }
}
