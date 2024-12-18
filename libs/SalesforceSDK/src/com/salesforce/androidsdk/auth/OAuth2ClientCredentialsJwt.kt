package com.salesforce.androidsdk.auth

import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.JwtTokenEndpointResponseBody.Companion.fromJson
import com.salesforce.androidsdk.auth.OAuth2.CLIENT_CREDENTIALS
import com.salesforce.androidsdk.auth.OAuth2.CLIENT_ID
import com.salesforce.androidsdk.auth.OAuth2.CLIENT_SECRET
import com.salesforce.androidsdk.auth.OAuth2.FORMAT
import com.salesforce.androidsdk.auth.OAuth2.GRANT_TYPE
import com.salesforce.androidsdk.auth.OAuth2.JSON
import com.salesforce.androidsdk.auth.OAuth2.OAUTH_TOKEN_PATH
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.auth.OAuth2.TokenErrorResponse
import okhttp3.FormBody
import okhttp3.Request

/**
 * Provides a Salesforce OAuth 2.0 JSON Web Token (JWT) with the client
 * credentials grant type.
 *
 * Salesforce Setup can provide ECA client id and secret in the External Client
 * App Manager under Settings, OAuth Settings, Consumer Key and Secret.
 *
 * An existing JWT may be provided on initialization or a new one will be
 * fetched.
 *
 * @param instanceUri The Salesforce instance URI
 * @param clientId The Salesforce Connected App (CA) or External Client App
 * (ECA) client id from which to request the JWT.  This is known as Consumer Key
 * for ECA
 * @param clientSecret The CA or ECA client secret.  This is known as Consumer
 * Secret
 * @param jwt A previously issued JWT to reuse rather than fetching a new one
 */
class OAuth2ClientCredentialsJwt(
    private val instanceUri: String,
    private val clientId: String,
    private val clientSecret: String,
    private var jwt: String? = null
) {

    /**
     * The current Salesforce OAuth 2.0 JSON Web Token (JWT) with the client
     * credentials grant type
     */
    val token: String?
        get() = jwt

    /**
     * Fetches a new Salesforce OAuth 2.0 JSON Web Token (JWT) with the client
     * credentials grant type.
     */
    @Throws(OAuthFailedException::class)
    fun fetch(): String? {

        // Request the new JWT.
        val response = DEFAULT.okHttpClient.newCall(
            Request.Builder().url(instanceUri + OAUTH_TOKEN_PATH).post(
                FormBody.Builder().apply {
                    add(CLIENT_ID, clientId)
                    add(CLIENT_SECRET, clientSecret)
                    add(FORMAT, JSON)
                    add(GRANT_TYPE, CLIENT_CREDENTIALS)
                }.build()
            ).build()
        ).execute()

        // Save the new JWT if possible.
        val responseBody = response.body
        return if (response.isSuccessful && responseBody != null) {
            val responseModel = fromJson(responseBody.string())
            jwt = responseModel.accessToken
            responseModel.accessToken
        } else {
            throw OAuthFailedException(
                TokenErrorResponse(response),
                response.code
            )
        }
    }

    /**
     * An exception indicating an invalid Salesforce OAuth 2.0 JSON Web Token (JWT) was used.
     */
    class OAuth2InvalidTokenException(message: String) : Exception(message)
}
