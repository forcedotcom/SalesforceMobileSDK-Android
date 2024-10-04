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
package com.salesforce.androidsdk.app

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Build.MODEL
import android.os.Build.VERSION.RELEASE
import android.os.Build.VERSION.SECURITY_PATCH
import android.os.Bundle
import android.os.Handler
import android.os.Looper.getMainLooper
import android.provider.Settings.Secure.ANDROID_ID
import android.provider.Settings.Secure.getString
import android.text.TextUtils.isEmpty
import android.text.TextUtils.join
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.webkit.CookieManager
import android.webkit.URLUtil.isHttpsUrl
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.core.content.ContextCompat.registerReceiver
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.WindowMetricsCalculator
import com.salesforce.androidsdk.BuildConfig.DEBUG
import com.salesforce.androidsdk.R.string.account_type
import com.salesforce.androidsdk.R.string.sf__dev_support_title
import com.salesforce.androidsdk.R.style.SalesforceSDK_AlertDialog
import com.salesforce.androidsdk.R.style.SalesforceSDK_AlertDialog_Dark
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManager.USER_SWITCH_TYPE_LOGOUT
import com.salesforce.androidsdk.analytics.AnalyticsPublishingWorker.Companion.enqueueAnalyticsPublishWorkRequest
import com.salesforce.androidsdk.analytics.EventBuilderHelper.createAndStoreEvent
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishOnAppBackground
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.Features.FEATURE_APP_IS_IDP
import com.salesforce.androidsdk.app.Features.FEATURE_APP_IS_SP
import com.salesforce.androidsdk.app.Features.FEATURE_BROWSER_LOGIN
import com.salesforce.androidsdk.app.SalesforceSDKManager.Theme.DARK
import com.salesforce.androidsdk.app.SalesforceSDKManager.Theme.SYSTEM_DEFAULT
import com.salesforce.androidsdk.auth.AuthenticatorService.KEY_INSTANCE_URL
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.NativeLoginManager
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.UNKNOWN
import com.salesforce.androidsdk.auth.OAuth2.revokeRefreshToken
import com.salesforce.androidsdk.auth.idp.SPConfig
import com.salesforce.androidsdk.auth.idp.interfaces.IDPManager
import com.salesforce.androidsdk.auth.idp.interfaces.SPManager
import com.salesforce.androidsdk.config.AdminPermsManager
import com.salesforce.androidsdk.config.AdminSettingsManager
import com.salesforce.androidsdk.config.BootConfig.getBootConfig
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.PRODUCTION_LOGIN_URL
import com.salesforce.androidsdk.config.LoginServerManager.SANDBOX_LOGIN_URL
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.IDPAppPackageName
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.developer.support.notifications.local.ShowDeveloperSupportNotifier.Companion.BROADCAST_INTENT_ACTION_SHOW_DEVELOPER_SUPPORT
import com.salesforce.androidsdk.developer.support.notifications.local.ShowDeveloperSupportNotifier.Companion.hideDeveloperSupportNotification
import com.salesforce.androidsdk.developer.support.notifications.local.ShowDeveloperSupportNotifier.Companion.showDeveloperSupportNotification
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.push.PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT
import com.salesforce.androidsdk.push.PushMessaging.isRegistered
import com.salesforce.androidsdk.push.PushMessaging.register
import com.salesforce.androidsdk.push.PushMessaging.unregister
import com.salesforce.androidsdk.push.PushNotificationInterface
import com.salesforce.androidsdk.push.PushService
import com.salesforce.androidsdk.push.PushService.Companion.pushNotificationsRegistrationType
import com.salesforce.androidsdk.push.PushService.PushNotificationReRegistrationType.ReRegistrationOnAppForeground
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.SalesforceKeyGenerator.getEncryptionKey
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.ui.AccountSwitcherActivity
import com.salesforce.androidsdk.ui.DevInfoActivity
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.util.AuthConfigUtil.getMyDomainAuthConfig
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.AppCreateComplete
import com.salesforce.androidsdk.util.EventsObservable.EventType.LogoutComplete
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.net.URI
import java.util.Locale.US
import java.util.SortedSet
import java.util.UUID.randomUUID
import java.util.concurrent.ConcurrentSkipListSet
import java.util.regex.Pattern
import com.salesforce.androidsdk.auth.idp.IDPManager as DefaultIDPManager
import com.salesforce.androidsdk.auth.idp.SPManager as DefaultSPManager
import com.salesforce.androidsdk.auth.interfaces.NativeLoginManager as NativeLoginManagerInterface
import com.salesforce.androidsdk.security.interfaces.BiometricAuthenticationManager as BiometricAuthenticationManagerInterface
import com.salesforce.androidsdk.security.interfaces.ScreenLockManager as ScreenLockManagerInterface

/**
 * This class serves as an interface to the various functions of the Salesforce
 * SDK. In order to use the SDK, your app must first instantiate the singleton
 * SalesforceSDKManager object by calling the static init() method. After
 * calling init(), use the static getInstance() method to access the singleton
 * SalesforceSDKManager object.
 *
 * TODO: Remove the Android `Context` property of this object, as it is a memory leak.
 *
 * @param context The Android context
 * @param mainActivity Activity that should be launched after the login flow
 * @param loginActivity Login activity
 */
