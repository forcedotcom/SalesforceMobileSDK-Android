/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

import java.io.File;
import java.io.FilenameFilter;

/**
 * This class manages the inactivity timeout, and keeps track of if the UI should locked etc.
 *
 * @author wmathurin
 * @author bhariharan
 *
 * @deprecated Will be removed in Mobile SDK 10.0.  Use {@link ScreenLockManager} instead.
 */
public class PasscodeManager  {
	
    // Default min passcode length
    public static final int MIN_PASSCODE_LENGTH = 4;

    // Request code used to start passcode activity
    public static final int PASSCODE_REQUEST_CODE = 777;

    /**
     * Parameterized constructor.
     *
     * @param ctx Context.
     */
   public PasscodeManager(Context ctx) { }

   public PasscodeManager(Context ctx, HashConfig verificationHashConfig) { }

    /**
     * Returns true if a passcode change is required.
     *
     * @return true if passcode change required.
     */
    public boolean isPasscodeChangeRequired() { return false; }


    /**
     * Set passcode change required flag to the passed value
     * @param ctx Context.
     * @param passcodeChangeRequired value to set passcode change required flag to
     */
    public void setPasscodeChangeRequired(Context ctx, boolean passcodeChangeRequired) { }

   /**
    * Returns the timeout value for the specified account.
    *
    * @param account UserAccount instance.
    * @return Timeout value.
    */
   	public int getTimeoutMsForOrg(UserAccount account) { return 0; }

    /**
     * Returns the minimum passcode length for the specified account.
     *
     * @param account UserAccount instance.
     * @return Minimum passcode length.
     */
    public int getPasscodeLengthForOrg(UserAccount account) { return 0; }

    /**
     * Stores the mobile policy for the specified account.
     *
     * @param account UserAccount instance.
     * @param timeout Timeout value, in ms.
     * @param passLen Minimum passcode length.
     * @param bioAllowed If biometric Unlock is Allowed by connected App
     */
    @SuppressLint("ApplySharedPref")
    public void storeMobilePolicyForOrg(UserAccount account, int timeout, int passLen, boolean bioAllowed) { }

    /**
     * Reset this passcode manager: delete stored passcode and reset fields to their starting value
     */
    @SuppressLint("ApplySharedPref")
    public void reset(Context ctx) { }

    /**
     * Resets the passcode policies for a particular org upon logout.
     *
     * @param context Context.
     * @param account User account.
     */
    @SuppressLint("ApplySharedPref")
    public void reset(Context context, UserAccount account) { }

    /**
     * Enable/disable passcode screen.
     */
    public void setEnabled(boolean enabled) { }

    /**
     * @return true if passcode manager is enabled.
     */
    public boolean isEnabled() { return false; }

    /**
     * @return the new failure count
     */
    public int addFailedPasscodeAttempt() { return 0; }

    /**
     * @param ctx Context.
     * @param passcode Passcode.
     * @return true if passcode matches the one stored (hashed) in private preference
     */
    public boolean check(Context ctx, String passcode) { return true; }

    /**
     * Store the given passcode (hashed) in private preference
     * @param ctx Context.
     * @param passcode Passcode.
     */
    @SuppressLint("ApplySharedPref")
    public void store(Context ctx, String passcode) { }

    /**
     * @param ctx Context.
     * @return true if passcode was already created
     */
    public boolean hasStoredPasscode(Context ctx) { return false; }

    /**
     * @return number of failed passcode attempts
     */
    public int getFailedPasscodeAttempts() { return 0; }

    /**
     * @return true if locked
     */
    public boolean isLocked() { return false; }

    /**
     * @param ctx Context.
     */
    public void lock(Context ctx) { }

    /**
     * @param frontActivity
     * @param registerActivity
     * @return
     */
    public boolean lockIfNeeded(Activity frontActivity, boolean registerActivity) { return false; }

    /**
     * To be called by passcode protected activity when being paused
     */
    public void onPause(Activity ctx) { }

    /**
     * To be called by passcode protected activity when being resumed
     * When passcode screen is about to be shown, false is returned, the activity will be resumed once
     * the user has successfully enter her passcode
     *
     * @return true if the resume should be allowed to continue and false otherwise
     */
    public boolean onResume(Activity ctx) { return false; }

