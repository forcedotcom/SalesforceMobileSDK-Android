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
package com.salesforce.samples.restexplorer;

import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.security.AbstractPasscodeActivity.PasscodeMode;
import com.salesforce.androidsdk.security.PasscodeManager;


/**
 * Tests for PasscodeActivity
 */
public class PasscodeActivityTest extends
		BaseActivityInstrumentationTestCase<PasscodeActivity> {

	private Context targetContext;
	private PasscodeActivity passcodeActivity;
	private PasscodeManager passcodeManager;


	public PasscodeActivityTest() {
		super("com.salesforce.samples.restexplorer", PasscodeActivity.class);
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		passcodeManager = ForceApp.APP.getPasscodeManager();
		passcodeManager.setTimeoutMs(60000);
		passcodeManager.reset(targetContext);
		assertTrue("Application should be locked", passcodeManager.isLocked());
		assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
		passcodeActivity = getActivity();
		assertEquals("Activity expected in create mode", PasscodeMode.Create, passcodeActivity.getMode());
	}
	
	/**
	 * Test passcode creation flow when no mistakes are made by user
	 */
	public void testCreateWithNoMistakes() {
		// Entering in 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Re-entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}
	
	/**
	 * Test passcode creation flow when user try to enter a passcode too short
	 */
	public void testCreateWithPasscodeTooShort() {
		// Entering in 123 and submitting -> expect passcode too short error
		setText(R.id.passcode_text, "123");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create mode still", PasscodeMode.Create, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode must be at least 6 characters long", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		
		// Entering in 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create confirm mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Re-entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	
	/**
	 * Test passcode creation flow when user enter a passcode too short during confirmation
	 */
	public void testCreateWithConfirmPasscodeTooShort() {
		// Entering in 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Entering in 123 and submitting -> expect passcode too short error
		setText(R.id.passcode_text, "123");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create confirm mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode must be at least 6 characters long", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());

		// Entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
	}

	
	/**
	 * Test passcode creation flow when user enter a different passcode during confirmation
	 */
	public void testCreateWithWrongConfirmPasscode() {
		// Entering in 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should be still locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		
		// Entering in 654321 and submitting -> expect passcodes don't match error
		setText(R.id.passcode_text, "654321");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should be still locked", passcodeManager.isLocked());
		assertEquals("Activity expected in create confirm mode still", PasscodeMode.CreateConfirm, passcodeActivity.getMode());
		assertEquals("Error expected", "Passcodes don't match!", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());

		// Entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
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
		gotoVerificationMode();

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
	}

	
	/**
	 * Test passcode verification flow when user enters wrong passcode once
	 */
	public void testVerificationWithWrongPasscodeOnce() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
		gotoVerificationMode();

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 654321 and submitting -> expect passcode incorrect error
		setText(R.id.passcode_text, "654321");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 2 attempts remaining.", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());

		// Entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
	}

	/**
	 * Test passcode verification flow when user enters wrong passcode twice
	 */
	public void testVerificationWithWrongPasscodeTwice() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
		gotoVerificationMode();

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 654321 and submitting -> expect passcode incorrect error
		setText(R.id.passcode_text, "654321");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 2 attempts remaining.", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());

		// Entering 321654 and submitting -> expect passcode incorrect error
		setText(R.id.passcode_text, "321654");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "Final passcode attempt", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		assertEquals("Wrong failure count", 2, passcodeManager.getFailedPasscodeAttempts());
		
		// Entering 123456 and submitting
		setText(R.id.passcode_text, "123456");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertFalse("Application should be unlocked", passcodeManager.isLocked());
	}
	
	
	/**
	 * Test passcode verification flow when user enters a passcode too short
	 */
	public void testVerificationWithPasscodeTooShort() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
		gotoVerificationMode();

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 123 and submitting -> expect passcode too short error, not counted as attempt
		setText(R.id.passcode_text, "654321");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 2 attempts remaining.", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());
	}
	
	
	/**
	 * Test passcode verification flow when user enters wrong passcode too many times
	 */
	public void testVerificationWithWrongPasscodeTooManyTimes() {
		// Store passcode and set mode to Check
		passcodeManager.store(targetContext, "123456");
		assertTrue("Stored passcode should match entered passcode", passcodeManager.check(targetContext, "123456"));
		gotoVerificationMode();

		// We should still be locked
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		
		// Entering 654321 and submitting -> expect passcode incorrect error
		setText(R.id.passcode_text, "654321");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "The passcode you entered is incorrect. Please try again. You have 2 attempts remaining.", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		assertEquals("Wrong failure count", 1, passcodeManager.getFailedPasscodeAttempts());

		// Entering 321654 and submitting -> expect passcode incorrect error
		setText(R.id.passcode_text, "321654");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should still be locked", passcodeManager.isLocked());
		assertEquals("Activity expected in check mode still", PasscodeMode.Check, passcodeActivity.getMode());
		assertEquals("Error expected", "Final passcode attempt", ((TextView) passcodeActivity.findViewById(R.id.passcode_error)).getText());
		assertEquals("Wrong failure count", 2, passcodeManager.getFailedPasscodeAttempts());
		
		// Entering 132645 and submitting -> expect passcode manager to be reset
		setText(R.id.passcode_text, "132645");
		doEditorAction(R.id.passcode_text, EditorInfo.IME_ACTION_GO);
		assertTrue("Application should be locked", passcodeManager.isLocked());
		assertFalse("Application should not have a passcode", passcodeManager.hasStoredPasscode(targetContext));
	}
	
	private void gotoVerificationMode() {
    	try {
	        runTestOnUiThread(new Runnable() {
	            @Override public void run() {
	            	passcodeActivity.setMode(PasscodeMode.Check);
	            }
	        });
    	}
    	catch (Throwable t) {
    		fail("Failed to go to check mode");
    	}
	}
	
}
