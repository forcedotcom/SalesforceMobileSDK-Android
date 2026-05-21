package com.salesforce.androidsdk.auth

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.Date

private object ScopeStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScopeString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.joinToString(" ") { it.jsonPrimitive.content }
            is JsonPrimitive -> element.content
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value)
    }
}

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
    @Serializable(with = ScopeStringSerializer::class)
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