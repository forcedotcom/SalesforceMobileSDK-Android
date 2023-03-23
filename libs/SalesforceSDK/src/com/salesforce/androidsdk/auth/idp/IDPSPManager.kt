package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

internal open class ActiveFlow(val context: Context) {
    private val messages = ArrayList<IDPSPMessage>()

    val firstMessage: IDPSPMessage
        get() = messages.first()
    fun addMessage(message: IDPSPMessage) : Boolean {
        return if (messages.isEmpty() || messages.last().uuid == message.uuid) {
            messages.add(message)
            true
        } else {
            false
        }
    }

    fun isPartOfFlow(message: IDPSPMessage) : Boolean {
        return messages.contains(message)
    }
}

internal abstract class IDPSPManager() {
    companion object {
        private const val SRC_APP_PACKAGE_NAME_KEY = "src_app_package_name"
    }

    val sdkMgr: SalesforceSDKManager
        get() = SalesforceSDKManager.getInstance()

    /**
     * Return the currently active flow if any
     */
    abstract fun getActiveFlow(): ActiveFlow?

    /**
     * The previously active flow is over
     */
    abstract fun endActiveFlow()

    /**
     * Return true if messages from srcAppPackageName are allowed
     */
    abstract fun isAllowed(srcAppPackageName: String): Boolean

    /**
     * Handle given message
     */
    abstract fun handle(context: Context, message: IDPSPMessage, srcAppPackageName: String)

    /**
     * Sends message
     */
    fun send(context: Context, message: IDPSPMessage, destinationAppPackageName: String) {
        val intent = message.toIntent().apply {
            putExtra(SRC_APP_PACKAGE_NAME_KEY, context.applicationInfo.packageName)
            setPackage(destinationAppPackageName)
        }
        SalesforceSDKLogger.d(this::class.java.simpleName, "send ${LogUtil.intentToString(intent)}")
        context.sendBroadcast(intent)
    }

    /**
     * Process received intent
     */
    fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(this::class.java.simpleName, "onReceive ${LogUtil.intentToString(intent)}")
        intent.getStringExtra(SRC_APP_PACKAGE_NAME_KEY)?.let { srcAppPackageName ->
            if (!isAllowed(srcAppPackageName)) {
                SalesforceSDKLogger.w(this::class.java.simpleName, "onReceive not allowed to handle ${LogUtil.intentToString(intent)}")
            } else {
                IDPSPMessage.fromIntent(intent)?.let { message ->
                    getActiveFlow()?.let {activeFlow ->
                        if (activeFlow.addMessage(message)) {
                            // There is an active flow and the message is part of it
                            // Handle the message with the active flow's context
                            handle(activeFlow.context, message, srcAppPackageName)
                        } else {
                            // There is an active flow and the message is NOT part of it
                            // End active flow and handle message with context passed in
                            endActiveFlow()
                            handle(context, message, srcAppPackageName)
                        }
                    } ?: run {
                        // There is NO active flow
                        // Handle message with context passed in
                        handle(context, message, srcAppPackageName)
                    }
                } ?: run {
                    SalesforceSDKLogger.w(this::class.java.simpleName, "onReceive could not parse ${LogUtil.intentToString(intent)}")
                }
            }
        }
    }
}