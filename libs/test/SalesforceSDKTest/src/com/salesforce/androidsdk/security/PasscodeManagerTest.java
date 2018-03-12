/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security;


import android.content.Context;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.salesforce.androidsdk.security.PasscodeManager.HashConfig;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for PasscodeManager
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PasscodeManagerTest {

    private static final HashConfig TEST_HASH_CONFIG = new HashConfig("", "", "dummy-key");
    private static final int TEST_TIMEOUT_MS = 1000;

    @Before
    public void setUp() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();	
    	}
        this.now = System.currentTimeMillis();
        this.pm = new TestPasscodeManager();
    }

    private PasscodeManager pm;
    private long now;
    private boolean startedLockActivity;

    // this lets us control the passage of time for tests.
    private class TestPasscodeManager extends PasscodeManager {

        TestPasscodeManager() {
            super(InstrumentationRegistry.getTargetContext(), TEST_HASH_CONFIG);
            setTimeoutMs(TEST_TIMEOUT_MS);
            setEnabled(true);
            // start in a known state.
            unlock();
        }

        @Override
        protected long now() {
            return now;
        }

        @Override
        public void showLockActivity(Context ctx, boolean changePasscodeFlow) {
            locked = true;
            startedLockActivity = true;
        }
    }

    /**
     * Check that app does get locked if no user interaction is reported to the PasscodeManager for a long enough time.
     */
    @Test
    public void testLocksAfterTimeout() {
        Assert.assertFalse(pm.isLocked());
        Assert.assertFalse(startedLockActivity);
        now += 500;
        Assert.assertFalse(pm.lockIfNeeded(null, true));
        Assert.assertFalse(pm.isLocked());
        Assert.assertFalse(startedLockActivity);
        now += 1001;
        Assert.assertTrue(pm.lockIfNeeded(null, true));
        Assert.assertTrue(pm.isLocked());
        Assert.assertTrue(startedLockActivity);
        pm.unlock();
        Assert.assertFalse(pm.isLocked());
    }

    /**
     * Check that app doesn't get locked if recordUserInteraction is called often enough.
     */
    @Test
    public void testActivityPreventsLock() {
        Assert.assertFalse(pm.isLocked());
        now += 500;
        pm.recordUserInteraction();
        now += 700;
        Assert.assertFalse(pm.lockIfNeeded(null, true));
        Assert.assertFalse(pm.isLocked());
    }

    /**
     * Check that app gets locked when expected after the lock timeout is changed.
     */
    @Test
    public void testTimeoutChange() {
        Assert.assertFalse(pm.isLocked());
        pm.setTimeoutMs(800);
        now += 700;
        Assert.assertFalse(pm.shouldLock());
        now += 101;
        Assert.assertTrue(pm.shouldLock());
    }

    /**
     * Check that failure count is reset on unlock.
     */
    @Test
    public void testUnlockResetsFailureCount() {
        Assert.assertEquals(0, pm.getFailedPasscodeAttempts());
        pm.addFailedPasscodeAttempt();
        Assert.assertEquals(1, pm.getFailedPasscodeAttempts());
        pm.unlock();
        Assert.assertEquals(0, pm.getFailedPasscodeAttempts());
    }
}
