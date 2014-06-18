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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.sobjectsdk.TestCredentials;
import com.salesforce.androidsdk.sobjectsdk.TestForceApp;
import com.salesforce.androidsdk.sobjectsdk.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.sobjectsdk.model.SalesforceObject;
import com.salesforce.androidsdk.sobjectsdk.model.SalesforceObjectType;
import com.salesforce.androidsdk.sobjectsdk.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.sobjectsdk.util.Constants;
import com.salesforce.androidsdk.sobjectsdk.util.JSONReader;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Test class for MetadataManager.
 *
 * @author bhariharan
 */
public class MetadataManagerTest extends InstrumentationTestCase {

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

    private Context targetContext;
    private EventsListenerQueue eq;
    private MetadataManager metadataManager;
    private HttpAccess httpAccess;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class,
        		targetContext);
        getInstrumentation().callApplicationOnCreate(app);
        TestCredentials.init(getInstrumentation().getContext());
        eq = new EventsListenerQueue();
        if (SalesforceSDKManager.getInstance() == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
    	MetadataManager.reset();
    	CacheManager.hardReset();
        metadataManager = MetadataManager.getInstance(initRestClient());
    }

    @Override
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
    	httpAccess.resetNetwork();
    	MetadataManager.reset();
    	CacheManager.hardReset();
        super.tearDown();
    }
    
    /**
     * Test for 'loadSmartScopeObjectTypes' (from the server).
     */
    public void testLoadSmartScopeTypesFromServer() {
    	final List<SalesforceObjectType> smartScopes = metadataManager.loadSmartScopeObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	/*
    	 * TODO: assert against static data.
    	 */
    }

    /**
     * Test for global 'loadMRUObjects' (from the server).
     */
    public void testLoadGlobalMRUObjectsFromServer() {
    	metadataManager.markObjectAsViewed(CASE_1_ID, Constants.CASE);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(null,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", CASE_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * This is to ensure that the next test run is actually
    	 * altering the MRU from what it is currently.
    	 */
    	metadataManager.markObjectAsViewed(CASE_2_ID, Constants.CASE);
    }

    /**
     * Test for account 'loadMRUObjects' (from the server).
     */
    public void testLoadAccountMRUObjectsFromServer() {
    	metadataManager.markObjectAsViewed(ACCOUNT_1_ID, Constants.ACCOUNT);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", ACCOUNT_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * This is to ensure that the next test run is actually
    	 * altering the MRU from what it is currently.
    	 */
    	metadataManager.markObjectAsViewed(ACCOUNT_2_ID, Constants.ACCOUNT);
    }

    /**
     * Test for opportunity 'loadMRUObjects' (from the server).
     */
    public void testLoadOpportunityMRUObjectsFromServer() {
    	metadataManager.markObjectAsViewed(OPPORTUNITY_1_ID, Constants.OPPORTUNITY);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", OPPORTUNITY_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * This is to ensure that the next test run is actually
    	 * altering the MRU from what it is currently.
    	 */
    	metadataManager.markObjectAsViewed(OPPORTUNITY_2_ID, Constants.OPPORTUNITY);
    }

    /**
     * Test for 'loadAllObjectTypes' (from the server).
     */
    public void testLoadAllObjectTypesFromServer() {
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	/*
    	 * TODO: assert against static data.
    	 */
    }

    /**
     * Test for account 'loadObjectType' (from the server).
     */
    public void testLoadAccountObjectTypeFromServer() {
    	final SalesforceObjectType account = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	/*
    	 * TODO: assert against static data.
    	 */
    }

    /**
     * Test for case 'loadObjectType' (from the server).
     */
    public void testLoadCaseObjectTypeFromServer() {
    	final SalesforceObjectType caseObj = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	/*
    	 * TODO: assert against static data.
    	 */
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
     * Test for 'loadSmartScopeObjectTypes' (from the cache).
     */
    public void testLoadSmartScopeTypesFromCache() {
    	final List<SalesforceObjectType> smartScopes = metadataManager.loadSmartScopeObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	/*
    	 * TODO: Turn off network and assert between live and cached data.
    	 */
    	final List<SalesforceObjectType> cachedSmartScopes = metadataManager.loadSmartScopeObjectTypes(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
    			REFRESH_INTERVAL);
    }

    /**
     * Test for global 'loadMRUObjects' (from the cache).
     */
    public void testLoadGlobalMRUObjectsFromCache() {
    	metadataManager.markObjectAsViewed(CASE_1_ID, Constants.CASE);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(null,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", CASE_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * We mark CASE_2 as most recently viewed. However, the cache results
    	 * should still return CASE_1 as most recently viewed.
    	 */
    	metadataManager.markObjectAsViewed(CASE_2_ID, Constants.CASE);
    	final List<SalesforceObject> cachedMruObjects = metadataManager.loadMRUObjects(null,
    			1, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", cachedMruObjects);
    	assertEquals("MRU list size should be 1", 1, cachedMruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", CASE_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for account 'loadMRUObjects' (from the cache).
     */
    public void testLoadAccountMRUObjectsFromCache() {
    	metadataManager.markObjectAsViewed(ACCOUNT_1_ID, Constants.ACCOUNT);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", ACCOUNT_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * We mark ACCOUNT_2 as most recently viewed. However, the cache results
    	 * should still return ACCOUNT_1 as most recently viewed.
    	 */
    	metadataManager.markObjectAsViewed(ACCOUNT_2_ID, Constants.ACCOUNT);
    	final List<SalesforceObject> cachedMruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
    			1, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", cachedMruObjects);
    	assertEquals("MRU list size should be 1", 1, cachedMruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", ACCOUNT_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for opportunity 'loadMRUObjects' (from the cache).
     */
    public void testLoadOpportunityMRUObjectsFromCache() {
    	metadataManager.markObjectAsViewed(OPPORTUNITY_1_ID, Constants.OPPORTUNITY);
    	final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
    			1, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", mruObjects);
    	assertEquals("MRU list size should be 1", 1, mruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", OPPORTUNITY_1_NAME,
    			mruObjects.get(0).getName());

    	/*
    	 * We mark OPPORTUNITY_2 as most recently viewed. However, the cache results
    	 * should still return OPPORTUNITY_1 as most recently viewed.
    	 */
    	metadataManager.markObjectAsViewed(OPPORTUNITY_2_ID, Constants.OPPORTUNITY);
    	final List<SalesforceObject> cachedMruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
    			1, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    	assertNotNull("MRU list should not be null", cachedMruObjects);
    	assertEquals("MRU list size should be 1", 1, cachedMruObjects.size());
    	assertEquals("Recently viewed object name is incorrect", OPPORTUNITY_1_NAME,
    			mruObjects.get(0).getName());
    }

    /**
     * Test for 'loadAllObjectTypes' (from the cache).
     */
    public void testLoadAllObjectTypesFromCache() {
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	/*
    	 * TODO: Turn off network and assert between live and cached data.
    	 */
    	final List<SalesforceObjectType> cachedObjectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
    			REFRESH_INTERVAL);
    }

    /**
     * Test for account 'loadObjectType' (from the cache).
     */
    public void testLoadAccountObjectTypeFromCache() {
    	final SalesforceObjectType account = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	/*
    	 * TODO: Turn off network and assert between live and cached data.
    	 */
    	final SalesforceObjectType cachedAccount = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
    }

    /**
     * Test for case 'loadObjectType' (from the cache).
     */
    public void testLoadCaseObjectTypeFromCache() {
    	final SalesforceObjectType caseObj = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	/*
    	 * TODO: Turn off network and assert between live and cached data.
    	 */
    	final SalesforceObjectType cachedCaseObj = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
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

    private RestClient initRestClient() throws Exception {
        httpAccess = new HttpAccess(null, null);
        final TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
        		new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID,
        		TestCredentials.REFRESH_TOKEN);
        final String authToken = refreshResponse.authToken;
        final ClientInfo clientInfo = new ClientInfo(TestCredentials.CLIENT_ID,
        		new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null);
        return new RestClient(clientInfo, authToken, httpAccess, null);
    }
}
