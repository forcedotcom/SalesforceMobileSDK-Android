package com.salesforce.androidsdk.auth

import kotlinx.serialization.Serializable

/**
 * Models a `sfap_api` "generations" endpoint request.
 * @param prompt The request prompt parameter value
 */
@Serializable
data class SfapApiGenerationsRequestBody(
    val prompt: String
)
