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
package com.salesforce.androidsdk.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;


/**
 * Passcode activity: takes care of creating/verifying a user passcode.
 *
 * @deprecated Will be removed in Mobile SDK 10.0.  Use {@link ScreenLockActivity} instead.
 */
public class PasscodeActivity extends Activity {

    protected static final int MAX_PASSCODE_ATTEMPTS = 10;
    public enum PasscodeMode {
        Create,
        CreateConfirm,
        Check,
        Change,
        EnableBiometric,
        BiometricCheck
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void biometricDeclined() { }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) { return false; }

    /**
     * Saves the entered text before activity rotation.
     */
    @Override
    protected void onSaveInstanceState(Bundle savedInstance) { }

    public PasscodeMode getMode() {
        return PasscodeMode.Create;
    }

    public void setMode(PasscodeMode newMode) { }

    /**
     * Used from tests to allow/disallow automatic logout when wrong passcode has been entered too many times.
     *
     * @param b True - if logout is enabled, False - otherwise.
     */
    public void enableLogout(boolean b) { }

    /**
     * Used for tests to allow biometric when the device is not set up
     *
     * @param b True - if biometric checks skipped, False - otherwise.
     */
    public void forceBiometric(boolean b) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { }

    public void unlockViaFingerprintScan() { }
}