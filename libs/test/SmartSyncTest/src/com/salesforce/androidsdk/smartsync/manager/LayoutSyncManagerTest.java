/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.smartsync.model.Layout;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link LayoutSyncManager}.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class LayoutSyncManagerTest extends ManagerTestCase {

    private static final String COMPACT = "Compact";
    private static final String ACCOUNT = "Account";

    private LayoutSyncManager layoutSyncManager;
    private LayoutSyncManager.LayoutSyncCallback layoutSyncCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        layoutSyncManager = LayoutSyncManager.getInstance();
        layoutSyncCallback = new LayoutSyncManager.LayoutSyncCallback() {

            @Override
            public void onSyncComplete(String objectType, Layout layout) {
                Assert.assertEquals("Object types should match", ACCOUNT, objectType);
                Assert.assertNotNull("Layout data should not be null", layout);
                Assert.assertEquals("Layout types should match", COMPACT, layout.getLayoutType());
                Assert.assertNotNull("Layout ID should not be null", layout.getId());
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        SyncManager.reset();
        layoutSyncManager.getSmartStore().dropAllSoups();
        LayoutSyncManager.reset();
        layoutSyncCallback = null;
        super.tearDown();
    }

    /**
     * Test for fetching layout in CACHE_ONLY mode.
     */
    @Test
    public void testFetchLayoutInCacheOnlyMode() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, LayoutSyncManager.Mode.CACHE_ONLY,
                layoutSyncCallback);
    }

    /**
     * Test for fetching layout in CACHE_FIRST mode with a hydrated cache.
     */
    @Test
    public void testFetchLayoutInCacheFirstModeWithCacheData() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, LayoutSyncManager.Mode.SERVER_FIRST,
                layoutSyncCallback);
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, LayoutSyncManager.Mode.CACHE_FIRST,
                layoutSyncCallback);
    }

    /**
     * Test for fetching layout in CACHE_FIRST mode with an empty cache.
     */
    @Test
    public void testFetchLayoutInCacheFirstModeWithoutCacheData() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, LayoutSyncManager.Mode.CACHE_FIRST,
                layoutSyncCallback);
    }

    /**
     * Test for fetching layout in SERVER_FIRST mode.
     */
    @Test
    public void testFetchLayoutInServerFirstMode() {
        layoutSyncManager.fetchLayout(ACCOUNT, COMPACT, LayoutSyncManager.Mode.SERVER_FIRST,
                layoutSyncCallback);
    }
}
