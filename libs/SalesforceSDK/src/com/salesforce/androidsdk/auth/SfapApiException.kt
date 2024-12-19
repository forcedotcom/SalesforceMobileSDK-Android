package com.salesforce.androidsdk.auth

/**
 * An exception derived from an `sfap_api` endpoint failure response.
 * @param message The exception message
 */
class SfapApiException(message: String?) : Exception(message)