    /**
     * To be called by passcode protected activity whenever there is a user interaction
     */
    public void recordUserInteraction() { }

    /**
     * Called when the access timeout for the org changes.
     *
     * @param newTimeout New access timeout value.
     */
    public void setTimeoutMs(int newTimeout) { }

    /**
     * The current inactivity timeout before the app locks, in milliseconds.
     *
     * @return the inactivity timeout
     */
    public int getTimeoutMs() { return 0; }

    /**
     * The exact length of the passcode if it is known.  It may be unknown on upgrade before first unlock.
     * Use {@link PasscodeManager#getPasscodeLengthKnown()} to check if return is exact length or org minimum.
     *
     * @return passcode length
     */
    public int getPasscodeLength() { return 0; }

    /**
     * Whether or not the exact passcode length is known.  It may be unknown on upgrade before first unlock.
     * Use {@link PasscodeManager#getPasscodeLength()} to get the length.
     *
     * @return true if the length is known
     */
    public boolean getPasscodeLengthKnown() { return false; }

    /**
     * Whether or not the connected app allows biometric as an alternative to passcode.
     *
     * @return true if biometric is allowed
     */
    public boolean biometricAllowed() { return false; }

    /**
     * Whether or not the user has been shown the screen prompting them to enroll in biometric unlock.
     * @return true if the user has been prompted to enable biometric
     */
    public boolean biometricEnrollmentShown() { return false; }

    /**
     * Whether or not the user has enabled the ability to use biometric to bypass passcode.
     *
     * @return true if the user has enabled biometric
     */
    public boolean biometricEnabled() { return false; }

    /**
     * @param ctx Context.
     * @param passcodeLength The new passcode length to set.
     */
    public void setPasscodeLength(Context ctx, int passcodeLength) { }

    /**
     * This method can be used to force the stored or default passcode length to be trusted
     * upon upgrade if set to 'true'.
     *
     * @param ctx Context
     * @param lengthKnown Whether or not the passcode length is known.
     */
    public void setPasscodeLengthKnown(Context ctx, boolean lengthKnown) { }

    /**
     * Called when the biometric unlock requirement for the org changes.
     *
     * This API is intended for internal Salesforce only.  Setting this value in an app overrides the server's connected app policy and is not recommended.
     * Although setting {@code allowed} to false prevents users from being able to enroll in biometric unlock, the proper
     * way to prevent user enrollment is through the connected app.
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.mobile_sdk.meta/mobile_sdk/android_passcodes.htm">Using Passcodes</a>
     */
    public void setBiometricAllowed(Context ctx, boolean allowed) { }

    /**
     * By default biometric enrollment is only shown to the user once.
     *
     * @param shown set to true to show biometric prompt on next passcode unlock.
     */
    public void setBiometricEnrollmentShown(Context ctx, boolean shown) { }

    /**
     * Enables biometric input.
     *
     * This API is intended to let the end user toggle the use of biometric entry.  Setting this property to false does not prevent
     * the biometric enrollment screen from being shown to the user, nor does it prevent the user from enabling the
     * feature.
     *
     * To prevent users from enrolling in biometric, ask an administrator in the Salesforce org to configure the
     * connected app. For details, see <a href ="https://developer.salesforce.com/docs/atlas.en-us.mobile_sdk.meta/mobile_sdk/android_passcodes.htm">Using Passcodes</a>
     * in the <i>Mobile SDK Development Guide</i>.
     *
     * If you absolutely must disable biometric input at the app level see {@link PasscodeManager#setBiometricAllowed(Context, boolean)}.
     */
    public void setBiometricEnabled(Context ctx, boolean enabled) { }

    /**
     * @return true if time elapsed since the last user activity in the app exceeds the timeoutMs
     */
    public boolean shouldLock() { return false; }

    public void showLockActivity(Context ctx) { }

    /**
     * This is used when unlocking via the fingerprint authentication.
     * The passcode hash isn't updated as the authentication is verified by the OS.
     */
    public void unlock() { }

    public String hashForVerification(String passcode) { return ""; }

    /**
     * Key for hashing and salts to be preprended and appended to data to increase entropy.
     */
    public static class HashConfig {

        public final String prefix;
        public final String suffix;
        public final String key;

        public HashConfig(String prefix, String suffix, String key) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.key = key;
        }
    }
}
