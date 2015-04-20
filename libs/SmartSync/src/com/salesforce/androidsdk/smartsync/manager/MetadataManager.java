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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartsync.R;
import com.salesforce.androidsdk.smartsync.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectLayoutColumn;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;

/**
 * This class contains APIs to fetch Salesforce object metadata, recently used
 * objects, and other object related data.
 *
 * @author bhariharan
 */
public class MetadataManager {

    private static final String TAG = "SmartSync: MetadataManager";
    private static final int MAX_QUERY_LIMIT = 200;
    private static final long DEFAULT_METADATA_REFRESH_INTERVAL = 7 * 24 * 60 * 60 * 1000;

    // Cache constants.
    private static final String MRU_CACHE_TYPE = "recent_objects";
    private static final String METADATA_CACHE_TYPE = "metadata";
    private static final String LAYOUT_CACHE_TYPE = "layout";
    private static final String SMART_SCOPES_CACHE_KEY = "smart_scopes";
    private static final String MRU_BY_OBJECT_TYPE_CACHE_KEY = "mru_for_%s";
    private static final String ALL_OBJECTS_CACHE_KEY = "all_objects";
    private static final String OBJECT_BY_TYPE_CACHE_KEY = "object_info_%s";
    private static final String OBJECT_LAYOUT_BY_TYPE_CACHE_KEY = "object_layout_%s";

    // Other constants.
    private static final String RECORD_TYPE_GLOBAL = "global";
    private static final String RECENTLY_VIEWED = "RecentlyViewed";

    private static Map<String, MetadataManager> INSTANCES;

    private String apiVersion;
    private CacheManager cacheManager;
	private RestClient restClient;
    private String communityId;

    /**
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized MetadataManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized MetadataManager getInstance(UserAccount account, String communityId) {
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
        MetadataManager instance = null;
        if (INSTANCES == null) {
            INSTANCES = new HashMap<String, MetadataManager>();
            instance = new MetadataManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        } else {
            instance = INSTANCES.get(uniqueId);
        }
        if (instance == null) {
            instance = new MetadataManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        }
        return instance;
    }

    /**
     * Resets the metadata manager associated with this user account.
     *
     * @param account User account.
     */
    public static synchronized void reset(UserAccount account) {
        reset(account, null);
    }

    /**
     * Resets the metadata manager associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    public static synchronized void reset(UserAccount account, String communityId) {
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
            if (INSTANCES != null) {
                INSTANCES.remove(uniqueId);
            }
        }
    }

    /**
     * Private parameterized constructor.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    private MetadataManager(UserAccount account, String communityId) {
        apiVersion = ApiVersionStrings.VERSION_NUMBER;
        this.communityId = communityId;
        cacheManager = CacheManager.getInstance(account, communityId);
        restClient = SalesforceSDKManager.getInstance().getClientManager().peekRestClient(account);
    }
    
    /**
     * Sets the rest client to be used.
     * This is primarily used only by tests.
     * 
     * @param restClient
     */
    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Sets the cache manager to be used.
     *
     * @param cacheMgr CacheManager instance.
     */   
    public void setCacheManager(CacheManager cacheMgr) {
    	cacheManager = cacheMgr;
    }

    /**
     * Sets the API version to be used (for example, 'v33.0').
     *
     * @param apiVer API version to be used.
     */
    public void setApiVersion(String apiVer) {
        apiVersion = apiVer;
    }

    /**
     * Returns the API version being used.
     *
     * @return API version being used.
     */
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Returns a list of smart scope object types.
     *
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @return List of smart scope object types.
     */
    public List<SalesforceObjectType> loadSmartScopeObjectTypes(CachePolicy cachePolicy,
            long refreshCacheIfOlderThan) {
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            cacheManager.removeCache(MRU_CACHE_TYPE, SMART_SCOPES_CACHE_KEY);
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD) {
            cacheManager.removeCache(MRU_CACHE_TYPE, SMART_SCOPES_CACHE_KEY);
        }

        // Checks the cache for data.
        long cachedTime = cacheManager.getLastCacheUpdateTime(MRU_CACHE_TYPE,
        		SMART_SCOPES_CACHE_KEY);
        final List<SalesforceObjectType> cachedData = getCachedObjectTypes(cachePolicy,
                MRU_CACHE_TYPE, SMART_SCOPES_CACHE_KEY);

        // Returns cache data if the cache policy explicitly states so.
        if (cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD) {
            return cachedData;
        }
        if (cachedData != null && cachedData.size() > 0 &&
                cachePolicy != CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE &&
                !cacheManager.needToReloadCache((cachedData != null), cachePolicy,
                        cachedTime, refreshCacheIfOlderThan)) {
            return cachedData;
        }

