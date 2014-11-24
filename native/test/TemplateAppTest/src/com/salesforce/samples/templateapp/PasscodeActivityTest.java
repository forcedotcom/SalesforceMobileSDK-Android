/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.samples.templateapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.PasscodeActivity;
import com.salesforce.androidsdk.ui.PasscodeActivity.PasscodeMode;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

/**
 * Tests for PasscodeActivity
 */
public class PasscodeActivityTest extends
		ActivityInstrumentationTestCase2<PasscodeActivity> {

    private EventsListenerQueue eq;
	private Context targetContext;
	private PasscodeActivity passcodeActivity;
	private PasscodeManager passcodeManager;

	public PasscodeActivityTest() {
		super(PasscodeActivity.class);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
        setActivityInitialTouchMode(false);
        eq = new EventsListenerQueue();

        // Waits for app initialization to complete.
        if (SalesforceSDKManager.getInstance() == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
		targetContext = getInstrumentation().getTargetContext();
		passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
		passcodeManager.reset(targetContext);
		passcodeManager.setTimeoutMs(600000);
		assertTrue("Application should be locked", passcodeManager.isLocked());
		assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
	}

	@Override
	public void tearDown() throws Exception {
		if (passcodeActivity != null) {
			passcodeActivity.finish();
			passcodeActivity = null;
		}
		if (eq != null) {
            eq.tearDown();
            eq = null;
        }
		super.tearDown();
	}

	/**
	 * Test passcode creation flow when no mistakes are made by user
	 */
	public void testCreateWithNoMistakes() {
		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

		// Entering in 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Re-entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	/**
	 * Test passcode change flow when no mistakes are made by user
	 */
	public void testChangeWithNoMistakes() {
		// Get activity
		final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
				SalesforceSDKManager.getInstance().getPasscodeActivity());
        i.putExtra(PasscodeManager.CHANGE_PASSCODE_KEY, true);
		setActivityIntent(i);
		passcodeActivity = getActivity();
		assertEquals("Activity expected in change mode", PasscodeMode.Change, passcodeActivity.getMode());

		// Entering in 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());

		// Re-entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	/**
	 * Test passcode creation flow when user try to enter a passcode too short
	 */
	public void testCreateWithPasscodeTooShort() {
		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

		// Entering in 123 and submitting -> expect passcode too short error
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create mode still", PasscodeMode.Create, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode must be at least 4 characters long",
				((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
		
		// Entering in 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create confirm mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Re-entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	/**
	 * Test passcode creation flow when user enter a passcode too short during confirmation
	 */
	public void testCreateWithConfirmPasscodeTooShort() {
		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

		// Entering in 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Entering in 123 and submitting -> expect passcode too short error
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create confirm mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode must be at least 4 characters long",
				((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());

		// Entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	/**
	 * Test passcode creation flow when user enter a different passcode during confirmation
	 */
	public void testCreateWithWrongConfirmPasscode() {
		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());

		// Entering in 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should be still locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Entering in 654321 and submitting -> expect passcodes don't match error
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should be still locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create confirm mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		assertEquals("Error expected", "Passcodes don't match!",
				((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());

		// Entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	/**
	 * Test passcode verification flow when no mistakes are made by user
	 */
	public void testVerificationWithNoMistakes() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
	}

	/**
	 * Test passcode verification flow when user enters wrong passcode once
	 */
	public void testVerificationWithWrongPasscodeOnce() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 654321 and submitting -> expect passcode incorrect error
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 9 attempts remaining.",
				((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
		assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());

		// Entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
	}

	/**
	 * Test passcode verification flow when user enters wrong passcode twice
	 */
	public void testVerificationWithWrongPasscodeTwice() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
		
		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());

		for (int i = 1; i < 10; i++) {
			enterWrongPasscode(i);
		}

		// Entering 123456 and submitting
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "123456");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
	}

	/**
	 * Test passcode verification flow when user enters a passcode too short
	 */
	public void testVerificationWithPasscodeTooShort() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 123 and submitting -> expect passcode too short error, not counted as attempt
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "654321");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 9 attempts remaining.",
				((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
		assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());
	}
	
	/**
	 * Test passcode verification flow when user enters wrong passcode too many times
	 */
	public void testVerificationWithWrongPasscodeTooManyTimes() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
		passcodeActivity.enableLogout(false); // logout is async, it creates havoc when running other tests
		
		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());

		for (int i = 1; i < 10; i++) {
			enterWrongPasscode(i);
		}
		
		// Entering 132645 and submitting -> expect passcode manager to be reset
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "132645");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
	}

	/**
	 * Test when user clicks on the 'Forgot Passcode' link.
	 */
	public void testForgotPasscodeLink() throws Throwable {
		// Store passcode and set mode to Check.
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));

		// Get activity
		passcodeActivity = getActivity();
		assertEquals("Activity expected in check mode", PasscodeMode.Check, passcodeActivity.getMode());
		passcodeActivity.enableLogout(false); // logout is async, it creates havoc when running other tests
		
		// We should still be locked.
		assertTrue("Application should still be locked", passcodeManager.isLocked());

		// Click on 'Forgot Passcode' link.
		assertFalse("Logout dialog should not be showing", getActivity().getIsLogoutDialogShowing());
		clickView(getActivity().findViewById(com.salesforce.androidsdk.R.id.sf__passcode_forgot)); 
		waitSome();
		assertTrue("Logout dialog should be showing", getActivity().getIsLogoutDialogShowing());

		// Clicking on 'Cancel' should take us back to the passcode screen.
		final AlertDialog logoutDialog = getActivity().getLogoutAlertDialog();
		clickView(logoutDialog.getButton(AlertDialog.BUTTON_NEGATIVE)); 
		waitSome();
		assertFalse("Logout dialog should not be showing", getActivity().getIsLogoutDialogShowing());

		// Clicking on 'Ok' should log the user out.
		clickView(getActivity().findViewById(com.salesforce.androidsdk.R.id.sf__passcode_forgot)); 
		waitSome();
		clickView(logoutDialog.getButton(AlertDialog.BUTTON_POSITIVE)); 
		waitSome();
		assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
	}

	private void enterWrongPasscode(int count) {

		// Entering 321654 and submitting -> expect passcode incorrect error
		setText(com.salesforce.androidsdk.R.id.sf__passcode_text, "321654");
		doEditorAction(com.salesforce.androidsdk.R.id.sf__passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		if (count == 9) {
			assertEquals("Error expected", "Final passcode attempt",
					((TextView) passcodeActivity.findViewById(com.salesforce.androidsdk.R.id.sf__passcode_error)).getText());
		}
		assertEquals("Wrong failure count", count, passcodeManager.getFailedPasscodeAttempts());
	}

    private void clickView(final View v) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    v.performClick();
                }
            });
        } catch (Throwable t) {
            fail("Failed to click view " + v);
        }
    }

    private void setText(final int textViewId, final String text) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    TextView v = (TextView) getActivity().findViewById(textViewId);
                    v.setText(text);
                    if (v instanceof EditText)
                        ((EditText) v).setSelection(v.getText().length());
                }
            });
        } catch (Throwable t) {
            fail("Failed to set text " + text);
        }
    }

    private void doEditorAction(final int textViewId, final int actionCode) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    TextView v = (TextView) getActivity().findViewById(textViewId);
                    v.onEditorAction(actionCode);
                }
            });
        } catch (Throwable t) {
            fail("Failed do editor action " + actionCode);
        }
    }

    private void waitSome() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }
}
