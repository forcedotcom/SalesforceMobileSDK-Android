/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.bridge;

import android.util.Log;
import android.util.SparseArray;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;
import com.salesforce.androidsdk.smartstore.store.StoreCursor;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartStoreReactBridge extends ReactContextBaseJavaModule {

	// Log tag
	static final String LOG_TAG = "SmartStoreReactBridge";

	// Keys in json from/to javascript
	static final String RE_INDEX_DATA = "reIndexData";
	static final String CURSOR_ID = "cursorId";
	static final String TYPE = "type";
	static final String SOUP_NAME = "soupName";
	static final String PATH = "path";
	static final String PATHS = "paths";
	static final String QUERY_SPEC = "querySpec";
    static final String SOUP_SPEC = "soupSpec";
    static final String SOUP_SPEC_NAME = "name";
    static final String SOUP_SPEC_FEATURES = "features";
	static final String EXTERNAL_ID_PATH = "externalIdPath";
	static final String ENTRIES = "entries";
	static final String ENTRY_IDS = "entryIds";
	static final String INDEX = "index";
	static final String INDEXES = "indexes";
	static final String IS_GLOBAL_STORE = "isGlobalStore";

	// Map of cursor id to StoreCursor, per database.
	private static Map<SQLiteDatabase, SparseArray<StoreCursor>> STORE_CURSORS = new HashMap<SQLiteDatabase, SparseArray<StoreCursor>>();

	private synchronized static SparseArray<StoreCursor> getSmartStoreCursors(SmartStore store) {
		final SQLiteDatabase db = store.getDatabase();
		if (!STORE_CURSORS.containsKey(db)) {
			STORE_CURSORS.put(db, new SparseArray<StoreCursor>());
		}
		return STORE_CURSORS.get(db);
	}

    public SmartStoreReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "SmartStoreReactBridge";
    }

	/**
	 * Native implementation of removeFromSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 */
	@ReactMethod
	public void removeFromSoup(ReadableMap args, final Callback successCallback,
							   final Callback errorCallback){

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);
		ReadableArray arraySoupEntryIds = (args.isNull(ENTRY_IDS) ? null : args.getArray(ENTRY_IDS));
		ReadableMap mapQuerySpec = (args.isNull(QUERY_SPEC) ? null : args.getMap(QUERY_SPEC));

        // Run remove
        try {
            if (arraySoupEntryIds != null) {
                List ids = ReactBridgeHelper.toJavaList(arraySoupEntryIds);
                Long[] soupEntryIds = new Long[ids.size()];
                for (int i = 0; i < ids.size(); i++) {
                    soupEntryIds[i] = ((Double) ids.get(i)).longValue();
                }
                smartStore.delete(soupName, soupEntryIds);
            } else {
                JSONObject querySpecJson = new JSONObject(ReactBridgeHelper.toJavaMap(mapQuerySpec));
                QuerySpec querySpec = QuerySpec.fromJSON(soupName, querySpecJson);
                smartStore.deleteByQuery(soupName, querySpec);
            }
            successCallback.invoke();
        } catch (JSONException e) {
            Log.e(LOG_TAG, "removeFromSoup", e);
            errorCallback.invoke(e.toString());
        }
	}

	/**
	 * Native implementation of retrieveSoupEntries
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void retrieveSoupEntries(ReadableMap args, final Callback successCallback,
                                    final Callback errorCallback){

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);
		Long[] soupEntryIds = ReactBridgeHelper.toJavaList(args.getArray(ENTRY_IDS)).toArray(new Long[0]);

		// Run retrieve
		try {
			JSONArray result = smartStore.retrieve(soupName, soupEntryIds);
			ReactBridgeHelper.invokeSuccess(successCallback, result);
		} catch (JSONException e) {
			Log.e(LOG_TAG, "retrieveSoupEntries", e);
			errorCallback.invoke(e.toString());
		}
	}

	/**
	 * Native implementation of closeCursor
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void closeCursor(ReadableMap args, final Callback successCallback,
                            final Callback errorCallback){

		// Parse args
		Integer cursorId = args.getInt(CURSOR_ID);
        final SmartStore smartStore = getSmartStore(args);

		// Drop cursor from storeCursors map
		getSmartStoreCursors(smartStore).remove(cursorId);
		successCallback.invoke();		
	}

	/**
	 * Native implementation of moveCursorToPageIndex
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void moveCursorToPageIndex(ReadableMap args, final Callback successCallback,
                                      final Callback errorCallback){

		// Parse args
		Integer cursorId = args.getInt(CURSOR_ID);
		Integer index = args.getInt(INDEX);
        final SmartStore smartStore = getSmartStore(args);

		// Get cursor
		final StoreCursor storeCursor = getSmartStoreCursors(smartStore).get(cursorId);
		if (storeCursor == null) {
			errorCallback.invoke("Invalid cursor id");
            return;
		}

		// Change page
		storeCursor.moveToPageIndex(index);

		// Build json result
		try {
			JSONObject result = storeCursor.getData(smartStore);
			ReactBridgeHelper.invokeSuccess(successCallback, result);
		} catch (JSONException e) {
			Log.e(LOG_TAG, "moveCursorToPageIndex", e);
			errorCallback.invoke(e.toString());
		}
	}

	/**
	 * Native implementation of soupExists
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void soupExists(ReadableMap args, final Callback successCallback,
                           final Callback errorCallback){

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);

		// Run upsert
		boolean exists = smartStore.hasSoup(soupName);
		ReactBridgeHelper.invokeSuccess(successCallback, exists);
	}

	/**
	 * Native implementation of upsertSoupEntries
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void upsertSoupEntries(ReadableMap args, final Callback successCallback,
                                  final Callback errorCallback){

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);

		List entriesList = ReactBridgeHelper.toJavaList(args.getArray(ENTRIES));
		String externalIdPath = args.getString(EXTERNAL_ID_PATH);
		List<JSONObject> entries = new ArrayList<JSONObject>();
		for (int i = 0; i < entriesList.size(); i++) {
			entries.add(new JSONObject((Map) entriesList.get(i)));
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
				ReactBridgeHelper.invokeSuccess(successCallback, results);
			} catch (JSONException e) {
				Log.e(LOG_TAG, "upsertSoupEntries", e);
				errorCallback.invoke(e.toString());
			} finally {
				smartStore.endTransaction();
			}
		}
	}

	/**
	 * Native implementation of registerSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void registerSoup(ReadableMap args, final Callback successCallback,
                             final Callback errorCallback) {

		// Parse args.
		String soupName = args.isNull(SOUP_NAME) ? null : args.getString(SOUP_NAME);
		final JSONArray indexesJson = new JSONArray(ReactBridgeHelper.toJavaList(args.getArray(INDEXES)));
        final SmartStore smartStore = getSmartStore(args);
		try {
			final IndexSpec[] indexSpecs = IndexSpec.fromJSON(indexesJson);

            // Get soup spec if available.
            final ReadableMap soupSpecObj = (args.hasKey(SOUP_SPEC) ? args.getMap(SOUP_SPEC) : null);
            if (soupSpecObj != null) {

                // Get soup name.
                soupName = soupSpecObj.getString(SOUP_SPEC_NAME);

                // Get features.
                final ReadableArray featuresJson = soupSpecObj.getArray(SOUP_SPEC_FEATURES);
                int size = (featuresJson == null) ? 0 : featuresJson.size();
                final String[] features = new String[size];
                if (featuresJson != null) {
                    for (int i = 0; i < featuresJson.size(); i++) {
                        features[i] = featuresJson.getString(i);
                    }
                }

                // Run register soup with spec.
                smartStore.registerSoupWithSpec(new SoupSpec(soupName, features), indexSpecs);
            } else {

                // Run register soup.
                smartStore.registerSoup(soupName, indexSpecs);
            }
			ReactBridgeHelper.invokeSuccess(successCallback, soupName);
		} catch (JSONException e) {
			Log.e(LOG_TAG, "registerSoup", e);
			errorCallback.invoke(e.toString());
		}
	}

	/**
	 * Native implementation of querySoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void querySoup(ReadableMap args, final Callback successCallback,
                          final Callback errorCallback){

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);
		JSONObject querySpecJson = new JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(QUERY_SPEC)));
		try {
			QuerySpec querySpec = QuerySpec.fromJSON(soupName, querySpecJson);
			if (querySpec.queryType == QuerySpec.QueryType.smart) {
				throw new RuntimeException("Smart queries can only be run through runSmartQuery");
			}

			// Run query
			runQuery(smartStore, querySpec, successCallback);

		} catch (Exception e) {
			Log.e(LOG_TAG, "querySoup", e);
			errorCallback.invoke(e.toString());
		}
	}

	/**
	 * Native implementation of runSmartSql
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 */
	@ReactMethod
	public void runSmartQuery(ReadableMap args, final Callback successCallback,
                              final Callback errorCallback){

		// Parse args
		JSONObject querySpecJson = new JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(QUERY_SPEC)));
        final SmartStore smartStore = getSmartStore(args);
		try {
			QuerySpec querySpec = QuerySpec.fromJSON(null, querySpecJson);
			if (querySpec.queryType != QuerySpec.QueryType.smart) {
				throw new RuntimeException("runSmartQuery can only run smart queries");
			}

			// Run query
			runQuery(smartStore, querySpec, successCallback);
		} catch (Exception e) {
			Log.e(LOG_TAG, "runSmartQuery", e);
			errorCallback.invoke(e.toString());
		}
	}

	/**
	 * Helper for querySoup and runSmartSql
	 * @param querySpec
	 * @param successCallback
	 * @throws JSONException
	 */
	private void runQuery(SmartStore smartStore, QuerySpec querySpec,
                         final Callback successCallback) throws JSONException {

		// Build store cursor
		final StoreCursor storeCursor = new StoreCursor(smartStore, querySpec);
		getSmartStoreCursors(smartStore).put(storeCursor.cursorId, storeCursor);

		// Build json result
		JSONObject result = storeCursor.getData(smartStore);

		// Done
        ReactBridgeHelper.invokeSuccess(successCallback, result);
	}

	/**
	 * Native implementation of removeSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void removeSoup(ReadableMap args, final Callback successCallback,
                           final Callback errorCallback) {

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);

		// Run remove
		smartStore.dropSoup(soupName);
		successCallback.invoke();
	}

	/**
	 * Native implementation of clearSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void clearSoup(ReadableMap args, final Callback successCallback,
                          final Callback errorCallback) {

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);

		// Run clear
		smartStore.clearSoup(soupName);
		successCallback.invoke();
	}

	/**
	 * Native implementation of getDatabaseSize
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void getDatabaseSize(ReadableMap args, final Callback successCallback,
                                final Callback errorCallback) {

		// Parse args
		final SmartStore smartStore = getSmartStore(args);
		int databaseSize = smartStore.getDatabaseSize();
		ReactBridgeHelper.invokeSuccess(successCallback, databaseSize);
	}

	/**
	 * Native implementation of alterSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void alterSoup(ReadableMap args, final Callback successCallback,
                          final Callback errorCallback) {

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);
		boolean reIndexData = args.getBoolean(RE_INDEX_DATA);
		JSONArray indexesJson = new JSONArray(ReactBridgeHelper.toJavaList(args.getArray(INDEXES)));
		try {
			IndexSpec[] indexSpecs = IndexSpec.fromJSON(indexesJson);

			// Run register
			smartStore.alterSoup(soupName, indexSpecs, reIndexData);
			ReactBridgeHelper.invokeSuccess(successCallback, soupName);
		} catch (JSONException e) {
			Log.e(LOG_TAG, "alterSoup", e);
			errorCallback.invoke(e.toString());
		}
	}

	/**
	 * Native implementation of reIndexSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void reIndexSoup(ReadableMap args, final Callback successCallback,
                            final Callback errorCallback){

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);
		List<String> indexPaths = ReactBridgeHelper.toJavaStringList(args.getArray(PATHS));

		// Run register
		smartStore.reIndexSoup(soupName, indexPaths.toArray(new String[0]), true);
		ReactBridgeHelper.invokeSuccess(successCallback, soupName);
	}

	/**
	 * Native implementation of getSoupIndexSpecs
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void getSoupIndexSpecs(ReadableMap args, final Callback successCallback,
                                  final Callback errorCallback) {

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);

		// Get soup index specs
		try {
			IndexSpec[] indexSpecs = smartStore.getSoupIndexSpecs(soupName);
			JSONArray indexSpecsJson = new JSONArray();
			for (int i = 0; i < indexSpecs.length; i++) {
				JSONObject indexSpecJson = new JSONObject();
				IndexSpec indexSpec = indexSpecs[i];
				indexSpecJson.put(PATH, indexSpec.path);
				indexSpecJson.put(TYPE, indexSpec.type);
				indexSpecsJson.put(indexSpecJson);
			}
			ReactBridgeHelper.invokeSuccess(successCallback, indexSpecsJson);
		} catch (JSONException e) {
			Log.e(LOG_TAG, "getSoupIndexSpecs", e);
			errorCallback.invoke(e.toString());
		}
	}

    /**
     * Native implementation of getSoupSpecs
     * @param args
     * @param successCallback
     * @param errorCallback
     * @return
     */
    @ReactMethod
    public void getSoupSpec(ReadableMap args, final Callback successCallback,
                                  final Callback errorCallback) {

        // Parse args.
        final String soupName = args.getString(SOUP_NAME);
        final SmartStore smartStore = getSmartStore(args);

        // Get soup specs.
        final SoupSpec soupSpec = smartStore.getSoupSpec(soupName);
        try {
            final JSONObject soupSpecJSON = soupSpec.toJSON();
            ReactBridgeHelper.invokeSuccess(successCallback, soupSpecJSON);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "getSoupSpec", e);
            errorCallback.invoke(e.toString());
        }
    }

    /**
     * Return smartstore to use
     * @param args first argument passed in plugin call
     * @return
     */
    private SmartStore getSmartStore(ReadableMap args) {
        boolean isGlobal = getIsGlobal(args);
        return (isGlobal
                ? SmartStoreSDKManager.getInstance().getGlobalSmartStore()
                : SmartStoreSDKManager.getInstance().getSmartStore());
    }

	/**
	 * Return the value of the isGlobalStore argument
	 * @param args
	 * @return
	 */
	private boolean getIsGlobal(ReadableMap args) {
		return args != null ? args.getBoolean(IS_GLOBAL_STORE) : false;
	}
}
