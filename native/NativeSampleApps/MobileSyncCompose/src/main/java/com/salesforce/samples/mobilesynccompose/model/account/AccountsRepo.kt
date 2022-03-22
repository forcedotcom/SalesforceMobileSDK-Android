package com.salesforce.samples.mobilesynccompose.model.account

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.samples.mobilesynccompose.core.repos.SObjectSyncableRepoBase
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SalesforceObjectDeserializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultAccountsRepo(
    account: UserAccount?, // TODO this shouldn't be nullable. The logic whether to instantiate this object should be moved higher up, but this is a quick fix to get things testable
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SObjectSyncableRepoBase<AccountObject>(
    account = account,
    ioDispatcher = ioDispatcher
) {
    override val deserializer: SalesforceObjectDeserializer<AccountObject> = AccountObject.Companion
    override val soupName: String = ACCOUNTS_SOUP_NAME
    override val syncDownName: String = SYNC_DOWN_ACCOUNTS
    override val syncUpName: String = SYNC_UP_ACCOUNTS
    override val TAG: String = "DefaultAccountsRepo"

    companion object {
        const val ACCOUNTS_SOUP_NAME = "accounts"
        const val SYNC_DOWN_ACCOUNTS = "syncDownAccounts"
        const val SYNC_UP_ACCOUNTS = "syncUpAccounts"
    }
}
