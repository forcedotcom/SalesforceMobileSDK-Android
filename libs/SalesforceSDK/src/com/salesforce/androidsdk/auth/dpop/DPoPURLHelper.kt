/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth.dpop

import android.net.Uri

object DPoPURLHelper {

    // WORKAROUND (W-24564590): server-side DPoP htu validation bug.
    // Experience Cloud sites with a URL path prefix (e.g. "/site") return an internal
    // community URL ("/sitevforcesite"). The server validates htu against the public
    // path and rejects the internal one with invalid_dpop_proof (token, refresh) or
    // INVALID_AUTH_HEADER (REST), so the internal suffix is dropped from the first path segment.
    // TODO: Remove this regex and its use in canonicalize() once W-24564590 is fixed.
    private val internalSitePrefix = Regex("^/([^/]+)vforcesite(?=/|$)", RegexOption.IGNORE_CASE)

    fun canonicalize(url: String): String {
        val uri = Uri.parse(url).buildUpon().clearQuery().fragment(null).build()
        val path = uri.encodedPath ?: return uri.toString()
        // WORKAROUND (W-24564590): see internalSitePrefix.
        val publicPath = internalSitePrefix.replaceFirst(path, "/$1")
        return if (publicPath == path) uri.toString()
            else uri.buildUpon().encodedPath(publicPath).build().toString()
    }
}
