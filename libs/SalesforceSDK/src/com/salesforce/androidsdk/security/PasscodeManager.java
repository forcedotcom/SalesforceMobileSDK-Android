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
 */
public class PasscodeManager  {

	// UUID keys
	private static final String VKEY = "vkey";
	private static final String VSUFFIX = "vsuffix";
	private static final String VPREFIX = "vprefix";
	private static final String TAG = "PasscodeManager";
	
    // Default min passcode length
    public static final int MIN_PASSCODE_LENGTH = 4;

    // Key in preference for the passcode
    protected static final String KEY_PASSCODE ="passcode";

    // Private preference where we stored the passcode (hashed)
    protected static final String PASSCODE_PREF_NAME = "user";

    // Private preference where we stored the org settings.
    protected static final String MOBILE_POLICY_PREF = "mobile_policy";

    // Key in preference for the access timeout.
    protected static final String KEY_TIMEOUT = "access_timeout";

    // Key in preference for the passcode length.
    protected static final String KEY_PASSCODE_LENGTH = "passcode_length";

    // Key in preferences for actual passcode length known
    protected static final String KEY_PASSCODE_LENGTH_KNOWN = "passcode_length_known";

    // Key in preference for connect app biometric flag.
    protected static final String KEY_BIOMETRIC_ALLOWED = "biometric_allowed";

    // Key in preferences to indicate if the user has been prompted to use biometric.
    protected static final String KEY_BIOMETRIC_ENROLLMENT = "biometric_enrollment";

    // Key in preferences to indicate if the user has enabled biometric.
    protected static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";

    // Key in preference to indicate passcode change is required.
    protected static final String KEY_PASSCODE_CHANGE_REQUIRED= "passcode_change_required";

    // Key in preference for failed attempts
    protected static final String KEY_FAILED_ATTEMPTS = "failed_attempts";

    // Request code used to start passcode activity
    public static final int PASSCODE_REQUEST_CODE = 777;

    // Misc
    private HashConfig verificationHashConfig;
    private Handler handler;
    private long lastActivity;
    boolean locked;
    private int timeoutMs;
    private int passcodeLength;
    private boolean biometricAllowed;
    private boolean biometricEnrollmentShown;
    private boolean biometricEnabled;
    private boolean passcodeChangeRequired;
    private LockChecker lockChecker;
    private boolean passcodeLengthKnown;

    /**
     * Parameterized constructor.
     *
     * @param ctx Context.
     */
   public PasscodeManager(Context ctx) {
	   this(ctx, new HashConfig(SalesforceKeyGenerator.getUniqueId(VPREFIX),
                   SalesforceKeyGenerator.getUniqueId(VSUFFIX),
                   SalesforceKeyGenerator.getUniqueId(VKEY)));
   }

   public PasscodeManager(Context ctx, HashConfig verificationHashConfig) {
       this.passcodeLength = MIN_PASSCODE_LENGTH;
       this.lastActivity = now();
       this.verificationHashConfig = verificationHashConfig;
       readMobilePolicy(ctx);

       // Locked at app startup if you're authenticated.
       this.locked = true;
       lockChecker = new LockChecker(); 
   }

    /**
     * Returns true if a passcode change is required.
     *
     * @return true if passcode change required.
     */
    public boolean isPasscodeChangeRequired() {
        return passcodeChangeRequired;
    }


    /**
     * Set passcode change required flag to the passed value
     * @param ctx Context.
     * @param passcodeChangeRequired value to set passcode change required flag to
     */
    public void setPasscodeChangeRequired(Context ctx, boolean passcodeChangeRequired) {
        this.passcodeChangeRequired = passcodeChangeRequired;
        storeMobilePolicy(ctx);
    }

