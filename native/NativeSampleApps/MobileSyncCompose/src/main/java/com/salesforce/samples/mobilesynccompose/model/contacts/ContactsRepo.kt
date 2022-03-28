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
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepoBase
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SalesforceObjectDeserializer
import kotlinx.coroutines.*

/**
 * The Contacts Repository. It exposes upserting, deleting, and undeleting [Contact] model objects
 * into SmartStore, and it supports the MobileSync operations.
 */

/**
 * The default implementation of the [ContactsRepo].
 */
class DefaultContactsRepo(
    account: UserAccount?, // TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SObjectSyncableRepoBase<ContactObject>(
    account = account,
    ioDispatcher = ioDispatcher
) {

    override val TAG: String = "DefaultContactsRepo"
    override val deserializer: SalesforceObjectDeserializer<ContactObject> = ContactObject.Companion
    override val soupName: String = CONTACTS_SOUP_NAME
    override val syncDownName: String = SYNC_DOWN_CONTACTS
    override val syncUpName: String = SYNC_UP_CONTACTS

    companion object {
        const val CONTACTS_SOUP_NAME = "contacts"
        const val SYNC_DOWN_CONTACTS = "syncDownContacts"
        const val SYNC_UP_CONTACTS = "syncUpContacts"
    }
}
