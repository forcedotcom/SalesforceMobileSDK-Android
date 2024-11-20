package com.salesforce.androidsdk.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.Date

@Serializable
data class JwtHeader(
    @SerialName("alg") val algorithn: String? = null,
    @SerialName("typ") val type: String? = null,
    @SerialName("kid") val keyId: String? = null,
    @SerialName("tty") val tokenType: String? = null,
    @SerialName("tnk") val tenantKey: String? = null,
    @SerialName("ver") val version: String? = null,
)

@Serializable
data class JwtPayload(
    @SerialName("aud") val audience: List<String>? = null,
    @SerialName("exp") val expirationTime: Int? = null,
    @SerialName("iss") val issuer: String? = null,
    @SerialName("nbf") val notBeforeTime: Int? = null,
    @SerialName("sub") val subject: String? = null,
    @SerialName("scp") val scopes: String? = null,
    @SerialName("client_id") val clientId: String? = null,
)

/**
 * Data class for decoding JWT-based access token
 */
data class JwtAccessToken(
    val rawJwt: String,             // Captures the raw JWT string
    val header: JwtHeader,
    val payload: JwtPayload
) {
    // Secondary constructor to parse the JWT string and initialize properties
    constructor(jwt: String) : this(
        rawJwt = jwt,
        header = parseJwtHeader(jwt),
        payload = parseJwtPayload(jwt)
    )

    fun expirationDate(): Date? {
        val expirationTime = payload.expirationTime ?: return null
        return Date(expirationTime.toLong() * 1000) // Convert seconds to milliseconds
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }  // Ignore unknown keys

        private fun parseJwtHeader(jwt: String): JwtHeader {
            val parts = jwt.split(".")
            require(parts.size >= 2) { "Invalid JWT format" }

            val headerJson = String(Base64.getUrlDecoder().decode(parts[0]))
            return json.decodeFromString(headerJson)
        }

        private fun parseJwtPayload(jwt: String): JwtPayload {
            val parts = jwt.split(".")
            require(parts.size >= 2) { "Invalid JWT format" }

            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]))
            return json.decodeFromString(payloadJson)
        }
    }
}