/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.util.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;

/**
 * Sync down target for object layouts. This uses the '/ui-api/layout' API to fetch object layouts.
 * The easiest way to use this sync target is through {@link com.salesforce.androidsdk.mobilesync.manager.LayoutSyncManager}.
 *
 * @author bhariharan
 */
public class LayoutSyncDownTarget extends SyncDownTarget {

    public static final String SOBJECT_TYPE = "sobjectType";
    public static final String LAYOUT_TYPE = "layoutType";
    public static final String ID_FIELD_VALUE = "%s-%s";

    private String objectType;
    private String layoutType;

    /**
     * Parameterized constructor.
     *
     * @param target JSON representation.
     * @throws JSONException Exception thrown.
     */
    public LayoutSyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        this.objectType = target.getString(SOBJECT_TYPE);
        this.layoutType = target.getString(LAYOUT_TYPE);
    }

    /**
     * Parameterized constructor.
     *
     * @param objectType Object type.
     * @param layoutType Layout type.
     */
    public LayoutSyncDownTarget(String objectType, String layoutType) {
        super();
        this.queryType = QueryType.layout;
        this.objectType = objectType;
        this.layoutType = layoutType;
    }

    /**
     * JSON representation of this target.
     *
     * @return JSON representation of this target.
     * @throws JSONException Exception thrown.
     */
    public JSONObject asJSON() throws JSONException {
        final JSONObject target = super.asJSON();
        target.put(SOBJECT_TYPE, objectType);
        target.put(LAYOUT_TYPE, layoutType);
        return target;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        final RestRequest request = RestRequest.getRequestForObjectLayout(syncManager.apiVersion,
                objectType, layoutType);
        final RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        final JSONObject responseJSON = response.asJSONObject();
        if (responseJSON != null) {
            responseJSON.put(Constants.ID, String.format(ID_FIELD_VALUE, objectType, layoutType));
        }
        final JSONArray records = new JSONArray();
        records.put(response.asJSONObject());

        // Recording total size.
        totalSize = 1;
        return records;
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) {
        return null;
    }

    @Override
    protected Set<String> getRemoteIds(SyncManager syncManager, Set<String> localIds) {
        return null;
    }

    @Override
    public int cleanGhosts(SyncManager syncManager, String soupName, long syncId) {
        return 0;
    }

    /**
     * Returns the object type associated with this target.
     *
     * @return Object type.
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Returns the layout type associated with this target.
     *
     * @return Layout type.
     */
    public String getLayoutType() {
        return layoutType;
    }
}
