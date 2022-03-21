package com.salesforce.samples.mobilesynccompose.core.extensions

import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.json.JSONObject

@Throws(ConcurrentModificationException::class, NoSuchElementException::class)
fun SmartStore.retrieveSingleById(
    soupName: String,
    idColName: String,
    id: String
): RetrievedSoupElt {
    val soupId = lookupSoupEntryId(soupName, idColName, id)
    if (soupId < 0) {
        throw NoSuchElementException("id=$id was not found in soup $soupName")
    } else {
        val results = retrieve(soupName, soupId)
        if (results.length() == 0) {
            throw ConcurrentModificationException("Found soupId=$soupId for id=$id, but retrieve operation for soupId=$soupId returned 0 results.")
        } else {
            return RetrievedSoupElt(results.first(), soupId) // guaranteed to succeed at this point
        }
    }
}

data class RetrievedSoupElt(val elt: JSONObject, val soupId: Long)
