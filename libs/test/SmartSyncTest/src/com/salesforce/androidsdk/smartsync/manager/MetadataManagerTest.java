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
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.salesforce.androidsdk.smartsync.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.JSONReader;

/**
 * Test class for MetadataManager.
 *
 * @author bhariharan
 */
public class MetadataManagerTest extends ManagerTestCase {

	private static final long REFRESH_INTERVAL = 24 * 60 * 60 * 1000;
	private static final String ACCOUNT_1_ID = "001S000000fkJKm";
	private static final String ACCOUNT_1_NAME = "Alpha4";
	private static final String ACCOUNT_2_ID = "001S000000gyAaj";
	private static final String OPPORTUNITY_1_ID = "006S0000007182b";
	private static final String OPPORTUNITY_1_NAME = "Test";
	private static final String OPPORTUNITY_2_ID = "006S0000007182l";
	private static final String CASE_1_ID = "500S0000003s6Sf";
	private static final String CASE_1_NAME = "00001007";
	private static final String CASE_2_ID = "500S0000004O7fd";
	private static final String ACCOUNT_LAYOUT_FILE = "account_layout.json";
	private static final String CASE_LAYOUT_FILE = "case_layout.json";
	private static final String OPPORTUNITY_LAYOUT_FILE = "opportunity_layout.json";
	private static final String ALL_OBJECTS_FILE = "all_objects.json";
	private static final String ACCOUNT_METADATA_FILE = "account_metadata.json";
	private static final String CASE_METADATA_FILE = "case_metadata.json";

