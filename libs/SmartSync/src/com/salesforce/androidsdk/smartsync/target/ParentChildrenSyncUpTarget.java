/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.target;

import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.target.ParentChildrenSyncTargetHelper.RelationshipType;
import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Target for sync that uploads parent with children records
 */
public class ParentChildrenSyncUpTarget extends SyncUpTarget {
    private static final String TAG = "ParentChildrenSyncUpTarget";

    public static final String CHILDREN_CREATE_FIELDLIST = "childrenCreateFieldlist";
    public static final String CHILDREN_UPDATE_FIELDLIST = "childrenUpdateFieldlist";

    private ParentInfo parentInfo;
    private ChildrenInfo childrenInfo;
    private List<String> childrenCreateFieldlist;
    private List<String> childrenUpdateFieldlist;
    private RelationshipType relationshipType;

    public ParentChildrenSyncUpTarget(JSONObject target) throws JSONException {
        this(
                new ParentInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.PARENT)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CREATE_FIELDLIST)),
                JSONObjectHelper.<String>toList(target.optJSONArray(UPDATE_FIELDLIST)),

                new ChildrenInfo(target.getJSONObject(ParentChildrenSyncTargetHelper.CHILDREN)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_CREATE_FIELDLIST)),
                JSONObjectHelper.<String>toList(target.optJSONArray(CHILDREN_UPDATE_FIELDLIST)),

                RelationshipType.valueOf(target.getString(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE))
        );
    }

    public ParentChildrenSyncUpTarget(ParentInfo parentInfo,
                                      List<String> parentCreateFieldlist,
                                      List<String> parentUpdateFieldlist,
                                      ChildrenInfo childrenInfo,
                                      List<String> childrenCreateFieldlist,
                                      List<String> childrenUpdateFieldlist,
                                      RelationshipType relationshipType) {

        super(parentCreateFieldlist, parentUpdateFieldlist);
        this.parentInfo = parentInfo;
        this.childrenInfo = childrenInfo;
        this.childrenCreateFieldlist = childrenCreateFieldlist;
        this.childrenUpdateFieldlist = childrenUpdateFieldlist;
        this.relationshipType = relationshipType;
    }

    @Override
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(ParentChildrenSyncTargetHelper.PARENT, parentInfo.asJSON());
        target.put(ParentChildrenSyncTargetHelper.CHILDREN, childrenInfo.asJSON());
        target.put(CHILDREN_CREATE_FIELDLIST, new JSONArray(childrenCreateFieldlist));
        target.put(CHILDREN_CREATE_FIELDLIST, new JSONArray(childrenUpdateFieldlist));
        target.put(ParentChildrenSyncTargetHelper.RELATIONSHIP_TYPE, relationshipType.name());
        return target;
    }

    @Override
    public String createOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        return super.createOnServer(syncManager, record, fieldlist);
    }

    @Override
    public int deleteOnServer(SyncManager syncManager, JSONObject record) throws JSONException, IOException {
        return super.deleteOnServer(syncManager, record);
    }

    @Override
    public int updateOnServer(SyncManager syncManager, JSONObject record, List<String> fieldlist) throws JSONException, IOException {
        return super.updateOnServer(syncManager, record, fieldlist);
    }

    @Override
    public String fetchLastModifiedDate(SyncManager syncManager, JSONObject record) {
        return super.fetchLastModifiedDate(syncManager, record);
    }

    @Override
    public Set<String> getIdsOfRecordsToSyncUp(SyncManager syncManager, String soupName) throws JSONException {
        return super.getIdsOfRecordsToSyncUp(syncManager, soupName);
    }
}
