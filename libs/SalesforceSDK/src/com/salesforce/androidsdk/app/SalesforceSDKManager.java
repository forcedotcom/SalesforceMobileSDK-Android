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
package com.salesforce.androidsdk.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.salesforce.androidsdk.BuildConfig;
import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.idp.SPConfig;
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager;
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager;
import com.salesforce.androidsdk.config.AdminPermsManager;
import com.salesforce.androidsdk.config.AdminSettingsManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.RuntimeConfig;
import com.salesforce.androidsdk.push.PushMessaging;
import com.salesforce.androidsdk.push.PushNotificationInterface;
import com.salesforce.androidsdk.push.PushService;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.BiometricAuthenticationManager;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.security.ScreenLockManager;
import com.salesforce.androidsdk.ui.AccountSwitcherActivity;
import com.salesforce.androidsdk.ui.DevInfoActivity;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * This class serves as an interface to the various
 * functions of the Salesforce SDK. In order to use the SDK,
 * your app must first instantiate the singleton SalesforceSDKManager
 * object by calling the static init() method. After calling init(),
 * use the static getInstance() method to access the
 * singleton SalesforceSDKManager object.
 */
public class SalesforceSDKManager implements LifecycleObserver {

    /**
     * Current version of this SDK.
     */
    public static final String SDK_VERSION = "11.1.0.dev";

    /**
     * Intent action meant for instances of SalesforceSDKManager residing in other processes
     * to order them to clean up in-memory caches
     */
    private static final String CLEANUP_INTENT_ACTION = "com.salesforce.CLEANUP";

    // Key in broadcast for process id
    private static final String PROCESS_ID_KEY = "processId";

    // Unique per process id added to broadcast to prevent processing broadcast from own process
    private static final String PROCESS_ID = UUID.randomUUID().toString();

    // Key in broadcast for user account
    private static final String USER_ACCOUNT = "userAccount";

    /**
     * Intent action that specifies that logout was completed.
     */
    public static final String LOGOUT_COMPLETE_INTENT_ACTION = "com.salesforce.LOGOUT_COMPLETE";

    /**
     * Default app name.
     */
    private static final String DEFAULT_APP_DISPLAY_NAME = "Salesforce";
    private static final String INTERNAL_ENTROPY = "6cgs4f";
    private static final String TAG = "SalesforceSDKManager";
    protected static String AILTN_APP_NAME;

    /**
     * Instance of the SalesforceSDKManager to use for this process.
     */
    protected static SalesforceSDKManager INSTANCE;

    protected Context context;
    private LoginOptions loginOptions;
    private final Class<? extends Activity> mainActivityClass;
    private Class<? extends Activity> loginActivityClass = LoginActivity.class;
    private Class<? extends AccountSwitcherActivity> switcherActivityClass = AccountSwitcherActivity.class;
    private ScreenLockManager screenLockManager;

    private BiometricAuthenticationManager bioAuthManager;

    private LoginServerManager loginServerManager;
    private boolean isTestRun = false;
	private boolean isLoggingOut = false;
    private AdminSettingsManager adminSettingsManager;
    private AdminPermsManager adminPermsManager;
    private PushNotificationInterface pushNotificationInterface;
    private Class<? extends PushService> pushServiceType = PushService.class;
    private final String uid; // device id
    private final SortedSet<String> features;
    private List<String> additionalOauthKeys;
    private String loginBrand;
    private boolean browserLoginEnabled;
    private boolean shareBrowserSessionEnabled;

    /**
     * When true, Salesforce integration users will be prohibited from initial
     * authentication.  An error message will be displayed.  Defaults to false.
     */
    private boolean blockSalesforceIntegrationUser = false; // Default to false as Salesforce-authored apps are the primary audience for this option.  This functionality will eventually be provided by the backend.

    private boolean useWebServerAuthentication = true; // web server flow ON by default - but app can opt out by calling setUseWebServerAuthentication(false)

    private boolean useHybridAuthentication = true; // hybrid authentication flows ON by default - but app can opt out by calling setUseHybridAuthentication(false)
    private Theme theme =  Theme.SYSTEM_DEFAULT;
    private String appName;

    private IDPManager idpManager;        // only set if this app is an IDP

    private SPManager spManager;         // only set if this app is an SP

    /**
     * Available Mobile SDK style themes.
     */
    public enum Theme {
        LIGHT,
        DARK,
        SYSTEM_DEFAULT
    }

    /**
     * ScreenLockManager and BiometricAuthenticationManager object locks.
     */
    private final Object screenLockManagerLock = new Object();
    private final Object bioAuthManagerLock = new Object();

    /**
     * Dev support
     */
    private AlertDialog devActionsDialog;
    private Boolean isDevSupportEnabled; // NB: if null, it defaults to BuildConfig.DEBUG

    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of SalesforceSDKManager.
     */
    public static SalesforceSDKManager getInstance() {
    	if (INSTANCE != null) {
    		return INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call SalesforceSDKManager.init() first.");
    	}
    }

    /**
     *
     * @return true if SalesforceSDKManager has been initialized already
     */
    public static boolean hasInstance() {
        return INSTANCE != null;
    }

    /**
     * Sets the app name to be used by the analytics framework.
     *
     * @param appName App name.
     */
    public static void setAiltnAppName(String appName) {
        if (!TextUtils.isEmpty(appName)) {
            AILTN_APP_NAME = appName;
        }
    }