    /**
     * Test for global 'loadMRUObjects' (from the server).
     */
    public void testLoadGlobalMRUObjectsFromServer() {
    	metadataManager.markObjectAsViewed(CASE_1_ID, Constants.CASE, null);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(null,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", CASE_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for account 'loadMRUObjects' (from the server).
     */
    public void testLoadAccountMRUObjectsFromServer() {
    	metadataManager.markObjectAsViewed(ACCOUNT_1_ID, Constants.ACCOUNT, null);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", ACCOUNT_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for opportunity 'loadMRUObjects' (from the server).
     */
    public void testLoadOpportunityMRUObjectsFromServer() {
    	metadataManager.markObjectAsViewed(OPPORTUNITY_1_ID, Constants.OPPORTUNITY, null);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", OPPORTUNITY_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for 'loadAllObjectTypes' (from the server).
     */
    public void testLoadAllObjectTypesFromServer() {
    	fail(); // FIXME test stops the whole suite
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	final JSONObject rawJson = JSONReader.readJSONObject(targetContext, ALL_OBJECTS_FILE);
    	assertNotNull("Expected raw data should not be null", rawJson);
    	final List<SalesforceObjectType> expectedObjectTypes = parseObjectTypes(rawJson);
    	assertEquals("List of object types should match expected list",
    			expectedObjectTypes, objectTypes);
    }

    /**
     * Test for account 'loadObjectType' (from the server).
     */
    public void testLoadAccountObjectTypeFromServer() {
    	final SalesforceObjectType account = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final JSONObject rawJson = JSONReader.readJSONObject(targetContext, ACCOUNT_METADATA_FILE);
    	assertNotNull("Expected raw data should not be null", rawJson);
    	final SalesforceObjectType expectedAccount = new SalesforceObjectType(rawJson);
    	assertEquals("Account metadata should match expected metadata",
    			expectedAccount, account);
    }

    /**
     * Test for case 'loadObjectType' (from the server).
     */
    public void testLoadCaseObjectTypeFromServer() {
    	final SalesforceObjectType actualCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final JSONObject rawJson = JSONReader.readJSONObject(targetContext, CASE_METADATA_FILE);
    	assertNotNull("Expected raw data should not be null", rawJson);
    	final SalesforceObjectType expectedCase = new SalesforceObjectType(rawJson);
    	assertEquals("Case metadata should match expected metadata",
    			expectedCase, actualCase);
    }

    /**
     * Test for account, case, opportunity 'loadObjectTypesLayout' (from the server).
     */
    public void testLoadObjectTypeLayoutsFromServer() {
    	final List<String> objectTypeNames = new ArrayList<String>();
    	objectTypeNames.add(Constants.ACCOUNT);
    	objectTypeNames.add(Constants.CASE);
    	objectTypeNames.add(Constants.OPPORTUNITY);
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadObjectTypes(objectTypeNames,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final List<SalesforceObjectTypeLayout> objectLayouts = metadataManager.loadObjectTypesLayout(objectTypes,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("Layout list should not be null", objectLayouts);
    	assertEquals("Layout list size should be 3", 3, objectLayouts.size());
    	for (final SalesforceObjectTypeLayout layout : objectLayouts) {
    		final String objType = layout.getObjectType();
    		assertNotNull("Object type should not be null", objType);
    		JSONObject expectedRawData = null;
			SalesforceObjectTypeLayout expectedLayout = null;
    		if (Constants.ACCOUNT.equals(objType)) {
    			expectedRawData = JSONReader.readJSONObject(targetContext,
    					ACCOUNT_LAYOUT_FILE);
    		} else if (Constants.CASE.equals(objType)) {
    			expectedRawData = JSONReader.readJSONObject(targetContext,
    					CASE_LAYOUT_FILE);
    		} else if (Constants.OPPORTUNITY.equals(objType)) {
    			expectedRawData = JSONReader.readJSONObject(targetContext,
    					OPPORTUNITY_LAYOUT_FILE);
    		}
    		assertNotNull("Expected raw data should not be null", expectedRawData);
			expectedLayout = new SalesforceObjectTypeLayout(objType, expectedRawData);
    		assertEquals("Received layout should be equal to the expected layout",
    				expectedLayout, layout);
    	}
    }

    /**
     * Test for global 'loadMRUObjects' (from the cache).
     */
    public void testLoadGlobalMRUObjectsFromCache() {
    	metadataManager.markObjectAsViewed(CASE_1_ID, Constants.CASE, null);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(null,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", CASE_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * We mark CASE_2 as most recently viewed. However, the cache results
    	 * should still return CASE_1 as most recently viewed.
    	 */
    	metadataManager.markObjectAsViewed(CASE_2_ID, Constants.CASE, null);
    	final List<SalesforceObject> cachedMruObjects = metadataManager.loadMRUObjects(null,
    			1, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", cachedMruObjects);
    	assertEquals("MRU list size should be 1", 1, cachedMruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", CASE_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for account 'loadMRUObjects' (from the cache).
     */
    public void testLoadAccountMRUObjectsFromCache() {
    	metadataManager.markObjectAsViewed(ACCOUNT_1_ID, Constants.ACCOUNT, null);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", ACCOUNT_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * We mark ACCOUNT_2 as most recently viewed. However, the cache results
    	 * should still return ACCOUNT_1 as most recently viewed.
    	 */
    	metadataManager.markObjectAsViewed(ACCOUNT_2_ID, Constants.ACCOUNT, null);
    	final List<SalesforceObject> cachedMruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
    			1, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", cachedMruObjects);
    	assertEquals("MRU list size should be 1", 1, cachedMruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", ACCOUNT_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for opportunity 'loadMRUObjects' (from the cache).
     */
    public void testLoadOpportunityMRUObjectsFromCache() {
    	metadataManager.markObjectAsViewed(OPPORTUNITY_1_ID, Constants.OPPORTUNITY, null);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", OPPORTUNITY_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * We mark OPPORTUNITY_2 as most recently viewed. However, the cache results
    	 * should still return OPPORTUNITY_1 as most recently viewed.
    	 */
    	metadataManager.markObjectAsViewed(OPPORTUNITY_2_ID, Constants.OPPORTUNITY, null);
    	final List<SalesforceObject> cachedMruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
    			1, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL, null);
    	assertNotNull("MRU list should not be null", cachedMruObjects);
    	assertEquals("MRU list size should be 1", 1, cachedMruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", OPPORTUNITY_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for 'loadAllObjectTypes' (from the cache).
     */
    public void testLoadAllObjectTypesFromCache() {

    	fail(); // FIXME test stops the whole suite
    	metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
    			REFRESH_INTERVAL);
    	final JSONObject rawJson = JSONReader.readJSONObject(targetContext, ALL_OBJECTS_FILE);
    	assertNotNull("Expected raw data should not be null", rawJson);
    	final List<SalesforceObjectType> expectedObjectTypes = parseObjectTypes(rawJson);
    	assertEquals("List of object types should match expected list",
    			expectedObjectTypes, objectTypes);
    }

    /**
     * Test for account 'loadObjectType' (from the cache).
     */
    public void testLoadAccountObjectTypeFromCache() {
    	metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final SalesforceObjectType account = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	final JSONObject rawJson = JSONReader.readJSONObject(targetContext, ACCOUNT_METADATA_FILE);
    	assertNotNull("Expected raw data should not be null", rawJson);
    	final SalesforceObjectType expectedAccount = new SalesforceObjectType(rawJson);
    	assertEquals("Account metadata should match expected metadata",
    			expectedAccount, account);
    }

    /**
     * Test for case 'loadObjectType' (from the cache).
     */
    public void testLoadCaseObjectTypeFromCache() {
    	metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final SalesforceObjectType actualCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	final JSONObject rawJson = JSONReader.readJSONObject(targetContext, CASE_METADATA_FILE);
    	assertNotNull("Expected raw data should not be null", rawJson);
    	final SalesforceObjectType expectedCase = new SalesforceObjectType(rawJson);
    	assertEquals("Case metadata should match expected metadata",
    			expectedCase, actualCase);
    }

    /**
     * Test for account, case, opportunity 'loadObjectTypesLayout' (from the cache).
     */
    public void testLoadObjectTypeLayoutsFromCache() {
    	final List<String> objectTypeNames = new ArrayList<String>();
    	objectTypeNames.add(Constants.ACCOUNT);
    	objectTypeNames.add(Constants.CASE);
    	objectTypeNames.add(Constants.OPPORTUNITY);
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadObjectTypes(objectTypeNames,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	metadataManager.loadObjectTypesLayout(objectTypes,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final List<SalesforceObjectTypeLayout> objectLayouts = metadataManager.loadObjectTypesLayout(objectTypes,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	assertNotNull("Layout list should not be null", objectLayouts);
    	assertEquals("Layout list size should be 3", 3, objectLayouts.size());
    	for (final SalesforceObjectTypeLayout layout : objectLayouts) {
    		final String objType = layout.getObjectType();
    		assertNotNull("Object type should not be null", objType);
    		JSONObject expectedRawData = null;
			SalesforceObjectTypeLayout expectedLayout = null;
    		if (Constants.ACCOUNT.equals(objType)) {
    			expectedRawData = JSONReader.readJSONObject(targetContext,
    					ACCOUNT_LAYOUT_FILE);
    		} else if (Constants.CASE.equals(objType)) {
    			expectedRawData = JSONReader.readJSONObject(targetContext,
    					CASE_LAYOUT_FILE);
    		} else if (Constants.OPPORTUNITY.equals(objType)) {
    			expectedRawData = JSONReader.readJSONObject(targetContext,
    					OPPORTUNITY_LAYOUT_FILE);
    		}
    		assertNotNull("Expected raw data should not be null", expectedRawData);
			expectedLayout = new SalesforceObjectTypeLayout(objType, expectedRawData);
    		assertEquals("Received layout should be equal to the expected layout",
    				expectedLayout, layout);
    	}
    }

    /**
     * Parses and returns object types from raw data.
     *
     * @param rawData Raw JSON data.
     * @return List of object types.
     */
    private List<SalesforceObjectType> parseObjectTypes(JSONObject rawData) {
        final List<SalesforceObjectType> returnList = new ArrayList<SalesforceObjectType>();
        final JSONArray objectTypes = rawData.optJSONArray("sobjects");
        if (objectTypes != null) {
            for (int i = 0; i < objectTypes.length(); i++) {
                final JSONObject metadata = objectTypes.optJSONObject(i);
                if (metadata != null) {
                    final boolean hidden = metadata.optBoolean(Constants.HIDDEN_FIELD, false);
                    if (!hidden) {
                    	final SalesforceObjectType objType = new SalesforceObjectType(metadata);
                        returnList.add(objType);
                    }
                }
            }
        }
        if (returnList.size() == 0) {
            return null;
        }
        return returnList;
    }
}
