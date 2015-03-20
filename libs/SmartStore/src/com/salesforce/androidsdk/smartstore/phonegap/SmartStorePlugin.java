/*
 * Copyright (c) 2011-2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.phonegap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.util.SparseArray;

import com.salesforce.androidsdk.phonegap.ForcePlugin;
import com.salesforce.androidsdk.phonegap.JavaScriptPluginVersion;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.QueryType;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;

/**
 * PhoneGap plugin for smart store.
 */
public class SmartStorePlugin extends ForcePlugin {

	// Keys in json from/to javascript
	public static final String BEGIN_KEY = "beginKey";
	public static final String END_KEY = "endKey";
	public static final String INDEX_PATH = "indexPath";
	public static final String LIKE_KEY = "likeKey";
	public static final String MATCH_KEY = "matchKey";
	public static final String SMART_SQL = "smartSql";
	public static final String ORDER = "order";
	public static final String PAGE_SIZE = "pageSize";
	public static final String QUERY_TYPE = "queryType";
	static final String TOTAL_ENTRIES = "totalEntries";
	static final String TOTAL_PAGES = "totalPages";
	static final String RE_INDEX_DATA = "reIndexData";
	static final String CURRENT_PAGE_INDEX = "currentPageIndex";
	static final String CURRENT_PAGE_ORDERED_ENTRIES = "currentPageOrderedEntries";
	static final String CURSOR_ID = "cursorId";
	private static final String TYPE = "type";
	private static final String SOUP_NAME = "soupName";
	private static final String PATH = "path";
	private static final String PATHS = "paths";
	private static final String QUERY_SPEC = "querySpec";
	private static final String EXTERNAL_ID_PATH = "externalIdPath";
	private static final String ENTRIES = "entries";
	private static final String ENTRY_IDS = "entryIds";
	private static final String INDEX = "index";
	private static final String INDEXES = "indexes";
	private static final String IS_GLOBAL_STORE = "isGlobalStore";

	// Map of cursor id to StoreCursor, per database.
	private static Map<SQLiteDatabase, SparseArray<StoreCursor>> STORE_CURSORS = new HashMap<SQLiteDatabase, SparseArray<StoreCursor>>();

	private synchronized static SparseArray<StoreCursor> getSmartStoreCursors(SmartStore store) {
		final SQLiteDatabase db = store.getDatabase();
		if (!STORE_CURSORS.containsKey(db)) {
			STORE_CURSORS.put(db, new SparseArray<StoreCursor>());
		}
		return STORE_CURSORS.get(db);
	}

	/**
	 * Supported plugin actions that the client can take.
	 */
	enum Action {
		pgAlterSoup,
		pgClearSoup,
		pgCloseCursor,
		pgGetDatabaseSize,
		pgGetSoupIndexSpecs,
		pgMoveCursorToPageIndex,
		pgQuerySoup,
		pgRegisterSoup,
		pgReIndexSoup,
		pgRemoveFromSoup,
		pgRemoveSoup,
		pgRetrieveSoupEntries,
		pgRunSmartQuery,
		pgShowInspector,
		pgSoupExists,
		pgUpsertSoupEntries
	}

    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion,
    		final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    	final long start = System.currentTimeMillis();

    	// Figure out action
    	final Action action;
    	try {
    		action = Action.valueOf(actionStr);
    	} catch (IllegalArgumentException e) {
    		Log.e("SmartStorePlugin.execute", "Unknown action " + actionStr);
            return false;
    	}

