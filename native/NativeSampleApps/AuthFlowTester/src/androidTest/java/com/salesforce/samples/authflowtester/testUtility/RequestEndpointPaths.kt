/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the copyright notice, conditions and disclaimer are
 * retained. Neither salesforce.com nor contributors' names may be used to endorse
 * derived products without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING
 * FROM USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.authflowtester.testUtility

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.RestClient
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Expected path-only observations for owner-bound requests (not the selected login host). */
internal object RequestEndpointPaths {
    fun rest(clientInfo: RestClient.ClientInfo, path: String): String =
        clientInfo.resolveUrl(path).toString().toHttpUrl().encodedPath

    // Refresh uses OAuth2's owner-specific server selection and literal endpoint concatenation,
    // not REST resolution (which normalizes the joining slash). Match capture's encodedPath.
    fun token(owner: UserAccount): String =
        "${OAuth2.overrideLoginServerIfNeeded(owner)}/services/oauth2/token".toHttpUrl().encodedPath
}
