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

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.salesforce.androidsdk.smartsync.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test class for CacheManager.
 *
 * @author bhariharan
 * @deprecated Will be removed in Mobile SDK 7.0.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
@Deprecated
public class CacheManagerTest extends ManagerTestCase {

	private static final long REFRESH_INTERVAL = 24 * 60 * 60 * 1000;
    private static final String MRU_CACHE_TYPE = "recent_objects";
    private static final String METADATA_CACHE_TYPE = "metadata";
    private static final String LAYOUT_CACHE_TYPE = "layout";
    private static final String MRU_BY_OBJECT_TYPE_CACHE_KEY = "mru_for_%s";
    private static final String ALL_OBJECTS_CACHE_KEY = "all_objects";
    private static final String OBJECT_LAYOUT_BY_TYPE_CACHE_KEY = "object_layout_%s";
    private static final String RECORD_TYPE_GLOBAL = "global";

	@Before
	public void setUp() throws Exception {
		super.setUp();
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
	}

    /**
     * Test for 'removeCache' (ensures that a specific cache type is
     * wiped - both in memory and smart store).
     */
    @Test
    public void testRemoveMRUCache() {
    	metadataManager.loadMRUObjects(null, 25,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	List<SalesforceObject> objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNotNull("List of objects should not be null", objects);
        Assert.assertTrue("List of objects should have 1 or more objects",
    			objects.size() > 0);
    	cacheManager.removeCache(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
    	objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNull("List of objects should be null", objects);
    }

    /**
     * Test for 'softReset' (ensures only in memory cache is wiped,
     * and that smart store hasn't been wiped).
     */
    @Test
    public void testSoftReset() {
    	metadataManager.loadMRUObjects(null, 25,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	List<SalesforceObject> objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNotNull("List of objects should not be null", objects);
        Assert.assertTrue("List of objects should have 1 or more objects",
    			objects.size() > 0);
    	CacheManager.softReset(null);
    	objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNotNull("List of objects should not be null", objects);
        Assert.assertTrue("List of objects should have 1 or more objects",
    			objects.size() > 0);
    }

    /**
     * Test for 'hardReset' (ensures both in memory cache and smart store are wiped).
     */
    @Test
    public void testHardReset() {
    	metadataManager.loadMRUObjects(null, 25,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	List<SalesforceObject> objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNotNull("List of objects should not be null", objects);
        Assert.assertTrue("List of objects should have 1 or more objects",
    			objects.size() > 0);
    	CacheManager.hardReset(null);
    	objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNull("List of objects should be null", objects);
    }

    /**
     * Test for 'readObjectTypes' (ensures that object types written to
     * the cache can be read back).
     */
    @Test
    public void testReadObjectTypes() {
    	List<SalesforceObjectType> objects = metadataManager.loadAllObjectTypes(CachePolicy.RELOAD_AND_RETURN_CACHE_DATA,
    			REFRESH_INTERVAL);
        Assert.assertNotNull("List of object types should not be null", objects);
        Assert.assertTrue("List of object types should have 1 or more objects",
    			objects.size() > 0);
    	objects = cacheManager.readObjectTypes(METADATA_CACHE_TYPE, ALL_OBJECTS_CACHE_KEY);
        Assert.assertNotNull("List of object types should not be null", objects);
        Assert.assertTrue("List of object types should have 1 or more objects",
    			objects.size() > 0);
    }

    /**
     * Test for 'readObjects' (ensures that objects written to
     * the cache can be read back).
     */
    @Test
    public void testReadObjects() {
    	metadataManager.loadMRUObjects(null, 25,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL, null);
    	final List<SalesforceObject> objects = cacheManager.readObjects(MRU_CACHE_TYPE,
    			String.format(MRU_BY_OBJECT_TYPE_CACHE_KEY, RECORD_TYPE_GLOBAL));
        Assert.assertNotNull("List of objects should not be null", objects);
        Assert.assertTrue("List of objects should have 1 or more objects",
    			objects.size() > 0);
    }

    /**
     * Test for 'readObjectLayouts' (ensures that object layouts written to
     * the cache can be read back).
     */
    @Test
    public void testReadObjectLayouts() {
    	final SalesforceObjectType account = metadataManager.loadObjectType("Account",
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
        Assert.assertNotNull("Account metadata should not be null", account);
    	metadataManager.loadObjectTypeLayout(account,
    			CachePolicy.RELOAD_AND_RETURN_CACHE_DATA, REFRESH_INTERVAL);
    	final List<SalesforceObjectTypeLayout> objects = cacheManager.readObjectLayouts(LAYOUT_CACHE_TYPE,
    			String.format(OBJECT_LAYOUT_BY_TYPE_CACHE_KEY, "Account"));
        Assert.assertNotNull("List of object layouts should not be null", objects);
        Assert.assertEquals("List of object layouts should have 1 layout", 1,
    			objects.size());
        Assert.assertNotNull("Account layout should not be null", objects.get(0));
    }
}
