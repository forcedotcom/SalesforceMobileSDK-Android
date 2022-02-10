package com.salesforce.samples.mobilesynccompose.model.contacts

import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import com.salesforce.androidsdk.mobilesync.util.Constants
import org.json.JSONObject

data class Contact(
    val id: String,
    val firstName: String,
    val lastName: String,
    val title: String,
    val locallyCreated: Boolean,
    val locallyDeleted: Boolean,
    val locallyUpdated: Boolean,
) {
    val fullName = "$firstName $lastName"
    val local: Boolean = locallyCreated || locallyDeleted || locallyUpdated

    //    private constructor(
//        id: String,
//        firstName: String,
//        lastName: String,
//        title: String,
//        locallyCreated: Boolean,
//        locallyDeleted: Boolean,
//        locallyUpdated: Boolean
//    ) : this (
//
//            )

    val raw: JSONObject by lazy {
        JSONObject()
            .putOpt(Constants.ID, id)
            .putOpt(KEY_FIRST_NAME, firstName)
            .putOpt(KEY_LAST_NAME, lastName)
            .putOpt(KEY_TITLE, title)
            .putOpt(Constants.NAME, fullName)
            .putOpt(LOCALLY_CREATED, locallyCreated)
            .putOpt(LOCALLY_DELETED, locallyDeleted)
            .putOpt(LOCALLY_UPDATED, locallyUpdated)
            .putOpt(LOCAL, local)
    }

    companion object {
        const val KEY_FIRST_NAME = "FirstName"
        const val KEY_LAST_NAME = "LastName"
        const val KEY_TITLE = "Title"

        fun fromExistingObject(json: JSONObject): Contact {
            val id = json.optString(Constants.ID)

            return Contact(
                id = id,
                firstName = json.optString(KEY_FIRST_NAME),
                lastName = json.optString(KEY_LAST_NAME),
                title = json.optString(KEY_TITLE),
                locallyCreated = json.optBoolean(LOCALLY_CREATED, id.isEmpty()),
                locallyDeleted = json.optBoolean(LOCALLY_DELETED, false),
                locallyUpdated = json.optBoolean(LOCALLY_UPDATED, false),
            )
        }

        fun createNewLocal() = Contact(
            id = SyncTarget.createLocalId(),
            firstName = "",
            lastName = "",
            title = "",
            locallyCreated = true,
            locallyUpdated = false,
            locallyDeleted = false
        )
    }
}

//class Contact private constructor(private val raw: JSONObject) {
//    val id: String = raw.optString(Constants.ID)
//    val firstName: String = raw.optString(KEY_FIRST_NAME)
//    val lastName: String = raw.optString(KEY_LAST_NAME)
//    val title: String = raw.optString(KEY_TITLE)
//    val locallyCreated: Boolean = raw.optBoolean(LOCALLY_CREATED, id.isBlank())
//    val locallyDeleted: Boolean = raw.optBoolean(LOCALLY_DELETED, false)
//    val locallyUpdated: Boolean = raw.optBoolean(LOCALLY_UPDATED, false)
//
//    val fullName = "$firstName $lastName"
//
//    fun mutate(
//        newFirstName: String = firstName,
//        newLastName: String = lastName,
//        newTitle: String = title,
//    ) = copy(
//        firstName = newFirstName,
//        lastName = newLastName,
//        title = newTitle,
//        locallyUpdated = true
//    )
//
//    fun getCopyMarkedForDeletion() = copy(locallyDeleted = true)
//
//    private fun copy(
//        firstName: String = this.firstName,
//        lastName: String = this.lastName,
//        title: String = this.title,
//        locallyDeleted: Boolean = this.locallyDeleted,
//        locallyUpdated: Boolean = this.locallyUpdated
//    ) = Contact(
//        JSONObject(raw.toString())
//            .putOpt(KEY_FIRST_NAME, firstName)
//            .putOpt(KEY_LAST_NAME, lastName)
//            .putOpt(KEY_TITLE, title)
//            .putOpt(LOCALLY_DELETED, locallyDeleted)
//            .putOpt(LOCALLY_UPDATED, locallyUpdated)
//    )
//
//    override fun equals(other: Any?): Boolean =
//        other === this || (other is Contact && other.rawObject == this.rawObject)
//
//    override fun hashCode(): Int = rawObject.hashCode()
//
//    companion object {
//        const val KEY_FIRST_NAME = "FirstName"
//        const val KEY_LAST_NAME = "LastName"
//        const val KEY_TITLE = "Title"
//
//        fun createNew(
//            firstName: String = "",
//            lastName: String = "",
//            title: String = ""
//        ) = Contact(
//            JSONObject()
//                .putOpt(KEY_FIRST_NAME, firstName)
//                .putOpt(KEY_LAST_NAME, lastName)
//                .putOpt(KEY_TITLE, title)
//                .putOpt(LOCALLY_CREATED, true)
//        )
//
//        @Throws(IllegalArgumentException::class)
//        fun fromRaw(json: JSONObject): Contact {
//            if (json.optBoolean(LOCALLY_CREATED, ) && !json.has(Constants.ID)) {
//                throw IllegalArgumentException("Raw JSON missing ID.  Contact cannot be created from existing raw JSON without an ID.  If creating a new instance of a Contact, use createNew(...)")
//            }
//            return Contact(json)
//        }
//    }
//}

