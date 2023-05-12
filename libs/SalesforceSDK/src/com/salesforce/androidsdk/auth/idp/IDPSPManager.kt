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
package com.salesforce.androidsdk.auth.idp

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.util.LogUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger

internal open class ActiveFlow(val context: Context) {
    val messages = ArrayList<IDPSPMessage>()

    val firstMessage: IDPSPMessage
        get() = messages.first()
    fun addMessage(message: IDPSPMessage) : Boolean {
        return if (messages.isEmpty() || (messages.last().uuid == message.uuid)) {
            SalesforceSDKLogger.d(this::class.java.simpleName, "Adding message to active flow: $message")
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

internal abstract class IDPSPManager(
    val sendBroadcast: (context: Context, intent: Intent) -> Unit,
    val startActivity: (context: Context, intent: Intent) -> Unit
) {
    companion object {
        const val SRC_APP_PACKAGE_NAME_KEY = "src_app_package_name"
    }

    /**
     * Return the currently active flow if any
     */
    abstract fun getActiveFlow(): ActiveFlow?

    /**
     * End the currently active flow
     */
    abstract fun endActiveFlow()

    /**
     * Make the passed flow the active flow
     */
    abstract fun startActiveFlow(flow: ActiveFlow)

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
     * - adds it to an existing active flow if the message uuid matches
     * - ends an existing active flow if the message uuid does not match
     */
    fun send(context: Context, message: IDPSPMessage, destinationAppPackageName: String) {
        addToActiveFlowIfApplicable(message)
        val intent = message.toIntent().apply {
            putExtra(SRC_APP_PACKAGE_NAME_KEY, context.applicationInfo.packageName)
            setPackage(destinationAppPackageName)
        }
        sendBroadcast(context, intent)
    }

    /**
     * Add message to an existing active flow if the message uuid matches
     * Ends an existing active flow if the message uuid does not match
     */
    fun addToActiveFlowIfApplicable(message: IDPSPMessage) {
        getActiveFlow()?.let { activeFlow ->
            if (activeFlow.addMessage(message)) {
                // There is an active flow and the message is part of it
            } else {
                // There is an active flow and the message is NOT part of it
                // End active flow
                endActiveFlow()
            }
        }
    }

    /**
     * Process received intent
     * Build IDPSPMessage from it
     * - adds it to an existing active flow if the message uuid matches
     * - ends an existing active flow if the message uuid does not match
     */
    fun onReceive(context: Context, intent: Intent) {
        SalesforceSDKLogger.d(
            this::class.java.simpleName,
            "onReceive ${LogUtil.intentToString(intent)}"
        )
        getSrcAppPackageName(intent)?.let { srcAppPackageName ->
            if (!isAllowed(srcAppPackageName)) {
                SalesforceSDKLogger.w(
                    this::class.java.simpleName,
                    "onReceive not allowed to handle ${LogUtil.intentToString(intent)}"
                )
            } else {
                IDPSPMessage.fromIntent(intent)?.let { message ->
                    getActiveFlow()?.let { activeFlow ->
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
                    SalesforceSDKLogger.w(
                        this::class.java.simpleName,
                        "onReceive could not parse ${LogUtil.intentToString(intent)}"
                    )
                }
            }
        }
    }

    /**
     * Return source app package name from intent
     */
    fun getSrcAppPackageName(intent: Intent): String? {
        return intent.getStringExtra(SRC_APP_PACKAGE_NAME_KEY)
    }
}