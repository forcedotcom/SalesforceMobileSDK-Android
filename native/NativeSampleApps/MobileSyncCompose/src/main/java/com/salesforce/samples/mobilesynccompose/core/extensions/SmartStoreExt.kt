package com.salesforce.samples.mobilesynccompose.core.extensions

import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONObject

@Throws(NoSuchElementException::class)
fun SmartStore.retrieveSingleById(
    soupName: String,
    idColName: String,
    id: String
): RetrievedSoupElt = synchronized(database) {
    beginTransaction()

    try {
        val soupId = lookupSoupEntryId(soupName, idColName, id)

        val result = if (soupId < 0) {
            throw NoSuchElementException("id=$id was not found in soup $soupName")
        } else {
            val results = retrieve(soupName, soupId)
            RetrievedSoupElt(
                results.first(),
                soupId
            ) // guaranteed to succeed at this point
        }

        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}

data class RetrievedSoupElt(val elt: JSONObject, val soupId: Long)
