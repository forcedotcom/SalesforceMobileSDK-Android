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
package com.salesforce.androidsdk.smartsync.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.smartsync.util.Constants;

/**
 * This class provides APIs to store and retrieve Salesforce object
 * metadata, object layouts and MRU objects from a simple cache.
 *
 * @author bhariharan
 */
public class CacheManager {

    private static final String TAG = "SmartSync: CacheManager";
    private static final String CACHE_KEY = "cache_key";
    private static final String CACHE_DATA = "cache_data";
    private static final String SOUP_OF_SOUPS = "master_soup";
    private static final String SOUP_NAMES_KEY = "soup_names";

    private static Map<String, CacheManager> INSTANCES;

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
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized CacheManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized CacheManager getInstance(UserAccount account, String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account == null) {
            return null;
        }
        String uniqueId = account.getUserId();
        if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
            communityId = null;
        }
        if (!TextUtils.isEmpty(communityId)) {
            uniqueId = uniqueId + communityId;
        }
        CacheManager instance = null;
        if (INSTANCES == null) {
            INSTANCES = new HashMap<String, CacheManager>();
            instance = new CacheManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        } else {
            instance = INSTANCES.get(uniqueId);
        }
        if (instance == null) {
            instance = new CacheManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        }
        instance.resetInMemoryCache();
        return instance;
    }

    /**
     * Resets the cache manager for this user account. This method clears
     * only the in memory cache.
     *
     * @param account User account.
     */
    public static synchronized void softReset(UserAccount account) {
        softReset(account, null);
    }

    /**
     * Resets the cache manager for this user account. This method clears
     * only the in memory cache.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    public static synchronized void softReset(UserAccount account, String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account != null) {
            String uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
            if (getInstance(account, communityId) != null) {
                getInstance(account, communityId).resetInMemoryCache();
                if (INSTANCES != null) {
                    INSTANCES.remove(uniqueId);
                }
            }
        }
    }

    /**
     * Resets the cache manager for this user account. This method clears
     * the in memory cache and the underlying cache in the database.
     *
     * @param account User account.
     */
    public static synchronized void hardReset(UserAccount account) {
        hardReset(account, null);
    }

    /**
     * Resets the cache manager for this user account. This method clears
     * the in memory cache and the underlying cache in the database.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    public static synchronized void hardReset(UserAccount account, String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account != null) {
            String uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
            if (getInstance(account, communityId) != null) {
                getInstance(account, communityId).cleanCache();
                if (INSTANCES != null) {
                    INSTANCES.remove(uniqueId);
                }
            }
        }
    }

    /**
     * Private parameterized constructor.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    private CacheManager(UserAccount account, String communityId) {
        smartStore = SmartSyncSDKManager.getInstance().getSmartStore(account, communityId);
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
        if (doesCacheExist(cacheType)) {
            smartStore.dropSoup(cacheType);
            removeSoupNameFromMasterSoup(cacheType);
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
            if (!doesCacheExist(cacheType)) {
                return 0;
            }
            final QuerySpec querySpec = QuerySpec.buildExactQuerySpec(cacheType,
            		CACHE_KEY, cacheKey, null, null, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
                final JSONObject jObj = results.optJSONObject(0);
                if (jObj != null) {
                    return jObj.optLong(SmartStore.SOUP_LAST_MODIFIED_DATE);
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
        if (!doesCacheExist(cacheType)) {
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
        try {
            final QuerySpec querySpec = QuerySpec.buildExactQuerySpec(cacheType,
            		CACHE_KEY, cacheKey, null, null, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
            	final JSONObject jObj = results.optJSONObject(0);
                if (jObj != null) {
                    final JSONArray res = jObj.optJSONArray(CACHE_DATA);
                    if (res != null && res.length() > 0) {
                    	final List<SalesforceObjectType> cachedList = new ArrayList<SalesforceObjectType>();
                        for (int j = 0; j < res.length(); j++) {
                            final JSONObject sfObj = res.optJSONObject(j);
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
        if (!doesCacheExist(cacheType)) {
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
        try {
            final QuerySpec querySpec = QuerySpec.buildExactQuerySpec(cacheType,
            		CACHE_KEY, cacheKey, null, null, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
            	final JSONObject jObj = results.optJSONObject(0);
                if (jObj != null) {
                    final JSONArray res = jObj.optJSONArray(CACHE_DATA);
                    if (res != null && res.length() > 0) {
                        final List<SalesforceObject> cachedList = new ArrayList<SalesforceObject>();
                        for (int j = 0; j < res.length(); j++) {
                            final JSONObject sfObj = res.optJSONObject(j);
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
        if (!doesCacheExist(cacheType)) {
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
        try {
            final QuerySpec querySpec = QuerySpec.buildExactQuerySpec(cacheType,
            		CACHE_KEY, cacheKey, null, null, 1);
            final JSONArray results = smartStore.query(querySpec, 0);
            if (results != null && results.length() > 0) {
            	final JSONObject jObj = results.optJSONObject(0);
                if (jObj != null) {
                    final JSONArray res = jObj.optJSONArray(CACHE_DATA);
                    if (res != null && res.length() > 0) {
                        final List<SalesforceObjectTypeLayout> cachedList = new ArrayList<SalesforceObjectTypeLayout>();
                        for (int j = 0; j < res.length(); j++) {
                            final JSONObject sfObj = res.optJSONObject(j);
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
                object.put(CACHE_KEY, cacheKey);
                object.put(CACHE_DATA, data);
                upsertData(cacheType, object, cacheKey);
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
                obj.put(CACHE_KEY, cacheKey);
                obj.put(CACHE_DATA, data);
                upsertData(cacheType, obj, cacheKey);
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
                obj.put(CACHE_KEY, cacheKey);
                obj.put(CACHE_DATA, data);
                upsertData(cacheType, obj, cacheKey);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while attempting to cache data", e);
            } catch (SmartStoreException e) {
                Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
            }
        }
    }
    
    /**
     * @return SmartStore instance used by this CacheManager
     */
    SmartStore getSmartStore() {
    	return smartStore;
    }

    /**
     * Helper method that registers a soup with index specs.
     *
     * @param soupName Soup name.
     * @param cacheKey Cache key.
     */
    private void registerSoup(String soupName, String cacheKey) {
    	registerMasterSoup();
        if (!doesCacheExist(soupName)) {
            final IndexSpec[] indexSpecs = {
                    new IndexSpec(CACHE_KEY, Type.string)
            };
            smartStore.registerSoup(soupName, indexSpecs);
        }
    }

    /**
     * Helper method that registers the master soup, if necessary.
     */
    private void registerMasterSoup() {
    	if (doesCacheExist(SOUP_OF_SOUPS)) {
    		return;
    	}
    	final IndexSpec[] indexSpecs = {
    			new IndexSpec(SOUP_NAMES_KEY, Type.string)
    	};
    	smartStore.registerSoup(SOUP_OF_SOUPS, indexSpecs);
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
            smartStore.upsert(soupName, object, cacheKey);
            addSoupNameToMasterSoup(soupName);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to cache data", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
        }
    }

    /**
     * Helper method that returns whether the specified soup name exists
     * in the master soup.
     *
     * @param soupName Soup name.
     * @return True - if it exists, False - otherwise.
     */
    private boolean doesMasterSoupContainSoup(String soupName) {
        final JSONArray soupNames = getAllSoupNames();
        for (int i = 0; i < soupNames.length(); i++) {
        	final JSONArray names = soupNames.optJSONArray(i);
        	if (names != null && names.length() > 0) {
                final String name = names.optString(0);
                if (soupName.equals(name)) {
                	return true;
                }
        	}
        }
        return false;
    }

    /**
     * Returns the list of soups being used in the cache.
     *
     * @return List of soup names.
     */
    private JSONArray getAllSoupNames() {
    	final String smartSql = "SELECT {" + SOUP_OF_SOUPS + ":" +
        		SOUP_NAMES_KEY + "} FROM {" + SOUP_OF_SOUPS + "}";
        JSONArray results = null;
    	QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1);
        try {
            int count = smartStore.countQuery(querySpec);
            querySpec = QuerySpec.buildSmartQuerySpec(smartSql, count);
			results = smartStore.query(querySpec, 0);
		} catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to read cached data", e);
		} catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to read cached data", e);
        }
        if (results == null) {
    		results = new JSONArray();
    	}
		return results;
    }

    /**
     * Adds a soup name to the master soup, if it does not exist.
     *
     * @param soupName Soup name to be added.
     */
    private void addSoupNameToMasterSoup(String soupName) {
    	if (doesMasterSoupContainSoup(soupName)) {
    		return;
    	}
    	final JSONObject object = new JSONObject();
    	try {
    		object.put(SOUP_NAMES_KEY, soupName);
        	smartStore.upsert(SOUP_OF_SOUPS, object);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while attempting to cache data", e);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
        }
    }

    /**
     * Removes a soup name from the master soup, if it exists.
     *
     * @param soupName Soup name to be removed.
     */
    private void removeSoupNameFromMasterSoup(String soupName) {
    	if (!doesMasterSoupContainSoup(soupName)) {
    		return;
    	}
    	try {
        	long soupEntryId = smartStore.lookupSoupEntryId(SOUP_OF_SOUPS,
        			SOUP_NAMES_KEY, soupName);
        	smartStore.delete(SOUP_OF_SOUPS, soupEntryId);
        } catch (SmartStoreException e) {
            Log.e(TAG, "SmartStoreException occurred while attempting to cache data", e);
        }
    }

    /**
     * Clears the master soup of all data.
     */
    private void clearMasterSoup() {
    	smartStore.dropSoup(SOUP_OF_SOUPS);
    }

    /**
     * Clears all soups used by this class and the master soup.
     */
    private void clearAllSoups() {
    	final JSONArray soupNames = getAllSoupNames();
    	for (int i = 0; i < soupNames.length(); i++) {
        	final JSONArray names = soupNames.optJSONArray(i);
        	if (names != null && names.length() > 0) {
                final String name = names.optString(0);
                if (name != null) {
        			smartStore.dropSoup(name);
                }
        	}
        }
    	clearMasterSoup();
    }

    /**
     * Resets the in memory cache.
     */
    private void resetInMemoryCache() {
        objectCacheMap = new HashMap<String, List<SalesforceObject>>();
        objectTypeCacheMap = new HashMap<String, List<SalesforceObjectType>>();
        objectTypeLayoutCacheMap = new HashMap<String, List<SalesforceObjectTypeLayout>>();
    }

    /**
     * Clears the cache and creates a new clean cache.
     */
    private void cleanCache() {
        resetInMemoryCache();

        // Checks to make sure SmartStore hasn't already been cleaned up.
        if (SmartSyncSDKManager.getInstance().hasSmartStore()) {
        	clearAllSoups();
        }
    }
}
