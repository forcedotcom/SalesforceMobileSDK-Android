/*
 * Copyright (c) 2012, salesforce.com, inc.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * Simple class to represent index spec
 */
public class IndexSpec {
    public final String path;
    public final Type type;
    public final String columnName;

    public IndexSpec(String path, Type type) {
        this.path = path;
        this.type = type;
        this.columnName = null; // undefined
    }

    public IndexSpec(String path, Type type, String columnName) {
        this.path = path;
        this.type = type;
        this.columnName = columnName;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + path.hashCode();
        result = 31 * result + type.hashCode();
        if (columnName != null) 
        	result = 31 * result + columnName.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(obj instanceof IndexSpec))
            return false;

        IndexSpec rhs = (IndexSpec) obj;
        boolean result = true;
        result  = result && path.equals(rhs.path);
        result = result && type.equals(rhs.type);
        if (columnName == null) 
        	result = result && (columnName == rhs.columnName);
    	else
    		result = result && columnName.equals(rhs.columnName);
        
        return result;
    }
    
    /**
     * @return path | type
     */
    public String getPathType() {
    	return path + "|" + type;
    }

	/**
	 * @return JSONObject for this IndexSpec
	 * @throws JSONException
	 */
	public JSONObject toJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("path", path);
		json.put("type", type);
		json.put("columnName", columnName);
		return json;
	}
	
	/**
	 * @param indexSpecs
	 * @return JSONArray for the array of IndexSpec's
	 * @throws JSONException 
	 */
	public static JSONArray toJSON(IndexSpec[] indexSpecs) throws JSONException {
		JSONArray json = new JSONArray();
		for(IndexSpec indexSpec : indexSpecs) {
			json.put(indexSpec.toJSON());
		}
		return json;
	}
	
	/**
	 * @param jsonArray
	 * @return IndexSpec[] from a JSONArray
	 * @throws JSONException
	 */
	public static IndexSpec[] fromJSON(JSONArray jsonArray) throws JSONException {
		List<IndexSpec> list = new ArrayList<IndexSpec>();
		for(int i=0; i<jsonArray.length(); i++) {
			list.add(IndexSpec.fromJSON(jsonArray.getJSONObject(i)));
		}
		return list.toArray(new IndexSpec[0]);
	}
	
	/**
	 * Return IndexSpec given JSONObject
	 * @param json
	 * @return
	 * @throws JSONException
	 */
	public static IndexSpec fromJSON(JSONObject json) throws JSONException {
		return new IndexSpec(json.getString("path"), Type.valueOf(json.getString("type")), json.optString("columnName"));
	}
	
	
	/**
	 * @param indexSpecs
	 * @return map index spec path to index spec
	 */
	public static Map<String, IndexSpec> mapForIndexSpecs(IndexSpec[] indexSpecs) {
		Map<String, IndexSpec> map = new HashMap<String, IndexSpec>();
		for (IndexSpec indexSpec : indexSpecs) {
			map.put(indexSpec.path, indexSpec);
		}
		return map;
	}

	/**
	 * @param indexSpecs
	 * @return true if at least one of the indexSpec is of type full_text
	 */
	public static boolean hasFTS(IndexSpec[] indexSpecs) {
		for (IndexSpec indexSpec : indexSpecs) {
			if (indexSpec.type == Type.full_text) {
				return true;
			}
		}
		return false;
	}
	
}