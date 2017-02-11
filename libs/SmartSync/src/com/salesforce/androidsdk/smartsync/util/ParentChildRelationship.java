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
package com.salesforce.androidsdk.smartsync.util;

import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple object to capture details of parent-child relationship
 */
public class ParentChildRelationship {

    //
    // Constants
    //

    // Key for group of parent attributes
    public static final String PARENT = "parent";
    
    // Key for group of children attributes
    public static final String CHILDREN = "children";

    // Keys found in attributes for parent or children
    public static final String SOBJECT_TYPE = "sobjectType";
    public static final String ID_FIELD_NAME = "idFieldName";
    public static final String MODIFICATION_DATE_FIELD_NAME = "modificationDateFieldName";

    // Keys found in attributes for children only
    public static final String SOBJECT_TYPE_PLURAL = "sobjectTypePlural"; // only children
    public static final String SOUP_NAME = "soupName";
    public static final String PARENT_ID_FIELD_NAME = "parentIdFieldName"; // name of field on  holding parent server id - only on children
    public static final String PARENT_LOCAL_ID_FIELD_NAME = "parentLocalIdFieldName"; // name of field on  holding parent local id - only on children

    // Key for relationship type
    public static final String RELATIONSHIP_TYPE = "relationshipType";

    //
    // Fields
    //

    private RelationshipType relationshipType;
    private ParentInfo parentInfo;
    private ChildrenInfo childrenInfo;


    /**
     * Construct for parent / children info and relationship type
     *
     * @param parentInfo
     * @param childrenInfo
     * @param relationshipType
     */
    public ParentChildRelationship(ParentInfo parentInfo, ChildrenInfo childrenInfo, RelationshipType relationshipType) {
        this.parentInfo = parentInfo;
        this.childrenInfo = childrenInfo;
        this.relationshipType = relationshipType;
    }

    /**
     * Construct from JSON
     */
    public ParentChildRelationship(JSONObject json) throws JSONException {
        this(
                new ParentInfo(json.getJSONObject(PARENT)),
                new ChildrenInfo(json.getJSONObject(CHILDREN)),
                RelationshipType.valueOf(json.getString(RELATIONSHIP_TYPE))
        );
    }


    /**
     * Enum for merge modes
     */
    public enum RelationshipType {
        MASTER_DETAIL,
        LOOKUP;
    }

    /**
     * Class for info on parent
     */
    public static class ParentInfo {
        public final String sobjectType;
        public final String idFieldName;
        public final String modificationDateFieldName;

        public ParentInfo(JSONObject json) throws JSONException {
            this(
                    json.getString(SOBJECT_TYPE),
                    JSONObjectHelper.optString(json, ID_FIELD_NAME),
                    JSONObjectHelper.optString(json, MODIFICATION_DATE_FIELD_NAME)
            );
        }

        public ParentInfo(String sobjectType) {
            this(sobjectType, null, null);
        }

        public ParentInfo(String sobjectType, String idFieldName, String modificationDateFieldName) {
            this.sobjectType = sobjectType;
            this.idFieldName = idFieldName;
            this.modificationDateFieldName = modificationDateFieldName;
        }

    }

    /**
     * Class for info on children
     */
    public static class ChildrenInfo extends ParentInfo {
        public final String sobjectTypePlural;
        public final String soupName;
        public final String parentIdFieldName;
        public final String parentLocalIdFieldName;

        public ChildrenInfo(JSONObject json) throws JSONException {
            this(
                    json.getString(SOBJECT_TYPE),
                    json.getString(SOBJECT_TYPE_PLURAL),
                    JSONObjectHelper.optString(json, ID_FIELD_NAME),
                    JSONObjectHelper.optString(json, MODIFICATION_DATE_FIELD_NAME),
                    json.getString(SOUP_NAME),
                    json.getString(PARENT_ID_FIELD_NAME),
                    json.getString(PARENT_LOCAL_ID_FIELD_NAME)
            );
        }

        public ChildrenInfo(String sobjectType, String sobjectTypePlural, String soupName, String parentIdFieldName, String parentLocalIdFieldName) {
            this(sobjectType, sobjectTypePlural, null, null, soupName, parentIdFieldName, parentLocalIdFieldName);
        }

        public ChildrenInfo(String sobjectType, String sobjectTypePlural, String idFieldName, String modificationDateFieldName, String soupName, String parentIdFieldName, String parentLocalIdFieldName) {
            super(sobjectType, idFieldName, modificationDateFieldName);
            this.sobjectTypePlural = sobjectTypePlural;
            this.soupName = soupName;
            this.parentIdFieldName = parentIdFieldName;
            this.parentLocalIdFieldName = parentLocalIdFieldName;
        }
    }
}
