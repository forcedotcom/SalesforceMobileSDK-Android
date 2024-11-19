/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.samples.restexplorer

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.salesforce.androidsdk.analytics.logger.SalesforceLogReceiver
import com.salesforce.androidsdk.analytics.logger.SalesforceLogReceiverFactory
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level.DEBUG
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level.ERROR
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level.INFO
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level.OFF
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level.VERBOSE
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger.Level.WARN
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.getInstance
import com.salesforce.androidsdk.ui.LoginActivity

/**
 * Application class for the REST Explorer Salesforce Mobile SDK sample app.
 */
internal class RestExplorerApp : Application() {

    // region REST Explorer App Implementation

    /** The map of custom Salesforce Log Receivers by their component name */
    private val logReceiversByComponentName = mutableMapOf<String, SalesforceLogReceiver>()

    // endregion
    // region Application Implementation

    override fun onCreate() {
        super.onCreate()

        /*
		 * Extend Salesforce SDK Manager to add custom developer menu options to the app.
		 *
		 * Normal use would be: SalesforceSDKManager.initNative(applicationContext, ExplorerActivity::class.java)
		 */
        RestExplorerSDKManager.initNative(applicationContext, ExplorerActivity::class.java)

        // Use the default browser for advanced authentication.
        getInstance().customTabBrowser = null

        /*
         * Uncomment the following line to enable the IDP login flow. This will allow the user to
         * either authenticate using the current app or use the designated IDP app for login.
         * Replace 'com.salesforce.samples.salesforceandroididptemplateapp' with the package name of
         * the IDP app meant to be used.
         */
        getInstance().setIDPAppPackageName("com.salesforce.samples.salesforceandroididptemplateapp")

        SalesforceLogger.setLogReceiverFactory(object : SalesforceLogReceiverFactory {
            override fun create(componentName: String): SalesforceLogReceiver = logReceiversByComponentName[componentName] ?: object : SalesforceLogReceiver {
                override fun receive(
                    level: Level,
                    tag: String,
                    message: String
                ) {
                    val resolvedMessage = "$componentName - $message"
                    when (level) {
                        OFF -> {}
                        ERROR -> Log.e("RestExplorerApp", resolvedMessage)
                        WARN -> Log.e("RestExplorerApp", resolvedMessage)
                        INFO -> Log.e("RestExplorerApp", resolvedMessage)
                        DEBUG -> Log.e("RestExplorerApp", resolvedMessage)
                        VERBOSE -> Log.e("RestExplorerApp", resolvedMessage)
                    }
                }

                override fun receive(
                    level: Level,
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    val resolvedMessage = "$componentName - $message"
                    when (level) {
                        OFF -> {}
                        ERROR -> Log.e("RestExplorerApp", resolvedMessage, throwable)
                        WARN -> Log.e("RestExplorerApp", resolvedMessage, throwable)
                        INFO -> Log.e("RestExplorerApp", resolvedMessage, throwable)
                        DEBUG -> Log.e("RestExplorerApp", resolvedMessage, throwable)
                        VERBOSE -> Log.e("RestExplorerApp", resolvedMessage, throwable)
                    }
                }
            }
        })

        /*
		 * Uncomment the following lines to enable push notifications in this app. Either implement
		 * the methods of the anonymous object or replace it with an instance of another class
		 * implementing 'PushNotificationInterface'. Add your Firebase 'google-services.json' file
		 * to the 'app' folder of your project.
		 */
//        getInstance().pushNotificationReceiver = object : PushNotificationInterface {
//            override fun onPushMessageReceived(data: Map<String?, String?>?) {
//            }
//
//            override fun supplyFirebaseMessaging(): FirebaseMessaging? = null
//        }
    }

    // endregion
    // region REST Explorer Salesforce SDK Manager

    internal class RestExplorerSDKManager

    /**
     * A protected constructor.
     *
     * @param context The application context
     * @param mainActivity The activity to be launched after the login flow
     * @param loginActivity The login activity
     */
    private constructor(
        context: Context,
        mainActivity: Class<out Activity?>,
        loginActivity: Class<out Activity?>
    ) : SalesforceSDKManager(
        context,
        mainActivity,
        loginActivity
    ) {

        private var frontActivityForDevActions: Activity? = null
        private var devActions: Map<String, DevActionHandler>? = null

        public override fun getDevActions(
            frontActivity: Activity
        ): Map<String, DevActionHandler> {
            var result: Map<String, DevActionHandler>? = devActions
            if (frontActivityForDevActions !== frontActivity) {
                frontActivityForDevActions = frontActivity
                result = null
            }

            if (result == null) {
                result = super.getDevActions(frontActivity)
            }

            devActions = result
            return result
        }

        fun addDevAction(
            frontActivity: Activity,
            name: String,
            handler: DevActionHandler
        ) {
            val updatedDevActions = getDevActions(frontActivity).toMutableMap()
            updatedDevActions[name] = handler
            devActions = updatedDevActions
        }

        companion object {

            /**
             * Initializes required components. Native apps must call one overload of this method
             * before using the Salesforce Mobile SDK.
             *
             * @param context Application context
             * @param mainActivity Activity that should be launched after the login flow
             */
            fun initNative(
                context: Context,
                mainActivity: Class<out Activity?>
            ) {
                if (!hasInstance()) {
                    setInstance(RestExplorerSDKManager(context, mainActivity, LoginActivity::class.java))
                }
                initInternal(context)
            }
        }
    }

    // endregion
}
