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

import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.ScreenLockActivity
import com.salesforce.androidsdk.util.EventsObservable

internal class ScreenLockManager: AppLockManager(
    MOBILE_POLICY_PREF, SCREEN_LOCK, SCREEN_LOCK_TIMEOUT
), com.salesforce.androidsdk.security.interfaces.ScreenLockManager {
    // @Suppress is necessary due to a Kotlin bug:  https://youtrack.jetbrains.com/issue/KT-31420
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("isEnabled")
    override val enabled: Boolean
        get() = getGlobalPolicy().first

    override fun storeMobilePolicy(account: UserAccount, enabled: Boolean, timeout: Int) {
        super.storeMobilePolicy(account, enabled, timeout)

        if (enabled) {
            val globalPrefs = getGlobalPrefs()
            val currentTimeout = globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, 0)
            val globalPrefsEditor = globalPrefs.edit()
            globalPrefsEditor.putBoolean(SCREEN_LOCK, true)
            if (currentTimeout == 0 || timeout < currentTimeout) {
                globalPrefsEditor.putInt(SCREEN_LOCK_TIMEOUT, timeout)
            }
            globalPrefsEditor.apply()
            lock()
        }
    }

    override fun shouldLock(): Boolean {
        val elapsedTime = System.currentTimeMillis() - lastBackgroundTimestamp
        val (hasLock, timeout) = getGlobalPolicy()

        return hasLock && (elapsedTime > timeout)
    }

    override fun lock() {
        val ctx = SalesforceSDKManager.getInstance().appContext
        val intent = Intent(ctx, ScreenLockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        EventsObservable.get().notifyEvent(EventsObservable.EventType.AppLocked)
    }

    override fun cleanUp(account: UserAccount) {
        // Clean up and remove lock for account.
        super.cleanUp(account)

        // Determine if any other users still need Screen Lock.
        val accounts = SalesforceSDKManager.getInstance()
            .userAccountManager.authenticatedUsers
        var lowestTimeout = Int.MAX_VALUE

        if (!accounts.isNullOrEmpty()) {
            accounts.remove(account)
            accounts.forEach { remainingAccount ->
                if (remainingAccount != null) {
                    val accountPrefs = getAccountPrefs(remainingAccount)
                    if (accountPrefs.getBoolean(SCREEN_LOCK, false)) {
                        val timeout = accountPrefs.getInt(SCREEN_LOCK_TIMEOUT, Int.MAX_VALUE)
                        if (timeout < lowestTimeout) {
                            lowestTimeout = timeout
                        }
                    }
                }
            }
            if (lowestTimeout < Int.MAX_VALUE) {
                getGlobalPrefs().edit()
                    .putInt(SCREEN_LOCK_TIMEOUT, lowestTimeout)
                    .apply()
                return
            }
        }

        // If we have not returned, no other accounts require Screen Lock.
        reset()
    }

    fun reset() {
        getGlobalPrefs().edit()
            .remove(SCREEN_LOCK)
            .remove(SCREEN_LOCK_TIMEOUT)
            .apply()
    }

    companion object {
        const val MOBILE_POLICY_PREF = "mobile_policy"
        const val SCREEN_LOCK = "screen_lock"
        const val SCREEN_LOCK_TIMEOUT = "screen_lock_timeout"
    }
}