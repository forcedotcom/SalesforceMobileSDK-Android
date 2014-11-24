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

import com.salesforce.androidsdk.smartsync.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.smartsync.util.Constants;

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
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	assertTrue("objectTypes list should contain objects", objectTypes != null && objectTypes.size() > 0);
    }

    /**
     * Test for account 'loadObjectType' (from the server).
     */
    public void testLoadAccountObjectTypeFromServer() {
    	final SalesforceObjectType account = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("account object should not be null.", account);
    	assertEquals(String.format("account object type name should be %s", Constants.ACCOUNT), account.getName(), Constants.ACCOUNT);
    }

    /**
     * Test for case 'loadObjectType' (from the server).
     */
    public void testLoadCaseObjectTypeFromServer() {
    	final SalesforceObjectType actualCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("case object should not be null.", actualCase);
    	assertEquals(String.format("case object type name should be %s", Constants.CASE), actualCase.getName(), Constants.CASE);
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

    	final List<SalesforceObjectType> serverObjectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	final List<SalesforceObjectType> cachedObjectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
    			REFRESH_INTERVAL);
    	assertNotNull("serverObjectTypes list should not be null.", serverObjectTypes);
    	assertNotNull("cachedObjectTypes list should not be null.", cachedObjectTypes);
    	assertTrue("serverObjectTypes should contain server objects", serverObjectTypes.size() > 0);
    	assertTrue("cachedObjectTypes should contain cached objects", cachedObjectTypes.size() > 0);
    	assertEquals("Number of cachedObjectTypes should be the same as serverObjectTypes.", cachedObjectTypes.size(), serverObjectTypes.size());
    }

    /**
     * Test for account 'loadObjectType' (from the cache).
     */
    public void testLoadAccountObjectTypeFromCache() {
    	final SalesforceObjectType serverAccount = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final SalesforceObjectType cachedAccount = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	assertNotNull("serverAccount should not be null.", serverAccount);
    	assertNotNull("cachedAccount should not be null.", cachedAccount);
    	assertEquals("serverAccount and cachedAccount should be equal.", serverAccount, cachedAccount);
    }

    /**
     * Test for case 'loadObjectType' (from the cache).
     */
    public void testLoadCaseObjectTypeFromCache() {
    	final SalesforceObjectType serverCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final SalesforceObjectType cachedCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	assertNotNull("serverCase should not be null.", serverCase);
    	assertNotNull("cachedCase should not be null.", cachedCase);
    	assertEquals("serverCase and cachedCase should be equal.", serverCase, cachedCase);
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
    }
}