   /**
    * Returns the timeout value for the specified account.
    *
    * @param account UserAccount instance.
    * @return Timeout value.
    */
   	public int getTimeoutMsForOrg(UserAccount account) {
   		if (account == null) {
   			return 0;
   		}
   		final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final SharedPreferences sp = context.getSharedPreferences(MOBILE_POLICY_PREF
        		+ account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        return sp.getInt(KEY_TIMEOUT, 0);
   	}

    /**
     * Returns the minimum passcode length for the specified account.
     *
     * @param account UserAccount instance.
     * @return Minimum passcode length.
     */
    public int getPasscodeLengthForOrg(UserAccount account) {
    	if (account == null) {
    		return MIN_PASSCODE_LENGTH;
    	}
    	final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final SharedPreferences sp = context.getSharedPreferences(MOBILE_POLICY_PREF
        		+ account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        return sp.getInt(KEY_PASSCODE_LENGTH, MIN_PASSCODE_LENGTH);
    }

    /**
     * Stores the mobile policy for the specified account.
     *
     * @param account UserAccount instance.
     * @param timeout Timeout value, in ms.
     * @param passLen Minimum passcode length.
     * @param bioAllowed If biometric Unlock is Allowed by connected App
     */
    @SuppressLint("ApplySharedPref")
    public void storeMobilePolicyForOrg(UserAccount account, int timeout, int passLen, boolean bioAllowed) {
        if (account == null) {
            return;
        }
        final Context context = SalesforceSDKManager.getInstance().getAppContext();
        final SharedPreferences sp = context.getSharedPreferences(MOBILE_POLICY_PREF
                + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        final Editor e = sp.edit();
        e.putInt(KEY_TIMEOUT, timeout);
        e.putInt(KEY_PASSCODE_LENGTH, passLen);
        e.putBoolean(KEY_PASSCODE_LENGTH_KNOWN, passcodeLengthKnown);
        e.putBoolean(KEY_BIOMETRIC_ALLOWED, bioAllowed);
        e.commit();
    }

    /**
     * Stores the mobile policy in a private file.
     *
     * @param context Context.
     */
    @SuppressLint("ApplySharedPref")
    private void storeMobilePolicy(Context context) {

        // Context will be null only in test runs.
        if (context != null) {
            final SharedPreferences sp = context.getSharedPreferences(MOBILE_POLICY_PREF,
            		Context.MODE_PRIVATE);
            Editor e = sp.edit();
            e.putInt(KEY_TIMEOUT, timeoutMs);
            e.putInt(KEY_PASSCODE_LENGTH, passcodeLength);
            e.putBoolean(KEY_PASSCODE_LENGTH_KNOWN, passcodeLengthKnown);
            e.putBoolean(KEY_PASSCODE_CHANGE_REQUIRED, passcodeChangeRequired);
            e.putBoolean(KEY_BIOMETRIC_ALLOWED, biometricAllowed);
            e.putBoolean(KEY_BIOMETRIC_ENROLLMENT, biometricEnrollmentShown);
            e.putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled);
            e.commit();
        }
    }

    /**
     * Reads the mobile policy from a private file.
     *
     * @param context Context.
     */
    private void readMobilePolicy(Context context) {

        // Context will be null only in test runs.
        if (context != null) {
            final SharedPreferences sp = context.getSharedPreferences(MOBILE_POLICY_PREF,
            		Context.MODE_PRIVATE);
            if (!sp.contains(KEY_TIMEOUT) || !sp.contains(KEY_PASSCODE_LENGTH)) {
                timeoutMs = 0;
                passcodeLength = MIN_PASSCODE_LENGTH;
                passcodeChangeRequired = false;
                biometricAllowed = true;
                biometricEnrollmentShown = false;
                biometricEnabled = false;
                storeMobilePolicy(context);
                return;
            }
            timeoutMs = sp.getInt(KEY_TIMEOUT, 0);
            passcodeLength = sp.getInt(KEY_PASSCODE_LENGTH, MIN_PASSCODE_LENGTH);
            passcodeLengthKnown = sp.getBoolean(KEY_PASSCODE_LENGTH_KNOWN, false);
            passcodeChangeRequired = sp.getBoolean(KEY_PASSCODE_CHANGE_REQUIRED, false);
            biometricAllowed = sp.getBoolean(KEY_BIOMETRIC_ALLOWED, true);
            biometricEnrollmentShown = sp.getBoolean(KEY_BIOMETRIC_ENROLLMENT, false);
            biometricEnabled = sp.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        }
    }

    /**
     * Reset this passcode manager: delete stored passcode and reset fields to their starting value
     */
    @SuppressLint("ApplySharedPref")
    public void reset(Context ctx) {

    	// Deletes the underlying org policy files for all orgs.
    	final String sharedPrefPath = ctx.getApplicationInfo().dataDir + "/shared_prefs";
    	final File dir = new File(sharedPrefPath);
    	final PasscodeFileFilter fileFilter = new PasscodeFileFilter();
    	for (final File file : dir.listFiles()) {
    		if (file != null && fileFilter.accept(dir, file.getName())) {
    			file.delete();
    		}
    	}
    	lastActivity = now();
        locked = true;
        SharedPreferences sp = ctx.getSharedPreferences(PASSCODE_PREF_NAME,
        		Context.MODE_PRIVATE);
        Editor e = sp.edit();
        e.remove(KEY_PASSCODE);
        e.remove(KEY_FAILED_ATTEMPTS);
        e.remove(KEY_PASSCODE_LENGTH);
        e.remove(KEY_PASSCODE_LENGTH_KNOWN);
        e.remove(KEY_BIOMETRIC_ALLOWED);
        e.remove(KEY_BIOMETRIC_ENROLLMENT);
        e.remove(KEY_BIOMETRIC_ENABLED);
        e.commit();
        timeoutMs = 0;
        passcodeLength = MIN_PASSCODE_LENGTH;
        passcodeLengthKnown = false;
        passcodeChangeRequired = false;
        biometricAllowed = true;
        biometricEnrollmentShown = false;
        biometricEnabled = false;
        storeMobilePolicy(ctx);
        handler = null;
    }

    /**
     * Resets the passcode policies for a particular org upon logout.
     *
     * @param context Context.
     * @param account User account.
     */
    @SuppressLint("ApplySharedPref")
    public void reset(Context context, UserAccount account) {
    	if (account == null) {
    		return;
    	}
        final SharedPreferences sp = context.getSharedPreferences(MOBILE_POLICY_PREF
        		+ account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        final Editor e = sp.edit();
        e.clear();
        e.commit();
    }

    /**
     * Enable/disable passcode screen.
     */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            handler = new Handler();
            handler.postDelayed(lockChecker, 20 * 1000);
        } else {
            if (handler != null) {
                handler.removeCallbacks(lockChecker);
            }
            handler = null;
        }
    }

    /**
     * @return true if passcode manager is enabled.
     */
    public boolean isEnabled() {
        return (handler != null);
    }

    /**
     * @return the new failure count
     */
    public int addFailedPasscodeAttempt() {
        int failedAttempts = getFailedPasscodeAttempts() + 1;
        setFailedPasscodeAttempts(failedAttempts);
        return failedAttempts;
    }

    /**
     * @param ctx Context.
     * @param passcode Passcode.
     * @return true if passcode matches the one stored (hashed) in private preference
     */
    public boolean check(Context ctx, String passcode) {
        final SharedPreferences sp = ctx.getSharedPreferences(PASSCODE_PREF_NAME, Context.MODE_PRIVATE);
        String hashedPasscode = sp.getString(KEY_PASSCODE, null);
        hashedPasscode = removeNewLine(hashedPasscode);
        if (hashedPasscode != null) {
            return hashedPasscode.equals(hashForVerification(passcode));
        }

        /*
         * If the stored passcode hash is null, there is no passcode.
         */
        return true;
    }

    /**
     * Removes a trailing newline character from the hash.
     *
     * @param hash Hash.
     * @return Hash with trailing newline character removed.
     */
    private String removeNewLine(String hash) {
        int length = hash == null ? 0 : hash.length();
        if (length > 0 && hash.endsWith("\n")) {
            return hash.substring(0, length - 1);
        }
        return hash;
    }

    /**
     * Store the given passcode (hashed) in private preference
     * @param ctx Context.
     * @param passcode Passcode.
     */
    @SuppressLint("ApplySharedPref")
    public void store(Context ctx, String passcode) {
        SharedPreferences sp = ctx.getSharedPreferences(PASSCODE_PREF_NAME, Context.MODE_PRIVATE);
        Editor e = sp.edit();
        e.putString(KEY_PASSCODE, hashForVerification(passcode));
        e.putInt(KEY_PASSCODE_LENGTH, passcode.length());
        e.putBoolean(KEY_PASSCODE_LENGTH_KNOWN, true);
        e.putBoolean(KEY_BIOMETRIC_ENROLLMENT, biometricEnrollmentShown);
        e.putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled);
        e.commit();
        setPasscodeChangeRequired(ctx,false);
        setPasscodeLengthKnown(ctx, true);
    }

