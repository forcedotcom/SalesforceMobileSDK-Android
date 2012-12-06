/*
 * Copyright (c) 2011-2012, salesforce.com, inc.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.app.ForceAppWithSmartStore;
import com.salesforce.androidsdk.store.SmartStore;
import com.salesforce.androidsdk.store.SmartStore.IndexSpec;
import com.salesforce.androidsdk.store.SmartStore.Order;
import com.salesforce.androidsdk.store.SmartStore.QuerySpec;
import com.salesforce.androidsdk.store.SmartStore.QueryType;
import com.salesforce.androidsdk.store.SmartStore.SmartStoreException;

/**
 * PhoneGap plugin for smart store.
 */
public class SmartStorePlugin extends ForcePlugin {
	// Keys in json from/to javascript
	private static final String BEGIN_KEY = "beginKey";
	private static final String CURRENT_PAGE_INDEX = "currentPageIndex";
	private static final String CURRENT_PAGE_ORDERED_ENTRIES = "currentPageOrderedEntries";
	private static final String CURSOR_ID = "cursorId";
	private static final String END_KEY = "endKey";
	private static final String ENTRIES = "entries";
	private static final String ENTRY_IDS = "entryIds";
	private static final String INDEX = "index";
	private static final String INDEXES = "indexes";
	private static final String INDEX_PATH = "indexPath";
	private static final String LIKE_KEY = "likeKey";
	private static final String MATCH_KEY = "matchKey";
	private static final String EXTERNAL_ID_PATH = "externalIdPath";
	private static final String ORDER = "order";
	private static final String PAGE_SIZE = "pageSize";
	private static final String PATH = "path";
	private static final String QUERY_SPEC = "querySpec";
	private static final String QUERY_TYPE = "queryType";
	private static final String SOUP_NAME = "soupName";
	private static final String TOTAL_PAGES = "totalPages";
	private static final String TYPE = "type";

	// Map of cursor id to StoreCursor
	private static Map<Integer, StoreCursor> storeCursors = new HashMap<Integer, StoreCursor>();

