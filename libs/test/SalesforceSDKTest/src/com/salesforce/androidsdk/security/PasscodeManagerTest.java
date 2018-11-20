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
import android.content.SharedPreferences;
import android.os.Looper;

import com.salesforce.androidsdk.security.PasscodeManager.HashConfig;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Tests for PasscodeManager
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PasscodeManagerTest {

    private static final HashConfig TEST_HASH_CONFIG = new HashConfig("", "", "dummy-key");
    private static final int TEST_TIMEOUT_MS = 1000;
    private Context ctx;

    @Before
    public void setUp() throws Exception {
        this.ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        if (Looper.myLooper() == null) {
            Looper.prepare();	
    	}
        this.now = System.currentTimeMillis();
        this.pm = new TestPasscodeManager();
    }

    @After
    public void tearDown() throws Exception {
        this.pm.reset(ctx);
    }

    private PasscodeManager pm;
    private long now;
    private boolean startedLockActivity;

    // this lets us control the passage of time for tests.
    private class TestPasscodeManager extends PasscodeManager {

        TestPasscodeManager() {
            super(ctx, TEST_HASH_CONFIG);
            setTimeoutMs(TEST_TIMEOUT_MS);
            setEnabled(true);
            // start in a known state.
            unlock();
        }

        @Override
        protected long now() {
            return now;
        }

        public void lock(Context ctx) {
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

    /**
     * Check that increasing passcode length makes passcode change required if there is a passcode stored.
     */
    @Test
    public void testIncreasePasscodeLengthMakesPasscodeChangeRequired() {
        Assert.assertFalse(pm.isPasscodeChangeRequired());

        // Increase passcode length without a passcode stored
        pm.setMinPasscodeLength(ctx, 5);
        Assert.assertFalse(pm.isPasscodeChangeRequired());

        // Increase passcode length with a passcode stored
        pm.store(ctx, "12345");
        pm.setMinPasscodeLength(ctx, 6);
        Assert.assertTrue(pm.isPasscodeChangeRequired());
    }

    /**
     * Check that passcode change is no longer required after a new passcode is stored
     */
    @Test
    public void testStorePasscodeResetPasscodeChangeRequired() {
        // Increase passcode length with a passcode stored
        Assert.assertFalse(pm.isPasscodeChangeRequired());
        pm.store(ctx, "1234");
        pm.setMinPasscodeLength(ctx, 5);
        Assert.assertTrue(pm.isPasscodeChangeRequired());


        // Store a new passcode
        pm.store(ctx, "123456");

        // Make sure passcodeChangeRequired is back to false
        Assert.assertFalse(pm.isPasscodeChangeRequired());
    }

    /**
     * Make sure passcode is stored hashed in prefs
     */
    @Test
    public void testPasscodePrefAfterStore() {
        checkPasscodePrefs(null);
        pm.store(ctx, "1234");
        checkPasscodePrefs("1234");
    }

    /**
     * Make sure mobile prefs are stored in prefs and updated when min length is changed
     */
    @Test
    public void testMobilePrefsWhenLengthChanged() {
        // Initial values
        checkMobilePrefs(TEST_TIMEOUT_MS, PasscodeManager.MIN_PASSCODE_LENGTH, false);
        // Decreasing length
        pm.setMinPasscodeLength(ctx, 3);
        checkMobilePrefs(TEST_TIMEOUT_MS, 3, false);
        // Increasing length
        pm.setMinPasscodeLength(ctx, 5);
        checkMobilePrefs(TEST_TIMEOUT_MS, 5, false);
    }

    /**
     * Make sure mobile prefs are stored in prefs and updated when time out is changed
     */
    @Test
    public void testMobilePrefsWhenTimeoutChanged() {
        // Initial values
        checkMobilePrefs(TEST_TIMEOUT_MS, PasscodeManager.MIN_PASSCODE_LENGTH, false);
        // Increasing timeout -> change should not be applied
        pm.setTimeoutMs(TEST_TIMEOUT_MS*2);
        checkMobilePrefs(TEST_TIMEOUT_MS, 4, false);
        // Decreasing timeout -> change should be applied
        pm.setTimeoutMs(TEST_TIMEOUT_MS/2);
        checkMobilePrefs(TEST_TIMEOUT_MS/2, 4, false);
        // Changing timeout to 0 => does a reset
        pm.setTimeoutMs(0);
        checkMobilePrefs(0, 4, false);
    }


    /**
     * Make sure mobile prefs are stored in prefs and updated when passcode change required / stored
     */
    @Test
    public void testMobilePrefsWhenPasscodeChangeRequiredOrStored() {
        // Initial values
        checkMobilePrefs(TEST_TIMEOUT_MS, PasscodeManager.MIN_PASSCODE_LENGTH, false);
        // Setting passcode
        pm.store(ctx, "1234");
        // Increasing length
        pm.setMinPasscodeLength(ctx, 5);
        checkMobilePrefs(TEST_TIMEOUT_MS, 5, true);
        // Changing passcode
        pm.store(ctx, "12345");
        checkMobilePrefs(TEST_TIMEOUT_MS, 5, false);
    }


    private void checkMobilePrefs(int timeoutMs, int minPasscodeLength, boolean passcodeChangeRequired) {
        final SharedPreferences sp = ctx.getSharedPreferences(PasscodeManager.MOBILE_POLICY_PREF,
                Context.MODE_PRIVATE);
        Assert.assertEquals(timeoutMs, sp.getInt(PasscodeManager.KEY_TIMEOUT, 0));
        Assert.assertEquals(minPasscodeLength, sp.getInt(PasscodeManager.KEY_PASSCODE_LENGTH, PasscodeManager.MIN_PASSCODE_LENGTH));
        Assert.assertEquals(passcodeChangeRequired, sp.getBoolean(PasscodeManager.KEY_PASSCODE_CHANGE_REQUIRED, false));

    }

    private void checkPasscodePrefs(String passcode) {
        final SharedPreferences sp = ctx.getSharedPreferences(PasscodeManager.PASSCODE_PREF_NAME, Context.MODE_PRIVATE);
            Assert.assertEquals(passcode != null, sp.contains(PasscodeManager.KEY_PASSCODE));
        if (passcode != null) {
            Assert.assertEquals(pm.hashForVerification(passcode), sp.getString(PasscodeManager.KEY_PASSCODE, ""));
        }
    }
}