    /**
     * @param ctx Context.
     * @return true if passcode was already created
     */
    public boolean hasStoredPasscode(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PASSCODE_PREF_NAME, Context.MODE_PRIVATE);
        return sp.contains(KEY_PASSCODE);
    }

    /**
     * @return number of failed passcode attempts
     */
    public int getFailedPasscodeAttempts() {
        SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(PASSCODE_PREF_NAME, Context.MODE_PRIVATE);
        return sp.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    @SuppressLint("ApplySharedPref")
    private void setFailedPasscodeAttempts(int failedPasscodeAttempts) {
        SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(PASSCODE_PREF_NAME, Context.MODE_PRIVATE);
        Editor e = sp.edit();
        e.putInt(KEY_FAILED_ATTEMPTS, failedPasscodeAttempts);
        e.commit();
    }

    /**
     * @return true if locked
     */
    public boolean isLocked() {
        return timeoutMs > 0 && locked;
    }

    /**
     * @param ctx Context.
     */
    public void lock(Context ctx) {
        showLockActivity(ctx);
    }

    /**
     * @param frontActivity
     * @param registerActivity
     * @return
     */
    public boolean lockIfNeeded(Activity frontActivity, boolean registerActivity) {
        if (isEnabled() && (isLocked() || shouldLock() || passcodeChangeRequired)) {
            lock(frontActivity);
            return true;
        } else {
            if (registerActivity) updateLast();
            return false;
        }
    }

    /**
     * To be called by passcode protected activity when being paused
     */
    public void onPause(Activity ctx) {

        // Disables passcode manager.
        setEnabled(false);
    }

    /**
     * To be called by passcode protected activity when being resumed
     * When passcode screen is about to be shown, false is returned, the activity will be resumed once
     * the user has successfully enter her passcode
     *
     * @return true if the resume should be allowed to continue and false otherwise
     */
    public boolean onResume(Activity ctx) {

        // Enables passcode manager.
        setEnabled(true);

        // Brings up passcode screen if needed.
        lockIfNeeded(ctx, true);

        // If locked, do nothing - when the app gets unlocked we will be back here.
        return !isLocked();
    }

    /**
     * To be called by passcode protected activity whenever there is a user interaction
     */
    public void recordUserInteraction() {
        updateLast();
    }

    /**
     * Called when the access timeout for the org changes.
     *
     * @param newTimeout New access timeout value.
     */
    public void setTimeoutMs(int newTimeout) {

        // Access timeout hasn't changed.
        if (timeoutMs == newTimeout) {
            return;
        }

        /*
         * Either access timeout has changed from one non-zero value to another,
         * which doesn't alter the passcode situation, or the app goes from
         * no passcode to passcode, which will trigger the passcode creation flow.
         */
        if (timeoutMs == 0 || (timeoutMs > 0 && newTimeout > 0)) {

            // Updates timeout only if the new timeout is smaller than the old one.
            if (timeoutMs == 0 || timeoutMs > newTimeout) {
                timeoutMs = newTimeout;
            }
            storeMobilePolicy(SalesforceSDKManager.getInstance().getAppContext());
            return;
        }

        // Passcode to no passcode.
        timeoutMs = newTimeout;
        reset(SalesforceSDKManager.getInstance().getAppContext());
    }

    /**
     * The current inactivity timeout before the app locks, in milliseconds.
     *
     * @return the inactivity timeout
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * The exact length of the passcode if it is known.  It may be unknown on upgrade before first unlock.
     * Use {@link PasscodeManager#getPasscodeLengthKnown()} to check if return is exact length or org minimum.
     *
     * @return passcode length
     */
    public int getPasscodeLength() {
        return passcodeLength;
    }

    /**
     * Whether or not the exact passcode length is known.  It may be unknown on upgrade before first unlock.
     * Use {@link PasscodeManager#getPasscodeLength()} to get the length.
     *
     * @return true if the length is known
     */
    public boolean getPasscodeLengthKnown() {
        return passcodeLengthKnown;
    }

    /**
     * Whether or not the connected app allows biometric as an alternative to passcode.
     *
     * @return true if biometric is allowed
     */
    public boolean biometricAllowed() {
        return biometricAllowed;
    }

    /**
     * Whether or not the user has been shown the screen prompting them to enroll in biometric unlock.
     * @return true if the user has been prompted to enable biometric
     */
    public boolean biometricEnrollmentShown() {
        return biometricEnrollmentShown;
    }

    /**
     * Whether or not the user has enabled the ability to use biometric to bypass passcode.
     *
     * @return true if the user has enabled biometric
     */
    public boolean biometricEnabled() {
        return biometricEnabled;
    }

    /**
     * @param ctx Context.
     * @param passcodeLength The new passcode length to set.
     */
    public void setPasscodeLength(Context ctx, int passcodeLength) {
    	if (passcodeLength > this.passcodeLength) {
            if (hasStoredPasscode(ctx) && passcodeLengthKnown) {
                this.passcodeChangeRequired = true;
            }
            this.passcodeLength = passcodeLength;
    	}
        this.passcodeLengthKnown = true;
        storeMobilePolicy(ctx);
    }

    /**
     * This method can be used to force the stored or default passcode length to be trusted
     * upon upgrade if set to 'true'.
     *
     * @param ctx Context
     * @param lengthKnown Whether or not the passcode length is known.
     */
    public void setPasscodeLengthKnown(Context ctx, boolean lengthKnown) {
        this.passcodeLengthKnown = lengthKnown;
        storeMobilePolicy(ctx);
    }

    /**
     * Called when the biometric unlock requirement for the org changes.
     *
     * This API is intended for internal Salesforce only.  Setting this value in an app overrides the server's connected app policy and is not recommended.
     * Although setting {@code allowed} to false prevents users from being able to enroll in biometric unlock, the proper
     * way to prevent user enrollment is through the connected app.
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.mobile_sdk.meta/mobile_sdk/android_passcodes.htm">Using Passcodes</a>
     */
    public void setBiometricAllowed(Context ctx, boolean allowed) {
        if (this.biometricAllowed) {
            this.biometricAllowed = allowed;
        }
        storeMobilePolicy(ctx);
    }

    /**
     * By default biometric enrollment is only shown to the user once.
     *
     * @param shown set to true to show biometric prompt on next passcode unlock.
     */
    public void setBiometricEnrollmentShown(Context ctx, boolean shown) {
        biometricEnrollmentShown = shown;
        storeMobilePolicy(ctx);
    }

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
    public void setBiometricEnabled(Context ctx, boolean enabled) {
        biometricEnabled = enabled && biometricAllowed();
        storeMobilePolicy(ctx);
    }

    /**
     * @return true if time elapsed since the last user activity in the app exceeds the timeoutMs
     */
    public boolean shouldLock() {
        return timeoutMs > 0 && now() >= (lastActivity + timeoutMs);
    }

    public void showLockActivity(Context ctx) {
        locked = true;
        if (ctx == null) {
            ctx = SalesforceSDKManager.getInstance().getAppContext();
        }

        final Intent i = new Intent(ctx, SalesforceSDKManager.getInstance().getPasscodeActivity());
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (ctx == SalesforceSDKManager.getInstance().getAppContext()) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (ctx instanceof Activity) {
            ((Activity) ctx).startActivityForResult(i, PASSCODE_REQUEST_CODE);
        } else {
            ctx.startActivity(i);
        }
        EventsObservable.get().notifyEvent(EventType.AppLocked);
    }

    /**
     * This is used when unlocking via the fingerprint authentication.
     * The passcode hash isn't updated as the authentication is verified by the OS.
     */
    public void unlock() {
        EventBuilderHelper.createAndStoreEvent("passcodeUnlock", null, TAG, null);
        locked = false;
        setFailedPasscodeAttempts(0);
        updateLast();
        EventsObservable.get().notifyEvent(EventType.AppUnlocked);
    }

    protected long now() {
        return System.currentTimeMillis();
    }

    private void updateLast() {
        lastActivity = now();
    }

    public String hashForVerification(String passcode) {
    	return hash(passcode, verificationHashConfig);
    }

    private String hash(String passcode, HashConfig hashConfig) {
        return Encryptor.hash(hashConfig.prefix + passcode + hashConfig.suffix, hashConfig.key);
    }

    /**
     * Thread checking periodically to see how much has elapsed since the last recorded activity
     * When that elapsed time exceed timeoutMs, it locks the app
     */
    private class LockChecker implements Runnable {
        public void run() {
            try {
                if (!locked) {
                    lockIfNeeded(null, false);
                }
            } finally {
                if (handler != null) {
                    handler.postDelayed(this, 20 * 1000);
                }
            }
        }
    }

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

    /**
     * This class acts as a filter to identify only the relevant passcode files.
     *
     * @author bhariharan
     */
    private static class PasscodeFileFilter implements FilenameFilter {

    	private static final String PASSCODE_FILE_PREFIX = MOBILE_POLICY_PREF + "_";

		@Override
		public boolean accept(File dir, String filename) {
		    return (filename != null && filename.startsWith(PASSCODE_FILE_PREFIX));
		}
    }
}
