/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest.files;

import java.util.*;

import android.net.Uri;

import com.salesforce.androidsdk.rest.*;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;

/**
 * Base class with helpers for building RestRequests of various types.
 * 
 * @author sfell
 */
class ApiRequests {

    protected static RestRequest make(ConnectUriBuilder builder) {
        return new RestRequest(RestMethod.GET, builder.toString(), null, HTTP_HEADERS);
    }

    protected static ConnectUriBuilder base(String firstPathSegment) {
        return new ConnectUriBuilder().appendPath(firstPathSegment);
    }

    protected static ConnectUriBuilder connectBase(String firstPathSegment) {
        return new ConnectUriBuilder(Uri.parse(ApiVersionStrings.BASE_CONNECT_PATH).buildUpon())
                .appendPath(firstPathSegment);
    }

    protected static void validateSfdcId(String sfdcId) {
        if (sfdcId == null || ConnectUriBuilder.EMPTY.equals(sfdcId)) {
            throw new IllegalArgumentException("invalid sfdcId");
        }
    }

    protected static void validateSfdcIds(String... sfdcIds) {
        for (String i : sfdcIds)
            validateSfdcId(i);
    }

    protected static void validateSfdcIds(List<String> sfdcIds) {
        for (String i : sfdcIds)
            validateSfdcId(i);
    }

    protected static final Map<String, String> HTTP_HEADERS;

    static {
        Map<String, String> h = new HashMap<String, String>();
        h.put("X-Chatter-Entity-Encoding", "false");
        HTTP_HEADERS = Collections.unmodifiableMap(h);
    }
}