        // Loads data from the server.
        return loadSmartScopes(cachePolicy);
    }

    /**
     * Returns a list of MRU objects based on object type.
     *
     * @param objectTypeName Object type name (set to 'null' for global MRU).
     * @param limit Limit on number of objects (max is 'MAX_QUERY_LIMIT').
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @param networkFieldName Network field name for this object type.
     * @return List of recently accessed objects.
     */
    public List<SalesforceObject> loadMRUObjects(String objectTypeName,
            int limit, CachePolicy cachePolicy, long refreshCacheIfOlderThan,
            String networkFieldName) {
        if (limit > MAX_QUERY_LIMIT || limit < 0) {
            limit = MAX_QUERY_LIMIT;
        }
        String cacheKey;
        boolean globalMRU = false;
        if (objectTypeName == null || Constants.EMPTY_STRING.equals(objectTypeName)) {
            globalMRU = true;
            cacheKey = String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL);
        } else {
            cacheKey = String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, objectTypeName);
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            cacheManager.removeCache(MRU_CACHE_TYPE, cacheKey);
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD) {
            cacheManager.removeCache(MRU_CACHE_TYPE, cacheKey);
        }

        // Checks the cache for data.
        long cachedTime = cacheManager.getLastCacheUpdateTime(MRU_CACHE_TYPE, cacheKey);
        List<SalesforceObject> cachedData = getCachedObjects(cachePolicy,
                MRU_CACHE_TYPE, cacheKey);

        // Returns cache data if the cache policy explicitly states so.
        if (cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD) {
            if (cachedData != null && limit > 0 && limit < cachedData.size()) {
                cachedData = cachedData.subList(0, limit - 1);
            }
            return cachedData;
        }
        if (cachedData != null && cachedData.size() > 0 &&
                cachePolicy != CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE &&
                !cacheManager.needToReloadCache((cachedData != null), cachePolicy,
                        cachedTime, refreshCacheIfOlderThan)) {
            if (limit > 0 && limit < cachedData.size()) {
                cachedData = cachedData.subList(0, limit - 1);
            }
            return cachedData;
        }
        return loadRecentObjects(objectTypeName, globalMRU, limit,
                cachePolicy, cacheKey, networkFieldName);
    }

    /**
     * Returns a list of all object types.
     *
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @return List of all object types.
     */
    public List<SalesforceObjectType> loadAllObjectTypes(CachePolicy cachePolicy,
            long refreshCacheIfOlderThan) {
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            cacheManager.removeCache(METADATA_CACHE_TYPE, ALL_OBJECTS_CACHE_KEY);
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD) {
            cacheManager.removeCache(METADATA_CACHE_TYPE, ALL_OBJECTS_CACHE_KEY);
        }
        long cachedTime = cacheManager.getLastCacheUpdateTime(METADATA_CACHE_TYPE,
        		ALL_OBJECTS_CACHE_KEY);

        // Checks if the cache needs to be refreshed.
        final List<SalesforceObjectType> cachedData = getCachedObjectTypes(cachePolicy,
                METADATA_CACHE_TYPE, ALL_OBJECTS_CACHE_KEY);

        // Returns cache data if the cache policy explicitly states so.
        if (cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD) {
            return cachedData;
        }
        if (cachedData != null && cachedData.size() > 0 &&
                cachePolicy != CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE &&
                !cacheManager.needToReloadCache((cachedData != null), cachePolicy,
                        cachedTime, refreshCacheIfOlderThan)) {
            return cachedData;
        }

        // Makes a live server call to fetch object types.
        final List<SalesforceObjectType> returnList = new ArrayList<SalesforceObjectType>();
        RestResponse response = null;
        try {
        	response = restClient.sendSync(RestRequest.getRequestForDescribeGlobal(apiVersion));
        } catch(IOException e) {
        	Log.e(TAG, "IOException occurred while sending request", e);
        }
        
        if (response != null && response.isSuccess()) {
            try {
                final JSONObject responseJSON = response.asJSONObject();
                if (responseJSON != null) {
                    final JSONArray objectTypes = responseJSON.optJSONArray("sobjects");
                    if (objectTypes != null) {
                        for (int i = 0; i < objectTypes.length(); i++) {
                            final JSONObject metadata = objectTypes.optJSONObject(i);
                            if (metadata != null) {
                                final boolean hidden = metadata.optBoolean(
                                        Constants.HIDDEN_FIELD, false);
                                if (!hidden) {
                                    final SalesforceObjectType objType = new SalesforceObjectType(metadata);
                                    returnList.add(objType);
                                }
                            }
                        }
                        if (returnList.size() > 0 && shouldCacheData(cachePolicy)) {
                            cacheObjectTypes(returnList, METADATA_CACHE_TYPE, ALL_OBJECTS_CACHE_KEY);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException occurred while reading data", e);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while parsing", e);
            }
        } else if (shouldFallBackOnCache(cachePolicy)) {
            return cachedData;
        }
        if (returnList.size() == 0) {
            return null;
        }
        return returnList;
    }

    /**
     * Returns metadata for a specific object type.
     *
     * @param objectTypeName Object type name.
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @return Metadata for a specific object type.
     */
    public SalesforceObjectType loadObjectType(String objectTypeName,
            CachePolicy cachePolicy, long refreshCacheIfOlderThan) {
        if (objectTypeName == null || Constants.EMPTY_STRING.equals(objectTypeName)) {
            Log.e(TAG, "Cannot load recently accessed objects for invalid object type");
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            cacheManager.removeCache(METADATA_CACHE_TYPE,
            		String.format(OBJECT_BY_TYPE_CACHE_KEY, objectTypeName));
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD) {
            cacheManager.removeCache(METADATA_CACHE_TYPE,
            		String.format(OBJECT_BY_TYPE_CACHE_KEY, objectTypeName));
        }
        long cachedTime = cacheManager.getLastCacheUpdateTime(METADATA_CACHE_TYPE,
                String.format(OBJECT_BY_TYPE_CACHE_KEY, objectTypeName));

        // Checks if the cache needs to be refreshed.
        final SalesforceObjectType cachedData = getCachedObjectType(objectTypeName);

        // Returns cache data if the cache policy explicitly states so.
        if (cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD) {
            return cachedData;
        }
        if (cachedData != null && cachePolicy != CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE &&
                !cacheManager.needToReloadCache((cachedData != null), cachePolicy,
                        cachedTime, refreshCacheIfOlderThan)) {
            return cachedData;
        }

        // Makes a live server call to fetch metadata.
        RestResponse response = null;
        try {
        	response = restClient.sendSync(RestRequest.getRequestForDescribe(apiVersion, objectTypeName));
        } catch(IOException e) {
        	Log.e(TAG, "IOException occurred while sending request", e);
        }
        
        if (response != null && response.isSuccess()) {
            try {
                final JSONObject responseJSON = response.asJSONObject();
                if (responseJSON != null) {
                    final SalesforceObjectType objType = new SalesforceObjectType(responseJSON);
                    if (shouldCacheData(cachePolicy)) {
                        final List<SalesforceObjectType> objList = new ArrayList<SalesforceObjectType>();
                        objList.add(objType);
                        cacheObjectTypes(objList, METADATA_CACHE_TYPE,
                                String.format(OBJECT_BY_TYPE_CACHE_KEY, objectTypeName));
                    }
                    return objType;
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException occurred while reading data", e);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while parsing", e);
            }
        } else if (shouldFallBackOnCache(cachePolicy)) {
            return cachedData;
        }
        return null;
    }

    /**
     * Returns metadata for the specified list of object types.
     *
     * @param objectTypeNames List of object type names.
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @return Metadata for the list of object types.
     */
    public List<SalesforceObjectType> loadObjectTypes(List<String> objectTypeNames,
            CachePolicy cachePolicy, long refreshCacheIfOlderThan) {
        if (objectTypeNames == null || objectTypeNames.size() == 0) {
            return null;
        }
        List<SalesforceObjectType> results = new ArrayList<SalesforceObjectType>();
        for (final String objectTypeName : objectTypeNames) {
            if (objectTypeName != null && !Constants.EMPTY_STRING.equals(objectTypeName)) {
                final SalesforceObjectType object = loadObjectType(objectTypeName,
                        cachePolicy, refreshCacheIfOlderThan);
                if (object != null) {
                    results.add(object);
                }
            }
        }
        if (results.size() == 0) {
            results = null;
        }
        return results;
    }

    /**
     * Returns whether the specified object type is searchable or not.
     *
     * @param objectType Object type.
     * @return True - if searchable, False - otherwise.
     */
    public boolean isObjectTypeSearchable(SalesforceObjectType objectType) {
        if (objectType == null) {
            return false;
        }
        final String objectName = ((objectType.getName() == null) ?
        		Constants.EMPTY_STRING : objectType.getName());
        if (!Constants.EMPTY_STRING.equals(objectName)) {
            if (objectType.getRawData() == null) {
                objectType = loadObjectType(objectName,
                		CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, 0);
            }
            if (objectType != null) {
                return (objectType.isSearchable());
            }
        }
        return false;
    }

    /**
     * Returns object type layouts for the specified list of object types.
     *
     * @param objectTypes List of object types.
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @return Object type layouts for the list of object types.
     */
    public List<SalesforceObjectTypeLayout> loadObjectTypesLayout(List<SalesforceObjectType> objectTypes,
            CachePolicy cachePolicy, long refreshCacheIfOlderThan) {
        if (objectTypes == null || objectTypes.size() == 0) {
            return null;
        }
        List<SalesforceObjectTypeLayout> results = new ArrayList<SalesforceObjectTypeLayout>();
        for (final SalesforceObjectType objectType : objectTypes) {
            if (objectType != null) {
                final SalesforceObjectTypeLayout layout = loadObjectTypeLayout(objectType,
                        cachePolicy, refreshCacheIfOlderThan);
                if (layout != null) {
                    results.add(layout);
                }
            }
        }
        if (results.size() == 0) {
            results = null;
        }
        return results;
    }

    /**
     * Loads the object layout for the specified object type.
     *
     * @param objectType Object type.
     * @param cachePolicy Cache policy.
     * @param refreshCacheIfOlderThan Time interval to refresh cache.
     * @return Object layout.
     */
    public SalesforceObjectTypeLayout loadObjectTypeLayout(SalesforceObjectType objectType,
            CachePolicy cachePolicy, long refreshCacheIfOlderThan) {
        if (objectType == null) {
            Log.e(TAG, "Cannot load object layout with an invalid object type");
            return null;
        }
        final String objectTypeName = objectType.getName();
        if (objectTypeName == null || Constants.EMPTY_STRING.equals(objectTypeName)) {
            Log.e(TAG, "Cannot load object layout with an invalid object type");
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            cacheManager.removeCache(LAYOUT_CACHE_TYPE,
                    String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY, objectTypeName));
            return null;
        }
        if (cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD) {
            cacheManager.removeCache(LAYOUT_CACHE_TYPE,
                    String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY, objectTypeName));
        }
        long cachedTime = cacheManager.getLastCacheUpdateTime(LAYOUT_CACHE_TYPE,
                String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY, objectTypeName));

        // Checks if the cache needs to be refreshed.
        final List<SalesforceObjectTypeLayout> cachedData = getCachedObjectLayouts(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
                LAYOUT_CACHE_TYPE, String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY, objectTypeName));

        // Returns cache data if the cache policy explicitly states so.
        if (cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD) {
            if (cachedData != null && cachedData.size() > 0) {
                return cachedData.get(0);
            } else {
                return null;
            }
        }
        if (cachedData != null && cachedData.size() > 0 &&
        		cachePolicy != CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE &&
                !cacheManager.needToReloadCache((cachedData != null), cachePolicy,
                        cachedTime, refreshCacheIfOlderThan)) {
            return cachedData.get(0);
        }

        // Checks if a layout can be loaded for this object type.
        if (objectType.getRawData() == null) {
            objectType = loadObjectType(objectTypeName, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, 0);
        }
        if (objectType == null || !objectType.isSearchable() || !objectType.isLayoutable()) {
            return null;
        }

        // Makes a live server call to fetch the object layout.
        RestResponse response = null;
        try {
        	response = restClient.sendSync(RestRequest.getRequestForSearchResultLayout(apiVersion, Arrays.asList(new String[] {objectTypeName})));
        } catch(IOException e) {
        	Log.e(TAG, "IOException occurred while sending request", e);
        }
        if (response != null && response.isSuccess()) {
            try {
                final JSONArray responseJSON = response.asJSONArray();
                if (responseJSON != null && responseJSON.length() > 0) {
                    final JSONObject objJSON = responseJSON.optJSONObject(0);
                    if (objJSON != null) {
                        final SalesforceObjectTypeLayout objTypeLayout = new SalesforceObjectTypeLayout(objectTypeName,
                                objJSON);
                        if (shouldCacheData(cachePolicy)) {
                            final List<SalesforceObjectTypeLayout> layoutList = new ArrayList<SalesforceObjectTypeLayout>();
                            layoutList.add(objTypeLayout);
                            cacheObjectLayouts(layoutList, LAYOUT_CACHE_TYPE,
                                    String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY,
                                    		objectTypeName));
                        }
                        return objTypeLayout;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException occurred while reading data", e);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while parsing", e);
            }
        } else if (shouldFallBackOnCache(cachePolicy)) {
            if (cachedData != null && cachedData.size() > 0) {
                return cachedData.get(0);
            }
        }
        return null;
    }

    /**
     * Returns the color resource associated with an object type.
     *
     * @param objTypeName Object type name.
     * @return Color resource associated with the object type.
     */
    public int getColorResourceForObjectType(String objTypeName) {
        int color = R.color.record_other;
        if (objTypeName == null) {
            return color;
        }
        if (Constants.ACCOUNT.equals(objTypeName)) {
            color = R.color.record_account;
        } else if (Constants.CONTACT.equals(objTypeName)) {
            color = R.color.record_contact;
        } else if (Constants.TASK.equals(objTypeName)) {
            color = R.color.record_task;
        } else if (Constants.CASE.equals(objTypeName)) {
            color = R.color.record_case;
        } else if (Constants.OPPORTUNITY.equals(objTypeName)) {
            color = R.color.record_opportunity;
        } else if (Constants.LEAD.equals(objTypeName)) {
            color = R.color.record_lead;
        } else if (Constants.CAMPAIGN.equals(objTypeName)) {
            color = R.color.record_campaign;
        }
        return color;
    }

    /**
     * Returns the community ID if set, returns null otherwise.
     *
     * @return Community ID, or null if not set.
     */
    public String getCommunityId() {
        return communityId;
    }

    /**
     * Sets the community ID. All subsequent calls will query
     * only within the given network.
     *
     * @param communityId ID to use for network aware calls.
     */
    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    /**
     * Marks an object as viewed on the server.
     *
     * @param objectId Object ID.
     * @param objectType Object type.
     * @param networkFieldName Network field name for this object type.
     */
    public void markObjectAsViewed(String objectId, String objectType,
    		String networkFieldName) {
        if (objectId == null || objectType == null
                || Constants.EMPTY_STRING.equals(objectId)
                || Constants.EMPTY_STRING.equals(objectType)
                || Constants.CONTENT_VERSION.equals(objectType)
                || Constants.CONTENT.equals(objectType)) {
            Log.w(TAG, "Cannot mark object as viewed");
            return;
        }
        final SalesforceObjectType result = loadObjectType(objectType,
                CachePolicy.RELOAD_IF_EXPIRED_AND_RETURN_CACHE_DATA,
                DEFAULT_METADATA_REFRESH_INTERVAL);
        final SOQLBuilder queryBuilder = SOQLBuilder.getInstanceWithFields(Constants.ID).from(objectType);
        try {
            String whereClause;
            if (result != null && isObjectTypeSearchable(result)) {
                whereClause = String.format("Id = '%s' FOR VIEW", objectId);
            } else {
                whereClause = String.format("Id = '%s'", objectId);
            }
            if (communityId != null && networkFieldName != null) {
            	whereClause = String.format("%s AND %s = '%s'", whereClause,
                        networkFieldName, communityId);
            }
            queryBuilder.where(whereClause);
            final String queryString = queryBuilder.build();

            RestResponse response = null;
            try {
            	response = restClient.sendSync(RestRequest.getRequestForQuery(apiVersion, queryString));
            } catch(IOException e) {
            	Log.e(TAG, "IOException occurred while sending request", e);
            }
            if (response != null && response.isSuccess()) {
                final JSONObject responseJSON = response.asJSONObject();
                if (responseJSON != null) {
                    final JSONArray records = responseJSON.optJSONArray("records");
                    if (records == null || records.length() == 0) {
                        Log.e(TAG, "Failed to mark object " + objectId + " as viewed, since object no longer exists");
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error occurred while attempting to mark object " + objectId + " as viewed", e);
        } catch (IOException e) {
            Log.e(TAG, "Error occurred while attempting to mark object " + objectId + " as viewed", e);
        }
    }

    /**
     * Returns whether a layout can be loaded for the specified object type.
     *
     * @param objType Object type.
     * @return True - if layout can be loaded, False - otherwise.
     */
    private boolean canLoadLayoutForObjectType(SalesforceObjectType objType) {
        if (objType == null) {
            return false;
        }
        return (objType.isLayoutable() && objType.isSearchable());
    }

    /**
     * Returns a list of layout fields for the specified object type.
     *
     * @param type Object type.
     * @return List of return fields.
     */
    private List<String> getLayoutFieldsForObjectType(SalesforceObjectType type) {
        if (type == null) {
            return null;
        }
        final SalesforceObjectTypeLayout layout = getCachedObjectLayout(type);
        if (layout == null) {
            return null;
        }
        final List<SalesforceObjectLayoutColumn> columns = layout.getColumns();
        if (columns == null || columns.size() == 0) {
            return null;
        }
        final List<String> results = new ArrayList<String>();
        for (final SalesforceObjectLayoutColumn col : columns) {
            if (col != null) {
                final String name = col.getName();
                if (name != null && !Constants.EMPTY_STRING.equals(name)) {
                    results.add(name);
                }
            }
        }
        if (results.size() == 0) {
            return null;
        }
        return results;
    }

    /**
     * Returns whether the data should be cached.
     *
     * @param cachePolicy Cache policy.
     * @return True - if the data should be cached, False - otherwise.
     */
    private boolean shouldCacheData(CachePolicy cachePolicy) {
        return ((cachePolicy != CachePolicy.IGNORE_CACHE_DATA)
                && (cachePolicy != CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD)
                && (cachePolicy != CachePolicy.INVALIDATE_CACHE_DONT_RELOAD));
    }

    /**
     * Caches a list of objects.
     *
     * @param objects List of objects.
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     */
    private void cacheObjects(List<SalesforceObject> objects,
    		String cacheType, String cacheKey) {
        if (objects != null && objects.size() > 0 &&
                cacheType != null && cacheKey != null) {
            cacheManager.writeObjects(objects, cacheKey, cacheType);
        }
    }

    /**
     * Caches a list of object types.
     *
     * @param objectTypes List of object types.
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     */
    private void cacheObjectTypes(List<SalesforceObjectType> objectTypes,
    		String cacheType, String cacheKey) {
        if (objectTypes != null && objectTypes.size() > 0 &&
                cacheType != null && cacheKey != null) {
            cacheManager.writeObjectTypes(objectTypes, cacheKey, cacheType);
        }
    }

    /**
     * Caches a list of object layouts.
     *
     * @param objects List of object layouts.
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     */
    private void cacheObjectLayouts(List<SalesforceObjectTypeLayout> objects,
    		String cacheType, String cacheKey) {
        if (objects != null && objects.size() > 0 &&
                cacheType != null && cacheKey != null) {
            cacheManager.writeObjectLayouts(objects, cacheKey, cacheType);
        }
    }

    /**
     * Returns a list of cached objects.
     *
     * @param cachePolicy Cache policy.
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return List of cached objects.
     */
    private List<SalesforceObject> getCachedObjects(CachePolicy cachePolicy,
            String cacheType, String cacheKey) {
        if (cachePolicy == CachePolicy.IGNORE_CACHE_DATA ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            return null;
        }
        return cacheManager.readObjects(cacheType, cacheKey);
    }

    /**
     * Returns cached metadata for a specific object type.
     *
     * @param objectTypeName Object type name.
     * @return Cached metadata for object type.
     */
    private SalesforceObjectType getCachedObjectType(String objectTypeName) {
        if (objectTypeName == null || Constants.EMPTY_STRING.equals(objectTypeName)) {
            return null;
        }
        SalesforceObjectType result = null;
        final List<SalesforceObjectType> objectTypes = getCachedObjectTypes(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
                METADATA_CACHE_TYPE, String.format(OBJECT_BY_TYPE_CACHE_KEY, objectTypeName));
        if (objectTypes != null && objectTypes.size() > 0) {
            result = objectTypes.get(0);
        }
        return result;
    }
    
    /**
     * Returns a list of cached object types.
     *
     * @param cachePolicy Cache policy.
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return List of cached object types.
     */
    private List<SalesforceObjectType> getCachedObjectTypes(CachePolicy cachePolicy,
            String cacheType, String cacheKey) {
    	if (cachePolicy == CachePolicy.IGNORE_CACHE_DATA ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            return null;
        }
        return cacheManager.readObjectTypes(cacheType, cacheKey);
    }

    /**
     * Returns cached object layout for a specific object type.
     *
     * @param objectType Object type.
     * @return Cached object layout.
     */
    private SalesforceObjectTypeLayout getCachedObjectLayout(SalesforceObjectType objectType) {
        if (objectType == null) {
            return null;
        }
        final String objectTypeName = objectType.getName();
        if (objectTypeName == null || Constants.EMPTY_STRING.equals(objectTypeName)) {
            return null;
        }
        SalesforceObjectTypeLayout result = null;
        final List<SalesforceObjectTypeLayout> objectTypes = getCachedObjectLayouts(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
                LAYOUT_CACHE_TYPE, String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY, objectTypeName));
        if (objectTypes != null && objectTypes.size() > 0) {
            result = objectTypes.get(0);
        }
        return result;
    }

    /**
     * Returns a list of cached object layouts.
     *
     * @param cachePolicy Cache policy.
     * @param cacheType Cache type.
     * @param cacheKey Cache key.
     * @return List of cached object layouts.
     */
    private List<SalesforceObjectTypeLayout> getCachedObjectLayouts(CachePolicy cachePolicy,
            String cacheType, String cacheKey) {
    	if (cachePolicy == CachePolicy.IGNORE_CACHE_DATA ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_AND_RELOAD ||
                cachePolicy == CachePolicy.INVALIDATE_CACHE_DONT_RELOAD) {
            return null;
        }
        return cacheManager.readObjectLayouts(cacheType, cacheKey);
    }

    /**
     * Returns fields for the specified object type.
     *
     * @param objectTypeName Object type name.
     * @return Fields for the object type.
     */
    private String getReturnFieldsForObjectType(String objectTypeName) {
        if (objectTypeName == null) {
            return null;
        }
        final SalesforceObjectType objectType = getCachedObjectType(objectTypeName);
        final List<String> returnFields = new ArrayList<String>();
        final List<String> extraValues = getLayoutReturnFieldsForObjectType(objectTypeName);
        if (extraValues != null && extraValues.size() > 0) {
            for (final String extraValue : extraValues) {
                if (extraValue != null && !Constants.EMPTY_STRING.equals(extraValue)) {
                    returnFields.add(extraValue);
                }
            }
        }
        if (!returnFields.contains(Constants.ID)) {
            returnFields.add(Constants.ID);
        }
        if (objectType != null) {
            final String nameField = objectType.getNameField();
            if (nameField != null && !returnFields.contains(nameField)) {
                returnFields.add(nameField);
            }
        }
        final StringBuilder result = new StringBuilder();
        result.append(returnFields.get(0));
        for (int i = 1; i < returnFields.size(); i++) {
            final String resultField = returnFields.get(i);
            if (resultField != null && !Constants.EMPTY_STRING.equals(resultField)) {
                result.append(",");
                result.append(resultField);
            }
        }
        if (Constants.EMPTY_STRING.equals(result.toString())) {
            return null;
        }
        return result.toString();
    }

    /**
     * Loads smart scopes using a REST call.
     *
     * @param cachePolicy Cache policy.
     * @return List of object types.
     */
    private List<SalesforceObjectType> loadSmartScopes(CachePolicy cachePolicy) {
        RestResponse response = null;
        try {
        	response = restClient.sendSync(RestRequest.getRequestForSearchScopeAndOrder(apiVersion));
        } catch(IOException e) {
        	Log.e(TAG, "IOException occurred while sending request", e);
        }
        
        List<SalesforceObjectType> recentItems = new ArrayList<SalesforceObjectType>();
        if (response != null && response.isSuccess()) {
            try {
                final JSONArray responseJSON = response.asJSONArray();
                if (responseJSON != null) {
                    for (int i = 0; i < responseJSON.length(); i++) {
                        final JSONObject object = responseJSON.optJSONObject(i);
                        if (object != null) {
                            final String name = object.optString(Constants.TYPE.toLowerCase(Locale.US));
                            if (name != null && !Constants.EMPTY_STRING.equals(name)) {
                                final SalesforceObjectType sfObj = new SalesforceObjectType(name);
                                if (isObjectTypeSearchable(sfObj)) {
                                    recentItems.add(sfObj);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException occurred while reading data", e);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while parsing", e);
            }
        } else if (shouldFallBackOnCache(cachePolicy)) {
            recentItems = getCachedObjectTypes(cachePolicy, MRU_CACHE_TYPE,
            		SMART_SCOPES_CACHE_KEY);
        }
        if (shouldCacheData(cachePolicy) && recentItems != null
        		&& recentItems.size() > 0) {
            cacheObjectTypes(recentItems, MRU_CACHE_TYPE,
            		SMART_SCOPES_CACHE_KEY);
        }
        return recentItems;
    }

    /**
     * Returns recently viewed objects.
     *
     * @param objectTypeName Object type name.
     * @param globalMRU True - if global MRU, False - otherwise.
     * @param limit Limit on number of items.
     * @param cachePolicy Cache policy.
     * @param cacheKey Cache key.
     * @param networkFieldName Network field name for this object type.
     * @return List of recently viewed objects.
     */
    private List<SalesforceObject> loadRecentObjects(String objectTypeName,
            boolean globalMRU, int limit, CachePolicy cachePolicy, String cacheKey,
            String networkFieldName) {
        final List<SalesforceObject> recentItems = new ArrayList<SalesforceObject>();
        SOQLBuilder queryBuilder;
        if (globalMRU) {
            queryBuilder = SOQLBuilder.getInstanceWithFields("Id, Name, Type");
            queryBuilder.from(RECENTLY_VIEWED);
            String whereClause = "LastViewedDate != NULL";
            if (communityId != null) {
                whereClause = String.format("%s AND NetworkId = '%s'",
                		whereClause, communityId);
            }
            queryBuilder.where(whereClause);
            queryBuilder.limit(limit);
        } else {
            boolean objContainsLastViewedDate = false;
            final SalesforceObjectType objType = loadObjectType(objectTypeName,
                    CachePolicy.RELOAD_IF_EXPIRED_AND_RETURN_CACHE_DATA,
                    DEFAULT_METADATA_REFRESH_INTERVAL);
            if (objType != null) {
                final JSONArray fields = objType.getFields();
                if (fields != null) {
                    for (int i = 0; i < fields.length(); i++) {
                        final JSONObject obj = fields.optJSONObject(i);
                        if (obj != null) {
                            final String nameField = obj.optString(Constants.NAME.toLowerCase(Locale.US));
                            if (nameField != null && "LastViewedDate".equals(nameField)) {
                                objContainsLastViewedDate = true;
                            }
                        }
                    }
                }
            }
            final String retFields = getReturnFieldsForObjectType(objectTypeName);
            if (retFields != null && !Constants.EMPTY_STRING.equals(retFields)) {
                queryBuilder = SOQLBuilder.getInstanceWithFields(retFields);
            } else {
                queryBuilder = SOQLBuilder.getInstanceWithFields("Id, Name, Type");
            }
            String whereClause;
            if (objContainsLastViewedDate) {

            	/*
            	 * TODO: This should be replaced with 'using SCOPE MRU'
            	 * in 'v32.0'.
            	 */
                queryBuilder.from(String.format("%s using MRU", objectTypeName));
                whereClause = "LastViewedDate != NULL";
                queryBuilder.orderBy("LastViewedDate DESC");
                queryBuilder.limit(limit);
            } else {
                queryBuilder.from(RECENTLY_VIEWED);
                whereClause = String.format("LastViewedDate != NULL and Type = '%s'", objectTypeName);
                queryBuilder.limit(limit);
            }
            if (communityId != null) {
                if (networkFieldName != null) {
                    whereClause = String.format("%s AND %s = '%s'",
                    		whereClause, networkFieldName, communityId);
                }
            }
            queryBuilder.where(whereClause);
        }
        final String query = queryBuilder.build();
        RestResponse response = null;
        try {
        	response = restClient.sendSync(RestRequest.getRequestForQuery(apiVersion, query));
        } catch(IOException e) {
        	Log.e(TAG, "IOException occurred while sending request", e);
        }
        
        if (response != null && response.isSuccess()) {
            try {
                final JSONObject responseJSON = response.asJSONObject();
                if (responseJSON != null) {
                    final JSONArray records = responseJSON.optJSONArray("records");
                    if (records != null) {
                        for (int i = 0; i < records.length(); i++) {
                            final JSONObject rec = records.optJSONObject(i);
                            if (rec != null) {
                                final SalesforceObject sfObj = new SalesforceObject(rec);
                                if (globalMRU) {
                                    if (sfObj != null && sfObj.getObjectType() != null
                                            && sfObj.getObjectType().equals(Constants.CONTENT)) {
                                        sfObj.setObjectType(Constants.CONTENT_VERSION);
                                    }
                                } else {
                                    String sfObjName = null;
                                    if (sfObj != null) {
                                        sfObj.setObjectType(objectTypeName);
                                        sfObjName = sfObj.getName();
                                    }
                                    if (sfObjName == null || Constants.EMPTY_STRING.equals(sfObjName)
                                            || Constants.NULL_STRING.equals(sfObjName)) {
                                        final SalesforceObjectType objType = getCachedObjectType(objectTypeName);
                                        if (objType != null) {
                                            final String nameField = objType.getNameField();
                                            if (nameField != null && !Constants.EMPTY_STRING.equals(nameField)) {
                                                final String scopedName = rec.optString(nameField);
                                                if (sfObj != null && scopedName != null) {
                                                    sfObj.setName(scopedName);
                                                }
                                            }
                                        }
                                    }
                                }
                                recentItems.add(sfObj);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException occurred while reading data", e);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException occurred while parsing", e);
            }
            if (recentItems.size() > 0) {
                if (shouldCacheData(cachePolicy)) {
                    cacheObjects(recentItems, MRU_CACHE_TYPE, cacheKey);
                }
                return recentItems;
            }
        } else if (shouldFallBackOnCache(cachePolicy)) {
            return getCachedObjects(cachePolicy, MRU_CACHE_TYPE, cacheKey);
        }
        return null;
    }

    /**
     * Returns the return fields for the object type specified
     * in the object layout, if a layout exists for this object type.
     *
     * @param objTypeName Object type name.
     * @return Layout return fields for the object type.
     */
    private List<String> getLayoutReturnFieldsForObjectType(String objTypeName) {
        if (objTypeName == null || Constants.EMPTY_STRING.equals(objTypeName)) {
            return null;
        }
        List<String> results = null;
        SalesforceObjectType objType = getCachedObjectType(objTypeName);
        if (objType == null) {
            objType = loadObjectType(objTypeName,
            		CachePolicy.RELOAD_IF_EXPIRED_AND_RETURN_CACHE_DATA,
                    DEFAULT_METADATA_REFRESH_INTERVAL);
        }
        if (objType != null && canLoadLayoutForObjectType(objType)) {
            results = getLayoutFieldsForObjectType(objType);
        }
        return results;
    }

    /**
     * Returns whether a method should fall back on cached data or return the
     * empty data set from the server, in the event that a server error occurs
     * or we do not receive a response from the server, for reasons such as loss
     * of connectivity, for instance.
     *
     * @param cachePolicy Cache policy.
     * @return True - if we should fall back on cached data, False - otherwise.
     */
    private boolean shouldFallBackOnCache(CachePolicy cachePolicy) {
        return (cachePolicy == CachePolicy.RELOAD_AND_RETURN_CACHE_DATA
                || cachePolicy == CachePolicy.RELOAD_AND_RETURN_CACHE_ON_FAILURE
                || cachePolicy == CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD
                || cachePolicy == CachePolicy.RELOAD_IF_EXPIRED_AND_RETURN_CACHE_DATA);
    }
}
