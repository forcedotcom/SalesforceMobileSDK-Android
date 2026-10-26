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
package com.salesforce.androidsdk.accounts

import com.salesforce.androidsdk.auth.downloadProfilePhotoToFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes photo downloads so two of them never write the same cache file at once. */
private val profilePhotoMutex = Mutex()

/**
 * Downloads this user's profile photo into the cache on a background coroutine.
 * The request is authenticated like other API calls, including a DPoP proof
 * for DPoP-bound credentials.  Failures are non-fatal.
 *
 * @param onlyIfMissing When true, does nothing if a photo is already cached
 * @return The download job, or null if there is nothing to download
 */
internal fun UserAccount.launchProfilePhotoDownload(onlyIfMissing: Boolean): Job? {
    val file = profilePhotoFile
    val url = photoUrl
    if (url == null || file == null || (onlyIfMissing && file.exists())) {
        return null
    }

    // Capture credentials now so a later token change can't affect this request.
    val token = authToken
    val type = tokenType
    val credentialsId = credentialsIdentifier
    return CoroutineScope(IO).launch {
        profilePhotoMutex.withLock {
            downloadProfilePhotoToFile(url, file, token, type, credentialsId)
        }
    }
}
