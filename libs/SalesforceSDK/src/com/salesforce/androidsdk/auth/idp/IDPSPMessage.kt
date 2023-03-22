package com.salesforce.androidsdk.auth.idp

import android.content.Intent
import android.os.Bundle
import java.util.*

/**
 * Super class of messages being sent between IDP and SP app
 * Message can be built from intent, and intent can be built from message.
 */
sealed class IDPSPMessage(
    val action:String,
    val uuid:String,
) {

    fun toIntent(): Intent {
        return Intent(action).apply {
            putExtras(toBundle())
        }
    }

    open fun toBundle(): Bundle {
        return Bundle().apply {
            putString(UUID_KEY, uuid)
        }
    }

    companion object {
        const val UUID_KEY = "uuid"

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
            val TAG = IDPLoginRequest::class.java.simpleName
            const val ACTION = "com.salesforce.IDP_LOGIN_REQUEST"
            const val USER_ID_KEY = "user_id"
            const val ORG_ID_KEY = "org_id"

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
        uuid: String
    ) : IDPSPMessage(uuid, ACTION) {

        companion object {
            val TAG = IDPLoginResponse::class.java.simpleName
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
     * or during an IDP initiated login when the SP app does not already have the hinted user
     */
    class SPLoginRequest(
        uuid: String = UUID.randomUUID().toString(),
        val challenge:String,
        val loginUrl: String
    ): IDPSPMessage(uuid, ACTION) {

        companion object {
            val TAG = SPLoginRequest::class.java.simpleName
            const val ACTION = "com.salesforce.SP_LOGIN_REQUEST"
            const val CHALLENGE_KEY = "challenge_key"
            const val LOGIN_URL_KEY = "login_url"

            fun fromBundle(bundle:Bundle?) : SPLoginRequest? {
                val uuid = bundle?.getString(UUID_KEY)
                val challenge = bundle?.getString(CHALLENGE_KEY)
                val loginUrl = bundle?.getString(LOGIN_URL_KEY)
                return if (uuid != null && challenge != null && loginUrl != null) {
                    SPLoginRequest(uuid, challenge, loginUrl)
                } else {
                    null
                }
            }
        }

        override fun toBundle(): Bundle {
            return super.toBundle().apply {
                putString(CHALLENGE_KEY, challenge)
                putString(LOGIN_URL_KEY, loginUrl)
            }
        }
    }

    /**
     * Message sent by IDP app to SP app in response to SPLoginRequest
     * when an auth code was successfully obtained from server
     */
    class SPLoginResponse(
        uuid: String = UUID.randomUUID().toString(),
        val code: String?
    ): IDPSPMessage(uuid, ACTION) {

        companion object {
            val TAG = SPLoginResponse::class.java.simpleName
            const val ACTION = "com.salesforce.SP_LOGIN_RESPONSE"
            const val CODE_KEY = "code_key"

            fun fromBundle(bundle:Bundle?) : SPLoginResponse? {
                val uuid = bundle?.getString(UUID_KEY)
                val code = bundle?.getString(CODE_KEY)
                return if (uuid != null && code != null) {
                    SPLoginResponse(uuid, code)
                } else {
                    null
                }
            }
        }

        override fun toBundle(): Bundle {
            return super.toBundle().apply {
                putString(CODE_KEY, code)
            }
        }
    }
}