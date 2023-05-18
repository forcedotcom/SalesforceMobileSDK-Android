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

import android.content.Intent
import android.os.Bundle
import com.salesforce.androidsdk.util.LogUtil
import java.util.UUID

/**
 * Super class of messages being sent between IDP and SP app
 * Message can be built from intent, and intent can be built from message.
 */
internal sealed class IDPSPMessage(
    open val uuid:String,
    val action:String,
) {
    fun toIntent(): Intent {
        return Intent(action).apply {
            putExtras(toBundle())
        }
    }


    override fun toString(): String {
        return "message for ${LogUtil.intentToString(toIntent())}"
    }

    open fun toBundle(): Bundle {
        return Bundle().apply {
            putString(UUID_KEY, uuid)
        }
    }

    companion object {
        const val ACTION_KEY = "action"
        const val UUID_KEY = "uuid"

        fun fromIntent(intent:Intent):IDPSPMessage? {
            return if (intent.extras == null) {
                null
            } else {
                // Using action from extras if found otherwise use intent's action
                // - message action is passed through extras in activity launch intents
                // - message action is set as intent action in broadcast intents
                val extrasAction = intent.getStringExtra(ACTION_KEY)
                val intentAction = intent.action
                return when (extrasAction ?: intentAction) {
                    IDPToSPRequest.ACTION -> {
                        IDPToSPRequest.fromBundle(intent.extras)
                    }
                    SPToIDPResponse.ACTION -> {
                        SPToIDPResponse.fromBundle(intent.extras)
                    }
                    SPToIDPRequest.ACTION -> {
                        SPToIDPRequest.fromBundle(intent.extras)
                    }
                    IDPToSPResponse.ACTION -> {
                        IDPToSPResponse.fromBundle(intent.extras)
                    }
                    else -> {
                        null
                    }
                }
            }
        }
    }

    /**
     * Message sent by IDP to SP to kick-off an IDP initiated login
     */
    class IDPToSPRequest(
        uuid: String = UUID.randomUUID().toString(),
        val orgId: String,
        val userId: String
    ) : IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.androidsdk.IDP_TO_SP_REQUEST"
            private const val USER_ID_KEY = "user_id"
            private const val ORG_ID_KEY = "org_id"

            fun fromBundle(bundle:Bundle?) : IDPToSPRequest? {
                val uuid = bundle?.getString(UUID_KEY)
                val userId = bundle?.getString(USER_ID_KEY)
                val orgId = bundle?.getString(ORG_ID_KEY)
                return if (uuid != null && userId != null && orgId != null) {
                    IDPToSPRequest(uuid, orgId, userId)
                } else {
                    null
                }
            }
        }

        override fun toBundle(): Bundle {
            return super.toBundle().apply {
                putString(USER_ID_KEY, userId)
                putString(ORG_ID_KEY, orgId)
            }
        }
    }

    /**
     * Message sent by SP to IDP when flow is complete either successfully or not
     */
    class SPToIDPResponse(
        uuid: String,
        val error: String? = null
    ) : IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.androidsdk.SP_TO_IDP_RESPONSE"
            private const val ERROR_KEY = "error"

            fun fromBundle(bundle:Bundle?) : SPToIDPResponse? {
                val uuid = bundle?.getString(UUID_KEY)
                val error = bundle?.getString(ERROR_KEY)
                return if (uuid != null) {
                    SPToIDPResponse(uuid, error)
                } else {
                    null
                }
            }
        }

        override fun toBundle(): Bundle {
            return super.toBundle().apply {
                putString(ERROR_KEY, error)
            }
        }
    }

    /**
     * Message sent from SP app to IDP either to kick-off a SP initiated login
     * or during an IDP initiated login when the SP app does not the requested user
     */
    class SPToIDPRequest(
        uuid: String = UUID.randomUUID().toString(),
        val codeChallenge:String,
    ): IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.androidsdk.SP_TO_IDP_REQUEST"
            private const val CODE_CHALLENGE_KEY = "code_challenge"

            fun fromBundle(bundle:Bundle?) : SPToIDPRequest? {
                val uuid = bundle?.getString(UUID_KEY)
                val codeChallenge = bundle?.getString(CODE_CHALLENGE_KEY)
                return if (uuid != null && codeChallenge != null) {
                    SPToIDPRequest(uuid, codeChallenge)
                } else {
                    null
                }
            }
        }

        override fun toBundle(): Bundle {
            return super.toBundle().apply {
                putString(CODE_CHALLENGE_KEY, codeChallenge)
            }
        }
    }

    /**
     * Message sent by IDP app to SP app in response to a SPToIDPRequest
     */
    class IDPToSPResponse(
        uuid: String = UUID.randomUUID().toString(),
        val code: String? = null,
        val loginUrl: String? = null,
        val error: String? = null
    ): IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.androidsdk.IDP_TO_SP_RESPONSE"
            private const val CODE_KEY = "code"
            private const val LOGIN_URL_KEY = "login_url"
            private const val ERROR_KEY = "error"

            fun fromBundle(bundle:Bundle?) : IDPToSPResponse? {
                val uuid = bundle?.getString(UUID_KEY)
                val code = bundle?.getString(CODE_KEY)
                val loginUrl = bundle?.getString(LOGIN_URL_KEY)
                val error = bundle?.getString(ERROR_KEY)
                return if (uuid != null && ((code != null && loginUrl != null) || error != null)) {
                    IDPToSPResponse(uuid, code, loginUrl, error)
                } else {
                    null
                }
            }
        }

        override fun toBundle(): Bundle {
            return super.toBundle().apply {
                putString(CODE_KEY, code)
                putString(LOGIN_URL_KEY, loginUrl)
                putString(ERROR_KEY, error)
            }
        }
    }
}