/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Object representation for soup specifications, such as soup name and features.
 */
public class SoupSpec {
    /** Soup features **/
    public static final String FEATURE_EXTERNAL_STORAGE = "externalStorage";

    /** List of all possible features for building soup_attrs table **/
    public static final String[] ALL_FEATURES = { FEATURE_EXTERNAL_STORAGE };

    private String soupName;
    private List<String> features;

    private static final String NAME = "name";
    private static final String FEATURES = "features";

    /**
     * Creates a soup spec without any features.
     *
     * @param soupName Name of the soup that will be used to store data.
     */
    public SoupSpec(String soupName) {
        this.soupName = soupName;
        this.features = Collections.emptyList();
    }

    /**
     * Creates a soup spec with the given features.
     *
     * @param soupName Name of the soup that will be used to store data.
     * @param features List of features that this soup should implement.
     */
    public SoupSpec(String soupName, String... features) {
        this.soupName = soupName;
        if (features != null) {
            this.features = Arrays.asList(features);
        } else {
            this.features = Collections.emptyList();
        }
    }

    /**
     * Returns the name of the soup represented by this soup spec.
     *
     * @return Name of the soup for this spec.
     */
    public String getSoupName() {
        return soupName;
    }

    /**
     * Returns the features that are implemented by the soup represented in this soup spec.
     *
     * @return A list of features represented by Strings that this soup implements.
     */
    public List<String> getFeatures() {
        return features;
    }

    /**
     * @return The JSON representation of this soup spec.
     * @throws JSONException
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject result = new JSONObject();
        result.put(NAME, soupName);
        result.put(FEATURES, new JSONArray(features));
        return result;
    }

    /**
     * Constructs a soup spec from JSON
     *
     * @param json JSON with which to construct soup spec
     * @return Soup Spec from given JSON
     * @throws JSONException
     */
    public static SoupSpec fromJSON(JSONObject json) throws JSONException {
        JSONArray jsonArray = json.optJSONArray(FEATURES);
        if (jsonArray != null) {
            String[] featureArray = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                featureArray[i] = (String) jsonArray.get(i);
            }

            return new SoupSpec(json.getString(NAME), featureArray);
        } else {
            return new SoupSpec(json.getString(NAME));
        }
    }
}
