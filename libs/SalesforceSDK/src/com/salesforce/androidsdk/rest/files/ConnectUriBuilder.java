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

import android.net.Uri;
import com.salesforce.androidsdk.rest.ApiVersionStrings;

/**
 * A URI builder for connect URIs, it handles special cases for userId and for
 * optional parameters.
 * 
 * @author sfell
 */
public class ConnectUriBuilder {

    public static final String EMPTY = "";

    private static final String ME = "me";
    private static final String PAGE = "page";
    private static final String PAGESIZE = "pageSize";
    private static final String VERSIONNUMBER = "versionNumber";

    public ConnectUriBuilder() {
        this(Uri.parse(ApiVersionStrings.BASE_CHATTER_PATH).buildUpon());
    }

    public ConnectUriBuilder(Uri.Builder b) {
        this.builder = b;
    }

    private final Uri.Builder builder;

    public ConnectUriBuilder appendPath(String pathSegment) {
        builder.appendEncodedPath(pathSegment);
        return this;
    }

    public ConnectUriBuilder appendUserId(String userId) {
        if (userId != null && EMPTY.equals(userId)) {
            throw new IllegalArgumentException("invalid user id");
        }
        return appendPath(userId == null ? ME : userId);
    }

    public ConnectUriBuilder appendFolderId(String folderId) {
        if (folderId != null && EMPTY.equals(folderId)) {
            throw new IllegalArgumentException("invalid folder id");
        }
        return appendPath(folderId);
    }

    public ConnectUriBuilder appendPageNum(Integer pageNum) {
        if (pageNum != null && pageNum < 0) {
            throw new IllegalArgumentException("page number cannot be negative");
        }
        return appendQueryParam(PAGE, pageNum);
    }

    public ConnectUriBuilder appendPageSize(Integer pageSize) {
        if (pageSize != null && pageSize < 0) {
            throw new IllegalArgumentException("page size cannot be negative");
        }
        return appendQueryParam(PAGESIZE, pageSize);
    }

    public ConnectUriBuilder appendVersionNum(String version) {
        if (version != null && (EMPTY.equals(version) || Integer.valueOf(version) <= 0)) {
            throw new IllegalArgumentException("version number cannot be smaller than 1");
        }
        return appendQueryParam(VERSIONNUMBER, version);
    }

    public ConnectUriBuilder appendQueryParam(String key, Integer val) {
        if (key != null && val != null)
            builder.appendQueryParameter(key, val.toString());
        return this;
    }

    public ConnectUriBuilder appendQueryParam(String key, String val) {
        if (key != null && val != null && !EMPTY.equals(val))
            builder.appendQueryParameter(key, val);
        return this;
    }

    public Uri build() {
        return builder.build();
    }

    @Override
    public String toString() {
        return build().toString();
    }
}
