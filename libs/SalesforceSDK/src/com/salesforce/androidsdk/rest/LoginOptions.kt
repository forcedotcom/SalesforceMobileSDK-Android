package com.salesforce.androidsdk.rest

/**
 * Class encapsulating login options.
 */
internal class LoginOptions(
    var loginUrl: String?,
    val oauthClientId: String,
    val oauthScopes: Array<String>,
)