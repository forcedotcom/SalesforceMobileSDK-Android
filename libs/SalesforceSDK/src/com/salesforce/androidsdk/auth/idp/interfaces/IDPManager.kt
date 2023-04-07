/*
 * Copyright (c) 2023-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth.idp.interfaces

import android.content.Context
import android.content.Intent
import com.salesforce.androidsdk.R

interface IDPManager {

    enum class Status(val resIdForDescription:Int) {
        LOGIN_REQUEST_SENT_TO_SP(R.string.sf__login_request_sent_to_sp),
        GETTING_AUTH_CODE_FROM_SERVER(R.string.sf__getting_auth_code_from_server),
        ERROR_RECEIVED_FROM_SERVER(R.string.sf__failed_to_get_authorization_code),
        AUTH_CODE_SENT_TO_SP(R.string.sf__auth_code_sent_to_sp),
        ERROR_RECEIVED_FROM_SP(R.string.sf__error_received_from_sp),
        SP_LOGIN_COMPLETE(R.string.sf__sp_login_complete)
    }
    interface StatusUpdateCallback {
        fun onStatusUpdate(status: Status)
    }


    /**
     * Process received intent
     */
    fun onReceive(context: Context, intent: Intent)

    /**
     * Kick off IDP initiated login flow for given SP app
     */
    fun kickOffIDPInitiatedLoginFlow(context: Context, spAppPackageName: String, callback: StatusUpdateCallback)
}