    	// Not running smartstore action on the main thread
    	cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {

		    	// All smart store action need to be serialized
				synchronized(SmartStorePlugin.class) {
	        		try {
		        		switch(action) {
		        		  case pgAlterSoup:             alterSoup(args, callbackContext); break;
		        		  case pgClearSoup:				clearSoup(args, callbackContext); break;
		                  case pgCloseCursor:           closeCursor(args, callbackContext); break;
		                  case pgGetDatabaseSize:       getDatabaseSize(args, callbackContext); break;
		                  case pgGetSoupIndexSpecs:     getSoupIndexSpecs(args, callbackContext); break;
		                  case pgMoveCursorToPageIndex: moveCursorToPageIndex(args, callbackContext); break;
		                  case pgQuerySoup:             querySoup(args, callbackContext); break;
		                  case pgRegisterSoup:          registerSoup(args, callbackContext); break;
		                  case pgReIndexSoup:			reIndexSoup(args, callbackContext); break;
		                  case pgRemoveFromSoup:        removeFromSoup(args, callbackContext); break;
		                  case pgRemoveSoup:            removeSoup(args, callbackContext); break;
		                  case pgRetrieveSoupEntries:   retrieveSoupEntries(args, callbackContext); break;
		                  case pgRunSmartQuery:         runSmartQuery(args, callbackContext); break;
		                  case pgShowInspector:         showInspector(args, callbackContext); break;
		                  case pgSoupExists:            soupExists(args, callbackContext); break;
		                  case pgUpsertSoupEntries:     upsertSoupEntries(args, callbackContext); break;
		                  default: throw new SmartStoreException("No handler for action " + action);
		    	    	}
	        		} catch (Exception e) {
	            		Log.w("SmartStorePlugin.execute", e.getMessage(), e);
	            		callbackContext.error(e.getMessage());
	            	}	        		
	            	Log.d("SmartSTorePlugin.execute", "Total time for " + action + "->" + (System.currentTimeMillis() - start));
	        	}
			}
    	});
    	Log.d("SmartSTorePlugin.execute", "Main thread time for " + action + "->" + (System.currentTimeMillis() - start));
    	return true;
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		JSONArray jsonSoupEntryIds = arg0.getJSONArray(ENTRY_IDS);
		Long[] soupEntryIds = new Long[jsonSoupEntryIds.length()];
		for (int i = 0; i < jsonSoupEntryIds.length(); i++) {
			soupEntryIds[i] = jsonSoupEntryIds.getLong(i);
		}
		
		// Run remove
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		JSONArray jsonSoupEntryIds = arg0.getJSONArray(ENTRY_IDS);
		Long[] soupEntryIds = new Long[jsonSoupEntryIds.length()];
		for (int i = 0; i < jsonSoupEntryIds.length(); i++) {
			soupEntryIds[i] = jsonSoupEntryIds.getLong(i);
		}
		
		// Run retrieve
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());

		// Drop cursor from storeCursors map
		getSmartStoreCursors(smartStore).remove(cursorId);
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);

		// Get cursor
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		final StoreCursor storeCursor = getSmartStoreCursors(smartStore).get(cursorId);
		if (storeCursor == null) {
			callbackContext.error("Invalid cursor id");
		}

		// Change page
		storeCursor.moveToPageIndex(index);

		// Build json result
		JSONObject result = storeCursor.getData(smartStore);

		// Done
		callbackContext.success(result);
	}

	/**
	 * Native implementation of pgShowInspector
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void showInspector(JSONArray args, CallbackContext callbackContext) throws JSONException {
		Activity activity = cordova.getActivity();
		final Intent i = new Intent(activity, SmartStoreInspectorActivity.class);
		activity.startActivity(i);
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);

		// Run upsert
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		JSONArray entriesJson = arg0.getJSONArray(ENTRIES);
		String externalIdPath = arg0.getString(EXTERNAL_ID_PATH);
		List<JSONObject> entries = new ArrayList<JSONObject>();
		for (int i = 0; i < entriesJson.length(); i++) {
			entries.add(entriesJson.getJSONObject(i));
		}

		// Run upsert
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		smartStore.beginTransaction();
		try {
			JSONArray results = new JSONArray();			
			for (JSONObject entry : entries) {
				results.put(smartStore.upsert(soupName, entry, externalIdPath, false));
			}
			smartStore.setTransactionSuccessful();
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, results);
			callbackContext.sendPluginResult(pluginResult);
	    } finally {
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		List<IndexSpec> indexSpecs = new ArrayList<IndexSpec>();
		JSONArray indexesJson = arg0.getJSONArray(INDEXES);
		for (int i = 0; i < indexesJson.length(); i++) {
			JSONObject indexJson = indexesJson.getJSONObject(i);
			indexSpecs.add(new IndexSpec(indexJson.getString(PATH), SmartStore.Type.valueOf(indexJson.getString(TYPE))));
		}

		// Run register
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		JSONObject querySpecJson = arg0.getJSONObject(QUERY_SPEC);
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		QuerySpec querySpec = QuerySpec.fromJSON(soupName, querySpecJson);
		if (querySpec.queryType == QueryType.smart) {
			throw new RuntimeException("Smart queries can only be run through runSmartQuery");
		}

		// Run query
		runQuery(smartStore, querySpec, callbackContext);
	}

	/**
	 * Native implementation of pgRunSmartSql
	 * @param args
	 * @param callbackContext
	 */
	private void runSmartQuery(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		JSONObject querySpecJson = arg0.getJSONObject(QUERY_SPEC);
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		QuerySpec querySpec = QuerySpec.fromJSON(null, querySpecJson);
		if (querySpec.queryType != QueryType.smart) {
			throw new RuntimeException("runSmartQuery can only run smart queries");
		}

		// Run query
		runQuery(smartStore, querySpec, callbackContext);
	}

	/**
	 * Helper for querySoup and runSmartSql
	 * @param querySpec
	 * @param callbackContext
	 * @throws JSONException
	 */
	private void runQuery(SmartStore smartStore, QuerySpec querySpec,
			CallbackContext callbackContext) throws JSONException {

		// Build store cursor
		final StoreCursor storeCursor = new StoreCursor(smartStore, querySpec);
		getSmartStoreCursors(smartStore).put(storeCursor.cursorId, storeCursor);

		// Build json result
		JSONObject result = storeCursor.getData(smartStore);

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
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);

		// Run remove
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		smartStore.dropSoup(soupName);
		callbackContext.success();
	}

	/**
	 * Native implementation of pgClearSoup
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void clearSoup(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);

		// Run clear
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		smartStore.clearSoup(soupName);
		callbackContext.success();
	}

	/**
	 * Native implementation of pgGetDatabaseSize
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void getDatabaseSize(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		final JSONObject arg0 = args.optJSONObject(0);
		boolean isGlobal = false;
		if (arg0 != null) {
			isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		}
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		int databaseSize = smartStore.getDatabaseSize();
		callbackContext.success(databaseSize);
	}	

	/**
	 * Native implementation of pgAlterSoup
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void alterSoup(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		List<IndexSpec> indexSpecs = new ArrayList<IndexSpec>();
		JSONArray indexesJson = arg0.getJSONArray(INDEXES);
		for (int i = 0; i < indexesJson.length(); i++) {
			JSONObject indexJson = indexesJson.getJSONObject(i);
			indexSpecs.add(new IndexSpec(indexJson.getString(PATH), SmartStore.Type.valueOf(indexJson.getString(TYPE))));
		}
		boolean reIndexData = arg0.getBoolean(RE_INDEX_DATA);

		// Run register
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		smartStore.alterSoup(soupName, indexSpecs.toArray(new IndexSpec[0]), reIndexData);
		callbackContext.success(soupName);
	}	

	/**
	 * Native implementation of pgReIndexSoup
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void reIndexSoup(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);
		List<String> indexPaths = new ArrayList<String>();
		JSONArray indexPathsJson = arg0.getJSONArray(PATHS);
		for (int i = 0; i < indexPathsJson.length(); i++) {
			indexPaths.add(indexPathsJson.getString(i));
		}

		// Run register
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		smartStore.reIndexSoup(soupName, indexPaths.toArray(new String[0]), true);
		callbackContext.success(soupName);
	}

	/**
	 * Native implementation of pgGetSoupIndexSpecs
	 * @param args
	 * @param callbackContext
	 * @return
	 * @throws JSONException 
	 */
	private void getSoupIndexSpecs(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		boolean isGlobal = arg0.optBoolean(IS_GLOBAL_STORE, false);

		// Get soup index specs
		final SmartStore smartStore = (isGlobal ? getGlobalSmartStore() : getUserSmartStore());
		IndexSpec[] indexSpecs = smartStore.getSoupIndexSpecs(soupName);
		JSONArray indexSpecsJson = new JSONArray();
		for (int i = 0; i < indexSpecs.length; i++) {
			JSONObject indexSpecJson = new JSONObject();
			IndexSpec indexSpec = indexSpecs[i];
			indexSpecJson.put(PATH, indexSpec.path);
			indexSpecJson.put(TYPE, indexSpec.type);
			indexSpecsJson.put(indexSpecJson);
		}
		callbackContext.success(indexSpecsJson);
	}	

	private SmartStore getUserSmartStore() {
		return SalesforceSDKManagerWithSmartStore.getInstance().getSmartStore();
	}

	private SmartStore getGlobalSmartStore() {
		return SalesforceSDKManagerWithSmartStore.getInstance().getGlobalSmartStore(null);
	}
}
