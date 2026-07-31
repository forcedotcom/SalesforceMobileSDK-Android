/*
 * Copyright (c) 2025-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.util

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import java.net.URL
import java.net.MalformedURLException

/**
 *  Returns the host if the string is a valid url.
 */
fun String.urlHostOrNull() : String? {
    return try {
        URL(this).host
    } catch (_: MalformedURLException) {
        null
    }
}

/**
 * Returns whether this account and an authenticated REST client represent the same Salesforce
 * user.
 *
 * [UserAccount.equals] already provides this comparison for two user accounts. This overload is
 * needed only because [ClientInfo] is a different type. Local Android account names and mutable
 * client properties such as tokens, instance URLs, and session-cookie fields are intentionally
 * ignored.
 */
fun UserAccount?.isSameUser(clientInfo: ClientInfo?): Boolean {
    val user = this ?: return false
    val client = clientInfo ?: return false
    return !user.userId.isNullOrBlank() &&
        !user.orgId.isNullOrBlank() &&
        !client.userId.isNullOrBlank() &&
        !client.orgId.isNullOrBlank() &&
        user.userId == client.userId &&
        user.orgId == client.orgId
}
