/*
 * Copyright (c) 2013-2014, salesforce.com, inc.
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

import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.Maps;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * This defines the HTTP requests in the connect API for files functionality.
 * 
 * @author sfell
 */
public class FileRequests extends ApiRequests {

    // files i follow
    static final String CONTENT_DOCUMENT_LINK = ApiVersionStrings.BASE_SOBJECT_PATH + "ContentDocumentLink";

    /**
     * Build a Request that can fetch a page from the files owned by the
     * specified user.
     * 
     * @param userId
     *            if null the context user is used, otherwise it should be an Id
     *            of a user.
     * @param pageNum
     *            if null fetches the first page, otherwise fetches the
     *            specified page.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest ownedFilesList(String userId, Integer pageNum) {
        return make(base("users").appendUserId(userId).appendPath("files").appendPageNum(pageNum));
    }

    /**
     * Build a Request that can fetch a page from the list of files from groups
     * that the user is a member of.
     * 
     * @param userId
     *            if null the context user is used, otherwise it should be an Id
     *            of a user.
     * @param pageNum
     *            if null fetches the first page, otherwise fetches the
     *            specified page.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest filesInUsersGroups(String userId, Integer pageNum) {
        return make(base("users").appendUserId(userId).appendPath("files/filter/groups").appendPageNum(pageNum));
    }

    /**
     * Build a Request that can fetch a page from the list of files that have
     * been shared with the user.
     * 
     * @param userId
     *            if null the context user is used, otherwise it should be an Id
     *            of a user.
     * @param pageNum
     *            if null fetches the first page, otherwise fetches the
     *            specified page.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest filesSharedWithUser(String userId, Integer pageNum) {
        return make(base("users").appendUserId(userId).appendPath("files/filter/sharedwithme").appendPageNum(pageNum));
    }

    /**
     * Build a Request that can fetch the file details of a particular version
     * of a file.
     * 
     * @param sfdcId
     *            The Id of the file
     * @param version
     *            if null fetches the most recent version, otherwise fetches
     *            this specific version.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest fileDetails(String sfdcId, String version) {
        validateSfdcId(sfdcId);
        return make(base("files").appendPath(sfdcId).appendVersionNum(version));
    }

    /**
     * Build a request that can fetch the latest file details of one or more
     * files in a single request.
     * 
     * @param sfdcIds
     *            The list of file Ids to fetch.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest batchFileDetails(List<String> sfdcIds) {
        validateSfdcIds(sfdcIds);
        String ids = TextUtils.join(",", sfdcIds);
        return make(base("files").appendPath("batch").appendPath(ids));
    }

    /**
     * Build a Request that can fetch the a preview/rendition of a particular
     * page of the file (and version)
     * 
     * @param sfdcId
     *            The Id of the file
     * @param version
     *            if null fetches the most recent version, otherwise fetches
     *            this specific version
     * @param renditionType
     *            What format of rendition do you want to get
     * @param pageNum
     *            which page to fetch, pages start at 0.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest fileRendition(String sfdcId, String version, RenditionType renditionType, Integer pageNum) {
        validateSfdcId(sfdcId);
        if (renditionType == null) {
            throw new NullPointerException("rendition type can't be null");
        }
        return make(base("files").appendPath(sfdcId).appendPath("rendition")
                .appendQueryParam("type", renditionType.toString()).appendVersionNum(version).appendPageNum(pageNum));
    }

    /**
     * Builds a request that can fetch the actual binary file contents of this
     * particular file.
     * 
     * @param sfdcId
     *            The Id of the file
     * @param version
     *            The version of the file
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest fileContents(String sfdcId, String version) {
        validateSfdcId(sfdcId);
        return make(base("files").appendPath(sfdcId).appendPath("content").appendVersionNum(version));
    }

    /**
     * Build a Request that can fetch a page from the list of entities that this
     * file is shared to.
     * 
     * @param sfdcId
     *            The Id of the file.
     * @param pageNum
     *            if null fetches the first page, otherwise fetches the
     *            specified page.
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest fileShares(String sfdcId, Integer pageNum) {
        validateSfdcId(sfdcId);
        return make(base("files").appendPath(sfdcId).appendPath("file-shares").appendPageNum(pageNum));
    }

    /**
     * Build a request that will add a file share for the specified fileId to
     * the specified entityId
     * 
     * @param fileId
     *            the Id of the file being shared.
     * @param entityId
     *            the Id of the entity to share the file to (e.g. a user or a
     *            group)
     * @param shareType
     *            the type of share (V - View, C - Collaboration)
     * 
     * @return A new RestRequest that be used to create this share.
     */
    public static RestRequest addFileShare(String fileId, String entityId, String shareType) {
        validateSfdcIds(fileId, entityId);
        return new RestRequest(RestMethod.POST, CONTENT_DOCUMENT_LINK, makeFileShare(fileId, entityId, shareType));
    }

    /**
     * Build a request that will delete the specified file share.
     * 
     * @param shareId
     *            The Id of the file share record (aka ContentDocumentLink)
     * 
     * @return
     */
    public static RestRequest deleteFileShare(String shareId) {
        validateSfdcId(shareId);
        return new RestRequest(RestMethod.DELETE, CONTENT_DOCUMENT_LINK + "/" + shareId, null);
    }

    private static HttpEntity makeFileShare(String fileId, String entityId, String shareType) {
        Map<String, String> share = Maps.newHashMap();
        share.put("ContentDocumentId", fileId);
        share.put("LinkedEntityId", entityId);
        share.put("ShareType", shareType);
        try {
            String json = null; // FIXME ConnectParser.toJson(share);
            StringEntity e = new StringEntity(json, HTTP.UTF_8);
            e.setContentType("application/json");
            return e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
