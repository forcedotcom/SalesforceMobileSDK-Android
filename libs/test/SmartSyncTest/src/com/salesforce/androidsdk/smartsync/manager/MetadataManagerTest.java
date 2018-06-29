/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.salesforce.androidsdk.smartsync.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.smartsync.util.Constants;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test class for MetadataManager.
 *
 * @author bhariharan
 * @deprecated Will be removed in Mobile SDK 7.0.
 */
@Deprecated
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MetadataManagerTest extends ManagerTestCase {

	private static final long REFRESH_INTERVAL = 24 * 60 * 60 * 1000;
    private static final int MAX_QUERY_LIMIT = 200;
    private static final int INDEX_WAITINGTIME = 2000;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test for global 'loadMRUObjects' (from the server).
     */
    @Test
    public void testLoadGlobalMRUObjectsFromServer() throws Exception {

        // Retrieves the MRU records before test.
        final List<SalesforceObject> mruObjectsBefore = metadataManager.loadMRUObjects(null,
                MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
        int numberOfRecords = 1;
        Map<String, String> idToSubjects = createRecordsOnServer(numberOfRecords, Constants.CASE);
        Assert.assertTrue("Not created the tested record successfully", idToSubjects.size() == numberOfRecords);
        Set<String> ids = idToSubjects.keySet();
        try {

		    /*
		     * MRU could take a few milliseconds to index on the server. This ensures
		     * that we don't query too soon and cause the test to flap.
		     */
			Thread.sleep(INDEX_WAITINGTIME);
			final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(null,
                    MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
            Assert.assertNotNull("MRU list should not be null", mruObjects);
            Assert.assertEquals("MRU list size should not be changed",
                    mruObjectsBefore.size() + 1, mruObjects.size());
		} finally {
			deleteRecordsOnServer(ids, Constants.CASE);
		}
	}

    /**
     * Test for account 'loadMRUObjects' (from the server).
     */
    @Test
    public void testLoadAccountMRUObjectsFromServer() throws Exception {
        //retrieve the MRU records before test
        final List<SalesforceObject> mruObjects_before = metadataManager.loadMRUObjects(Constants.ACCOUNT,
                MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
        int mruObjectsNumberBefore = 0;
        if (mruObjects_before != null) {
            mruObjectsNumberBefore = mruObjects_before.size();
        }
        int numberOfRecords = 1;
        Map<String, String> idToNames = createRecordsOnServer(numberOfRecords, Constants.ACCOUNT);
        Assert.assertTrue("Not created the tested record successfully", idToNames.size() == numberOfRecords);
        Collection<String> names = idToNames.values();
        Set<String> ids = idToNames.keySet();
        try {
		/*
		 * MRU could take a few milliseconds to index on the server. This ensures
		 * that we don't query too soon and cause the test to flap.
		 */
            Thread.sleep(INDEX_WAITINGTIME);
            final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
                    MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
            Assert.assertNotNull("MRU list should not be null", mruObjects);
            Assert.assertEquals("MRU list size should not be changed", mruObjectsNumberBefore+numberOfRecords, mruObjects.size());
            Assert.assertEquals("Recently viewed object name is incorrect", names.toArray()[0],
                    mruObjects.get(0).getName());
        } finally {
            deleteRecordsOnServer(ids, Constants.ACCOUNT);
        }
    }

    /**
     * Test for opportunity 'loadMRUObjects' (from the server).
     */
    @Test
    public void testLoadOpportunityMRUObjectsFromServer() throws Exception {
        //retrieve the MRU records before test
        final List<SalesforceObject> mruObjects_before = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
                MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
        int mruObjectsNumberBefore = 0;
        if (mruObjects_before != null) {
            mruObjectsNumberBefore = mruObjects_before.size();
        }
        int numberOfRecords = 1;
        Map<String, String> idToNames = createRecordsOnServer(numberOfRecords, Constants.OPPORTUNITY);
        Assert.assertTrue("Not created the tested record successfully", idToNames.size() == numberOfRecords);
        Collection<String> names = idToNames.values();
        Set<String> ids = idToNames.keySet();
        try {
		/*
		 * MRU could take a few milliseconds to index on the server. This ensures
		 * that we don't query too soon and cause the test to flap.
		 */
            Thread.sleep(INDEX_WAITINGTIME);
            final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
                    MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
            Assert.assertNotNull("MRU list should not be null", mruObjects);
            Assert.assertEquals("MRU list size should not be changed", mruObjectsNumberBefore+numberOfRecords, mruObjects.size());
            Assert.assertEquals("Recently viewed object name is incorrect", names.toArray()[0],
                    mruObjects.get(0).getName());
        } finally {
            deleteRecordsOnServer(ids, Constants.OPPORTUNITY);
        }
    }

    /**
     * Test for 'loadAllObjectTypes' (from the server).
     */
    @Test
    public void testLoadAllObjectTypesFromServer() {
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
        Assert.assertTrue("objectTypes list should contain objects", objectTypes != null && objectTypes.size() > 0);
    }

    /**
     * Test for account 'loadObjectType' (from the server).
     */
    @Test
    public void testLoadAccountObjectTypeFromServer() {
    	final SalesforceObjectType account = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
        Assert.assertNotNull("account object should not be null.", account);
        Assert.assertEquals(String.format("account object type name should be %s", Constants.ACCOUNT), account.getName(), Constants.ACCOUNT);
    }

    /**
     * Test for case 'loadObjectType' (from the server).
     */
    @Test
    public void testLoadCaseObjectTypeFromServer() {
    	final SalesforceObjectType actualCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
        Assert.assertNotNull("case object should not be null.", actualCase);
        Assert.assertEquals(String.format("case object type name should be %s", Constants.CASE), actualCase.getName(), Constants.CASE);
    }

    /**
     * Test for account, case, opportunity 'loadObjectTypesLayout' (from the server).
     */
    @Test
    public void testLoadObjectTypeLayoutsFromServer() {
    	final List<String> objectTypeNames = new ArrayList<String>();
    	objectTypeNames.add(Constants.ACCOUNT);
    	objectTypeNames.add(Constants.CASE);
    	objectTypeNames.add(Constants.OPPORTUNITY);
    	final List<SalesforceObjectType> objectTypes = metadataManager.loadObjectTypes(objectTypeNames,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final List<SalesforceObjectTypeLayout> objectLayouts = metadataManager.loadObjectTypesLayout(objectTypes,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
        Assert.assertNotNull("Layout list should not be null", objectLayouts);
        Assert.assertEquals("Wrong Layout list size loaded from server", objectTypeNames.size(), objectLayouts.size());
    }

    /**
     * Test for global 'loadMRUObjects' (from the cache).
     */
    @Test
    public void testLoadGlobalMRUObjectsFromCache() throws Exception {
        //retrieve the MRU records before test
        final List<SalesforceObject> mruObjects_before = metadataManager.loadMRUObjects(null,
                MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
        int numberOfRecords = 1;
        Map<String, String> idToSubjects = createRecordsOnServer(numberOfRecords, Constants.CASE);
        Assert.assertTrue("Not created the tested record successfully", idToSubjects.size() == numberOfRecords);
        Set<String> ids = idToSubjects.keySet();
        try {

		/*
		 * MRU could take a few milliseconds to index on the server. This ensures
		 * that we don't query too soon and cause the test to flap.
		 */
            Thread.sleep(INDEX_WAITINGTIME);
            final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(null,
                    MAX_QUERY_LIMIT, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL, null);
            Assert.assertNotNull("MRU list should not be null", mruObjects);
            Assert.assertEquals("MRU list size should not be changed", mruObjects_before.size(), mruObjects.size());
        } finally {
            deleteRecordsOnServer(ids, Constants.CASE);
        }
    }

    /**
     * Test for account 'loadMRUObjects' (from the cache).
     */
    @Test
    public void testLoadAccountMRUObjectsFromCache() throws Exception {
        //retrieve the MRU records before test
        final List<SalesforceObject> mruObjects_before = metadataManager.loadMRUObjects(Constants.ACCOUNT,
                MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
        int numberOfRecords = 1;
        Map<String, String> idToNames = createRecordsOnServer(numberOfRecords, Constants.ACCOUNT);
        Assert.assertTrue("Not created the tested record successfully", idToNames.size() == numberOfRecords);
        Set<String> ids = idToNames.keySet();
        try {
		/*
		 * MRU could take a few milliseconds to index on the server. This ensures
		 * that we don't query too soon and cause the test to flap.
		 */
            Thread.sleep(INDEX_WAITINGTIME);
            final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.ACCOUNT,
                    MAX_QUERY_LIMIT, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL, null);
            //for the case that there is some MRU Account record before test
            if (mruObjects_before != null) {
                Assert.assertNotNull("MRU list should not be null", mruObjects);
                Assert.assertEquals("MRU list size should not be changed", mruObjects_before.size(), mruObjects.size());
                Assert.assertEquals("Recently viewed object name is incorrect", mruObjects_before.get(0).getName(),
                        mruObjects.get(0).getName());
            }
            else {
                Assert.assertNull("MRU list should be null", mruObjects);
            }
        } finally {
            deleteRecordsOnServer(ids, Constants.ACCOUNT);
        }
    }

    /**
     * Test for opportunity 'loadMRUObjects' (from the cache).
     */
    @Test
    public void testLoadOpportunityMRUObjectsFromCache() throws Exception {
        //retrieve the MRU records before test
        final List<SalesforceObject> mruObjects_before = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
                MAX_QUERY_LIMIT, CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);

        int numberOfRecords = 1;
        Map<String, String> idToNames = createRecordsOnServer(numberOfRecords, Constants.OPPORTUNITY);
        Assert.assertTrue("Not created the tested record successfully", idToNames.size() == numberOfRecords);
        Set<String> ids = idToNames.keySet();
        try {
		/*
		 * MRU could take a few milliseconds to index on the server. This ensures
		 * that we don't query too soon and cause the test to flap.
		 */
            Thread.sleep(INDEX_WAITINGTIME);
            final List<SalesforceObject> mruObjects = metadataManager.loadMRUObjects(Constants.OPPORTUNITY,
                    MAX_QUERY_LIMIT, CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL, null);
            //for the case that there is some MRU Account record before test
            if (mruObjects_before != null) {
                Assert.assertNotNull("MRU list should not be null", mruObjects);
                Assert.assertEquals("MRU list size should not be changed", mruObjects_before.size(), mruObjects.size());
                Assert.assertEquals("Recently viewed object name is incorrect", mruObjects_before.get(0).getName(),
                        mruObjects.get(0).getName());
            } else {
                Assert.assertNull("MRU list should be null", mruObjects);
            }
        } finally {
            deleteRecordsOnServer(ids, Constants.OPPORTUNITY);
        }
    }

    /**
     * Test for 'loadAllObjectTypes' (from the cache).
     */
    @Test
    public void testLoadAllObjectTypesFromCache() {
    	final List<SalesforceObjectType> serverObjectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
    	final List<SalesforceObjectType> cachedObjectTypes = metadataManager.loadAllObjectTypes(CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD,
    			REFRESH_INTERVAL);
        Assert.assertNotNull("serverObjectTypes list should not be null.", serverObjectTypes);
        Assert.assertNotNull("cachedObjectTypes list should not be null.", cachedObjectTypes);
        Assert.assertTrue("serverObjectTypes should contain server objects", serverObjectTypes.size() > 0);
        Assert.assertTrue("cachedObjectTypes should contain cached objects", cachedObjectTypes.size() > 0);
        Assert.assertEquals("Number of cachedObjectTypes should be the same as serverObjectTypes.", cachedObjectTypes.size(), serverObjectTypes.size());
    }

    /**
     * Test for account 'loadObjectType' (from the cache).
     */
    @Test
    public void testLoadAccountObjectTypeFromCache() {
    	final SalesforceObjectType serverAccount = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final SalesforceObjectType cachedAccount = metadataManager.loadObjectType(Constants.ACCOUNT,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
        Assert.assertNotNull("serverAccount should not be null.", serverAccount);
        Assert.assertNotNull("cachedAccount should not be null.", cachedAccount);
        Assert.assertEquals("serverAccount and cachedAccount should be equal.", serverAccount, cachedAccount);
    }

    /**
     * Test for case 'loadObjectType' (from the cache).
     */
    @Test
    public void testLoadCaseObjectTypeFromCache() {
    	final SalesforceObjectType serverCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final SalesforceObjectType cachedCase = metadataManager.loadObjectType(Constants.CASE,
    			CachePolicy.RETURN_CACHE_DATA_DONT_RELOAD, REFRESH_INTERVAL);
        Assert.assertNotNull("serverCase should not be null.", serverCase);
        Assert.assertNotNull("cachedCase should not be null.", cachedCase);
        Assert.assertEquals("serverCase and cachedCase should be equal.", serverCase, cachedCase);
    }

    /**
     * Test for account, case, opportunity 'loadObjectTypesLayout' (from the cache).
     */
    @Test
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
        Assert.assertNotNull("Layout list should not be null", objectLayouts);
        Assert.assertEquals("Wrong Layout list size loaded from cache", objectTypeNames.size(), objectLayouts.size());
    }
}
