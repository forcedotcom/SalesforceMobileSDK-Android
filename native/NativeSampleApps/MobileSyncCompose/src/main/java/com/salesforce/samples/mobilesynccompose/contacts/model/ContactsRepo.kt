package com.salesforce.samples.mobilesynccompose.contacts.model

import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import kotlinx.coroutines.flow.Flow

interface ContactsRepo {
    val contactUpdates: Flow<List<TempContactObject>>
//    fun sync()
//    fun saveContact(...)
//    fun deleteContact(...)
}