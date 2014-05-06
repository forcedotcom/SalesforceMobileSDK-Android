/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.sobjectsdk.model;

import java.util.Locale;

import org.json.JSONObject;

import com.salesforce.androidsdk.sobjectsdk.util.Constants;

/**
 * This class represents a typical Salesforce object.
 *
 * @author bhariharan
 */
public class SalesforceObject {

    private String objectType;
    private String name;
    private String objectId;
    private final boolean isExternal;
    private final JSONObject rawData;

    /**
     * Parameterized constructor.
     *
     * @param object Raw data for object.
     */
    public SalesforceObject(JSONObject object) {
        objectId = object.optString(Constants.ID);
        if (objectId == null || Constants.EMPTY_STRING.equals(objectId)) {
            objectId = object.optString(Constants.ID.toLowerCase(Locale.US));
            objectType = object.optString(Constants.TYPE.toLowerCase(Locale.US));
            name = object.optString(Constants.NAME.toLowerCase(Locale.US));
        } else {
            name = object.optString(Constants.NAME);
            final JSONObject attributes = object.optJSONObject(Constants.ATTRIBUTES);
            if (attributes != null) {
                objectType = attributes.optString(Constants.TYPE.toLowerCase(Locale.US));
                if (objectType == null || Constants.RECENTLY_VIEWED.equals(objectType)
                        || Constants.NULL_STRING.equals(objectType)) {
                    objectType = object.optString(Constants.TYPE);
                }
            }
        }
        rawData = object;
        isExternal = isObjectExternal();
    }

    /**
     * Returns the object type.
     *
     * @return Object type.
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Sets the object type.
     *
     * @param objectType Object type.
     */
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    /**
     * Returns the name.
     *
     * @return Name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the object name.
     *
     * @param name Object name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the object ID.
     *
     * @return Object ID.
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * Returns the complete metadata.
     *
     * @return Complete metadata.
     */
    public JSONObject getRawData() {
        return rawData;
    }

    /**
     * Returns if the object is external or not. This is applicable
     * only to the user and group object types. For other types, this
     * method will always return false.
     *
     * @return True - if external, False - otherwise.
     */
    public boolean isExternal() {
        return isExternal;
    }

    @Override
    public String toString() {
        return String.format("name: [%s], objectId: [%s], type: [%s], rawData: " +
                "[%s]", name, objectId, objectType, rawData);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof SalesforceObject)) {
            return false;
        }
        final SalesforceObject obj = (SalesforceObject) object;
        if (objectId == null || obj.getObjectId() == null || !objectId.equals(obj.getObjectId())) {
            return false;
        }
        if (name == null || obj.getName() == null || !name.equals(obj.getName())) {
            return false;
        }
        if (rawData == null || obj.getRawData() == null || !rawData.equals(obj.getRawData())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = objectId.hashCode();
        result ^= rawData.hashCode() + result * 37;
        return result;
    }

    private boolean isObjectExternal() {
        boolean isObjExternal = false;
        if (objectType != null && rawData != null) {
            if (!Constants.USER.equals(objectType)
                    && !Constants.GROUP.equals(objectType)) {
                return isObjExternal;
            }
            if (Constants.USER.equals(objectType)) {
                String userType = rawData.optString(Constants.USER_TYPE);
                if (userType == null || Constants.EMPTY_STRING.equals(userType)) {
                    userType = rawData.optString(Constants.USER_TYPE_ALTERNATE);
                }
                if (userType != null && Constants.CSN_ONLY.equals(userType)) {
                    final JSONObject profile = rawData.optJSONObject(Constants.PROFILE);
                    if (profile != null) {
                        final String profileName = profile.optString(Constants.NAME);
                        if (profileName != null && Constants.CHATTER_EXTERNAL_USER.equals(profileName)) {
                            isObjExternal = true;
                        }
                    }
                } else if (Constants.CHATTER_GUEST.equals(userType)) {
                    isObjExternal = true;
                }
            } else if (Constants.GROUP.equals(objectType)) {
                isObjExternal = rawData.optBoolean(Constants.CAN_HAVE_GUESTS);
                if (!isObjExternal) {
                    isObjExternal = rawData.optBoolean(Constants.CAN_HAVE_CHATTER_GUESTS);
                }
            }
        }
        return isObjExternal;
    }
}