    /**
     * Returns the app name being used by the analytics framework.
     *
     * @return App name.
     */
    public static String getAiltnAppName() {
        return AILTN_APP_NAME;
    }

    /**
     * Protected constructor.
     *
     * @param context Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected SalesforceSDKManager(Context context, Class<? extends Activity> mainActivity,
                                   Class<? extends Activity> loginActivity) {
        this.uid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.context = context;
        this.mainActivityClass = mainActivity;
        if (loginActivity != null) {
            this.loginActivityClass = loginActivity;
        }
        this.features = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

        /*
         * Checks if an analytics app name has already been set by the app.
         * If not, fetches the default app name to be used and sets it.
         */
        final String currentAiltnAppName = getAiltnAppName();
        if (TextUtils.isEmpty(currentAiltnAppName)) {
            String ailtnAppName = null;
            try {
                final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                ailtnAppName = context.getString(packageInfo.applicationInfo.labelRes);
            } catch (NameNotFoundException e) {
                SalesforceSDKLogger.e(TAG, "Package not found", e);
            }
            if (!TextUtils.isEmpty(ailtnAppName)) {
                setAiltnAppName(ailtnAppName);
            }
        }

        // If your app runs in multiple processes, all the SalesforceSDKManager need to run cleanup during a logout
        final CleanupReceiver cleanupReceiver = new CleanupReceiver();
        context.registerReceiver(cleanupReceiver, new IntentFilter(SalesforceSDKManager.CLEANUP_INTENT_ACTION));
        new Handler(Looper.getMainLooper()).post(() -> {
            ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        });
    }

    /**
     * Returns the class for the main activity.
     *
     * @return The class for the main activity.
     */
    public Class<? extends Activity> getMainActivityClass() {
    	return mainActivityClass;
    }

	/**
     * Returns the class for the account switcher activity.
     *
     * @return The class for the account switcher activity.
     */
    public Class<? extends AccountSwitcherActivity> getAccountSwitcherActivityClass() {
    	return switcherActivityClass;
    }

    /**
     * Returns the class for the account switcher activity.
     *
     * @return The class for the account switcher activity.
     */
    public void setAccountSwitcherActivityClass(Class<? extends AccountSwitcherActivity> activity) {
    	if (activity != null) {
        	switcherActivityClass = activity;
    	}
    }

    /**
     * Returns the class of the activity used to perform the login process and create the account.
     *
     * @return the class of the activity used to perform the login process and create the account.
     */
    public Class<? extends Activity> getLoginActivityClass() {
    	return loginActivityClass;
    }

    /**
     * Returns unique device ID.
     *
     * @return Device ID.
     */
    public String getDeviceId() {
        return uid;
    }

	/**
     * Returns login options associated with the app.
     *
	 * @return LoginOptions instance.
	 */
	public LoginOptions getLoginOptions() {
		return getLoginOptions(null, null);
	}

    public LoginOptions getLoginOptions(String jwt, String url) {
        if (loginOptions == null) {
            final BootConfig config = BootConfig.getBootConfig(context);
            if (TextUtils.isEmpty(jwt)) {
                loginOptions = new LoginOptions(url, config.getOauthRedirectURI(),
                        config.getRemoteAccessConsumerKey(), config.getOauthScopes());
            } else {
                loginOptions = new LoginOptions(url, config.getOauthRedirectURI(),
                        config.getRemoteAccessConsumerKey(), config.getOauthScopes(), jwt);
            }
        } else {
            loginOptions.setJwt(jwt);
            loginOptions.setUrl(url);
        }
        return loginOptions;
    }

    private static void init(Context context, Class<? extends Activity> mainActivity,
                             Class<? extends Activity> loginActivity) {
    	if (INSTANCE == null) {
    		INSTANCE = new SalesforceSDKManager(context, mainActivity, loginActivity);
    	}
    	initInternal(context);
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
    }

	/**
	 * For internal use by Salesforce Mobile SDK or by subclasses
	 * of SalesforceSDKManager. Initializes required components.
	 *
	 * @param context Application context.
	 */
    public static void initInternal(Context context) {

        // Upgrades to the latest version.
        SalesforceSDKUpgradeManager.getInstance().upgrade();

        // Initializes the HTTP client.
        HttpAccess.init(context);

        // Enables IDP login flow if it's set through MDM.
        final RuntimeConfig runtimeConfig = RuntimeConfig.getRuntimeConfig(context);
        final String idpAppPackageName = runtimeConfig.getString(RuntimeConfig.ConfigKey.IDPAppPackageName);
        if (!TextUtils.isEmpty(idpAppPackageName)) {
            INSTANCE.setIDPAppPackageName(idpAppPackageName);
        }
    }

    /**
     * Initializes required components. Native apps must call one overload of
     * this method before using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     */
    public static void initNative(Context context, Class<? extends Activity> mainActivity) {
        SalesforceSDKManager.init(context, mainActivity, LoginActivity.class);
    }

    /**
     * Initializes required components. Native apps must call one overload of
     * this method before using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    public static void initNative(Context context, Class<? extends Activity> mainActivity,
                                  Class<? extends Activity> loginActivity) {
        SalesforceSDKManager.init(context, mainActivity, loginActivity);
    }

    /**
     * Indicates whether the SDK should automatically log out when the
     * access token is revoked. If you override this method to return
     * false, your app is responsible for handling its own cleanup when the
     * access token is revoked.
     *
     * @return True if the SDK should automatically logout.
     */
    public boolean shouldLogoutWhenTokenRevoked() {
    	return true;
    }

    /**
     * Returns the application context.
     *
     * @return Application context.
     */
    public Context getAppContext() {
    	return context;
    }

    /**
     * Returns the login server manager associated with SalesforceSDKManager.
     *
     * @return LoginServerManager instance.
     */
    public synchronized LoginServerManager getLoginServerManager() {
        if (loginServerManager == null) {
        	loginServerManager = new LoginServerManager(context);
        }
        return loginServerManager;
    }

    /**
     * Sets a receiver that handles received push notifications.
     *
     * @param pnInterface Implementation of PushNotificationInterface.
     */
    public synchronized void setPushNotificationReceiver(PushNotificationInterface pnInterface) {
    	pushNotificationInterface = pnInterface;
    }

    /**
     * Returns the receiver that's configured to handle incoming push notifications.
     *
     * @return Configured implementation of PushNotificationInterface.
     */
    public synchronized PushNotificationInterface getPushNotificationReceiver() {
    	return pushNotificationInterface;
    }

    /**
     * Sets the class fulfilling push notification registration features.  A
     * default implementation is provided in {@link PushService}.  Setting this
     * property to a subclass will inject the specified class instead.
     * <p>
     * Note, the specified class no longer needs to be specified in the Android
     * manifest as a service.
     *
     * @param pushServiceType A class extending {@link PushService}
     */
    @SuppressWarnings("unused")
    public synchronized void setPushServiceType(
            Class<? extends PushService> pushServiceType
    ) {
        this.pushServiceType = pushServiceType;
    }

    /**
     *  Returns the class that will be used as a push service.
     *
     *  @return the service class
     */
    public synchronized Class<? extends PushService> getPushServiceType() {
        return pushServiceType;
    }

    /**
     * Returns the ScreenLock manager that's associated with SalesforceSDKManager.
     *
     * @return ScreenLockManager instance.
     */
    public com.salesforce.androidsdk.security.interfaces.ScreenLockManager getScreenLockManager() {
        synchronized (screenLockManagerLock) {
            if (screenLockManager == null) {
                screenLockManager = new ScreenLockManager();
            }
            return screenLockManager;
        }
    }

    /**
     * Returns the Biometric Authentication manager that's associated wth SalesforceSDKManager.
     *
     * @return BiometricAuthenticationManager instance.
     */
    public com.salesforce.androidsdk.security.interfaces.BiometricAuthenticationManager getBiometricAuthenticationManager() {
        synchronized (bioAuthManagerLock) {
            if (bioAuthManager == null) {
                bioAuthManager = new BiometricAuthenticationManager();
            }
            return bioAuthManager;
        }
    }


    /**
     * Returns the user account manager that's associated with SalesforceSDKManager.
     *
     * @return UserAccountManager instance.
     */
    public UserAccountManager getUserAccountManager() {
    	return UserAccountManager.getInstance();
    }

    /**
     * Returns the administrator settings manager that's associated with SalesforceSDKManager.
     *
     * @return AdminSettingsManager instance.
     */
    public synchronized AdminSettingsManager getAdminSettingsManager() {
    	if (adminSettingsManager == null) {
    		adminSettingsManager = new AdminSettingsManager();
    	}
    	return adminSettingsManager;
    }

    /**
     * Returns the administrator permissions manager that's associated with SalesforceSDKManager.
     *
     * @return AdminPermsManager instance.
     */
    public synchronized AdminPermsManager getAdminPermsManager() {
        if (adminPermsManager == null) {
            adminPermsManager = new AdminPermsManager();
        }
        return adminPermsManager;
    }

    /**
     * Returns the login brand parameter.
     *
     * @return Login brand, if configured.
     */
    public String getLoginBrand() {
    	return loginBrand;
    }

    /**
     * Sets the login brand. In the following example, "<brand>" should be set here.
     * https://community.force.com/services/oauth2/authorize/<brand>?response_type=code&...
     * Note: This API might change in the future.
     *
     * @param loginBrand Login brand param.
     */
    public synchronized void setLoginBrand(String loginBrand) {
        this.loginBrand = loginBrand;
    }

    /**
     * Returns whether browser based login should be used instead of WebView.
     *
     * @return True - if Chrome should be used for login, False - otherwise.
     */
    public boolean isBrowserLoginEnabled() {
        return browserLoginEnabled;
    }


    /**
     * Determines if Salesforce integration users will be prohibited from
     * initial authentication.
     *
     * @return True indicates authentication is blocked and false indicates
     * authentication is allowed for Salesforce integration users
     */
    public boolean shouldBlockSalesforceIntegrationUser() {
        return blockSalesforceIntegrationUser;
    }

    /**
     * Sets authentication ability for Salesforce integration users.  When true,
     * Salesforce integration users will be prohibited from initial
     * authentication and receive an error message.  Defaults to false.
     *
     * @param value True blocks authentication or false allows authentication
     *              for Salesforce integration users
     */
    public synchronized void setBlockSalesforceIntegrationUser(boolean value) {
        blockSalesforceIntegrationUser = value;
    }

    /**
     * Returns whether web server flow should be used when logging through the WebView
     *
     * @return True - if web server flow should be used, False - otherwise.
     */
    public boolean shouldUseWebServerAuthentication() {
        return useWebServerAuthentication;
    }

    /**
     * Sets whether web server flow should be used when logging through the WebView
     * @param useWebServerAuthentication
     */
    public synchronized void setUseWebServerAuthentication(boolean useWebServerAuthentication) {
        this.useWebServerAuthentication = useWebServerAuthentication;
    }

    /**
     * Returns whether share browser session is enabled.
     *
     * @return True - if share browser session is enabled, False - otherwise.
     */
    public boolean isShareBrowserSessionEnabled() {
        return shareBrowserSessionEnabled;
    }

    /**
     * Sets whether browser based login should be used instead of WebView. This should NOT be used
     * directly by apps, this is meant for internal use, based on the value configured on the server.
     *
     * @param browserLoginEnabled True - if Chrome should be used for login, False - otherwise.
     */
    public synchronized void setBrowserLoginEnabled(boolean browserLoginEnabled, boolean shareBrowserSessionEnabled) {
        this.browserLoginEnabled = browserLoginEnabled;
        this.shareBrowserSessionEnabled = shareBrowserSessionEnabled;
        if (browserLoginEnabled) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_BROWSER_LOGIN);
        } else {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(Features.FEATURE_BROWSER_LOGIN);
        }
    }

    /**
     * Returns whether hybrid authentication flow should be used
     *
     * @return True - if hybrid authentication flow should be used, False - otherwise.
     */
    public boolean shouldUseHybridAuthentication() {
        return useHybridAuthentication;
    }

    /**
     * Sets whether hybrid authentication flow should be used
     * @param useHybridAuthentication
     */
    public synchronized void setUseHybridAuthentication(boolean useHybridAuthentication) {
        this.useHybridAuthentication = useHybridAuthentication;
    }

    /**
     * Returns whether the IDP login flow is enabled.
     *
     * @return True - if IDP login flow is enabled, False - otherwise.
     */
    public boolean isIDPLoginFlowEnabled() {
        return spManager != null;
    }

    /**
     * @return True - if this application is configured as a Identity Provider
     */
    private boolean isIdentityProvider() {
        return idpManager != null;
    }

    /**
     * Returns the SP manager
     * Only defined if setIdpAppPackageName() was first called
     */
    public SPManager getSPManager() {
        return spManager;
    }

    /**
     * Sets the IDP package name for this app.
     * As a result this application gets a SPManager and can be used as SP.
     */
    public void setIDPAppPackageName(String idpAppPackageName) {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_APP_IS_SP);
        spManager = new com.salesforce.androidsdk.auth.idp.SPManager(idpAppPackageName);
    }

    /**
     * Returns the IDP manager
     * Only defined if setAllowedSPApps() was first called
     */
    public IDPManager getIDPManager() {
        return idpManager;
    }

    /**
     * Sets the allowed SP apps for this app.
     * As a result this application gets a IDPManager and can be used as IDP.
     */
    public void setAllowedSPApps(List<SPConfig> allowedSPApps) {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_APP_IS_IDP);
        idpManager = new com.salesforce.androidsdk.auth.idp.IDPManager(allowedSPApps);
    }


    /**
     * Returns the app display name used by the passcode dialog.
     *
     * @return App display string.
     */
    public String getAppDisplayString() {
        return DEFAULT_APP_DISPLAY_NAME;
    }

    /**
     * Returns the name of the application (as defined in AndroidManifest.xml).
     *
     * @return The name of the application.
     */
    public String getApplicationName() {
        return context.getPackageManager().getApplicationLabel(context.getApplicationInfo()).toString();
    }

    /**
     * Checks if network connectivity exists.
     *
     * @return True if a network connection is available.
     */
    public boolean hasNetwork() {
    	return HttpAccess.DEFAULT.hasNetwork();
    }

    /**
     * Adds an additional set of OAuth keys to fetch and store from the token endpoint.
     *
     * @param additionalOauthKeys List of additional OAuth keys.
     */
    public void setAdditionalOauthKeys(List<String> additionalOauthKeys) {
        this.additionalOauthKeys = additionalOauthKeys;
    }

    /**
     * Returns the list of additional OAuth keys set for this application.
     *
     * @return List of additional OAuth keys.
     */
    public List<String> getAdditionalOauthKeys() {
        return additionalOauthKeys;
    }

    /**
     * Cleans up cached credentials and data.
     *
     * @param frontActivity Front activity.
     * @param account Account.
     * @param shouldDismissActivity Dismisses current activity if true, does nothing otherwise.
     */
    private void cleanUp(Activity frontActivity, Account account, boolean shouldDismissActivity) {
        final UserAccount userAccount = UserAccountManager.getInstance().buildUserAccount(account);

        // Clean up in this process
        cleanUp(userAccount);

        // Have SalesforceSDKManager living in separate processes also clean up
        sendCleanupIntent(userAccount);

        final List<UserAccount> users = getUserAccountManager().getAuthenticatedUsers();

        // Finishes front activity if specified, if this is the last account.
        if (shouldDismissActivity && frontActivity != null && (users == null || users.size() <= 1)) {
            frontActivity.finish();
        }

        /*
         * Checks how many accounts are left that are authenticated. If only one
         * account is left, this is the account that is being removed. In this
         * case, we can safely reset screen lock manager, admin prefs, and encryption keys.
         * Otherwise, we don't reset screen lock manager and admin prefs since
         * there might be other accounts on that same org, and these policies
         * are stored at the org level.
         */
        if (users == null || users.size() <= 1) {
            getAdminSettingsManager().resetAll();
            getAdminPermsManager().resetAll();
            adminSettingsManager = null;
            adminPermsManager = null;

            ((ScreenLockManager) getScreenLockManager()).reset();
            screenLockManager = null;
            bioAuthManager = null;
        }
    }

    /**
     * Clean up cached data
     *
     * @param userAccount
     */
    protected void cleanUp(UserAccount userAccount) {
        SalesforceAnalyticsManager.reset(userAccount);
        RestClient.clearCaches(userAccount);
        UserAccountManager.getInstance().clearCachedCurrentUser();
        ((ScreenLockManager) getScreenLockManager()).cleanUp(userAccount);
        ((BiometricAuthenticationManager) getBiometricAuthenticationManager())
                .cleanUp(userAccount);
    }

    /**
     * Starts login flow if user account has been removed.
     */
    protected void startLoginPage() {

        // Clears cookies.
        CookieManager.getInstance().removeAllCookies(null);

        // Restarts the application.
        final Intent i = new Intent(context, getMainActivityClass());
        i.setPackage(getAppContext().getPackageName());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

	/**
     * Starts account switcher activity if an account has been removed.
     */
    public void startSwitcherActivityIfRequired() {

        // Clears cookies.
        CookieManager.getInstance().removeAllCookies(null);

        /*
         * If the number of accounts remaining is 0, shows the login page.
         * If the number of accounts remaining is 1, switches to that user
         * automatically. If there is more than 1 account logged in, shows
         * the account switcher screen, so that the user can pick which
         * account to switch to.
         */
        final UserAccountManager userAccMgr = getUserAccountManager();
        final List<UserAccount> accounts = userAccMgr.getAuthenticatedUsers();
        if (accounts == null || accounts.size() == 0) {
        	startLoginPage();
        } else if (accounts.size() == 1) {
        	userAccMgr.switchToUser(accounts.get(0), UserAccountManager.USER_SWITCH_TYPE_LOGOUT, null);
        } else {
        	final Intent i = new Intent(context, switcherActivityClass);
    		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		context.startActivity(i);
        }
	}

    private synchronized void unregisterPush(final ClientManager clientMgr, final boolean showLoginPage,
    		final String refreshToken, final String loginServer,
            final Account account, final Activity frontActivity, boolean isLastAccount) {
        final IntentFilter intentFilter = new IntentFilter(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT);
        final BroadcastReceiver pushUnregisterReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT.equals(intent.getAction())) {
                    postPushUnregister(this, clientMgr, showLoginPage,
                    		refreshToken, loginServer, account, frontActivity);
                }
            }
        };
        context.registerReceiver(pushUnregisterReceiver, intentFilter);

        // Unregisters from notifications on logout.
		final UserAccount userAcc = getUserAccountManager().buildUserAccount(account);
        PushMessaging.unregister(context, userAcc, isLastAccount);
    }

    private void postPushUnregister(BroadcastReceiver pushReceiver,
    		final ClientManager clientMgr, final boolean showLoginPage,
    		final String refreshToken, final String loginServer,
            final Account account, Activity frontActivity) {
        try {
            context.unregisterReceiver(pushReceiver);
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Exception occurred while un-registering", e);
        }
        removeAccount(clientMgr, showLoginPage, refreshToken, loginServer, account, frontActivity);
    }

    /**
     * Destroys the stored authentication credentials (removes the account).
     *
     * @param frontActivity Front activity.
     */
    public void logout(Activity frontActivity) {
        logout(frontActivity, true);
    }

    /**
     * Destroys the stored authentication credentials (removes the account).
     *
     * @param account Account.
     * @param frontActivity Front activity.
     */
    public void logout(Account account, Activity frontActivity) {
        logout(account, frontActivity, true);
    }

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * @param frontActivity Front activity.
     * @param showLoginPage If true, displays the login page after removing the account.
     */
    public void logout(Activity frontActivity, final boolean showLoginPage) {
        final ClientManager clientMgr = new ClientManager(context, getAccountType(),
        		null, shouldLogoutWhenTokenRevoked());
		final Account account = clientMgr.getAccount();
		logout(account, frontActivity, showLoginPage);
    }

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * @param account Account.
     * @param frontActivity Front activity.
     * @param showLoginPage If true, displays the login page after removing the account.
     */
    public void logout(Account account, Activity frontActivity, final boolean showLoginPage) {
        EventBuilderHelper.createAndStoreEvent("userLogout", null, TAG, null);
        final ClientManager clientMgr = new ClientManager(context, getAccountType(),
        		null, shouldLogoutWhenTokenRevoked());
        isLoggingOut = true;
		final AccountManager mgr = AccountManager.get(context);
		String refreshToken = null;
		String loginServer = null;
		if (account != null) {
		    final String encryptionKey = SalesforceSDKManager.getEncryptionKey();
			refreshToken = SalesforceSDKManager.decrypt(mgr.getPassword(account), encryptionKey);
	        loginServer = SalesforceSDKManager.decrypt(mgr.getUserData(account,
	        		AuthenticatorService.KEY_INSTANCE_URL), encryptionKey);
		}

		/*
		 * Makes a call to un-register from push notifications, only
		 * if the refresh token is available.
		 */
		final UserAccount userAcc = getUserAccountManager().buildUserAccount(account);
		int numAccounts = mgr.getAccountsByType(getAccountType()).length;
    	if (PushMessaging.isRegistered(context, userAcc) && refreshToken != null) {
    		unregisterPush(clientMgr, showLoginPage, refreshToken,
    				loginServer, account, frontActivity, (numAccounts == 1));
    	} else {
    		removeAccount(clientMgr, showLoginPage, refreshToken,
                    loginServer, account, frontActivity);
    	}
    }

    /**
     * Removes the account upon logout.
     *
     * @param clientMgr ClientManager instance.
     * @param showLoginPage If true, displays the login page after removing the account.
     * @param refreshToken Refresh token.
     * @param loginServer Login server.
     * @param account Account instance.
     * @param frontActivity Front activity.
     */
    private void removeAccount(ClientManager clientMgr, final boolean showLoginPage,
    		String refreshToken, String loginServer,
    		Account account, Activity frontActivity) {

    	cleanUp(frontActivity, account, showLoginPage);
        clientMgr.removeAccount(account);
        isLoggingOut = false;
        notifyLogoutComplete(showLoginPage);

    	// Revokes the existing refresh token.
        if (shouldLogoutWhenTokenRevoked() && refreshToken != null) {
        	new RevokeTokenTask(refreshToken, loginServer).execute();
        }
    }

    private void notifyLogoutComplete(boolean showLoginPage) {
    	EventsObservable.get().notifyEvent(EventType.LogoutComplete);
        sendLogoutCompleteIntent();
		if (showLoginPage) {
			startSwitcherActivityIfRequired();
		}
    }

    /**
     * Returns a user agent string based on the Mobile SDK version. The user agent takes the following form:
     * SalesforceMobileSDK/{salesforceSDK version} android/{android OS version} appName/appVersion {Native|Hybrid} uid_{device id}
     *
     * @return The user agent string to use for all requests.
     */
    public final String getUserAgent() {
    	return getUserAgent("");
    }

    /**
     * Provides the app name to use in {@link #getUserAgent(String)}. This string must only contain printable ASCII characters.
     * By default, the display name under {@link android.content.pm.ApplicationInfo#labelRes} will be used.
     *
     * @return The app name to use when constructing the user agent string
     */
    public String provideAppName() {
       try {
            if (appName == null) {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                appName = context.getString(packageInfo.applicationInfo.labelRes);
            }
            return appName;
        } catch (NameNotFoundException | Resources.NotFoundException e) {
            SalesforceSDKLogger.w(TAG, "Package info could not be retrieved", e);
            return "";
        }
    }

    /**
     * Returns a user agent string based on the Mobile SDK version. The user agent takes the following form:
     * SalesforceMobileSDK/{salesforceSDK version} android/{android OS version} {provideAppName()}/appVersion {Native|Hybrid} uid_{device id}
     *
     * @param qualifier Qualifier.
     * @return The user agent string to use for all requests.
     */
    public String getUserAgent(String qualifier) {
        final String appName = provideAppName();
        final String appTypeWithQualifier = getAppType() + qualifier;
        return String.format("SalesforceMobileSDK/%s android mobile/%s (%s) %s/%s %s uid_%s ftr_%s SecurityPatch/%s",
                SDK_VERSION, Build.VERSION.RELEASE, Build.MODEL, appName, getAppVersion(),
                appTypeWithQualifier, uid, TextUtils.join(".", features),
                Build.VERSION.SECURITY_PATCH);
    }

    /**
     * Returns the app version of the app.
     *
     * @return App version.
     */
    public String getAppVersion() {
        String appVersion = "";
        try {
            final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = packageInfo.versionName;
            if (packageInfo.versionCode > 0) {
                appVersion = String.format(Locale.US, "%s(%s)",
                        packageInfo.versionName, packageInfo.versionCode);
            }
        } catch (NameNotFoundException | Resources.NotFoundException e) {
            SalesforceSDKLogger.w(TAG, "Package info could not be retrieved", e);
        }
        return appVersion;
    }

    /**
     * Adds AppFeature code to User Agent header for reporting.
     */
    public void registerUsedAppFeature(String appFeatureCode) {
        features.add(appFeatureCode);
    }

    /**
     * Removed AppFeature code to User Agent header for reporting.
     */
    public void unregisterUsedAppFeature(String appFeatureCode) {
        features.remove(appFeatureCode);
    }

    /**
     * @return app type as String
     */
    public String getAppType() {
        return "Native";
    }

	/**
	 * Indicates whether the application is a hybrid application.
	 *
	 * @return True if this is a hybrid application.
	 */
	public boolean isHybrid() {
        return false;
	}

    /**
     * Returns the authentication account type (which should match authenticator.xml).
     *
     * @return Account type string.
     */
    public String getAccountType() {
        return context.getString(R.string.account_type);
    }

    @NonNull
    @Override
    public String toString() {
        return this.getClass() + ": {\n" +
                "   accountType: " + getAccountType() + "\n" +
                "   userAgent: " + getUserAgent() + "\n" +
                "   mainActivityClass: " + getMainActivityClass() + "\n" +
                "\n";
    }

    /**
     * Encrypts the given data with the given key.
     *
     * @param data Data to be encrypted.
     * @param key Encryption key.
     * @return Encrypted data.
     */
    public static String encrypt(String data, String key) {
        return Encryptor.encrypt(data, key);
    }

    /**
     * Returns the encryption key being used.
     *
     * @return Encryption key.
     */
    public static String getEncryptionKey() {
        return SalesforceKeyGenerator.getEncryptionKey(INTERNAL_ENTROPY);
    }

    /**
     * Decrypts the given data with the given key.
     *
     * @param data Data to be decrypted.
     * @param key Encryption key.
     * @return Decrypted data.
     */
    public static String decrypt(String data, String key) {
        return Encryptor.decrypt(data, key);
    }

    /**
     * Asynchronous task for revoking the refresh token on logout.
     *
     * @author bhariharan
     */
    private static class RevokeTokenTask extends AsyncTask<Void, Void, Void> {

    	private final String refreshToken;
    	private final String loginServer;

    	public RevokeTokenTask(String refreshToken, String loginServer) {
    		this.refreshToken = refreshToken;
    		this.loginServer = loginServer;
    	}

		@Override
		protected Void doInBackground(Void... nothings) {
	        try {
	        	OAuth2.revokeRefreshToken(HttpAccess.DEFAULT, new URI(loginServer), refreshToken);
	        } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Revoking token failed", e);
	        }
	        return null;
		}
    }

    /**
     * Retrieves a property value that indicates whether the current run is a test run.
     *
     * @return True if the current run is a test run.
     */
    public boolean getIsTestRun() {
    	return INSTANCE.isTestRun;
    }

    /**
     * Sets a property that indicates whether the current run is a test run.
     *
     * @param isTestRun True if the current run is a test run.
     */
    public void setIsTestRun(boolean isTestRun) {
    	INSTANCE.isTestRun = isTestRun;
    }

    /**
     * Retrieves a property value that indicates whether logout is in progress.
     *
     * @return True if logout is in progress.
     */
    public boolean isLoggingOut() {
    	return isLoggingOut;
    }

    /**
     * @return ClientManager
     */
    public ClientManager getClientManager() {
    	return new ClientManager(getAppContext(), getAccountType(), getLoginOptions(), true);
    }

    /**
     * @return ClientManager
     */
    public ClientManager getClientManager(String jwt, String url) {
        return new ClientManager(getAppContext(), getAccountType(), getLoginOptions(jwt, url), true);
    }

    /**
     * Show dev support dialog
     */
    public void showDevSupportDialog(final Activity frontActivity) {
        if (!isDevSupportEnabled()) {
            return;
        }

        frontActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final LinkedHashMap<String, DevActionHandler> devActions = getDevActions(frontActivity);
                final DevActionHandler[] devActionHandlers = devActions.values().toArray(new DevActionHandler[0]);

                devActionsDialog =
                        new AlertDialog.Builder(frontActivity)
                                .setItems(
                                        devActions.keySet().toArray(new String[0]),
                                        (dialog, which) -> {
                                            devActionHandlers[which].onSelected();
                                            devActionsDialog = null;
                                        })
                                .setOnCancelListener(dialog -> devActionsDialog = null)
                                .setTitle(R.string.sf__dev_support_title)
                                .create();
                devActionsDialog.show();
            }
        });
    }

    /**
     * Build dev actions to display in dev support dialog
     * @param frontActivity
     * @return map of title to dev actions handlers to display
     */
    protected LinkedHashMap<String,DevActionHandler> getDevActions(final Activity frontActivity) {
        LinkedHashMap<String, DevActionHandler> devActions = new LinkedHashMap<>();
        devActions.put(
                "Show dev info", new DevActionHandler() {
                    @Override
                    public void onSelected() {
                        frontActivity.startActivity(new Intent(frontActivity, DevInfoActivity.class));
                    }
                });
        devActions.put(
                "Logout", new DevActionHandler() {
                    @Override
                    public void onSelected() {
                        SalesforceSDKManager.getInstance().logout(frontActivity);
                    }
                });
        devActions.put(
                "Switch user", new DevActionHandler() {
                    @Override
                    public void onSelected() {
                        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                                SalesforceSDKManager.getInstance().getAccountSwitcherActivityClass());
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        SalesforceSDKManager.getInstance().getAppContext().startActivity(i);
                    }
                });
        return devActions;
    }

    /**
     * If the application did not call setDevSupportEnabled(..) then it defaults to BuildConfig.DEBUG
     * @return true if dev support is enabled
     */
    public boolean isDevSupportEnabled() {
        return isDevSupportEnabled == null ? isDebugBuild() : isDevSupportEnabled;
    }

    /**
     * Set isDevSupportEnabled
     * @param isDevSupportEnabled
     */
    public void setDevSupportEnabled(boolean isDevSupportEnabled) {
        this.isDevSupportEnabled = isDevSupportEnabled;
    }

    /**
     * @return Dev info (list of name1, value1, name2, value2 etc) to show in DevInfoActivity
     */
    public List<String> getDevSupportInfos() {
        List<String> devInfos =  new ArrayList<>(Arrays.asList(
                "SDK Version", SDK_VERSION,
                "App Type", getAppType(),
                "User Agent", getUserAgent(),
                "Use Web Server Authentication", shouldUseWebServerAuthentication() + "",
                "Browser Login Enabled", isBrowserLoginEnabled() + "",
                "IDP Enabled", isIDPLoginFlowEnabled() + "",
                "Identity Provider", isIdentityProvider() + "",
                "Current User", usersToString(getUserAccountManager().getCachedCurrentUser()),
                "Authenticated Users", usersToString(getUserAccountManager().getAuthenticatedUsers())
        ));
        devInfos.addAll(getDevInfosFor(BootConfig.getBootConfig(context).asJSON(), "BootConfig"));
        RuntimeConfig runtimeConfig = RuntimeConfig.getRuntimeConfig(context);
        devInfos.addAll(Arrays.asList("Managed?", runtimeConfig.isManagedApp() + ""));
        if (runtimeConfig.isManagedApp()) {
            devInfos.addAll(getDevInfosFor(runtimeConfig.asJSON(), "Managed Pref"));
        }
        return devInfos;
    }

    private List<String> getDevInfosFor(JSONObject jsonObject, String keyPrefix) {
        List<String> devInfos = new ArrayList<>();
        if (jsonObject != null) {
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                devInfos.add(keyPrefix + " - " + key);
                devInfos.add(jsonObject.opt(key) + "");
            }
        }
        return devInfos;
    }

    private String usersToString(UserAccount... userAccounts) {
        List<String> accountNames = new ArrayList<>();
        if (userAccounts != null) {
            for (final UserAccount userAccount : userAccounts) {
                if (userAccount != null) {
                    accountNames.add(userAccount.getAccountName());
                }
            }
        }
        return TextUtils.join(", ", accountNames);
    }

    private String usersToString(List<UserAccount> userAccounts) {
        return usersToString(userAccounts == null ? null : userAccounts.toArray(new UserAccount[0]));
    }

    private void sendLogoutCompleteIntent() {
        final Intent intent = new Intent(LOGOUT_COMPLETE_INTENT_ACTION);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void sendCleanupIntent(UserAccount userAccount) {
        final Intent intent = new Intent(CLEANUP_INTENT_ACTION);
        intent.setPackage(context.getPackageName());
        intent.putExtra(PROCESS_ID_KEY, PROCESS_ID);
        if (null != userAccount) {
            intent.putExtra(USER_ACCOUNT, userAccount.toBundle());
        }
        context.sendBroadcast(intent);
    }

    private class CleanupReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null
                    && SalesforceSDKManager.CLEANUP_INTENT_ACTION.equals(intent.getAction())
                    && !PROCESS_ID.equals(intent.getStringExtra(PROCESS_ID_KEY))) {
                UserAccount userAccount = null;
                if (intent.hasExtra(USER_ACCOUNT)) {
                    userAccount = new UserAccount(intent.getBundleExtra(USER_ACCOUNT));
                }
                cleanUp(userAccount);
            }
        }
    }

    /**
     * Action handler in dev support dialog.
     */
    public interface DevActionHandler {

        /**
         * Triggered in case when user select the action.
         */
        void onSelected();
    }

    /**
     * Get BuildConfig.DEBUG by reflection (since it's only available in the app project)
     * @return true if app's BuildConfig.DEBUG is true
     */
    private boolean isDebugBuild() {
        return ((Boolean) getBuildConfigValue(getAppContext(), "DEBUG"));
    }

    /**
     * Gets a field from the project's BuildConfig.
     * @param context       Used to find the correct file
     * @param fieldName     The name of the field-to-access
     * @return              The value of the field, or {@code null} if the field is not found.
     */
    private Object getBuildConfigValue(Context context, String fieldName) {
        try {
            Class<?> clazz = Class.forName(context.getClass().getPackage().getName() + ".BuildConfig");
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "getBuildConfigValue failed", e);
        }
        return BuildConfig.DEBUG; // we don't want to return a null value; return this value at minimum
    }

    /**
     * Indicates whether dark theme should be displayed.  The value is retrieved from the OS, if no value is set.
     * @see SalesforceSDKManager#setTheme
     *
     * @return             True if dark theme should be displayed, otherwise false.
     */
    public boolean isDarkTheme() {
        if (theme == Theme.SYSTEM_DEFAULT) {
            int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        } else {
            return theme == Theme.DARK;
        }
    }

    /**
     * Sets the theme for the SDK.  This value only persists as long as the instance of SalesforceSDKManager.
     * @see Theme
     *
     * @param theme     The theme to use.
     */
    public synchronized void setTheme(Theme theme) {
        this.theme = theme;
    }

    /**
     * Makes the status and navigation bars visible regardless of style and OS dark theme states.
     *
     * @param activity     Activity used to set style attributes.
     */
    public void setViewNavigationVisibility(Activity activity) {
        if (!isDarkTheme() || activity.getClass().getName().equals(getLoginActivityClass().getName())) {
            // This covers the case where OS dark theme is true, but app has disabled.
            // TODO: Remove SalesforceSDK_AccessibleNav style when min API becomes 26.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                activity.setTheme(R.style.SalesforceSDK_AccessibleNav);
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    protected void onAppBackgrounded() {
        ((ScreenLockManager) getScreenLockManager()).onAppBackgrounded();
        ((BiometricAuthenticationManager) getBiometricAuthenticationManager())
                .onAppBackgrounded();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    protected void onAppForegrounded() {
        ((ScreenLockManager) getScreenLockManager()).onAppForegrounded();
        ((BiometricAuthenticationManager) getBiometricAuthenticationManager())
                .onAppForegrounded();
    }
}
