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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.widget.TextView;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.PasscodeActivity;
import com.salesforce.androidsdk.ui.PasscodeActivity.PasscodeMode;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.pressImeActionButton;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

/**
 * Tests for PasscodeActivity.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PasscodeActivityTest {

    private Context targetContext;
    private PasscodeActivity passcodeActivity;
    private PasscodeManager passcodeManager;

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
            targetContext = InstrumentationRegistry.getTargetContext();
            passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        }
    }

    @Rule
    public PasscodeActivityRule<PasscodeActivity> passcodeActivityTestRule = new PasscodeActivityRule<>(PasscodeActivity.class);

    @Before
    public void setUp() throws Exception {
        passcodeManager.reset(targetContext);
        passcodeManager.setTimeoutMs(600000);
        Assert.assertTrue("Application should be locked", passcodeManager.isLocked());
        Assert.assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
    }

    @After
    public void tearDown() throws Exception {
        passcodeManager.reset(targetContext);
        passcodeManager.setTimeoutMs(600000);
    }

    /**
     * Test passcode creation flow when no mistakes are made by user.
     */
    @Test
    public void testCreateWithNoMistakes() {

        // Get activity
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        Assert.assertTrue("Error message should be empty", TextUtils.isEmpty(
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText()));

        // Re-entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode change flow when no mistakes are made by user.
     */
    @Test
    public void testChangeWithNoMistakes() {

        // Get activity
        final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
                SalesforceSDKManager.getInstance().getPasscodeActivity());
        i.putExtra(PasscodeManager.CHANGE_PASSCODE_KEY, true);
        passcodeActivityTestRule.launchActivity(i);
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in change mode", PasscodeMode.Change, passcodeActivity.getMode());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        Assert.assertTrue("Error message should be empty", TextUtils.isEmpty(
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText()));

        // Re-entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode creation flow when user try to enter a passcode too short.
     */
    @Test
    public void testCreateWithPasscodeTooShort() {

        // Get activity
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        // Entering nothing and submitting -> expect passcode too short error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create mode still", PasscodeMode.Create, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", "The passcode must be at least 4 characters long",
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());

        // Entering in 123 and submitting -> expect passcode too short error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create mode still", PasscodeMode.Create, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", "The passcode must be at least 4 characters long",
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create confirm mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());

        // Re-entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode creation flow when user try to enter a passcode matching the minimum value.
     */
    @Test
    public void testCreatewithPasscodeMinimumLength() {

        // Get activity
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        // Entering nothing and submitting -> expect passcode too short error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "1234");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        Assert.assertTrue("Error Message should be empty", TextUtils.isEmpty(
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText()));
    }

    /**
     * Test passcode creation flow when user enter a passcode too short during confirmation.
     */
    @Test
    public void testCreateWithConfirmPasscodeTooShort() {

        // Get activity
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());

        // Entering in 123 and submitting -> expect passcode too short error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create confirm mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", "The passcode must be at least 4 characters long",
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode creation flow when user enter a different passcode during confirmation.
     */
    @Test
    public void testCreateWithWrongConfirmPasscode() {

        // Get activity
        passcodeActivity = passcodeActivityTestRule.getActivity();
        Assert.assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

        // Entering in 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should be still locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());

        // Entering in 654321 and submitting -> expect passcodes don't match error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should be still locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in create confirm mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", "Passcodes don't match!",
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
    }

    /**
     * Test passcode verification flow when no mistakes are made by user.
     */
    @Test
    public void testVerificationWithNoMistakes() {

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

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test passcode verification flow when user enters wrong passcode once.
     */
    @Test
    public void testVerificationWithWrongPasscodeOnce() {

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

        // Entering 654321 and submitting -> expect passcode incorrect error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 9 attempts remaining.",
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
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
            enterWrongPasscode(i);
        }

        // Entering 123456 and submitting
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should be unlocked", passcodeManager.isLocked());
    }

    /**
     * Test passcode verification flow when user enters a passcode too short.
     */
    @Test
    public void testVerificationWithPasscodeTooShort() {

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

        // Entering 123 and submitting -> expect passcode too short error, not counted as attempt
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
        Assert.assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 9 attempts remaining.",
                ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
        Assert.assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());
    }

    /**
     * Test passcode verification flow when user enters wrong passcode too many times.
     */
    @Test
    public void testVerificationWithWrongPasscodeTooManyTimes() {

        // Store passcode and set mode to Check
        passcodeManager.store(targetContext, "123456");
        Assert.assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

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
            enterWrongPasscode(i);
        }

        // Entering 132645 and submitting -> expect passcode manager to be reset
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "132645");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
    }

    /**
     * Test when user clicks on the 'Forgot Passcode' link.
     */
    @Test
    public void testForgotPasscodeLink() throws Throwable {

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

        // Click on 'Forgot Passcode' link.
        Assert.assertFalse("Logout dialog should not be showing", passcodeActivityTestRule.getActivity().getIsLogoutDialogShowing());
        clickView(com.salesforce.androidsdk.R.id.sf__passcode_forgot);
        waitSome();
        Assert.assertTrue("Logout dialog should be showing", passcodeActivityTestRule.getActivity().getIsLogoutDialogShowing());

        // Clicking on 'Cancel' should take us back to the passcode screen.
        final AlertDialog logoutDialog = passcodeActivityTestRule.getActivity().getLogoutAlertDialog();
        passcodeActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logoutDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            }
        });
        waitSome();
        Assert.assertFalse("Logout dialog should not be showing", passcodeActivityTestRule.getActivity().getIsLogoutDialogShowing());

        // Clicking on 'Ok' should log the user out.
        clickView(com.salesforce.androidsdk.R.id.sf__passcode_forgot);
        waitSome();
        passcodeActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logoutDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            }
        });
        waitSome();
        Assert.assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
    }

    private void enterWrongPasscode(int count) {

        // Entering 321654 and submitting -> expect passcode incorrect error
        setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "321654");
        doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text);
        Assert.assertTrue("Application should still be locked", passcodeManager.isLocked());
        Assert.assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
        if (count == 9) {
            Assert.assertEquals("Error expected", "Final passcode attempt",
                    ((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
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
}
