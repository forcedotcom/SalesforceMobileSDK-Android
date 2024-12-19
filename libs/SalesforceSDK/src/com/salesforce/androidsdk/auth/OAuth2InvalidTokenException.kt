package com.salesforce.androidsdk.auth

/**
 * An exception indicating an invalid Salesforce OAuth 2.0 JSON Web Token (JWT) was used.
 */
class OAuth2InvalidTokenException(message: String) : Exception(message)
