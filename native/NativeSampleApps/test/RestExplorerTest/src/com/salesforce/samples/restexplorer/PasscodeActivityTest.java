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
package com.salesforce.samples.restexplorer;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.FingerprintAuthDialogFragment;
import com.salesforce.androidsdk.ui.PasscodeActivity;
import com.salesforce.androidsdk.ui.PasscodeActivity.PasscodeMode;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * Tests for PasscodeActivity.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PasscodeActivityTest {

    private Context targetContext;
    private PasscodeActivity passcodeActivity;
    private PasscodeManager passcodeManager;
    private FingerprintAuthDialogFragment fingerprintDialog;

    /**
     * Custom activity launch rules to run steps before the activity is launched.
     *
     * @param <T> Activity.
     */
    public class PasscodeActivityRule<T extends PasscodeActivity> extends ActivityTestRule<T> {

        public PasscodeActivityRule(Class<T> activityClass) {
            super(activityClass);
        }

        @Override
        protected void beforeActivityLaunched() {
            targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        }
    }

    @Rule
    public PasscodeActivityRule<PasscodeActivity> passcodeActivityTestRule = new PasscodeActivityRule<>(PasscodeActivity.class);

    @Before
    public void setUp() {
        passcodeManager.reset(targetContext);
        passcodeManager.setTimeoutMs(600000);
        Assert.assertTrue("Application should be locked", passcodeManager.isLocked());
        Assert.assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
    }

    @After
    public void tearDown() {
        passcodeManager.reset(targetContext);
        passcodeManager.setTimeoutMs(600000);
    }

    /**
     * Test passcode creation flow when no mistakes are made by user.
     */
    @Test
    public void testCreateWithNoMistakes() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());
        checkUi();

        // Entering in 123456
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        checkUi();

        // Re-entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode change flow when no mistakes are made by user.
     */
    @Test
    public void testChangeWithNoMistakes() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Make passcode change required
        Assert.assertFalse(passcodeManager.isPasscodeChangeRequired());
        passcodeManager.setPasscodeChangeRequired(SalesforceSDKManager.getInstance().getAppContext(), true);
        Assert.assertTrue(passcodeManager.isPasscodeChangeRequired());

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in change mode", PasscodeMode.Change, passcodeActivity.getMode());
        checkUi();

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        checkUi();

        // Re-entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

        // Make sure passcode change is no longer required
        Assert.assertFalse(passcodeManager.isPasscodeChangeRequired());
    }

    /**
     * Test passcode creation flow when user try to enter a passcode matching the minimum value.
     */
    @Test
    public void testCreatewithPasscodeMinimumLength() {

        // Set passcode length and Get activity
        passcodeManager.setPasscodeLength(targetContext, 4);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "1234");
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
    }

    /**
     * Test passcode creation flow when user try to enter a passcode matching the minimum value.
     */
    @Test
    public void testCreatewithPasscodeMaximumLength() {

        // Set passcode length and Get activity
        passcodeManager.setPasscodeLength(targetContext, 8);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "12345678");
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
    }

    /**
     * Test passcode creation flow when user enter a different passcode during confirmation.
     */
    @Test
    public void testCreateWithWrongConfirmPasscode() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertTrue("Application should be still locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in Create Confirm mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        checkUi();

        // Entering in 654321 and submitting -> expect passcodes don't match error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
        Assert.assertTrue("Application should be still locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());
        Assert.assertEquals("Expected error message.", passcodeActivity.getString(R.string.sf__passcodes_dont_match),
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions)).getText());

        // Entering 123456 twice to create passcode
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        checkUi();
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode verification flow when no mistakes are made by user.
     */
    @Test
    public void testVerificationWithNoMistakes() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
        checkUi();

        // We should still be locked
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test passcode verification flow when user enters wrong passcode once.
     */
    @Test
    public void testVerificationWithWrongPasscodeOnce() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());

        // We should still be locked
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());

        // Entering 654321 -> expect passcode incorrect error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", passcodeActivity.getString(R.string.sf__passcode_try_again, 9),
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions)).getText());
        Assert.assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test passcode verification flow when user enters wrong passcode twice.
     */
    @Test
    public void testVerificationWithWrongPasscodeTwice() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

        // Get activity
        final Intent intent = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(intent);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());

        // We should still be locked
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        for (int i = 1; i < 10; i++) {
            enterWrongPasscode(i, true);
        }

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test passcode verification flow when user enters wrong passcode too many times.
     */
    @Test
    public void testVerificationWithWrongPasscodeTooManyTimes() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
        waitSome();

        // Get activity
        final Intent intent = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(intent);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
        passcodeActivity.enableLogout(false); // logout is async, it creates havoc when running other tests

        // We should still be locked
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        for (int i = 1; i < 10; i++) {
            enterWrongPasscode(i, true);
        }

        // Entering 132645 and submitting -> expect passcode manager to be reset
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "132645");
        Assert.assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
    }

    /**
     * Test when user clicks on the 'Logout' button.
     */
    @Test
    public void testForgotPasscodeLogout() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check.
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
        passcodeActivity.enableLogout(false); // logout is async, it creates havoc when running other tests

        // We should still be locked.
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Logout button should not be visible.", View.GONE,
                passcodeActivity.findViewById(R.id.sf__passcode_logout_button).getVisibility());

        // Entering 654321 -> Logout button should be shown
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
        Assert.assertEquals("Logout button should be visible.", View.VISIBLE,
                passcodeActivity.findViewById(R.id.sf__passcode_logout_button).getVisibility());
        Assert.assertEquals("Logout button has wrong text.", passcodeActivity.getString(R.string.sf__passcode_logout),
                ((Button) passcodeActivity.findViewById(R.id.sf__passcode_logout_button)).getText());
        clickView(R.id.sf__passcode_logout_button);
        Assert.assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
    }

    /**
     * Test upgrade when passcode length is not known
     */
    @Test
    public void testPasscodeLengthUnknown() {

        // Store passcode and set mode to Check.
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
        passcodeManager.setPasscodeLengthKnown(targetContext, false);

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
        // Verify button should be shown
        checkUi();

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        clickView(R.id.sf__passcode_verify_button);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test entering wrong passcode on upgrade with unknown length
     */
    @Test
    public void testPasscodeLengthUnknownError() {

        // Store passcode and set mode to Check.
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
        passcodeManager.setPasscodeLengthKnown(targetContext, false);

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());

        enterWrongPasscode(1, false);
        // Verify button should still be shown
        Assert.assertEquals("Verify passcode button should be visible.", View.VISIBLE,
                passcodeActivity.findViewById(R.id.sf__passcode_verify_button).getVisibility());
        Assert.assertEquals("Verify passcode button has wrong text.",
                passcodeActivity.getString(R.string.sf__passcode_verify_button),
                ((Button) passcodeActivity.findViewById(R.id.sf__passcode_verify_button)).getText());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        clickView(R.id.sf__passcode_verify_button);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test biometric enrollment declined
     */
    @Test
    @SdkSuppress(minSdkVersion = 23)
    public void testBiometricEnrollmentDecline() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check.
        passcodeManager.store(targetContext, "123456");
        Assert.assertFalse("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        Assert.assertFalse("Biometric should not be enabled.", passcodeManager.biometricEnabled());

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();

        // Force biometric
        passcodeActivity.forceBiometric(true);
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        waitSome();
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.EnableBiometric, passcodeActivity.getMode());
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());

        // Verify biometric screen
        checkUi();

        // Tap not now button
        clickView(R.id.sf__biometric_not_now_button);
        Assert.assertFalse("Application should not be locked", passcodeManager.isLocked());
        Assert.assertTrue("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        Assert.assertFalse("Biometric should not be enabled.", passcodeManager.biometricEnabled());
    }

    /**
     * Biometric enrollment prompt should only be shown once
     */
    @Test
    @SdkSuppress(minSdkVersion = 23)
    public void testBiometricEnrollmentNotShown() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check.
        passcodeManager.store(targetContext, "123456");
        Assert.assertFalse("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        Assert.assertFalse("Biometric should not be enabled.", passcodeManager.biometricEnabled());

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();

        // Force biometric
        passcodeActivity.forceBiometric(true);
        // Set enrollment screen already shown
        passcodeManager.setBiometricEnrollmentShown(targetContext, true);
        Assert.assertTrue("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");

        // Biometric enrollment should not be shown
        Assert.assertFalse("Application should not be locked", passcodeManager.isLocked());
        Assert.assertTrue("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
    }

    /**
     * Test respecting connected app setting to disable biometric
     */
    @Test
    public void testConnectedAppDisableBiometric() {

        passcodeManager.setPasscodeLength(targetContext, 6);
        // Store passcode and set mode to Check.
        passcodeManager.store(targetContext, "123456");
        Assert.assertFalse("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        Assert.assertFalse("Biometric should not be enabled.", passcodeManager.biometricEnabled());

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();

        // Force biometric
        passcodeActivity.forceBiometric(true);
        // Set connected app setting
        passcodeManager.setBiometricAllowed(targetContext, false);
        Assert.assertFalse("Biometric allowed not set.", passcodeManager.biometricAllowed());
        Assert.assertFalse("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");

        // Biometric enrollment should not be shown
        Assert.assertFalse("Application should not be locked", passcodeManager.isLocked());
        Assert.assertFalse("Biometric enrollment shown wrong.", passcodeManager.biometricEnrollmentShown());
        Assert.assertFalse("Biometric should not be enabled.", passcodeManager.biometricEnabled());
        Assert.assertFalse("Biometric should not be allowed.", passcodeManager.biometricAllowed());
    }

    private void enterWrongPasscode(int count, boolean passcodeLengthKnow) {

        // Entering 321654 and submitting -> expect passcode incorrect error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "321654");
        if (!passcodeLengthKnow) {
            clickView(R.id.sf__passcode_verify_button);
        }

        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
        if (count == 9) {
            Assert.assertEquals("Error expected", passcodeActivity.getString(R.string.sf__passcode_final),
                    ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions)).getText());
        }
        Assert.assertEquals("Wrong failure count", count, passcodeManager.getFailedPasscodeAttempts());
    }

    private void clickView(final int resId) {
        try {
            onView(withId(resId)).perform(click());
        } catch (Throwable t) {
            Assert.fail("Failed to click view " + resId);
        }
    }

    private void setText(final int textViewId, final String text) {
        try {
            onView(withId(textViewId)).perform(replaceText(text), closeSoftKeyboard());
        } catch (Throwable t) {
            Assert.fail("Failed to set text " + text);
        }
    }

    private void doEditorAction(final int textViewId) {
        try {
            onView(withId(textViewId)).perform(pressImeActionButton());
        } catch (Throwable t) {
            Assert.fail("Failed to perform editor action");
        }
    }

    private void waitSome() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Assert.fail("Test interrupted");
        }
    }

    private void checkUi() {
        switch (passcodeActivity.getMode()) {
            case Check:
                // Verify Button
                if (passcodeManager.getPasscodeLengthKnown()) {
                    Assert.assertEquals("Verify passcode button should be visible.", View.GONE,
                            passcodeActivity.findViewById(R.id.sf__passcode_verify_button).getVisibility());
                } else {
                    Assert.assertEquals("Verify passcode button should be visible.", View.VISIBLE,
                            passcodeActivity.findViewById(R.id.sf__passcode_verify_button).getVisibility());
                    Assert.assertEquals("Verify passcode button has wrong text.",
                            passcodeActivity.getString(R.string.sf__passcode_verify_button),
                            ((Button) passcodeActivity.findViewById(R.id.sf__passcode_verify_button)).getText());
                }

                // Title
                Assert.assertEquals("Expected title to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_title).getVisibility());
                Assert.assertEquals("Title not correct.", passcodeActivity.getString(R.string.sf__passcode_enter_title),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__passcode_title)).getText());

                // instructions
                Assert.assertEquals("Instructions should be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_instructions).getVisibility());
                Assert.assertEquals("Instructions text is wrong.", passcodeActivity.getString(R.string.sf__passcode_enter_instructions),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__passcode_instructions)).getText());

                // Passcode Field
                Assert.assertEquals("Expected passcode box to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_box).getVisibility());
                Assert.assertEquals("Expected passcode field to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_text).getVisibility());

                // Biometric
                Assert.assertEquals("Expected fingerprint image to not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__fingerprint_icon).getVisibility());
                Assert.assertEquals("Expected biometric UI to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_box).getVisibility());
                Assert.assertEquals("Expected biometric title instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions_title).getVisibility());
                Assert.assertEquals("Expected biometric instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions).getVisibility());
                Assert.assertEquals("Expected not now button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_not_now_button).getVisibility());
                Assert.assertEquals("Expected enable button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_enable_button).getVisibility());
                break;
            case Change:
                // Title
                Assert.assertEquals("Expected title to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_title).getVisibility());
                Assert.assertEquals("Title not correct.", passcodeActivity.getString(R.string.sf__passcode_change_title),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__passcode_title)).getText());

                // instructions
                Assert.assertEquals("Change passcode instructions should be shown.", passcodeActivity.getString(R.string.sf__passcode_change_instructions),
                        ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions)).getText());
                Assert.assertEquals("Change passcode title expected.", passcodeActivity.getString(R.string.sf__passcode_change_title),
                        ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_title)).getText());

                // Passcode Field
                Assert.assertEquals("Expected passcode box to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_box).getVisibility());
                Assert.assertEquals("Expected passcode field to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_text).getVisibility());

                // Biometric
                Assert.assertEquals("Expected fingerprint image to not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__fingerprint_icon).getVisibility());
                Assert.assertEquals("Expected biometric UI to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_box).getVisibility());
                Assert.assertEquals("Expected biometric title instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions_title).getVisibility());
                Assert.assertEquals("Expected biometric instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions).getVisibility());
                Assert.assertEquals("Expected not now button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_not_now_button).getVisibility());
                Assert.assertEquals("Expected enable button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_enable_button).getVisibility());
                break;
            case Create:
                // Title
                Assert.assertEquals("Expected title to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_title).getVisibility());
                Assert.assertEquals("Title not correct.", passcodeActivity.getString(R.string.sf__passcode_create_title),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__passcode_title)).getText());

                // instructions
                Assert.assertEquals("Create instructions should be shown.", passcodeActivity.getString(R.string.sf__passcode_create_instructions),
                        ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions)).getText());
                Assert.assertEquals("Create title expected.", passcodeActivity.getString(R.string.sf__passcode_create_title),
                        ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_title)).getText());

                // Passcode Field
                Assert.assertEquals("Expected passcode box to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_box).getVisibility());
                Assert.assertEquals("Expected passcode field to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_text).getVisibility());

                // Biometric
                Assert.assertEquals("Expected fingerprint image to not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__fingerprint_icon).getVisibility());
                Assert.assertEquals("Expected biometric UI to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_box).getVisibility());
                Assert.assertEquals("Expected biometric title instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions_title).getVisibility());
                Assert.assertEquals("Expected biometric instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions).getVisibility());
                Assert.assertEquals("Expected not now button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_not_now_button).getVisibility());
                Assert.assertEquals("Expected enable button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_enable_button).getVisibility());
                break;
            case CreateConfirm:
                // Title
                Assert.assertEquals("Expected title to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_title).getVisibility());
                Assert.assertEquals("Title not correct.", passcodeActivity.getString(R.string.sf__passcode_confirm_title),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__passcode_title)).getText());

                // instructions
                Assert.assertEquals("Confirm instructions should be shown.", passcodeActivity.getString(R.string.sf__passcode_confirm_instructions),
                        ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions)).getText());
                Assert.assertEquals("Confirm title expected.", passcodeActivity.getString(R.string.sf__passcode_confirm_title),
                        ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_title)).getText());

                // Passcode Field
                Assert.assertEquals("Expected passcode box to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_box).getVisibility());
                Assert.assertEquals("Expected passcode field to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_text).getVisibility());

                // Biometric
                Assert.assertEquals("Expected fingerprint image to not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__fingerprint_icon).getVisibility());
                Assert.assertEquals("Expected biometric UI to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_box).getVisibility());
                Assert.assertEquals("Expected biometric title instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions_title).getVisibility());
                Assert.assertEquals("Expected biometric instructions to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions).getVisibility());
                Assert.assertEquals("Expected not now button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_not_now_button).getVisibility());
                Assert.assertEquals("Expected enable button to be not visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__biometric_enable_button).getVisibility());
                break;

            case EnableBiometric:
                // Title
                Assert.assertEquals("Expected title to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__passcode_title).getVisibility());
                Assert.assertEquals("Title not correct.", passcodeActivity.getString(R.string.sf__fingerprint_title),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__passcode_title)).getText());
                // Image
                Assert.assertEquals("Expected fingerprint image to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__fingerprint_icon).getVisibility());
                // UI Box
                Assert.assertEquals("Expected biometric UI to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__biometric_box).getVisibility());

                // Instructions
                Assert.assertEquals("Expected biometric title instructions to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions_title).getVisibility());
                Assert.assertEquals("Biometric instructions title is wrong.",
                        passcodeActivity.getString(R.string.sf__biometric_allow_instructions_title),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__biometric_instructions_title)).getText());
                Assert.assertEquals("Expected biometric instructions to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__biometric_instructions).getVisibility());
                Assert.assertEquals("Biometric instructions are wrong.",
                        passcodeActivity.getString(R.string.sf__biometric_allow_instructions, "RestExplorer"),
                        ((TextView) passcodeActivity.findViewById(R.id.sf__biometric_instructions)).getText());

                // Buttons
                Assert.assertEquals("Expected not now button to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__biometric_not_now_button).getVisibility());
                Assert.assertEquals("Not now button text incorrect.", passcodeActivity.getString(R.string.sf__biometric_not_now_button),
                        ((Button) passcodeActivity.findViewById(R.id.sf__biometric_not_now_button)).getText());
                Assert.assertEquals("Expected enable button to be visible.", View.VISIBLE,
                        passcodeActivity.findViewById(R.id.sf__biometric_enable_button).getVisibility());
                Assert.assertEquals("Not now button text incorrect.", passcodeActivity.getString(R.string.sf__biometric_enable_button),
                        ((Button) passcodeActivity.findViewById(R.id.sf__biometric_enable_button)).getText());

                // Passcode
                Assert.assertEquals("Passcode instructions should not be shown.", View.GONE,
                        passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_instructions).getVisibility());
                Assert.assertEquals("Expected passcode box to not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__passcode_box).getVisibility());
                Assert.assertEquals("Expected passcode field to not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__passcode_text).getVisibility());
                Assert.assertEquals("Verify passcode button should not be visible.", View.GONE,
                        passcodeActivity.findViewById(R.id.sf__passcode_verify_button).getVisibility());
                break;
        }
    }
}
