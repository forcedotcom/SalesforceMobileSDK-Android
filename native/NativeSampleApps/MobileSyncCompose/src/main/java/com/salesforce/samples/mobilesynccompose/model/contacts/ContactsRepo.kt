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

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore.SOUP_ENTRY_ID
import com.salesforce.samples.mobilesynccompose.core.extensions.map
import com.salesforce.samples.mobilesynccompose.core.extensions.partitionBySuccess
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepoBase
import com.salesforce.samples.mobilesynccompose.core.repos.RepoOperationException
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepo
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SalesforceObjectDeserializer
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject.Companion.KEY_ACCOUNT_ID
import kotlinx.coroutines.*

/**
 * The Contacts Repository. It exposes upserting, deleting, and undeleting [Contact] model objects
 * into SmartStore, and it supports the MobileSync operations.
 */
interface ContactsRepo : SObjectSyncableRepo<ContactObject> {
    @Throws(RepoOperationException::class)
    suspend fun getContactsForAccountId(accountId: String): List<ContactObject>

    @Throws(RepoOperationException::class)
    suspend fun associateContactWithAccount(contactId: String, accountId: String): ContactObject
}

/**
 * The default implementation of the [ContactsRepo].
 */
class DefaultContactsRepo(
    account: UserAccount?, // TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SObjectSyncableRepoBase<ContactObject>(
    account = account,
    ioDispatcher = ioDispatcher
), ContactsRepo {

    override val TAG: String = "DefaultContactsRepo"
    override val deserializer: SalesforceObjectDeserializer<ContactObject> = ContactObject.Companion
    override val soupName: String = CONTACTS_SOUP_NAME
    override val syncDownName: String = SYNC_DOWN_CONTACTS
    override val syncUpName: String = SYNC_UP_CONTACTS

    @Throws(RepoOperationException::class)
    override suspend fun getContactsForAccountId(accountId: String): List<ContactObject> =
        withContext(ioDispatcher) {
            try {
                // TODO What to do with failed parses?
                store.query(
                    QuerySpec.buildExactQuerySpec(
                        soupName,
                        KEY_ACCOUNT_ID,
                        accountId,
                        SOUP_ENTRY_ID,
                        QuerySpec.Order.ascending,
                        10_000
                    ),
                    0
                )
                    .map { runCatching { ContactObject.coerceFromJsonOrThrow(it) } }
                    .partitionBySuccess()
                    .successes
            } catch (ex: Exception) {
                throw RepoOperationException.SmartStoreOperationFailed(
                    message = "Query for retrieving accounts failed.",
                    cause = ex
                )
            }
        }

    @Throws(RepoOperationException::class)
    override suspend fun associateContactWithAccount(
        contactId: String,
        accountId: String
    ): ContactObject = withContext(ioDispatcher) {
        // WIP should we add business logic for checking if the operation would re-parent?
        val accountRet = retrieveByIdOrThrowOperationException(accountId)

        ensureActive()

        val contactRet = retrieveByIdOrThrowOperationException(contactId)

        ensureActive()

        withContext(NonCancellable) {
            val newContactElt = try {
                store.update(
                    CONTACTS_SOUP_NAME,
                    contactRet.elt.put(KEY_ACCOUNT_ID, accountRet.elt.getString(Constants.ID)),
                    contactRet.soupId
                )
            } catch (ex: Exception) {
                throw RepoOperationException.SmartStoreOperationFailed(
                    message = "Failed to update the Contact in SmartStore with the account ID.",
                    cause = ex
                )
            }

            val contact = newContactElt.coerceUpdatedObjToModelOrCleanupAndThrow()
            replaceAllOrAddNewToObjectList(newValue = contact) { it.serverId == contact.serverId }
            contact
        }
    }

    companion object {
        const val CONTACTS_SOUP_NAME = "contacts"
        const val SYNC_DOWN_CONTACTS = "syncDownContacts"
        const val SYNC_UP_CONTACTS = "syncUpContacts"
    }
}