	/**
	 * Supported plugin actions that the client can take.
	 */
	enum Action {
		pgCloseCursor,
		pgMoveCursorToPageIndex,
		pgQuerySoup,
		pgRegisterSoup,
		pgRemoveFromSoup,
		pgRemoveSoup,
		pgRetrieveSoupEntries,
		pgSoupExists,
		pgUpsertSoupEntries
	}

    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // All smart store action need to be serialized
    	synchronized(SmartStorePlugin.class) {
	    	// Figure out action
	    	Action action = null;
	    	try {
	    		action = Action.valueOf(actionStr);
				switch(action) {
                  case pgCloseCursor:           closeCursor(args, callbackContext); return true;
                  case pgMoveCursorToPageIndex: moveCursorToPageIndex(args, callbackContext); return true;
                  case pgQuerySoup:             querySoup(args, callbackContext); return true;
                  case pgRegisterSoup:          registerSoup(args, callbackContext); return true;
                  case pgRemoveFromSoup:        removeFromSoup(args, callbackContext); return true;
                  case pgRemoveSoup:            removeSoup(args, callbackContext); return true;
                  case pgRetrieveSoupEntries:   retrieveSoupEntries(args, callbackContext); return true;
                  case pgSoupExists:            soupExists(args, callbackContext); return true;
                  case pgUpsertSoupEntries:     upsertSoupEntries(args, callbackContext); return true;
                  default: return false;
		    	}
	    	}
	    	catch (IllegalArgumentException e) {
                return false;
	    	}
	    	catch (SmartStoreException e) {
	    		callbackContext.error(e.getMessage());
	    		return true;
	    	}
    	}
    }

	/**
	 * Native implementation of pgRemoveFromSoup
	 * @param args
	 * @param callbackContext
	 * @throws JSONException 
	 */
	private void removeFromSoup(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		JSONArray jsonSoupEntryIds = arg0.getJSONArray(ENTRY_IDS);
		Long[] soupEntryIds = new Long[jsonSoupEntryIds.length()];
		for (int i=0; i<jsonSoupEntryIds.length(); i++)
			soupEntryIds[i] = jsonSoupEntryIds.getLong(i); 
		
		// Run remove
		SmartStore smartStore = getSmartStore();
		smartStore.delete(soupName, soupEntryIds);
		
		callbackContext.success();
	}

	/**
	 * Native implementation of pgRetrieveSoupEntries
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void retrieveSoupEntries(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		JSONArray jsonSoupEntryIds = arg0.getJSONArray(ENTRY_IDS);
		Long[] soupEntryIds = new Long[jsonSoupEntryIds.length()];
		for (int i=0; i<jsonSoupEntryIds.length(); i++)
			soupEntryIds[i] = jsonSoupEntryIds.getLong(i); 
		
		// Run retrieve
		SmartStore smartStore = getSmartStore();
		JSONArray result = smartStore.retrieve(soupName, soupEntryIds);

		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
		callbackContext.sendPluginResult(pluginResult);
	}

	/**
	 * Native implementation of pgCloseCursor
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void closeCursor(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		Integer cursorId = arg0.getInt(CURSOR_ID);
		
		// Drop cursor from storeCursors map
		storeCursors.remove(cursorId);
		
		callbackContext.success();		
	}	
	
	/**
	 * Native implementation of pgMoveCursorToPageIndex
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void moveCursorToPageIndex(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		Integer cursorId = arg0.getInt(CURSOR_ID);
		Integer index = arg0.getInt(INDEX);

		// Get cursor
		StoreCursor storeCursor = storeCursors.get(cursorId);
		if (storeCursor == null) {
			callbackContext.error("Invalid cursor id");
		}
		
		// Change page
		storeCursor.moveToPageIndex(index);
		
		// Build json result
		SmartStore smartStore = getSmartStore();
		JSONObject result = storeCursor.toJSON(smartStore);
		
		// Done
		callbackContext.success(result);
	}

	/**
	 * Native implementation of pgSoupExists
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void soupExists(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);

		// Run upsert
		SmartStore smartStore = getSmartStore();
		boolean exists = smartStore.hasSoup(soupName);
		
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, exists);
		callbackContext.sendPluginResult(pluginResult);
	}	
	
	/**
	 * Native implementation of pgUpsertSoupEntries
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void upsertSoupEntries(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		JSONArray entriesJson = arg0.getJSONArray(ENTRIES);
		String externalIdPath = arg0.getString(EXTERNAL_ID_PATH);
		List<JSONObject> entries = new ArrayList<JSONObject>();
		for (int i=0; i<entriesJson.length(); i++) {
			entries.add(entriesJson.getJSONObject(i));
		}
		
		// Run upsert
		SmartStore smartStore = getSmartStore();
		smartStore.beginTransaction();
		try {
			JSONArray results = new JSONArray();			
			for (JSONObject entry : entries) {
				results.put(smartStore.upsert(soupName, entry, externalIdPath, false));
			}
			smartStore.setTransactionSuccessful();
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, results);
			callbackContext.sendPluginResult(pluginResult);
	    }
		finally {
			smartStore.endTransaction();
		}
		
	}

	/**
	 * Native implementation of pgRegisterSoup
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void registerSoup(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.isNull(SOUP_NAME) ? null : arg0.getString(SOUP_NAME);
		List<IndexSpec> indexSpecs = new ArrayList<IndexSpec>();
		JSONArray indexesJson = arg0.getJSONArray(INDEXES);
		for (int i=0; i<indexesJson.length(); i++) {
			JSONObject indexJson = indexesJson.getJSONObject(i);
			indexSpecs.add(new IndexSpec(indexJson.getString(PATH), SmartStore.Type.valueOf(indexJson.getString(TYPE))));
		}

		// Run register
		SmartStore smartStore = getSmartStore();
		smartStore.registerSoup(soupName, indexSpecs.toArray(new IndexSpec[0]));
		callbackContext.success(soupName);
	}

	/**
	 * Native implementation of pgQuerySoup
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void querySoup(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		JSONObject querySpecJson = arg0.getJSONObject(QUERY_SPEC);
		QueryType queryType = QueryType.valueOf(querySpecJson.getString(QUERY_TYPE));
		String path = querySpecJson.isNull(INDEX_PATH) ? null : querySpecJson.getString(INDEX_PATH);
		String matchKey = querySpecJson.isNull(MATCH_KEY) ? null : querySpecJson.getString(MATCH_KEY);
		String beginKey = querySpecJson.isNull(BEGIN_KEY) ? null : querySpecJson.getString(BEGIN_KEY);
		String endKey = querySpecJson.isNull(END_KEY) ? null : querySpecJson.getString(END_KEY);
		String likeKey = querySpecJson.isNull(LIKE_KEY) ? null : querySpecJson.getString(LIKE_KEY);
		Order order = Order.valueOf(querySpecJson.optString(ORDER, "ascending"));
		int pageSize = querySpecJson.getInt(PAGE_SIZE); 

		// Building query spec
		QuerySpec querySpec = null;
		switch (queryType) {
        case exact:   querySpec = QuerySpec.buildExactQuerySpec(path, matchKey, pageSize); break;
        case range:   querySpec = QuerySpec.buildRangeQuerySpec(path, beginKey, endKey, order, pageSize); break;
        case like:    querySpec = QuerySpec.buildLikeQuerySpec(path, likeKey, order, pageSize); break;
        default: throw new RuntimeException("Fell through switch: " + queryType);
		}
		
		// Run query
		SmartStore smartStore = getSmartStore();
		int countRows = smartStore.countQuerySoup(soupName, querySpec);
		int totalPages = countRows / querySpec.pageSize + 1;
		
		// Build store cursor
		StoreCursor storeCursor = new StoreCursor(soupName, querySpec, totalPages, 0);
		storeCursors.put(storeCursor.cursorId, storeCursor);
		
		// Build json result
		JSONObject result = storeCursor.toJSON(smartStore);
		
		// Done
		callbackContext.success(result);
	}

	/**
	 * Native implementation of pgRemoveSoup
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void removeSoup(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		
		// Run remove
		SmartStore smartStore = getSmartStore();
		smartStore.dropSoup(soupName);
		callbackContext.success();
	}

	
	private SmartStore getSmartStore() {
		return ((ForceAppWithSmartStore) ForceApp.APP).getSmartStore();
	}
	
	/**
	 * Store Cursor
	 * We don't actually keep a cursor opened, instead, we wrap the query spec and page index
	 */
	public static class StoreCursor {
		
		private static int LAST_ID = 0;
		
		// Id / soup / query / totalPages immutable
		public  final int cursorId;
		private final String soupName;
		private final QuerySpec querySpec;
		private final int totalPages;
		
		// Current page can change - by calling moveToPageIndex
		private int currentPageIndex;
		
		/**
		 * @param soupName
		 * @param querySpec
		 * @param totalPages
		 * @param currentPageIndex
		 */
		public StoreCursor(String soupName, QuerySpec querySpec, int totalPages, int currentPageIndex) {
			this.cursorId = LAST_ID++;
			this.soupName = soupName;
			this.querySpec = querySpec;
			this.totalPages = totalPages;
			this.currentPageIndex = currentPageIndex;
		}
		
		/**
		 * @param newPageIndex
		 */
		public void moveToPageIndex(int newPageIndex) {
			// Always between 0 and totalPages-1
			this.currentPageIndex = (newPageIndex < 0 ? 0 : newPageIndex >= totalPages ? totalPages - 1 : newPageIndex);
		}
		
		/**
		 * @param smartStore
		 * @return json containing cursor meta data (page index, size etc) and data (entries in page)
		 * Note: query is run to build json
		 * @throws JSONException 
		 */
		public JSONObject toJSON(SmartStore smartStore) throws JSONException {
			JSONObject json = new JSONObject();
			json.put(CURSOR_ID, cursorId);
			json.put(CURRENT_PAGE_INDEX, currentPageIndex);
			json.put(PAGE_SIZE, querySpec.pageSize);
			json.put(TOTAL_PAGES, totalPages);
			json.put(CURRENT_PAGE_ORDERED_ENTRIES, smartStore.querySoup(soupName, querySpec, currentPageIndex));
			return json;
		}
	}
}
