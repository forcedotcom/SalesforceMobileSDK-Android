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
package com.salesforce.androidsdk.sobjectsdk.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.sobjectsdk.model.SalesforceObject;
import com.salesforce.androidsdk.sobjectsdk.model.SalesforceObjectType;
import com.salesforce.androidsdk.sobjectsdk.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.sobjectsdk.util.Constants;

/**
 * This class provides APIs to store and retrieve Salesforce object
 * metadata, object layouts and MRU objects from a simple cache.
 *
 * @author bhariharan
 */
public class CacheManager {

    private static final String TAG = "SObjectSDK: CacheManager";
    private static final String CACHE_TIME_KEY = "cache_time_%s";
    private static final String CACHE_DATA_KEY = "cache_data_%s";

    private static CacheManager INSTANCE;

    private final SmartStore smartStore;

    private Map<String, List<SalesforceObjectType>> objectTypeCacheMap;
    private Map<String, List<SalesforceObject>> objectCacheMap;
    private Map<String, List<SalesforceObjectTypeLayout>> objectTypeLayoutCacheMap;

    /**
     * This enum defines different possible cache policies.
     *
     * @author bhariharan
     */
    public enum CachePolicy {
        IGNORE_CACHE_DATA, // Ignores cache data and always loads from server.
        RELOAD_AND_RETURN_CACHE_ON_FAILURE, // Always reloads data and returns cache data only on failure.
        RETURN_CACHE_DATA_DONT_RELOAD, // Returns cache data and does not refresh cache if cache exists.
        RELOAD_AND_RETURN_CACHE_DATA, // Reloads and returns cache data.
        RELOAD_IF_EXPIRED_AND_RETURN_CACHE_DATA, // Refreshes cache if the refresh time interval is up and returns cache data.
        INVALIDATE_CACHE_DONT_RELOAD, // Invalidates the cache and does not refresh the cache.
        INVALIDATE_CACHE_AND_RELOAD; // Invalidates the cache and refreshes the cache.
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of this class.
     */
    public static CacheManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CacheManager();
        }
        return INSTANCE;
    }

    /**
     * Resets the cache manager. This method clears only the in memory cache,
     * and does not clear the underlying cache in the database. In order to
     * clear the cached data in the database, use the 'removeCache()' method.
     */
    public static void reset() {
        if (INSTANCE != null) {
            INSTANCE.resetInMemoryCache();
            INSTANCE = null;
        }
    }

    /**
     * Private constructor.
     */
    private CacheManager() {
        smartStore = SalesforceSDKManagerWithSmartStore.getInstance().getSmartStore();
        resetInMemoryCache();
    }

    /**
     * Returns whether the specified cache exists.
     *
     * @param soupName Soup name.
     * @return True - if the cache exists, False - otherwise.
     */
    public boolean doesCacheExist(String soupName) {
        if (soupName == null || Constants.EMPTY_STRING.equals(soupName)
                || !smartStore.hasSoup(soupName)) {
            return false;
        }
        return true;
    }

    /**
     * Removes existing data from the specified cache.
     *
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     */
    public void removeCache(String cacheType, String cacheKey) {
        if (cacheType == null || cacheKey == null ||
                Constants.EMPTY_STRING.equals(cacheType) ||
                Constants.EMPTY_STRING.equals(cacheKey)) {
            return;
        }
        final String soupName = cacheType + cacheKey;
        if (doesCacheExist(soupName)) {
            smartStore.dropSoup(soupName);
            resetInMemoryCache();
        }
    }

    /**
     * Returns whether the cache needs to be refreshed. Before this method is
     * called, either 'doesCacheExist', or 'lastCacheUpdateTime' should be
     * called to determine whether the cache already exists and the
     * last update time of the cache.
     *
     * @param cacheExists True - if the cache exists, False - otherwise.
     * @param cachePolicy Cache policy being used.
     * @param lastCachedTime Last time the cache was updated.
     * @param refreshIfOlderThan Refresh time interval. A negative value will
     *        result in the cache not being refreshed.
     * @return True - if cache needs to be refreshed, False - otherwise.
     */
    public boolean needToReloadCache(boolean cacheExists, CachePolicy cachePolicy,
            long lastCachedTime, long refreshIfOlderThan) {
        if (cachePolicy == CachePolicy.IGNORE_CACHE_DATA ||
                cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            return false;
        }
        if (cachePolicy == CachePolicy.RELOAD_AND_RETURN_CACHE_DATA ||
                cachePolicy == CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD) {
            return true;
        }
        if (!cacheExists || refreshIfOlderThan <= 0 || lastCachedTime <= 0) {
            return true;
        }
        long timeDiff = System.currentTimeMillis() - lastCachedTime;
        return (timeDiff > refreshIfOlderThan);
    }

    /**
     * Returns the last time the cache was refreshed.
     *
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return Last update time of the cache.
     */
    public long getLastCacheUpdateTime(String cacheType, String cacheKey) {
        try {
            if (cacheType == null || cacheKey == null ||
            		Constants.EMPTY_STRING.equals(cacheType) ||
            		Constants.EMPTY_STRING.equals(cacheKey)) {
                return 0;
            }
            final String soupName = cacheType + cacheKey;
            if (!doesCacheExist(soupName)) {
                return 0;
            }
            final String smartSql = "SELECT {" + soupName + ":" + String.format(CACHE_TIME_KEY,
                    cacheKey) + "} FROM {" + soupName + "}";
            final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
                final JSONArray array = results.optJSONArray(0);
                if (array != null && array.length() > 0) {
                    return array.optLong(0);
                }
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException occurred while attempting to read last cached time", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to read last cached time", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to read last cached time", e);
        }
        return 0;
    }

    /**
     * Reads a list of Salesforce object types from the cache.
     *
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return List of Salesforce object types.
     */
    public List<SalesforceObjectType> readObjectTypes(String cacheType,
            String cacheKey) {
        if (cacheType == null || cacheKey == null ||
        		Constants.EMPTY_STRING.equals(cacheType) ||
        		Constants.EMPTY_STRING.equals(cacheKey)) {
            return null;
        }
        final String soupName = cacheType + cacheKey;
        if (!doesCacheExist(soupName)) {
            return null;
        }

        // Checks in memory cache first.
        if (objectTypeCacheMap != null) {
            final List<SalesforceObjectType> cachedObjTypes = objectTypeCacheMap.get(cacheKey);
            if (cachedObjTypes != null && cachedObjTypes.size() > 0) {
                return cachedObjTypes;
            }
        }

        // Falls back on smart store cache if in memory cache is empty.
        final String smartSql = "SELECT {" + soupName + ":" + String.format(CACHE_DATA_KEY,
                cacheKey) + "} FROM {" + soupName + "}";
        try {
            final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
                final JSONArray array = results.optJSONArray(0);
                if (array != null && array.length() > 0) {
                    final String res = array.optString(0);
                    if (res != null && res.length() > 0) {
                        final JSONArray cachedResults = new JSONArray(res);
                        if (cachedResults.length() > 0) {
                            final List<SalesforceObjectType> cachedList = new ArrayList<SalesforceObjectType>();
                            for (int j = 0; j < cachedResults.length(); j++) {
                                final JSONObject sfObj = cachedResults.optJSONObject(j);
                                if (sfObj != null) {
                                    cachedList.add(new SalesforceObjectType(sfObj));
                                }
                            }
                            if (cachedList.size() > 0) {

                                // Inserts or updates data in memory cache.
                                if (objectTypeCacheMap != null) {
                                    if (objectTypeCacheMap.get(cacheKey) != null) {
                                        objectTypeCacheMap.remove(cacheKey);
                                    }
                                    objectTypeCacheMap.put(cacheKey, cachedList);
                                }
                                return cachedList;
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to read cached data", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to read cached data", e);
        }
        return null;
    }

    /**
     * Reads a list of Salesforce objects from the cache.
     *
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return List of Salesforce objects.
     */
    public List<SalesforceObject> readObjects(String cacheType, String cacheKey) {
        if (cacheType == null || cacheKey == null ||
        		Constants.EMPTY_STRING.equals(cacheType) ||
        		Constants.EMPTY_STRING.equals(cacheKey)) {
            return null;
        }
        final String soupName = cacheType + cacheKey;
        if (!doesCacheExist(soupName)) {
            return null;
        }

        // Checks in memory cache first.
        if (objectCacheMap != null) {
            final List<SalesforceObject> cachedObjs = objectCacheMap.get(cacheKey);
            if (cachedObjs != null && cachedObjs.size() > 0) {
                return cachedObjs;
            }
        }

        // Falls back on smart store cache if in memory cache is empty.
        final String smartSql = "SELECT {" + soupName + ":" + String.format(CACHE_DATA_KEY,
                cacheKey) + "} FROM {" + soupName + "}";
        try {
            final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
                final JSONArray array = results.optJSONArray(0);
                if (array != null && array.length() > 0) {
                    final String res = array.optString(0);
                    if (res != null && res.length() > 0) {
                        final JSONArray cachedResults = new JSONArray(res);
                        if (cachedResults.length() > 0) {
                            final List<SalesforceObject> cachedList = new ArrayList<SalesforceObject>();
                            for (int j = 0; j < cachedResults.length(); j++) {
                                final JSONObject sfObj = cachedResults.optJSONObject(j);
                                if (sfObj != null) {
                                    cachedList.add(new SalesforceObject(sfObj));
                                }
                            }
                            if (cachedList.size() > 0) {

                                // Inserts or updates data in memory cache.
                                if (objectCacheMap != null) {
                                    if (objectCacheMap.get(cacheKey) != null) {
                                        objectCacheMap.remove(cacheKey);
                                    }
                                    objectCacheMap.put(cacheKey, cachedList);
                                }
                                return cachedList;
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to read cached data", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to read cached data", e);
        }
        return null;
    }

    /**
     * Reads a list of Salesforce object layouts from the cache.
     *
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return List of Salesforce object layouts.
     */
    public List<SalesforceObjectTypeLayout> readObjectLayouts(String cacheType, String cacheKey) {
        if (cacheType == null || cacheKey == null ||
        		Constants.EMPTY_STRING.equals(cacheType) ||
        		Constants.EMPTY_STRING.equals(cacheKey)) {
            return null;
        }
        final String soupName = cacheType + cacheKey;
        if (!doesCacheExist(soupName)) {
            return null;
        }

        // Checks in memory cache first.
        if (objectTypeLayoutCacheMap != null) {
            final List<SalesforceObjectTypeLayout> cachedObjs = objectTypeLayoutCacheMap.get(cacheKey);
            if (cachedObjs != null && cachedObjs.size() > 0) {
                return cachedObjs;
            }
        }

        // Falls back on smart store cache if in memory cache is empty.
        final String smartSql = "SELECT {" + soupName + ":" + String.format(CACHE_DATA_KEY,
                cacheKey) + "} FROM {" + soupName + "}";
        try {
            final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
                final JSONArray array = results.optJSONArray(0);
                if (array != null && array.length() > 0) {
                    final String res = array.optString(0);
                    if (res != null && res.length() > 0) {
                        final JSONArray cachedResults = new JSONArray(res);
                        if (cachedResults.length() > 0) {
                            final List<SalesforceObjectTypeLayout> cachedList = new ArrayList<SalesforceObjectTypeLayout>();
                            for (int j = 0; j < cachedResults.length(); j++) {
                                final JSONObject sfObj = cachedResults.optJSONObject(j);
                                if (sfObj != null) {
                                    final JSONObject rawData = sfObj.optJSONObject("rawData");
                                    final String type = sfObj.optString("type");
                                    if (rawData != null && type != null &&
                                    		!Constants.EMPTY_STRING.equals(type)) {
                                        cachedList.add(new SalesforceObjectTypeLayout(type, rawData));
                                    }
                                }
                            }
                            if (cachedList.size() > 0) {

                                // Inserts or updates data in memory cache.
                                if (objectTypeLayoutCacheMap != null) {
                                    if (objectTypeLayoutCacheMap.get(cacheKey) != null) {
                                        objectTypeLayoutCacheMap.remove(cacheKey);
                                    }
                                    objectTypeLayoutCacheMap.put(cacheKey, cachedList);
                                }
                                return cachedList;
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to read cached data", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to read cached data", e);
        }
        return null;
    }

    /**
     * Writes a list of Salesforce object types to the cache.
     *
     * @param objectTypes List of Salesforce object types.
     * @param cacheKey Cache key.
     * @param cacheType Cache type.
     */
    public void writeObjectTypes(List<SalesforceObjectType> objectTypes,
            String cacheKey, String cacheType) {
        if (objectTypes == null || cacheType == null || cacheKey == null ||
        		Constants.EMPTY_STRING.equals(cacheType) ||
        		Constants.EMPTY_STRING.equals(cacheKey) ||
                objectTypes.size() == 0) {
            return;
        }
        final String soupName = cacheType + cacheKey;

        // Inserts or updates data in memory cache.
        if (objectTypeCacheMap != null) {
            if (objectTypeCacheMap.get(cacheKey) != null) {
                objectTypeCacheMap.remove(cacheKey);
            }
            objectTypeCacheMap.put(cacheKey, objectTypes);
        }

        // Inserts or updates data in smart store.
        final JSONArray data = new JSONArray();
        for (final SalesforceObjectType objectType : objectTypes) {
            if (objectType != null) {
                data.put(objectType.getRawData());
            }
        }
        if (data.length() > 0) {
            final JSONObject object = new JSONObject();
            try {
                object.put(String.format(CACHE_DATA_KEY, cacheKey), data);
                object.put(String.format(CACHE_TIME_KEY, cacheKey), System.currentTimeMillis());
                upsertData(soupName, object, cacheKey);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while attempting to cache data", e);
            } catch (SmartStoreException e) {
                Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
            }
        }
    }

    /**
     * Writes a list of Salesforce object layouts to the cache.
     *
     * @param objects List of Salesforce object layouts.
     * @param cacheKey Cache key.
     * @param cacheType Cache type.O
     */
    public void writeObjectLayouts(List<SalesforceObjectTypeLayout> objects,
    		String cacheKey, String cacheType) {
    	if (objects == null || cacheType == null || cacheKey == null ||
        		Constants.EMPTY_STRING.equals(cacheType) ||
        		Constants.EMPTY_STRING.equals(cacheKey) ||
                objects.size() == 0) {
            return;
        }
        final String soupName = cacheType + cacheKey;

        // Inserts or updates data in memory cache.
        if (objectTypeLayoutCacheMap != null) {
            if (objectTypeLayoutCacheMap.get(cacheKey) != null) {
                objectTypeLayoutCacheMap.remove(cacheKey);
            }
            objectTypeLayoutCacheMap.put(cacheKey, objects);
        }

        // Inserts or updates data in smart store.
        final JSONArray data = new JSONArray();
        for (final SalesforceObjectTypeLayout object : objects) {
            if (object != null) {
                final JSONObject obj = new JSONObject();
                try {
                    obj.put("rawData", object.getRawData());
                    obj.put("type", object.getObjectType());
                    data.put(obj);
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException occurred while attempting to cache data", e);
                }
            }
        }
        if (data.length() > 0) {
            final JSONObject obj = new JSONObject();
            try {
                obj.put(String.format(CACHE_DATA_KEY, cacheKey), data);
                obj.put(String.format(CACHE_TIME_KEY, cacheKey), System.currentTimeMillis());
                upsertData(soupName, obj, cacheKey);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while attempting to cache data", e);
            } catch (SmartStoreException e) {
                Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
            }
        }
    }

    /**
     * Writes a list of Salesforce objects to the cache.
     *
     * @param objects List of Salesforce objects.
     * @param cacheKey Cache key.
     * @param cacheType Cache type.
     */
    public void writeObjects(List<SalesforceObject> objects, String cacheKey,
            String cacheType) {
    	if (objects == null || cacheType == null || cacheKey == null ||
        		Constants.EMPTY_STRING.equals(cacheType) ||
        		Constants.EMPTY_STRING.equals(cacheKey) ||
                objects.size() == 0) {
            return;
        }
        final String soupName = cacheType + cacheKey;

        // Inserts or updates data in memory cache.
        if (objectCacheMap != null) {
            if (objectCacheMap.get(cacheKey) != null) {
                objectCacheMap.remove(cacheKey);
            }
            objectCacheMap.put(cacheKey, objects);
        }

        // Inserts or updates data in smart store.
        final JSONArray data = new JSONArray();
        for (final SalesforceObject object : objects) {
            if (object != null) {
                data.put(object.getRawData());
            }
        }
        if (data.length() > 0) {
            final JSONObject obj = new JSONObject();
            try {
                obj.put(String.format(CACHE_DATA_KEY, cacheKey), data);
                obj.put(String.format(CACHE_TIME_KEY, cacheKey), System.currentTimeMillis());
                upsertData(soupName, obj, cacheKey);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while attempting to cache data", e);
            } catch (SmartStoreException e) {
                Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
            }
        }
    }

    /**
     * Switches the context of the cache to the current account, by
     * re-instantiating this class.
     */
    public void switchUserAccount() {
        INSTANCE = new CacheManager();
    }

    /**
     * Helper method that registers a soup with index specs.
     *
     * @param soupName Soup name.
     * @param cacheKey Cache key.
     */
    private void registerSoup(String soupName, String cacheKey) {
        if (!doesCacheExist(soupName)) {
            final IndexSpec[] indexSpecs = {
                    new IndexSpec(String.format(CACHE_DATA_KEY, cacheKey), Type.string),
                    new IndexSpec(String.format(CACHE_TIME_KEY, cacheKey), Type.integer)
            };
            smartStore.registerSoup(soupName, indexSpecs);
        }
    }

    /**
     * Helper method that inserts/updates a record in the cache.
     *
     * @param soupName Soup name.
     * @param object Object to be inserted.
     * @param cacheKey Cache key.
     */
    private void upsertData(String soupName, JSONObject object, String cacheKey) {
        if (soupName == null || object == null ||
        		Constants.EMPTY_STRING.equals(soupName)) {
            return;
        }
        registerSoup(soupName, cacheKey);
        try {
            smartStore.upsert(soupName, object, String.format(CACHE_DATA_KEY, cacheKey));
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to cache data", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
        }
    }

    /**
     * Resets the in memory cache.
     */
    private void resetInMemoryCache() {
        objectCacheMap = new HashMap<String, List<SalesforceObject>>();
        objectTypeCacheMap = new HashMap<String, List<SalesforceObjectType>>();
        objectTypeLayoutCacheMap = new HashMap<String, List<SalesforceObjectTypeLayout>>();
    }
}
