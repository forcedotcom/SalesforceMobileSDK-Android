/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security

import android.content.Context
import android.content.SharedPreferences
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager

typealias Policy = Pair<Boolean, Int>

internal abstract class AppLockManager(
    private val policyKey: String,
    private val enabledKey: String,
    private val timeoutKey: String,
) {
    abstract fun shouldLock(): Boolean
    abstract fun lock()

    internal var lastBackgroundTimestamp: Long = 0

    fun onAppForegrounded() {
        if (shouldLock()) {
            lock()
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun getAccountPrefs(account: UserAccount): SharedPreferences {
        val ctx = SalesforceSDKManager.getInstance().appContext
        return ctx.getSharedPreferences(policyKey + account.userLevelFilenameSuffix, Context.MODE_PRIVATE)
    }

    fun getGlobalPrefs(): SharedPreferences {
        val ctx = SalesforceSDKManager.getInstance().appContext
        return ctx.getSharedPreferences(policyKey, Context.MODE_PRIVATE)
    }

    fun getPolicy(account: UserAccount): Policy {
        val accountPolicy = getAccountPrefs(account)
        return accountPolicy.getBoolean(enabledKey, false) to accountPolicy.getInt(timeoutKey, 0)
    }

    fun getGlobalPolicy(): Policy {
        val globalPolicy = getGlobalPrefs()
        return globalPolicy.getBoolean(enabledKey, false) to globalPolicy.getInt(timeoutKey, 0)
    }

    open fun storeMobilePolicy(account: UserAccount, enabled: Boolean, timeout: Int) {
        getAccountPrefs(account).edit()
            .putBoolean(enabledKey, enabled)
            .putInt(timeoutKey, timeout)
            .apply()
    }

    open fun cleanUp(account: UserAccount) {
        val editor = getAccountPrefs(account).edit()
        getAccountPrefs(account).all.keys.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
    }
}