//class ContactObject(raw: JSONObject) : SalesforceObject(raw) {
//
//    val firstName: String = raw.optString(KEY_FIRST_NAME)
//    val lastName: String = raw.optString(KEY_LAST_NAME)
//    val title: String = raw.optString(KEY_TITLE)
//
//    val isLocallyCreated: Boolean = raw.optBoolean(LOCALLY_CREATED)
//    val isLocallyDeleted: Boolean = raw.optBoolean(LOCALLY_DELETED)
//    val isLocallyUpdated: Boolean = raw.optBoolean(LOCALLY_UPDATED)
//
//    init {
//        name = "$firstName $lastName"
//    }
//
//    fun copy(
//        firstName: String = this.firstName,
//        lastName: String = this.lastName,
//        title: String = this.title,
//        isLocallyCreated: Boolean = this.isLocallyCreated,
//        isLocallyDeleted: Boolean = this.isLocallyDeleted,
//        isLocallyUpdated: Boolean = this.isLocallyUpdated
//    ): ContactObject = ContactObject(
//        rawData
//            .putOpt(KEY_FIRST_NAME, firstName)
//            .putOpt(KEY_LAST_NAME, lastName)
//            .putOpt(KEY_TITLE, title)
//            .putOpt(Constants.NAME, "$firstName $lastName")
//            .putOpt(LOCALLY_CREATED, isLocallyCreated)
//            .putOpt(LOCALLY_DELETED, isLocallyDeleted)
//            .putOpt(LOCALLY_UPDATED, isLocallyUpdated)
//    )
//
//    //        ContactObject(
////            id,
////            firstName,
////            lastName,
////            title
////        )
//    @TestOnly
//    constructor(
//        id: String,
//        firstName: String,
//        lastName: String,
//        title: String,
//        isLocallyCreated: Boolean,
//        isLocallyDeleted: Boolean,
//        isLocallyUpdated: Boolean
//    ) : this(
//        JSONObject()
//            .putOpt(Constants.ID, id)
//            .putOpt(KEY_FIRST_NAME, firstName)
//            .putOpt(KEY_LAST_NAME, lastName)
//            .putOpt(KEY_TITLE, title)
//            .putOpt(Constants.NAME, "$firstName $lastName")
//            .putOpt(LOCALLY_CREATED, isLocallyCreated)
//            .putOpt(LOCALLY_DELETED, isLocallyDeleted)
//            .putOpt(LOCALLY_UPDATED, isLocallyUpdated)
//            .putOpt(LOCAL, isLocallyCreated || isLocallyDeleted || isLocallyUpdated)
//    )
//
//    companion object {
//        const val KEY_FIRST_NAME = "FirstName"
//        const val KEY_LAST_NAME = "LastName"
//        const val KEY_TITLE = "Title"
//    }
//}

//fun ContactObject.toViewContactUiState(): ViewingContact = ViewingContact(
//    contactObject = this,
//    firstNameVm = toFirstNameVm(),
//    lastNameVm = toLastNameVm(),
//    titleVm = toTitleVm()
//)
//
//fun ContactObject.toEditContactUiState(originalContactObject: ContactObject = this) =
//    EditingContact(
//        originalContactObj = originalContactObject,
//        firstNameVm = toFirstNameVm(),
//        lastNameVm = toLastNameVm(),
//        titleVm = toTitleVm(),
//    )
