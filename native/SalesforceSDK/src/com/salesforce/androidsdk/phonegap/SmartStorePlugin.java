/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.store.SmartStore;
import com.salesforce.androidsdk.store.SmartStore.IndexSpec;
import com.salesforce.androidsdk.store.SmartStore.QuerySpec;

/**
 * PhoneGap plugin for smart store.
 */
public class SmartStorePlugin extends Plugin {
	// Keys in json coming from javascript
	private static final String END_KEY = "endKey";
	private static final String ORDER = "order";
	private static final String BEGIN_KEY = "beginKey";
	private static final String QUERY_SPEC = "querySpec";
	private static final String PATH = "path";
	private static final String TYPE = "type";
	private static final String INDEXES = "indexes";
	private static final String SOUP_NAME = "soupName";
	private static final String SOUP_ENTRY_ID = "soupEntryId";
	private static final String ENTRIES = "entries";

	/**
	 * Supported plugin actions that the client can take.
	 */
	enum Action {
		pgRegisterSoup,
		pgRemoveSoup,
		pgQuerySoup,
		pgUpsertSoupEntries,
		pgMoveCursorToPageIndex,
		pgRetrieveSoupEntry,
		pgRemoveFromSoup
	}

    /**
     * Executes the plugin request and returns PluginResult.
     * 
     * @param actionStr     The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback ID used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public PluginResult execute(String actionStr, JSONArray args, String callbackId) {
    	// All smart store action need to be serialized
    	synchronized(SmartStorePlugin.class) {
	    	Log.i("SmartStorePlugin.execute", "actionStr: " + actionStr);
	    	// Figure out action
	    	Action action = null;
	    	try {
	    		action = Action.valueOf(actionStr);
				switch(action) {
					case pgMoveCursorToPageIndex: return moveCursorToPageIndex(args, callbackId);
					case pgQuerySoup:             return querySoup(args, callbackId);
					case pgRegisterSoup:          return registerSoup(args, callbackId);
					case pgRemoveSoup:            return removeSoup(args, callbackId);
					case pgUpsertSoupEntries:     return upsertSoupEntries(args, callbackId);
					case pgRetrieveSoupEntry:     return retrieveSoupEntry(args, callbackId);
					case pgRemoveFromSoup:        return removeFromSoup(args, callbackId);
					default: return new PluginResult(PluginResult.Status.INVALID_ACTION, actionStr); // should never happen
		    	}
	    	}
	    	catch (IllegalArgumentException e) {
	    		return new PluginResult(PluginResult.Status.INVALID_ACTION, e.getMessage());
	    	}
	    	catch (JSONException e) {
	    		return new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());    		
	    	}
    	}
    }

	/**
	 * Native implementation of pgRemoveFromSoup
	 * @param args
	 * @param callbackId
	 * @return
	 * @throws JSONException 
	 */
	private PluginResult removeFromSoup(JSONArray args, String callbackId) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		Long soupEntryId = arg0.getLong(SOUP_ENTRY_ID);
		
		SmartStore smartStore = ForceApp.APP.getSmartStore();
		smartStore.delete(soupName, soupEntryId);
		return new PluginResult(PluginResult.Status.OK);
	}

	/**
	 * Native implementation of pgRetrieveSoupEntry
	 * @param args
	 * @param callbackId
	 * @return
	 * @throws JSONException 
	 */
	private PluginResult retrieveSoupEntry(JSONArray args, String callbackId) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		Long soupEntryId = arg0.getLong(SOUP_ENTRY_ID);
		
		SmartStore smartStore = ForceApp.APP.getSmartStore();
		JSONObject result = smartStore.retrieve(soupName, soupEntryId);
		return new PluginResult(PluginResult.Status.OK, result);
	}

	/**
	 * Native implementation of pgMoveCursorToPageIndex
	 * @param args
	 * @param callbackId
	 * @return
	 */
	private PluginResult moveCursorToPageIndex(JSONArray args, String callbackId) {
		return new PluginResult(PluginResult.Status.ERROR, "Cursor not supported"); // TODO implement once cursor support is added to smart store 
	}

	/**
	 * Native implementation of pgUpsertSoupEntries
	 * @param args
	 * @param callbackId
	 * @return
	 * @throws JSONException 
	 */
	private PluginResult upsertSoupEntries(JSONArray args, String callbackId) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		JSONArray entriesJson = arg0.getJSONArray(ENTRIES);
		List<JSONObject> entries = new ArrayList<JSONObject>();
		for (int i=0; i<entriesJson.length(); i++) {
			entries.add(entriesJson.getJSONObject(i));
		}
		
		SmartStore smartStore = ForceApp.APP.getSmartStore();
		JSONArray results = new JSONArray();
		
		// TODO do it in one transaction
		for (JSONObject entry : entries) {
			results.put(smartStore.upsert(soupName, entry));
		}
		return new PluginResult(PluginResult.Status.OK, results);
	}

	/**
	 * Native implementation of pgRegisterSoup
	 * @param args
	 * @param callbackId
	 * @return
	 * @throws JSONException 
	 */
	private PluginResult registerSoup(JSONArray args, String callbackId) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		List<IndexSpec> indexSpecs = new ArrayList<IndexSpec>();
		JSONArray indexesJson = arg0.getJSONArray(INDEXES);
		for (int i=0; i<indexesJson.length(); i++) {
			JSONObject indexJson = indexesJson.getJSONObject(i);
			indexSpecs.add(new IndexSpec(indexJson.getString(PATH), SmartStore.Type.valueOf(indexJson.getString(TYPE))));
		}

		SmartStore smartStore = ForceApp.APP.getSmartStore();
		smartStore.registerSoup(soupName, indexSpecs.toArray(new IndexSpec[0]));
		return new PluginResult(PluginResult.Status.OK);
	}

	/**
	 * Native implementation of pgQuerySoup
	 * @param args
	 * @param callbackId
	 * @return
	 * @throws JSONException 
	 */
	private PluginResult querySoup(JSONArray args, String callbackId) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		JSONObject querySpecJson = arg0.getJSONObject(QUERY_SPEC);
		QuerySpec querySpec = new QuerySpec(querySpecJson.getString(PATH), querySpecJson.getString(BEGIN_KEY), querySpecJson.getString(END_KEY), SmartStore.Order.valueOf(querySpecJson.getString(ORDER)));
		
		SmartStore smartStore = ForceApp.APP.getSmartStore();
		JSONArray result = smartStore.querySoup(soupName, querySpec);
		return new PluginResult(PluginResult.Status.OK, result);
	}

	/**
	 * Native implementation of pgRemoveSoup
	 * @param args
	 * @param callbackId
	 * @return
	 * @throws JSONException 
	 */
	private PluginResult removeSoup(JSONArray args, String callbackId) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		
		SmartStore smartStore = ForceApp.APP.getSmartStore();
		smartStore.dropSoup(soupName);
		return new PluginResult(PluginResult.Status.OK);
	}
	
}
