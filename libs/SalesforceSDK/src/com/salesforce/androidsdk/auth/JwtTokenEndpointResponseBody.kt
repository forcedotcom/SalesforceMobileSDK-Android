package com.salesforce.androidsdk.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json.Default.decodeFromString

/**
 * Models an OAuth2 token endpoint JSON Web Token (JWT) response.
 */
@Serializable
data class JwtTokenEndpointResponseBody(
    @SerialName("access_token") val accessToken: String? = null,
    val signature: String? = null,
    @SerialName("token_format") val tokenFormat: String? = null,
    val scope: String? = null,
    @SerialName("instance_url") val instanceUrl: String? = null,
    val id: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("api_instance_url") val apiInstanceUrl: String? = null
) {

    companion object {

        /**
         * Returns an OAuth 2.0 token endpoint JSON Web Token (JWT) response
         * from the JSON text.
         * @param json The JSON text
         * @return The OAuth 2.0 token endpoint JSON Web Token (JWT) response
         */
        fun fromJson(json: String) = decodeFromString<JwtTokenEndpointResponseBody>(json)
    }
}