open class SalesforceSDKManager protected constructor(
    @JvmField
    protected val context: Context,
    mainActivity: Class<out Activity>,
    private val loginActivity: Class<out Activity>? = null,
    internal val nativeLoginActivity: Class<out Activity>? = null,
) : LifecycleObserver {

    constructor(
        context: Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>? = null
    ) : this(context, mainActivity, loginActivity, nativeLoginActivity = null)

    /** The Android context */
    val appContext: Context = context

    /** Login options associated with the app */
    private var loginOptionsInternal: LoginOptions? = null

    /**
     * Returns the class for the main activity.
     *
     * @return The class for the main activity.
     */
    val mainActivityClass: Class<out Activity>

    /**
     * Null or an authenticated Activity for private use when developer support
     * is enabled.
     */
    private var authenticatedActivityForDeveloperSupport: Activity? = null

    /**
     * Null or the Android Activity lifecycle callbacks object registered when
     * developer support is enabled.
     */
    private var activityLifecycleCallbacksForDeveloperSupport: ActivityLifecycleCallbacks? = null

    /**
     * Null or the show developer support Android broadcast Intent receiver when
     * developer support is enabled.
     */
    private var showDeveloperSupportBroadcastIntentReceiver: BroadcastReceiver? = null

    val webviewLoginActivityClass: Class<out Activity> = loginActivity ?: LoginActivity::class.java

    /**
     * The class of the activity used to perform the login process and create
     * the account.
     */
    val loginActivityClass: Class<out Activity> = nativeLoginActivity ?: webviewLoginActivityClass

    /**
     * For Salesforce Identity API UI Bridge support, the overriding front door bridge URL to
     * use in place of the default initial login URL
     */
    var frontDoorBridgeUrl: String? = null

    /**
     * For Salesforce Identity API UI Bridge support, the overriding front door bridge URL's
     * optional web server flow code verifier
     */
    var frontDoorBridgeCodeVerifier: String? = null

    /** The class for the account switcher activity */
    var accountSwitcherActivityClass = AccountSwitcherActivity::class.java

    /** The screen lock manager initialization synchronization lock object */
    private val screenLockManagerLock = Any()

    /** The Salesforce SDK manager's screen lock manager */
    var screenLockManager: ScreenLockManagerInterface? = null
        @JvmName("getScreenLockManager")
        get() = field ?: synchronized(screenLockManagerLock) {
            ScreenLockManager()
        }.also { field = it }

    /** The screen lock manager initialization synchronization lock object */
    private val bioAuthManagerLock = Any()

    /** The Salesforce SDK manager's biometric authentication manager */
    var biometricAuthenticationManager: BiometricAuthenticationManagerInterface? = null
        get() = field ?: synchronized(bioAuthManagerLock) {
            BiometricAuthenticationManager()
        }.also { field = it }

    /** The Salesforce SDK manager's login server manager */
    open val loginServerManager by lazy {
        LoginServerManager(appContext)
    }

    /** The Salesforce SDK manager's native login manager */
    var nativeLoginManager: NativeLoginManagerInterface? = null
        private set

    /** Optionally enables features for automated testing.
     *
     * Note that accessors are named for compatibility with Salesforce Mobile
     * SDK 11 */
    @Suppress("RedundantGetter", "RedundantSetter")
    var isTestRun = false
        @JvmName("getIsTestRun")
        get() = field
        @JvmName("setIsTestRun")
        set(value) {
            field = value
        }

    /** Indicates if login via QR Code and UI bridge API is enabled */
    @set:Synchronized
    open var isQrCodeLoginEnabled = false

    /** Indicates if logout is in progress */
    var isLoggingOut = false
        private set

    /** The Salesforce SDK manager's admin settings manager */
    var adminSettingsManager: AdminSettingsManager? = null
        get() = field ?: AdminSettingsManager().also { field = it }

    /** The Salesforce SDK manager's admin permissions manager */
    var adminPermsManager: AdminPermsManager? = null
        get() = field ?: AdminPermsManager().also { field = it }

    /**
     * An implementation of [PushNotificationInterface] that handles received
     * push notifications.
     */
    @get:Synchronized
    @set:Synchronized
    var pushNotificationReceiver: PushNotificationInterface? = null
        set(value) {
            field = value
            PushMessaging.onPushReceiverSetup(appContext)
        }

    /**
     * The class fulfilling push notification registration features.  A default
     * implementation is provided in [PushService].  Setting this property to a
     * subclass will inject the specified class instead.
     *
     * Note, the specified class no longer needs to be specified in the Android
     * manifest as a service.
     */
    @Suppress("unused")
    @get:Synchronized
    @set:Synchronized
    var pushServiceType: Class<out PushService> = PushService::class.java

    /** The unique device ID */
    @SuppressLint("HardwareIds")
    val deviceId: String = getString(appContext.contentResolver, ANDROID_ID)

    /** App feature codes for reporting in the user agent header */
    private val features: SortedSet<String?>

    /**
     * An additional list of OAuth keys to fetch and store from the token
     * endpoint
     */
    var additionalOauthKeys: List<String>? = null

    /**
     * The login brand. In the following example, "<brand>" should be set here.
     * https://community.force.com/services/oauth2/authorize/<brand>?response_type=code&...
     *
     * Note: This API might change in the future.
     */
    @set:Synchronized
    var loginBrand: String? = null

    /**
     * Optionally enables browser based login instead of web view login. This
     * should NOT be used directly by apps as this is meant for internal use
     * based on the value configured on the server
     */
    @set:Synchronized
    open var isBrowserLoginEnabled = false
        protected set

    /** Optionally enables browser session sharing */
    var isShareBrowserSessionEnabled = false
        private set

    /**
     * The custom tab browser to use during advanced authentication.
     * NB: The default browser on the device is used:
     * - If null is specified
     * - Or, if the specified browser is not installed
     *
     * Defaults to Chrome.
     */
    @get:Synchronized
    @set:Synchronized
    var customTabBrowser: String? = "com.android.chrome"

    /**
     * Optionally enables the web server flow for web view log on.  Defaults to
     * true
     */
    @get:JvmName("shouldUseWebServerAuthentication")
    @set:Synchronized
    var useWebServerAuthentication = true

    /**
     * Optionally, enables the hybrid authentication flow.  Defaults to true
     */
    @get:JvmName("shouldUseHybridAuthentication")
    @set:Synchronized
    var useHybridAuthentication = true

    /**
     * The regular expression pattern used to detect "Use Custom Domain" input
     * from login web view.
     *
     * Example for a specific org:
     * "^https:\\/\\/mobilesdk\\.my\\.salesforce\\.com\\/\\?startURL=%2Fsetup%2Fsecur%2FRemoteAccessAuthorizationPage\\.apexp"
     *
     * For any my domain:
     * "^https:\\/\\/[a-zA-Z0-9]+\\.my\\.salesforce\\.com/\\?startURL=%2Fsetup%2Fsecur%2FRemoteAccessAuthorizationPage\\.apexp"
     */
    @JvmField
    @get:Synchronized
    @set:Synchronized
    var customDomainInferencePattern: Pattern? = null

    /**
     * The Salesforce Mobile SDK user interface theme.  The value is not
     * persistent across instances of Salesforce SDK Manager
     */
    @set:Synchronized
    var theme = SYSTEM_DEFAULT

    /**
     * The app name to use in [.getUserAgent]. This string must only contain
     * printable ASCII characters.
     *
     * By default, the display name under
     * [android.content.pm.ApplicationInfo.labelRes] will be used.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("provideAppName")
    open var appName: String? = null
        get() = runCatching {
            if (field == null) {
                val packageInfo = appContext.packageManager.getPackageInfo(
                    appContext.packageName, 0
                )
                appName = appContext.getString(
                    packageInfo.applicationInfo.labelRes
                )
            }
            field
        }.onFailure { e ->
            w(TAG, "Package info could not be retrieved", e)
        }.getOrDefault("")


    /**
     * The Salesforce SDK manager's admin settings manager. Only
     * defined if setAllowedSPApps() was called first
     */
    var idpManager: IDPManager? = null
        private set

    /**
     * The Salesforce SDK manager's SP manager. Only defined if
     * setIdpAppPackageName() was called first
     */
    var spManager: SPManager? = null
        private set

    /** Indicates if IDP login flow is enabled */
    val isIDPLoginFlowEnabled
        get() = spManager != null

    /**
     * The available Mobile SDK style themes.
     */
    enum class Theme {
        LIGHT,
        DARK,
        SYSTEM_DEFAULT
    }

    /** The developer support dialog */
    private var devActionsDialog: AlertDialog? = null

    /**
     * The app specified option to enable developer support features, which
     * overrides the default value from BuildConfig.DEBUG.
     */
    private var isDevSupportEnabledOverride: Boolean? = null

    /**
     * Indicates if developer support features are enabled.  Defaults to
     * BuildConfig.DEBUG unless another value is specified.
     * @return Boolean true enables developer support features; false otherwise
     */
    open fun isDevSupportEnabled() = isDevSupportEnabledOverride ?: isDebugBuild

    /**
     * Sets if developer support features are enabled.
     * @param value Boolean true enables developer support features; false
     * otherwise
     */
    @Suppress("unused")
    fun setIsDevSupportEnabled(value: Boolean) {
        isDevSupportEnabledOverride = value
    }

    /** Initializer */
    init {
        mainActivityClass = mainActivity
        features = ConcurrentSkipListSet(CASE_INSENSITIVE_ORDER)

        /*
         * Checks if an analytics app name has already been set by the app.
         * If not, fetches the default app name to be used and sets it.
         */
        val currentAiltnAppName = ailtnAppName
        if (isEmpty(currentAiltnAppName)) {
            var ailtnAppName: String? = null
            runCatching {
                val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                ailtnAppName = appContext.getString(packageInfo.applicationInfo.labelRes)
            }.onFailure { e ->
                e(TAG, "Package not found", e)
            }
            if (!isEmpty(ailtnAppName)) {
                Companion.ailtnAppName = ailtnAppName
            }
        }

        // If the app runs in multiple processes, all Salesforce SDK manager instances need to run cleanup during logout.
        val cleanupReceiver = CleanupReceiver()
        registerReceiver(
            appContext,
            cleanupReceiver,
            IntentFilter(CLEANUP_INTENT_ACTION),
            RECEIVER_NOT_EXPORTED
        )
        Handler(getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    /** Login options associated with the app */
    open val loginOptions: LoginOptions get() = getLoginOptions(null, null)

    /**
     * Sets the login options associated with the app.
     *
     * @param jwt The `jwt`
     * @param url The URL
     */
    open fun getLoginOptions(
        jwt: String?,
        url: String?
    ) = loginOptionsInternal?.apply {
        this.jwt = jwt
        setUrl(url)
    } ?: getBootConfig(
        appContext
    ).let { config ->
        when {
            isEmpty(jwt) -> LoginOptions(
                url,
                config.oauthRedirectURI,
                config.remoteAccessConsumerKey,
                config.oauthScopes
            )

            else -> LoginOptions(
                url,
                config.oauthRedirectURI,
                config.remoteAccessConsumerKey,
                config.oauthScopes,
                jwt
            )
        }.also { loginOptionsInternal = it }
    }

    /**
     * Indicates if the Salesforce Mobile SDK should automatically log out when
     * the access token is revoked. When overriding this method to return false,
     * the subclass is responsible for handling cleanup when the access token is
     * revoked.
     *
     * @return True if the Salesforce Mobile SDK should automatically logout when
     * the access token is revoked
     */
    open fun shouldLogoutWhenTokenRevoked() = true

    /** The Salesforce SDK manager's user account manager */
    open val userAccountManager: UserAccountManager by lazy {
        UserAccountManager.getInstance()
    }

    /**
     * Sets authentication ability for Salesforce integration users.  When true,
     * Salesforce integration users will be prohibited from initial
     * authentication and receive an error message.
     *
     * Defaults to false as Salesforce-authored apps are the primary audience
     * for this option.  This functionality will eventually be provided by the
     * backend.
     */
    open var shouldBlockSalesforceIntegrationUser = false

    /**
     * Creates a NativeLoginManager instance that allows the app to use its
     * own native UI for authentication.
     *
     * @param consumerKey The Connected App consumer key.
     * @param callbackUrl The Connected App redirect URI.
     * @param communityUrl The login url for native login.
     * @param reCaptchaSiteKeyId The Google Cloud project reCAPTCHA Key's "Id"
     * as shown in Google Cloud Console under "Products & Solutions", "Security"
     * and "reCAPTCHA Enterprise"
     * @param googleCloudProjectId The Google Cloud project's "Id" as shown in
     * Google Cloud Console
     * @param isReCaptchaEnterprise Specifies if reCAPTCHA uses the enterprise
     * license
     * @return The Native Login Manager.
     */
    @Suppress("unused")
    fun useNativeLogin(
        consumerKey: String,
        callbackUrl: String,
        communityUrl: String,
        reCaptchaSiteKeyId: String? = null,
        googleCloudProjectId: String? = null,
        isReCaptchaEnterprise: Boolean = false
    ): NativeLoginManagerInterface {
        nativeLoginManager = NativeLoginManager(
            consumerKey,
            callbackUrl,
            communityUrl,
            reCaptchaSiteKeyId,
            googleCloudProjectId,
            isReCaptchaEnterprise
        )
        return nativeLoginManager as NativeLoginManagerInterface
    }

    /**
     * Optionally enables browser based login instead of web view login. This
     * should NOT be used directly by apps as this is meant for internal use
     * based on the value configured on the server.
     *
     * @param browserLoginEnabled True if Chrome should be used for login; false
     * otherwise
     * @param shareBrowserSessionEnabled True enables browser session sharing;
     * false otherwise
     */
    @Synchronized
    fun setBrowserLoginEnabled(
        browserLoginEnabled: Boolean,
        shareBrowserSessionEnabled: Boolean
    ) {
        isBrowserLoginEnabled = browserLoginEnabled
        isShareBrowserSessionEnabled = shareBrowserSessionEnabled
        when {
            browserLoginEnabled ->
                registerUsedAppFeature(FEATURE_BROWSER_LOGIN)

            else ->
                unregisterUsedAppFeature(FEATURE_BROWSER_LOGIN)
        }
    }

    /** Indicates if this app is configured as an identity provider */
    private val isIdentityProvider
        get() = idpManager != null

    /**
     * Sets the IDP package name for this app.
     * As a result, this app gets an SP Manager and can be used as an SP
     * @param idpAppPackageName The IDP app package name
     */
    fun setIDPAppPackageName(idpAppPackageName: String?) {
        registerUsedAppFeature(FEATURE_APP_IS_SP)
        spManager = idpAppPackageName?.let { DefaultSPManager(it) }
    }

    /**
     * Sets the allowed SP apps for this app.
     * As a result this app gets an IDP manager and can be used as an IDP
     * @param allowedSPApps The list of allows SP app
     */
    @Suppress("unused")
    fun setAllowedSPApps(allowedSPApps: List<SPConfig>) {
        registerUsedAppFeature(FEATURE_APP_IS_IDP)
        idpManager = DefaultIDPManager(allowedSPApps)
    }


    /** Returns the app display name used by the passcode dialog */
    @Suppress("unused")
    open val appDisplayString = DEFAULT_APP_DISPLAY_NAME

    /** Returns the name of the app as defined in AndroidManifest.xml */
    val applicationName
        get() = appContext.packageManager.getApplicationLabel(
            appContext.applicationInfo
        ).toString()

    /**
     * Indicates if a network connection is available.
     *
     * @return True if a network connection is available
     */
    fun hasNetwork() = DEFAULT.hasNetwork()

    /**
     * Cleans cached credentials and data.
     *
     * @param frontActivity The front activity
     * @param account The user account
     * @param shouldDismissActivity Dismisses the current activity if true; does
     * nothing otherwise
     */
    private fun cleanUp(
        frontActivity: Activity?,
        account: Account?,
        shouldDismissActivity: Boolean
    ) {
        val userAccount = UserAccountManager.getInstance().buildUserAccount(account)

        // Clean up within this process
        cleanUp(userAccount)

        // Clean up Salesforce SDK manager instances in separate processes
        sendCleanupIntent(userAccount)
        val users = userAccountManager.authenticatedUsers

        // If this is the last account, finish the front activity if specified
        if (shouldDismissActivity && frontActivity != null && (users == null || users.size <= 1)) {
            frontActivity.finish()
        }

        /*
         * Check how many authenticated accounts are left. If only one account
         * is left, this is the account that is being removed. In this case,
         * safely reset the screen lock manager, admin prefs and encryption
         * keys. Otherwise, don't reset the screen lock manager and admin prefs
         * since there might be other accounts on that same org and these
         * policies are stored at the org level.
         */
        if (users == null || users.size <= 1) {
            adminSettingsManager?.resetAll()
            adminPermsManager?.resetAll()
            adminSettingsManager = null
            adminPermsManager = null
            (screenLockManager as ScreenLockManager?)?.reset()
            screenLockManager = null
            biometricAuthenticationManager = null
        }
    }

    /**
     * Clean up cached data.
     *
     * @param userAccount The user account
     */
    protected open fun cleanUp(userAccount: UserAccount?) {
        SalesforceAnalyticsManager.reset(userAccount)
        RestClient.clearCaches(userAccount)
        UserAccountManager.getInstance().clearCachedCurrentUser()

        userAccount?.let { userAccountResolved ->
            (screenLockManager as ScreenLockManager?)?.cleanUp(userAccountResolved)
            (biometricAuthenticationManager as BiometricAuthenticationManager)
                .cleanUp(userAccountResolved)
        }
    }

    /**
     * Starts the login flow if user account has been removed.
     */
    protected open fun startLoginPage() {

        // Clear cookies
        CookieManager.getInstance().removeAllCookies(null)

        // Restart the app
        appContext.startActivity(
            Intent(
                appContext,
                mainActivityClass
            ).apply {
                setPackage(appContext.packageName)
                flags = FLAG_ACTIVITY_NEW_TASK
            })
    }

    /**
     * Starts the account switcher activity if an account has been removed.
     */
    private fun startSwitcherActivityIfRequired() {

        // Clear cookies
        CookieManager.getInstance().removeAllCookies(null)

        /*
         * If the number of accounts remaining is 0, show the login page.
         *
         * If the number of accounts remaining is 1, switch to that user
         * automatically.
         *
         * If there is more than 1 account logged in, shows the account switcher
         * screen so that the user can pick which account to switch to
         */
        val userAccMgr = userAccountManager
        val accounts = userAccMgr.authenticatedUsers
        if (accounts == null || accounts.size == 0) {
            startLoginPage()
        } else if (accounts.size == 1) {
            userAccMgr.switchToUser(
                accounts[0],
                USER_SWITCH_TYPE_LOGOUT,
                null
            )
        } else {
            appContext.startActivity(Intent(
                appContext,
                accountSwitcherActivityClass
            ).apply {
                flags = FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    /**
     * Updates developer support features to match an Android Activity lifecycle
     * event, including updating the app's authenticated Android Activity.
     *
     * @param authenticatedActivity The new authenticated Android Activity
     * instance, or null
     * @param lifecycleActivity The Android Activity instance provided by the
     * Activity lifecycle event
     */
    private fun updateDeveloperSupportForActivityLifecycle(
        authenticatedActivity: Activity?,
        lifecycleActivity: Activity
    ) {

        // Assign the authenticated Activity
        authenticatedActivityForDeveloperSupport = authenticatedActivity

        // Display or hide the show developer support notification
        when (userAccountManager.currentAccount == null || authenticatedActivityForDeveloperSupport == null) {
            true -> hideDeveloperSupportNotification(lifecycleActivity)
            else -> showDeveloperSupportNotification(lifecycleActivity)
        }
    }

    /**
     * Unregisters from push notifications.
     * @param clientMgr The client manager
     * @param showLoginPage Shows the login page after push notification
     * unregistration
     * @param refreshToken The refresh token
     * @param loginServer The login server
     * @param account The user account
     * @param frontActivity The front activity
     * @param isLastAccount Indicates if the account is the last authenticated
     * account
     */
    @Synchronized
    private fun unregisterPush(
        clientMgr: ClientManager,
        showLoginPage: Boolean,
        refreshToken: String,
        loginServer: String?,
        account: Account?,
        frontActivity: Activity?,
        isLastAccount: Boolean,
        logoutReason: LogoutReason,
    ) {
        val intentFilter = IntentFilter(UNREGISTERED_ATTEMPT_COMPLETE_EVENT)

        val pushUnregisterReceiver = object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                if (UNREGISTERED_ATTEMPT_COMPLETE_EVENT == intent.action) {
                    runCatching {
                        appContext.unregisterReceiver(this)
                    }.onFailure { e ->
                        e(TAG, "Exception occurred while un-registering", e)
                    }
                    removeAccount(
                        clientMgr,
                        showLoginPage,
                        refreshToken,
                        loginServer,
                        account,
                        frontActivity,
                        logoutReason,
                    )
                }
            }
        }

        registerReceiver(
            appContext,
            pushUnregisterReceiver,
            intentFilter,
            RECEIVER_NOT_EXPORTED
        )

        // Unregisters from notifications on logout
        unregister(
            appContext,
            userAccountManager.buildUserAccount(account),
            isLastAccount
        )
    }

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * This overload uses a null user account.
     *
     * @param frontActivity The front activity
     * @param showLoginPage If true, displays the login page after removing the
     * account
     */
    open fun logout(
        /* Note: Kotlin's @JvmOverloads annotations does not correctly
           generate this overload due to a JVM naming conflict.         */
        frontActivity: Activity?,
        showLoginPage: Boolean = true,
    ) {
        logout(null, frontActivity, showLoginPage)
    }

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * @param account The user account to logout. Defaults to the current user
     * account
     * @param frontActivity The front activity
     * @param showLoginPage If true, displays the login page after removing the
     * account
     */
    @JvmOverloads
    open fun logout(
        account: Account? = null,
        frontActivity: Activity?,
        showLoginPage: Boolean = true,
    ) {
        logout(
            account = account,
            frontActivity = frontActivity,
            showLoginPage = showLoginPage,
            reason = UNKNOWN
        )
    }

    // Note the below overload exists because @JvmOverloads generates non-overrideable
    // signatures for all but the overload with all params. see:
    // https://youtrack.jetbrains.com/issue/KT-33240/Generated-overloads-for-JvmOverloads-on-open-methods-should-be-final
    //
    // I highly doubt any apps are overriding the above function but it is technically breaking and shouldn't be
    // combined until Mobile SDK 13.0.  TODO: remove above method and move @JvmOverloads to below overload -- or remove open.

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * @param account The user account to logout. Defaults to the current user
     * account
     * @param frontActivity The front activity
     * @param showLoginPage If true, displays the login page after removing the
     * account
     * @param reason The reason for the logout.
     */
    open fun logout(
        account: Account? = null,
        frontActivity: Activity?,
        showLoginPage: Boolean = true,
        reason: LogoutReason = UNKNOWN,
    ) {
        createAndStoreEvent("userLogout", null, TAG, null)
        val clientMgr = ClientManager(
            appContext,
            accountType,
            null,
            shouldLogoutWhenTokenRevoked()
        )

        val accountToLogout = account ?: clientMgr.account

        frontDoorBridgeUrl = null
        frontDoorBridgeCodeVerifier = null

        isLoggingOut = true
        val mgr = AccountManager.get(appContext)
        var refreshToken: String? = null
        var loginServer: String? = null
        if (accountToLogout != null) {
            val encryptionKey = encryptionKey
            refreshToken = decrypt(
                mgr.getPassword(accountToLogout),
                encryptionKey
            )
            loginServer = decrypt(
                mgr.getUserData(
                    accountToLogout,
                    KEY_INSTANCE_URL
                ),
                encryptionKey
            )
        }

        /*
         * Makes a call to un-register from push notifications only if the
         * refresh token is available.
         */
        val userAcc = userAccountManager.buildUserAccount(accountToLogout)
        val numAccounts = mgr.getAccountsByType(accountType).size
        if (isRegistered(
                appContext,
                userAcc
            ) && refreshToken != null
        ) {
            unregisterPush(
                clientMgr,
                showLoginPage,
                refreshToken,
                loginServer,
                accountToLogout,
                frontActivity,
                isLastAccount = (numAccounts == 1),
                reason,
            )
        } else {
            removeAccount(
                clientMgr,
                showLoginPage,
                refreshToken,
                loginServer,
                accountToLogout,
                frontActivity,
                reason,
            )
        }
    }

    /**
     * Removes the account upon logout.
     *
     * @param clientMgr The client manager instance
     * @param showLoginPage If true, displays the login page after removing the
     * account
     * @param refreshToken The refresh token
     * @param loginServer The login server
     * @param account The user account
     * @param frontActivity The front activity
     */
    private fun removeAccount(
        clientMgr: ClientManager,
        showLoginPage: Boolean,
        refreshToken: String?,
        loginServer: String?,
        account: Account?,
        frontActivity: Activity?,
        logoutReason: LogoutReason,
    ) {
        cleanUp(
            frontActivity,
            account,
            showLoginPage
        )
        clientMgr.removeAccount(account)
        isLoggingOut = false
        notifyLogoutComplete(showLoginPage, logoutReason)

        // Revoke the existing refresh token
        if (shouldLogoutWhenTokenRevoked() && refreshToken != null) {
            CoroutineScope(Default).launch {
                runCatching {
                    revokeRefreshToken(
                        DEFAULT,
                        URI(loginServer),
                        refreshToken,
                        logoutReason,
                    )
                }.onFailure { e ->
                    w(TAG, "Revoking token failed", e)
                }
            }
        }
    }

    /**
     * Sends the logout complete event.
     * @param showLoginPage When true, shows the login page
     */
    private fun notifyLogoutComplete(showLoginPage: Boolean, logoutReason: LogoutReason) {
        EventsObservable.get().notifyEvent(LogoutComplete, logoutReason)
        sendLogoutCompleteIntent(logoutReason)
        if (showLoginPage) {
            startSwitcherActivityIfRequired()
        }
    }

    /**
     * Returns a user agent string based on the Salesforce Mobile SDK version.
     * The user agent takes the following form:
     * SalesforceMobileSDK/{salesforceSDK version} android/{android OS version} appName/appVersion {Native|Hybrid} uid_{device id}
     *
     * @return The user agent string to use for all requests
     */
    val userAgent
        get() = getUserAgent("")

    /**
     * Returns a user agent string based on the Salesforce Mobile SDK version.
     * The user agent takes the following form:
     * SalesforceMobileSDK/{salesforceSDK version} android/{android OS version} {provideAppName()}/appVersion {Native|Hybrid} uid_{device id}
     *
     * @param qualifier The user agent qualifier
     * @return The user agent string to use for all requests
     */
    open fun getUserAgent(qualifier: String) =
        String.format(
            "SalesforceMobileSDK/%s android mobile/%s (%s) %s/%s %s uid_%s ftr_%s SecurityPatch/%s",
            SDK_VERSION,
            RELEASE,
            MODEL,
            appName,
            appVersion,
            "$appType$qualifier",
            deviceId,
            join(".", features),
            SECURITY_PATCH
        )

    /** The app version */
    val appVersion: String
        get() = runCatching {
            val packageInfo = appContext.packageManager.getPackageInfo(
                appContext.packageName,
                0
            )
            var result = packageInfo.versionName
            if (packageInfo.versionCode > 0) {
                result = String.format(
                    US,
                    "%s(%s)",
                    packageInfo.versionName,
                    packageInfo.versionCode
                )
            }
            result
        }.onFailure { e ->
            w(TAG, "Package info could not be retrieved", e)
        }.getOrDefault("")

    /**
     * Adds an app feature code for reporting in the user agent header
     * @param appFeatureCode The app feature code
     */
    fun registerUsedAppFeature(appFeatureCode: String?) =
        features.add(appFeatureCode)

    /**
     * Removes an app feature code from reporting in the user agent header
     * @param appFeatureCode The app feature code
     */
    fun unregisterUsedAppFeature(appFeatureCode: String?) =
        features.remove(appFeatureCode)

    /** The app type */
    open val appType = "Native"

    /** Indicates whether the app is a hybrid app */
    open val isHybrid = false

    /** The authentication account type, which should match authenticator.xml */
    val accountType = appContext.getString(account_type)

    override fun toString() =
        """
            $javaClass: {
              accountType: $accountType
              userAgent: $userAgent
              mainActivityClass: $mainActivityClass
            }
        """.trimIndent()

    /** The client manager */
    val clientManager by lazy {
        ClientManager(
            appContext,
            accountType,
            loginOptions,
            true
        )
    }

    /**
     * Returns a client manager for the provided parameters.
     * @return A new client manager for the provided parameters
     */
    @Suppress("unused")
    fun getClientManager(
        jwt: String?,
        url: String?
    ): ClientManager = ClientManager(
        appContext,
        accountType,
        getLoginOptions(jwt, url),
        true
    )

    /**
     * Displays developer support for a specified Android activity.
     *
     * @param frontActivity The Android activity to chose developer support
     * features for and display the dialog
     */
    fun showDevSupportDialog(frontActivity: Activity?) {
        if (!isDevSupportEnabled() || frontActivity == null) return

        devActionsDialog?.dismiss()

        CoroutineScope(Main).launch {
            val devActions = getDevActions(frontActivity)
            val devActionHandlers = devActions.values.toTypedArray<DevActionHandler>()

            devActionsDialog = AlertDialog.Builder(
                frontActivity,
                if (isDarkTheme)
                    SalesforceSDK_AlertDialog_Dark
                else
                    SalesforceSDK_AlertDialog
            ).setItems(
                devActions.keys.toTypedArray<String>()
            ) { _, which: Int ->
                devActionHandlers[which].onSelected()
                devActionsDialog = null
            }.setOnCancelListener {
                devActionsDialog = null
            }.setTitle(
                sf__dev_support_title
            ).create().also { dialog ->
                dialog.show()
            }
        }
    }

    /**
     * Builds developer support actions for the developer support dialog.
     *
     * @param frontActivity The Android activity to chose developer support
     * features for
     * @return map of title to dev actions handlers to display
     */
    protected open fun getDevActions(
        frontActivity: Activity
    ) = mapOf(

        "Show dev info" to object : DevActionHandler {
            override fun onSelected() {
                frontActivity.startActivity(
                    Intent(
                        frontActivity,
                        DevInfoActivity::class.java
                    )
                )
            }
        },

        "Logout" to object : DevActionHandler {
            override fun onSelected() {
                logout(frontActivity = frontActivity, reason = LogoutReason.USER_LOGOUT)
            }
        },

        "Switch user" to object : DevActionHandler {
            override fun onSelected() {
                appContext.startActivity(Intent(
                    appContext,
                    accountSwitcherActivityClass
                ).apply {
                    flags = FLAG_ACTIVITY_NEW_TASK
                })
            }
        })

    /** Information to display in the developer support dialog */
    open val devSupportInfos: List<String>
        get() = mutableListOf(
            "SDK Version", SDK_VERSION,
            "App Type", appType,
            "User Agent", userAgent,
            "Use Web Server Authentication", "$useWebServerAuthentication",
            "Browser Login Enabled", "$isBrowserLoginEnabled",
            "IDP Enabled", "$isIDPLoginFlowEnabled",
            "Identity Provider", "$isIdentityProvider",
            "Current User", usersToString(userAccountManager.cachedCurrentUser),
            "Authenticated Users", usersToString(userAccountManager.authenticatedUsers)
        ).apply {
            addAll(
                getDevInfosFor(
                    getBootConfig(appContext).asJSON(),
                    "BootConfig"
                )
            )
            val runtimeConfig = getRuntimeConfig(appContext)
            addAll(
                listOf(
                    "Managed?",
                    "${runtimeConfig.isManagedApp}"
                )
            )
            if (runtimeConfig.isManagedApp) {
                addAll(
                    getDevInfosFor(
                        runtimeConfig.asJSON(),
                        "Managed Pref"
                    )
                )
            }
        }

    /**
     * Information to display in the developer support dialog for a specified
     * JSON configuration.
     * @param jsonObject JSON for an object such as boot or runtime
     * configuration
     * @return The developer support dialog information
     */
    private fun getDevInfosFor(
        jsonObject: JSONObject?,
        keyPrefix: String
    ): List<String> {
        val devInfos: MutableList<String> = ArrayList()
        val jsonObjectResolved = jsonObject ?: return devInfos
        val keys = jsonObjectResolved.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            devInfos.add("$keyPrefix - $key")
            jsonObjectResolved.opt(key)?.toString()?.let {
                devInfos.add(it)
            }
        }
        return devInfos
    }

    /**
     * Returns a string representation of the provided users.
     * @param userAccounts The user accounts
     * @return A string representation of the provided users.
     */
    private fun usersToString(
        vararg userAccounts: UserAccount
    ) = join(
        ", ",
        userAccounts.map { userAccount ->
            userAccount.accountName
        }
    )

    /**
     * Returns a string representation of the provided users.
     * @param userAccounts The user accounts
     * @return A string representation of the provided users.
     */
    private fun usersToString(
        userAccounts: List<UserAccount>?
    ) = userAccounts?.toTypedArray<UserAccount>()?.let {
        usersToString(*it)
    } ?: ""

    /** Sends the logout completed intent */
    private fun sendLogoutCompleteIntent(logoutReason: LogoutReason) =
        appContext.sendBroadcast(Intent(
            LOGOUT_COMPLETE_INTENT_ACTION
        ).apply {
            setPackage(appContext.packageName)
            putExtra(LOGOUT_REASON_KEY, logoutReason.toString())
        })

    /**
     * Sends the cleanup intent for the specified user account.
     * @param userAccount The user account to cleanup for
     */
    private fun sendCleanupIntent(
        userAccount: UserAccount?
    ) = appContext.sendBroadcast(
        Intent(CLEANUP_INTENT_ACTION).apply {
            setPackage(appContext.packageName)
            putExtra(
                PROCESS_ID_KEY,
                PROCESS_ID
            )
            userAccount?.let { userAccount ->
                putExtra(
                    USER_ACCOUNT,
                    userAccount.toBundle()
                )
            }
        }
    )

    private inner class CleanupReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            if (intent.action == CLEANUP_INTENT_ACTION && intent.getStringExtra(PROCESS_ID_KEY) != PROCESS_ID) {
                cleanUp(intent.getBundleExtra(USER_ACCOUNT)?.let { bundle ->
                    UserAccount(bundle)
                })
            }
        }
    }

    /**
     * An action handler in the developer support dialog.
     */
    interface DevActionHandler {

        /** Called when the action is selected */
        fun onSelected()
    }

    /** Indicates if this is a debug build */
    private val isDebugBuild
        get() = getBuildConfigValue(
            appContext,
            "DEBUG"
        ) as Boolean

    /**
     * Gets a field from the project's build configuration.
     *
     * @param context An Android context providing the build configuration's
     * package
     * @param fieldName The name of the build configuration field
     * @return The value of the build configuration field or null if the field
     * is not found
     */
    private fun getBuildConfigValue(
        context: Context,
        @Suppress("SameParameterValue") fieldName: String
    ) = runCatching {
        Class.forName(
            "${context.packageName ?: ""}.BuildConfig"
        ).getField(
            fieldName
        )[null]
    }.onFailure { e ->
        e(TAG, "getBuildConfigValue failed", e)
    }.getOrDefault(DEBUG)

    /**
     * Indicates if the The Salesforce Mobile SDK user interface dark theme is
     * enabled. It defaults to Android's dark theme enablement if a Salesforce
     * Mobile SDK theme is not set.
     *
     * The value is not persistent across instances of Salesforce SDK Manager
     *
     * @see [theme]
     * @return The enabled Salesforce Mobile SDK user interface theme
     */
    val isDarkTheme: Boolean
        get() = when (theme) {
            SYSTEM_DEFAULT ->
                appContext.resources.configuration.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES

            else -> theme == DARK
        }

    /**
     * Sets the system status and navigation bars as visible regardless of style
     * and OS dark theme states.
     *
     * @param activity The activity used to set style attributes
     */
    open fun setViewNavigationVisibility(activity: Activity) {
        if (!isDarkTheme || activity.javaClass.name == loginActivityClass.name) {
            /*
             * This covers the case where OS dark theme is true, but app has
             * disabled.
             */
            activity.window.decorView.systemUiVisibility = SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    /**
     * Determines whether the device has a compact screen.
     * Taken directly from https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#kotlin
     */
    open fun compactScreen(activity: Activity): Boolean {
        val metrics = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(activity)
        val width = metrics.bounds.width()
        val height = metrics.bounds.height()
        val density = activity.resources.displayMetrics.density
        val windowSizeClass = WindowSizeClass.compute(width / density, height / density)

        return windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT ||
                windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
    }

    @Suppress("unused")
    @OnLifecycleEvent(ON_STOP)
    protected open fun onAppBackgrounded() {
        (screenLockManager as ScreenLockManager?)?.onAppBackgrounded()

        // Publish analytics one-time on app background, if enabled.
        if (SalesforceAnalyticsManager.analyticsPublishingType() == PublishOnAppBackground) {
            enqueueAnalyticsPublishWorkRequest(
                getInstance().appContext
            )
        }

        (biometricAuthenticationManager as? BiometricAuthenticationManager)?.onAppBackgrounded()

        // Hide the Salesforce Mobile SDK "Show Developer Support" notification
        authenticatedActivityForDeveloperSupport?.let {
            hideDeveloperSupportNotification(it)
        }
    }

    @Suppress("unused")
    @OnLifecycleEvent(ON_START)
    protected open fun onAppForegrounded() {
        (screenLockManager as ScreenLockManager?)?.onAppForegrounded()
        (biometricAuthenticationManager as? BiometricAuthenticationManager)?.onAppForegrounded()

        // Review push-notifications registration for the current user, if enabled.
        userAccountManager.currentUser?.let { userAccount ->
            if (pushNotificationsRegistrationType == ReRegistrationOnAppForeground) {
                register(
                    context = appContext,
                    account = userAccount,
                    recreateKey = false
                )
            }
        }

        // Display the Salesforce Mobile SDK "Show Developer Support" notification
        if (userAccountManager.currentAccount != null && authenticatedActivityForDeveloperSupport != null) {
            showDeveloperSupportNotification(
                authenticatedActivityForDeveloperSupport
            )
        }
    }

    companion object {

        /**
         * The instance of the SalesforceSDKManager to use for this process
         * TODO: Remove the suppress lint annotation once the Android context is no longer retained.
         */
        @JvmField
        @SuppressLint("StaticFieldLeak")
        protected var INSTANCE: SalesforceSDKManager? = null

        /** The current version of this SDK */
        const val SDK_VERSION = "12.2.0.dev"

        /**
         * An intent action meant for instances of Salesforce SDK manager
         * residing in other processes ordering them to clean up in-memory
         * caches
         */
        private const val CLEANUP_INTENT_ACTION = "com.salesforce.CLEANUP"

        /** The process id key for broadcast intents */
        private const val PROCESS_ID_KEY = "processId"

        /** The unique per-process id added to broadcast intents to prevent self-processing */
        private val PROCESS_ID = randomUUID().toString()

        /** The user account key for broadcast intents  */
        private const val USER_ACCOUNT = "userAccount"

        /** An intent action indicating logout was completed */
        const val LOGOUT_COMPLETE_INTENT_ACTION = "com.salesforce.LOGOUT_COMPLETE"

        /** Key for Logout Reason sent with the logout intent */
        internal const val LOGOUT_REASON_KEY = "logout_reason"

        /** The default app name */
        private const val DEFAULT_APP_DISPLAY_NAME = "Salesforce"

        /** The name for the encryption key */
        private const val INTERNAL_ENTROPY = "6cgs4f"

        /** The logging tag */
        private const val TAG = "SalesforceSDKManager"

        /** The app's AILTN name used by the analytics framework */
        @JvmField
        protected var AILTN_APP_NAME: String? = null

        /** The instance of the SalesforceSDKManager to use for this process */
        @JvmStatic // This removes the `Companion` reference in in Java code
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") // This allows Java subtypes to override this property without an inspector warning
        open fun getInstance() = INSTANCE ?: throw RuntimeException("Apps must call SalesforceSDKManager.init() first.")

        /** Allow Kotlin subclasses to set themselves as the instance. */
        @Suppress("unused")
        @JvmSynthetic
        fun setInstance(subclass: SalesforceSDKManager) {
            INSTANCE = subclass
        }

        /**
         * Indicates if a Salesforce SDK manager instance is initialized.
         * @return true if SalesforceSDKManager has been initialized already
         */
        @JvmStatic
        fun hasInstance() = INSTANCE != null

        /** Returns the app's AILTN name used by the analytics framework */
        @JvmStatic
        var ailtnAppName: String?
            get() = AILTN_APP_NAME
            set(appName) {
                if (!isEmpty(appName)) AILTN_APP_NAME = appName
            }

        /**
         * An initializer for the Salesforce mobile SDK.
         * @param context The Android context
         * @param mainActivity The app's main activity class
         * @param loginActivity The app login activity class
         */
        private fun init(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>? = null,
            nativeLoginActivity: Class<out Activity>? = null,
        ) {
            if (INSTANCE == null) {
                INSTANCE = SalesforceSDKManager(
                    context,
                    mainActivity,
                    loginActivity,
                    nativeLoginActivity,
                )
            }
            initInternal(context)
            EventsObservable.get().notifyEvent(
                AppCreateComplete
            )
        }

        /**
         * An internal initializer for the Salesforce mobile SDK.
         *
         * For internal use by Salesforce mobile SDK or by subclasses of
         * Salesforce SDK manager.
         *
         * @param context The Android context
         */
        @JvmStatic
        fun initInternal(context: Context) {

            // Upgrades to the latest version
            SalesforceSDKUpgradeManager.getInstance().upgrade()

            // Initializes the HTTP client
            HttpAccess.init(context)

            // Enables IDP login flow if it's set through MDM
            val runtimeConfig = getRuntimeConfig(context)
            val idpAppPackageName = runtimeConfig.getString(IDPAppPackageName)
            if (!isEmpty(idpAppPackageName)) {
                INSTANCE?.setIDPAppPackageName(idpAppPackageName)
            }

            // Set up developer support
            setupDeveloperSupport(context)
        }

        /**
         * Initializes required components. Native apps must call one overload
         * of this method before using the Salesforce Mobile SDK.
         *
         * @param context The Android context
         * @param mainActivity The app's main activity class
         */
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        open fun initNative(
            context: Context,
            mainActivity: Class<out Activity>
        ) = init(
            context,
            mainActivity,
            LoginActivity::class.java
        )

        /**
         * Initializes required components. Native apps must call one overload
         * of this method before using the Salesforce Mobile SDK.
         *
         * @param context The Android context
         * @param mainActivity The app's main activity class
         * @param loginActivity The app's login activity
         */
        fun initNative(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>
        ) = init(
            context,
            mainActivity,
            loginActivity,
        )

        /**
         * Initializes required components. Native apps must call one overload
         * of this method before using the Salesforce Mobile SDK.
         *
         * @param context The Android context
         * @param mainActivity The app's main activity class
         * @param loginActivity The app's login activity
         * @param nativeLoginActivity The app's native login activity
         */
        fun initNative(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>? = null,
            nativeLoginActivity: Class<out Activity>,
        ) = init(
            context,
            mainActivity,
            loginActivity,
            nativeLoginActivity,
        )

        /**
         * Sets up an Android activity lifecycle callback to maintain the app's
         * authenticated Android activity reference used when developer support
         * is enabled.
         *
         * @param context Either of the Android app context or an activity
         * context
         */
        private fun setupDeveloperSupport(context: Context) {

            // Resolve the Android application object from the provided context.
            val application = when (context) {
                is Application -> context
                is Activity -> context.application
                else -> null
            }

            // Guards
            val salesforceSDKManager = getInstance()
            if (!salesforceSDKManager.isDevSupportEnabled() || application == null || salesforceSDKManager.activityLifecycleCallbacksForDeveloperSupport != null || salesforceSDKManager.showDeveloperSupportBroadcastIntentReceiver != null)
                return

            /*
             * Register an Android activity lifecycle listener to update the
             * authenticated Android activity at appropriate activity lifecycle
             * events.
             */
            val activityLifecycleCallbacks: ActivityLifecycleCallbacks = object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {
                    /* Intentionally Blank */
                }

                override fun onActivityStarted(activity: Activity) {
                    /* Intentionally Blank */
                }

                override fun onActivityResumed(activity: Activity) {
                    when (activity.javaClass) {
                        salesforceSDKManager.loginActivityClass ->
                            salesforceSDKManager.updateDeveloperSupportForActivityLifecycle(
                                authenticatedActivity = null,
                                lifecycleActivity = activity
                            )

                        else ->
                            salesforceSDKManager.updateDeveloperSupportForActivityLifecycle(
                                authenticatedActivity = activity,
                                lifecycleActivity = activity
                            )
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    /* Intentionally Blank */
                }

                override fun onActivityStopped(activity: Activity) {
                    /* Intentionally Blank */
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    /* Intentionally Blank */
                }

                override fun onActivityDestroyed(activity: Activity) {
                    /* Intentionally Blank */
                }
            }
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            salesforceSDKManager.activityLifecycleCallbacksForDeveloperSupport = activityLifecycleCallbacks

            // Register an Android broadcast intent receiver to respond to the show developer support notification.
            val showDeveloperSupportBroadcastIntentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    salesforceSDKManager.showDevSupportDialog(
                        salesforceSDKManager.authenticatedActivityForDeveloperSupport
                    )
                }
            }
            registerReceiver(
                application,
                showDeveloperSupportBroadcastIntentReceiver,
                IntentFilter(BROADCAST_INTENT_ACTION_SHOW_DEVELOPER_SUPPORT),
                RECEIVER_EXPORTED
            )
            salesforceSDKManager.showDeveloperSupportBroadcastIntentReceiver = showDeveloperSupportBroadcastIntentReceiver
        }

        /**
         * Encrypts the provided data with the provided key.
         *
         * @param data The data to be encrypted
         * @param key The encryption key to use
         * @return The encrypted data
         */
        @JvmStatic
        fun encrypt(
            data: String?,
            key: String?
        ): String? = if (data == null || key == null) null else Encryptor.encrypt(data, key)

        /** The active encryption key */
        @JvmStatic
        val encryptionKey: String
            get() = getEncryptionKey(INTERNAL_ENTROPY)

        /**
         * Decrypts the provided data with the provided key.
         *
         * @param data The data to be decrypted
         * @param key  The encryption key to use
         * @return The decrypted data
         */
        @JvmStatic
        fun decrypt(
            data: String?,
            key: String?
        ): String? = if (data == null || key == null) null else Encryptor.decrypt(data, key)
    }

    /**
     * Fetches the authentication configuration, if required.
     *
     * If this takes more than five seconds it can cause Android's application
     * not responding report.
     *
     * @param completion An optional function to invoke at the end of the action
     */
    fun fetchAuthenticationConfiguration(
        completion: (() -> Unit)? = null
    ) = CoroutineScope(Default).launch {
        runCatching {
            withTimeout(5000L) {
                val loginServer = loginServerManager.selectedLoginServer?.url?.trim { it <= ' ' } ?: return@withTimeout

                if (loginServer == PRODUCTION_LOGIN_URL || loginServer == SANDBOX_LOGIN_URL || !isHttpsUrl(loginServer) || loginServer.toHttpUrlOrNull() == null) {
                    setBrowserLoginEnabled(
                        browserLoginEnabled = false,
                        shareBrowserSessionEnabled = false
                    )

                    return@withTimeout
                }

                getMyDomainAuthConfig(loginServer).let { authConfig ->
                    setBrowserLoginEnabled(
                        browserLoginEnabled = authConfig?.isBrowserLoginEnabled ?: false,
                        shareBrowserSessionEnabled = authConfig?.isShareBrowserSessionEnabled ?: false
                    )
                }
            }
        }.onFailure { e ->
            e(TAG, "Exception occurred while fetching authentication configuration", e)
        }

        completion?.invoke()
    }
}
