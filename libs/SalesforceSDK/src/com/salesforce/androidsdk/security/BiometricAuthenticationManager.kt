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
import android.util.Log
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.MOBILE_POLICY_PREF
import com.salesforce.androidsdk.util.EventsObservable

internal class BiometricAuthenticationManager: AppLockManager(
    MOBILE_POLICY_PREF, BIO_AUTH, BIO_AUTH_TIMEOUT
) {
    var isLocked = true

    override fun shouldLock(): Boolean {
        val elapsedTime = System.currentTimeMillis() - lastBackgroundTimestamp
        val account = SalesforceSDKManager.getInstance().userAccountManager.currentAccount ?: return false
        val userAccount = SalesforceSDKManager.getInstance().userAccountManager.buildUserAccount(account)
        val (enabled, timeout) = getPolicy(userAccount)

        return enabled && (elapsedTime > timeout)
    }

    override fun lock() {
        isLocked = true
        val ctx = SalesforceSDKManager.getInstance().appContext
        val options = SalesforceSDKManager.getInstance().loginOptions.asBundle()
        val intent = Intent(ctx, SalesforceSDKManager.getInstance().loginActivityClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtras(options)
        ctx.startActivity(intent)
        EventsObservable.get().notifyEvent(EventsObservable.EventType.AppLocked)

        val currentUser = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        Log.i("bpage", "storing current refresh token: ${currentUser.refreshToken}")
        getAccountPrefs(currentUser, BIO_AUTH)
            .edit().putString("token", currentUser.refreshToken).apply()

    }

    fun biometricOptIn(account: UserAccount, optIn: Boolean = true) {
        getAccountPrefs(account, BIO_AUTH).edit()
            .putBoolean(USER_BIO_OPT_IN, optIn)
            .apply()
    }

    fun hasOptedIn(account: UserAccount): Boolean {
        return getAccountPrefs(account, BIO_AUTH).getBoolean(USER_BIO_OPT_IN, false)
    }

    fun isEnabled(): Boolean {
        val currentUser = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        return currentUser != null && getPolicy(currentUser).first
    }

    fun shouldAllowRefresh(): Boolean {
        return !(isEnabled() && isLocked)
    }

    fun enableNativeBiometricLoginButton(account: UserAccount, enabled: Boolean) {
        getAccountPrefs(account, BIO_AUTH)
            .edit().putBoolean(BIO_AUTH_NATIVE_BUTTON, enabled).apply()
    }

    fun isNativeBiometricLoginButtonEnabled(): Boolean {
        val currentUser = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        return currentUser != null && getAccountPrefs(currentUser, BIO_AUTH).getBoolean(BIO_AUTH_NATIVE_BUTTON, true)
    }

    fun presentOptInDialog() {

    }

    companion object {
        internal const val BIO_AUTH = "bio_auth"
        internal const val BIO_AUTH_TIMEOUT = "bio_auth_timeout"
        internal const val USER_BIO_OPT_IN = "user_bio_opt_in"
        internal const val BIO_AUTH_NATIVE_BUTTON = "bio_auth_native_button"
    }
}