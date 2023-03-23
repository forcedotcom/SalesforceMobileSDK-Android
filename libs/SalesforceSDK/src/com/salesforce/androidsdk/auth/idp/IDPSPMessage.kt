package com.salesforce.androidsdk.auth.idp

import android.content.Intent
import android.os.Bundle
import com.salesforce.androidsdk.util.LogUtil
import java.util.*

/**
 * Super class of messages being sent between IDP and SP app
 * Message can be built from intent, and intent can be built from message.
 */
internal sealed class IDPSPMessage(
    val uuid:String,
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
        private const val UUID_KEY = "uuid"

        fun fromIntent(intent:Intent):IDPSPMessage? {
            return if (intent.extras == null) {
                null
            } else {
                return when (intent.action) {
                    IDPLoginRequest.ACTION -> {
                        IDPLoginRequest.fromBundle(intent.extras)
                    }
                    IDPLoginResponse.ACTION -> {
                        IDPLoginResponse.fromBundle(intent.extras)
                    }
                    SPLoginRequest.ACTION -> {
                        SPLoginRequest.fromBundle(intent.extras)
                    }
                    SPLoginResponse.ACTION -> {
                        SPLoginResponse.fromBundle(intent.extras)
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
    class IDPLoginRequest(
        uuid: String = UUID.randomUUID().toString(),
        val orgId: String,
        val userId: String
    ) : IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.IDP_LOGIN_REQUEST"
            private const val USER_ID_KEY = "user_id"
            private const val ORG_ID_KEY = "org_id"

            fun fromBundle(bundle:Bundle?) : IDPLoginRequest? {
                val uuid = bundle?.getString(UUID_KEY)
                val userId = bundle?.getString(USER_ID_KEY)
                val orgId = bundle?.getString(ORG_ID_KEY)
                return if (uuid != null && userId != null && orgId != null) {
                    IDPLoginRequest(uuid, userId, orgId)
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
     * Message sent by SP to IDP in response to IDPLoginRequest
     * when SP app already has hinted user
     */
    class IDPLoginResponse(
        uuid: String,
    ) : IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.IDP_LOGIN_RESPONSE"

            fun fromBundle(bundle:Bundle?) : IDPLoginResponse? {
                val uuid = bundle?.getString(UUID_KEY)
                return if (uuid != null) {
                    IDPLoginResponse(uuid)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Message sent from SP app to IDP either to kick-off a SP initiated login
     * or during an IDP initiated login when the SP app does not the requested user
     */
    class SPLoginRequest(
        uuid: String = UUID.randomUUID().toString(),
        val codeChallenge:String,
    ): IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.SP_LOGIN_REQUEST"
            private const val CODE_CHALLENGE_KEY = "code_challenge"

            fun fromBundle(bundle:Bundle?) : SPLoginRequest? {
                val uuid = bundle?.getString(UUID_KEY)
                val codeChallenge = bundle?.getString(CODE_CHALLENGE_KEY)
                return if (uuid != null && codeChallenge != null) {
                    SPLoginRequest(uuid, codeChallenge)
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
     * Message sent by IDP app to SP app in response to SPLoginRequest
     */
    class SPLoginResponse(
        uuid: String = UUID.randomUUID().toString(),
        val code: String? = null,
        val loginUrl: String? = null,
        val error: String? = null
    ): IDPSPMessage(uuid, ACTION) {

        companion object {
            const val ACTION = "com.salesforce.SP_LOGIN_RESPONSE"
            private const val CODE_KEY = "code"
            private const val LOGIN_URL_KEY = "login_url"
            private const val ERROR_KEY = "error"

            fun fromBundle(bundle:Bundle?) : SPLoginResponse? {
                val uuid = bundle?.getString(UUID_KEY)
                val code = bundle?.getString(CODE_KEY)
                val loginUrl = bundle?.getString(LOGIN_URL_KEY)
                val error = bundle?.getString(ERROR_KEY)
                return if (uuid != null && ((code != null && loginUrl != null) || error != null)) {
                    SPLoginResponse(uuid, code, loginUrl, error)
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