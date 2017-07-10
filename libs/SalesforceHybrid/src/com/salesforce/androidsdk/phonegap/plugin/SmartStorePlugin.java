/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.plugin;

import android.app.Activity;
import android.util.SparseArray;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.QueryType;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;
import com.salesforce.androidsdk.smartstore.store.StoreCursor;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.CURSOR_ID;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.ENTRIES;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.ENTRY_IDS;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.EXTERNAL_ID_PATH;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.INDEX;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.INDEXES;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.IS_GLOBAL_STORE;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.PATH;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.PATHS;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.QUERY_SPEC;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.RE_INDEX_DATA;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SOUP_NAME;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SOUP_SPEC;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.STORE_NAME;
import static com.salesforce.androidsdk.phonegap.plugin.PluginConstants.TYPE;


/**
 * PhoneGap plugin for smart store.
 */
public class SmartStorePlugin extends ForcePlugin {

	private static final String TAG = "SmartStorePlugin";

	// Map of cursor id to StoreCursor, per database.
	private static Map<SQLiteDatabase, SparseArray<StoreCursor>> STORE_CURSORS = new HashMap<>();

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
		pgGetSoupSpec,
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
		pgUpsertSoupEntries,
		pgGetAllGlobalStores,
		pgGetAllStores,
		pgRemoveStore,
		pgRemoveAllGlobalStores,
		pgRemoveAllStores
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
            SalesforceHybridLogger.e(TAG, "Unknown action: " + actionStr, e);
            return false;
    	}

    	// Not running smartstore action on the main thread
    	cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {

				// All smart store action need to be serialized
				synchronized (this) {
					try {
						switch (action) {
		        		  case pgAlterSoup:             alterSoup(args, callbackContext); break;
		        		  case pgClearSoup:				clearSoup(args, callbackContext); break;
		                  case pgCloseCursor:           closeCursor(args, callbackContext); break;
		                  case pgGetDatabaseSize:       getDatabaseSize(args, callbackContext); break;
		                  case pgGetSoupIndexSpecs:     getSoupIndexSpecs(args, callbackContext); break;
		                  case pgGetSoupSpec:           getSoupSpec(args, callbackContext); break;
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
						  case pgGetAllGlobalStores:    getAllGlobalStorePrefixes(args, callbackContext); break;
						  case pgGetAllStores:    		getAllStorePrefixes(args, callbackContext); break;
						  case pgRemoveStore:    		removeStore(args, callbackContext); break;
						  case pgRemoveAllGlobalStores: removeAllGlobalStores(args, callbackContext); break;
						  case pgRemoveAllStores:       removeAllStores(args, callbackContext); break;
						  default: throw new SmartStoreException("No handler for action " + action);
						}
					} catch (Exception e) {
                        SalesforceHybridLogger.w(TAG, "execute call failed", e);
						callbackContext.error(e.getMessage());
					}
                    SalesforceHybridLogger.d(TAG, "Total time for " + action + " -> " + (System.currentTimeMillis() - start));
				}
			}
		});
        SalesforceHybridLogger.d(TAG, "Main thread time for " + action + " -> " + (System.currentTimeMillis() - start));
    	return true;
    }

	/**
	 * Native implementation of pgRemoveFromSoup
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws Exception
	 */
	private void removeFromSoup(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);
		JSONArray jsonSoupEntryIds = arg0.optJSONArray(ENTRY_IDS);
		JSONObject querySpecJson = arg0.optJSONObject(QUERY_SPEC);

		if (jsonSoupEntryIds != null) {
			Long[] soupEntryIds = new Long[jsonSoupEntryIds.length()];
			for (int i = 0; i < jsonSoupEntryIds.length(); i++) {
				soupEntryIds[i] = jsonSoupEntryIds.getLong(i);
			}

			// Run remove
			smartStore.delete(soupName, soupEntryIds);
		}
		else {
			QuerySpec querySpec = QuerySpec.fromJSON(soupName, querySpecJson);

			// Run remove
			smartStore.deleteByQuery(soupName, querySpec);
		}

		callbackContext.success();
	}

	/**
	 * Native implementation of pgRetrieveSoupEntries
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws Exception
	 */
	private void retrieveSoupEntries(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		JSONArray jsonSoupEntryIds = arg0.getJSONArray(ENTRY_IDS);
		Long[] soupEntryIds = new Long[jsonSoupEntryIds.length()];
		for (int i = 0; i < jsonSoupEntryIds.length(); i++) {
			soupEntryIds[i] = jsonSoupEntryIds.getLong(i);
		}

		// Run retrieve
		JSONArray result = smartStore.retrieve(soupName, soupEntryIds);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
		callbackContext.sendPluginResult(pluginResult);
	}

	/**
	 * Native implementation of pgCloseCursor
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return void
	 * @throws Exception
	 */
	private void closeCursor(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		Integer cursorId = arg0.getInt(CURSOR_ID);
        final SmartStore smartStore = getSmartStore(arg0);

		// Drop cursor from storeCursors map
		getSmartStoreCursors(smartStore).remove(cursorId);
		callbackContext.success();
	}

	/**
	 * Native implementation of pgMoveCursorToPageIndex
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void moveCursorToPageIndex(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		Integer cursorId = arg0.getInt(CURSOR_ID);
		Integer index = arg0.getInt(INDEX);
        final SmartStore smartStore = getSmartStore(arg0);

		// Get cursor
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
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws JSONException
	 */
	private void showInspector(JSONArray args, CallbackContext callbackContext) throws JSONException {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		boolean isGlobal = getIsGlobal(arg0);
		String storeName = getStoreName(arg0);

		Activity activity = cordova.getActivity();
		activity.startActivity(SmartStoreInspectorActivity.getIntent(activity, isGlobal, storeName));
	}

	/**
	 * Native implementation of pgSoupExists
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void soupExists(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		// Run hasSoup
		boolean exists = smartStore.hasSoup(soupName);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, exists);
		callbackContext.sendPluginResult(pluginResult);
	}

	/**
	 *
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws JSONException
     */
	private void getAllGlobalStorePrefixes(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// return list of StoreConfigs
		List<String> globalDBNames = SmartStoreSDKManager.getInstance().getGlobalStoresPrefixList();
		this.sendStoreConfig(callbackContext,globalDBNames,true);
	}

	/**
	 *
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws JSONException
	 */
	private void getAllStorePrefixes(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// return list of StoreConfigs
		List<String> userDBNames = SmartStoreSDKManager.getInstance().getUserStoresPrefixList();
		this.sendStoreConfig(callbackContext,userDBNames,false);
	}

	/**
	 *
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws Exception
	 */
    private void removeStore(JSONArray args, CallbackContext callbackContext) throws Exception {
		final JSONObject arg0 = args.getJSONObject(0);
		boolean isGlobal = getIsGlobal(arg0);
		final String storeName = getStoreName(arg0);
        if (isGlobal) {
            SmartStoreSDKManager.getInstance().removeGlobalSmartStore(storeName);
        } else {
            final UserAccount account = UserAccountManager.getInstance().getCurrentUser();
            if (account == null) {
                throw new Exception("No user account found");
            }  else {
                SmartStoreSDKManager.getInstance().removeSmartStore(storeName, account, account.getCommunityId());
            }
        }
		final PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, true);
		callbackContext.sendPluginResult(pluginResult);
	}

	/**
	 *
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws JSONException
	 */
	private void removeAllGlobalStores(JSONArray args, CallbackContext callbackContext) throws JSONException {
		SmartStoreSDKManager.getInstance().removeAllGlobalStores();
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, true);
		callbackContext.sendPluginResult(pluginResult);
	}

	/**
	 *
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @throws JSONException
	 */
    private void removeAllStores(JSONArray args, CallbackContext callbackContext) throws JSONException {
		SmartStoreSDKManager.getInstance().removeAllUserStores();
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, true);
		callbackContext.sendPluginResult(pluginResult);
	}

	/**
	 * Native implementation of pgUpsertSoupEntries
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void upsertSoupEntries(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		JSONArray entriesJson = arg0.getJSONArray(ENTRIES);
		String externalIdPath = arg0.getString(EXTERNAL_ID_PATH);
		List<JSONObject> entries = new ArrayList<>();
		for (int i = 0; i < entriesJson.length(); i++) {
			entries.add(entriesJson.getJSONObject(i));
		}

		// Run upsert
		synchronized(smartStore.getDatabase()) {
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
	}

	/**
	 * Native implementation of pgRegisterSoup
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void registerSoup(JSONArray args, CallbackContext callbackContext) throws Exception {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.isNull(SOUP_NAME) ? null : arg0.getString(SOUP_NAME);
		SoupSpec soupSpec = getSoupSpecFromArg(arg0);
		IndexSpec[] indexSpecs = getIndexSpecsFromArg(arg0);
		final SmartStore smartStore = getSmartStore(arg0);

		// Run register
		if (soupSpec != null) {
			smartStore.registerSoupWithSpec(soupSpec, indexSpecs);
		} else {
			smartStore.registerSoup(soupName, indexSpecs);
		}

		callbackContext.success(soupName);
	}

	/**
	 * Native implementation of pgQuerySoup
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void querySoup(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		JSONObject querySpecJson = arg0.getJSONObject(QUERY_SPEC);
		QuerySpec querySpec = QuerySpec.fromJSON(soupName, querySpecJson);
		if (querySpec.queryType == QueryType.smart) {
			throw new RuntimeException("Smart queries can only be run through runSmartQuery");
		}

		// Run query
		runQuery(smartStore, querySpec, callbackContext);
	}

	/**
	 * Native implementation of pgRunSmartSql
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 */
	private void runSmartQuery(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		JSONObject querySpecJson = arg0.getJSONObject(QUERY_SPEC);
        final SmartStore smartStore = getSmartStore(arg0);
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
	 * @param callbackContext CallbackContext for plugin
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
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void removeSoup(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		// Run remove
		smartStore.dropSoup(soupName);
		callbackContext.success();
	}

	/**
	 * Native implementation of pgClearSoup
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void clearSoup(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		// Run clear
		smartStore.clearSoup(soupName);
		callbackContext.success();
	}

	/**
	 * Native implementation of pgGetDatabaseSize
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void getDatabaseSize(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		final JSONObject arg0 = args.optJSONObject(0);
		final SmartStore smartStore = getSmartStore(arg0);
		int databaseSize = smartStore.getDatabaseSize();
		callbackContext.success(databaseSize);
	}

	/**
	 * Native implementation of pgAlterSoup
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void alterSoup(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
		SoupSpec soupSpec = getSoupSpecFromArg(arg0);
		IndexSpec[] indexSpecs = getIndexSpecsFromArg(arg0);
		boolean reIndexData = arg0.getBoolean(RE_INDEX_DATA);
		final SmartStore smartStore = getSmartStore(arg0);

		// Run alter
		if (soupSpec != null) {
			smartStore.alterSoup(soupName, soupSpec, indexSpecs, reIndexData);
		} else {
			smartStore.alterSoup(soupName, indexSpecs, reIndexData);
		}
		callbackContext.success(soupName);
	}

	/**
	 * Native implementation of pgReIndexSoup
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void reIndexSoup(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);
		List<String> indexPaths = new ArrayList<String>();
		JSONArray indexPathsJson = arg0.getJSONArray(PATHS);
		for (int i = 0; i < indexPathsJson.length(); i++) {
			indexPaths.add(indexPathsJson.getString(i));
		}

		// Run register
		smartStore.reIndexSoup(soupName, indexPaths.toArray(new String[0]), true);
		callbackContext.success(soupName);
	}

	/**
	 * Native implementation of pgGetSoupIndexSpecs
	 * @param args JSONArray with arguments from JS
	 * @param callbackContext CallbackContext for plugin
	 * @return
	 * @throws Exception
	 */
	private void getSoupIndexSpecs(JSONArray args, CallbackContext callbackContext) throws Exception {

		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(arg0);

		// Get soup index specs
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

    /**
     * Native implementation of pgGetSoupSpec
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
	private void getSoupSpec(JSONArray args, CallbackContext callbackContext) throws Exception {
		// Parse args
		JSONObject arg0 = args.getJSONObject(0);
		String soupName = arg0.getString(SOUP_NAME);

		// Get soup specs
		SmartStore smartStore = getSmartStore(arg0);
		SoupSpec soupSpec = smartStore.getSoupSpec(soupName);
		callbackContext.success(soupSpec.toJSON());
	}

    /**
     * Return smartstore to use
     * @param arg0 first argument passed in plugin call
     * @return
     */
    public static SmartStore getSmartStore(JSONObject arg0) throws Exception {
        boolean isGlobal = getIsGlobal(arg0);
		final String storeName = getStoreName(arg0);
        if (isGlobal) {
            return SmartStoreSDKManager.getInstance().getGlobalSmartStore(storeName);
        } else {
            final UserAccount account = UserAccountManager.getInstance().getCurrentUser();
            if (account == null) {
                throw new Exception("No user account found");
            }  else {
                return SmartStoreSDKManager.getInstance().getSmartStore(storeName, account, account.getCommunityId());
            }
        }
    }

	/**
	 * Return the value of the isGlobalStore argument
	 * @param arg0
	 * @return
	 */
	public static boolean getIsGlobal(JSONObject arg0) {
		return arg0 != null ? arg0.optBoolean(IS_GLOBAL_STORE, false) : false;
	}

	/**
	 * Return the value of the storename argument
	 * @param arg0
	 * @return
	 */
	public static String getStoreName(JSONObject arg0) {
		return  arg0 != null ? arg0.optString(STORE_NAME, DBOpenHelper.DEFAULT_DB_NAME) : DBOpenHelper.DEFAULT_DB_NAME;
	}

    /**
     * Build index specs array from json object argument
     * @param arg0
     * @return
     * @throws JSONException
     */
    private IndexSpec[] getIndexSpecsFromArg(JSONObject arg0) throws JSONException {
        JSONArray indexesJson = arg0.getJSONArray(INDEXES);
        return IndexSpec.fromJSON(indexesJson);
    }

    /**
     * Build soup spec from json object argument
     * @param arg0
     * @return
     * @throws JSONException
     */
    private SoupSpec getSoupSpecFromArg(JSONObject arg0) throws JSONException {
        JSONObject soupSpecObj = arg0.optJSONObject(SOUP_SPEC);
        return soupSpecObj == null ? null : SoupSpec.fromJSON(soupSpecObj);
    }

	private void sendStoreConfig(CallbackContext callbackContext,List<String>dbNames, boolean isGlobal)  throws JSONException {
		JSONArray jsonArray = new JSONArray();
		for (String name : dbNames) {
			JSONObject object = new JSONObject();
			object.put(STORE_NAME, name);
			object.put(IS_GLOBAL_STORE, true);
			jsonArray.put(object);
		}
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonArray);
		callbackContext.sendPluginResult(pluginResult);
	}
}
