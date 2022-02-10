package com.salesforce.samples.mobilesynccompose.model.contacts

import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget.*
import com.salesforce.androidsdk.mobilesync.util.Constants
import org.json.JSONObject

class Contact private constructor(raw: JSONObject) {
//    private constructor(
//        id: String,
//        firstName: String,
//        lastName: String,
//        title: String,
//        locallyCreated: Boolean,
//        locallyDeleted: Boolean,
//        locallyUpdated: Boolean,
//    ) : this(
//        JSONObject()
//            .putOpt(Constants.ID, id)
//            .putOpt(KEY_FIRST_NAME, firstName)
//            .putOpt(KEY_LAST_NAME, lastName)
//            .putOpt(KEY_TITLE, title)
//            .putOpt(Constants.NAME, "$firstName $lastName")
//            .putOpt(LOCALLY_CREATED, locallyCreated)
//            .putOpt(LOCALLY_DELETED, locallyDeleted)
//            .putOpt(LOCALLY_UPDATED, locallyUpdated)
//            .putOpt(LOCAL, locallyCreated || locallyDeleted || locallyUpdated)
//    )

    private val raw = JSONObject(raw.toString())

    val id: String = raw.optString(Constants.ID)
    val firstName: String = raw.optString(KEY_FIRST_NAME)
    val lastName: String = raw.optString(KEY_LAST_NAME)
    val title: String = raw.optString(KEY_TITLE)
    val fullName: String = raw.optString(Constants.NAME)
    private val locallyCreated: Boolean = raw.optBoolean(LOCALLY_CREATED, false)
    private val locallyDeleted: Boolean = raw.optBoolean(LOCALLY_DELETED, false)
    private val locallyUpdated: Boolean = raw.optBoolean(LOCALLY_UPDATED, false)
    private val local: Boolean =
        raw.optBoolean(LOCAL, locallyCreated || locallyDeleted || locallyUpdated)

//    val raw: JSONObject by lazy {
//        JSONObject()
//            .putOpt(Constants.ID, id)
//            .putOpt(KEY_FIRST_NAME, firstName)
//            .putOpt(KEY_LAST_NAME, lastName)
//            .putOpt(KEY_TITLE, title)
//            .putOpt(Constants.NAME, fullName)
//            .putOpt(LOCALLY_CREATED, locallyCreated)
//            .putOpt(LOCALLY_DELETED, locallyDeleted)
//            .putOpt(LOCALLY_UPDATED, locallyUpdated)
//            .putOpt(LOCAL, local)
//    }

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
        local: Boolean = this.local
    ) = Contact(
        raw
            .putOpt(Constants.ID, id)
            .putOpt(KEY_FIRST_NAME, firstName)
            .putOpt(KEY_LAST_NAME, lastName)
            .putOpt(KEY_TITLE, title)
            .putOpt(Constants.NAME, "$firstName $lastName")
            .putOpt(LOCALLY_CREATED, locallyCreated)
            .putOpt(LOCALLY_DELETED, locallyDeleted)
            .putOpt(LOCALLY_UPDATED, locallyUpdated)
            .putOpt(LOCAL, local)
    )

    fun markForDeletion() = copy(locallyDeleted = true)
    fun toJson() = JSONObject(raw.toString())

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

            val local = rawCopy.optBoolean(
                LOCAL, locallyCreated || locallyDeleted || locallyUpdated
            )

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
        ) = Contact(
            JSONObject()
                .putOpt(Constants.ID, SyncTarget.createLocalId())
                .putOpt(KEY_FIRST_NAME, firstName)
                .putOpt(KEY_LAST_NAME, lastName)
                .putOpt(KEY_TITLE, title)
                .putOpt(Constants.NAME, "$firstName $lastName") // TODO If first and last are empty, the NAME field will be " ", and I don't know if that is a problem.
                .putOpt(LOCALLY_CREATED, true)
                .putOpt(LOCALLY_DELETED, false)
                .putOpt(LOCALLY_UPDATED, false)
                .putOpt(LOCAL, true)
//            id = SyncTarget.createLocalId(),
//            firstName = "",
//            lastName = "",
//            title = "",
//            locallyCreated = true,
//            locallyUpdated = false,
//            locallyDeleted = false
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
