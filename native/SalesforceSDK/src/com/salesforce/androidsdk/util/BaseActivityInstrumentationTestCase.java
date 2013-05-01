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
package com.salesforce.androidsdk.util;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TabHost;
import android.widget.TextView;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.EventsListenerQueue;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Super class to activity tests
 */
public class BaseActivityInstrumentationTestCase<T extends Activity> extends ActivityInstrumentationTestCase2<T> {

    private EventsListenerQueue eq;

    public BaseActivityInstrumentationTestCase(String pkg, Class<T> activityClass) {
        super(pkg, activityClass);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);

        eq = new EventsListenerQueue();
        // Wait for app initialization to complete
        if (SalesforceSDKManager.getInstance() == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        super.tearDown();
    }

    protected void clickTab(final TabHost tabHost, final int tabIndex) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    tabHost.setCurrentTab(tabIndex);
            } });
        }
        catch (Throwable t) {
            fail("Failed to click tab " + tabIndex);
        }
    }

    protected void clickView(final View v) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    v.performClick();
                }
            });
        }
        catch (Throwable t) {
            fail("Failed to click view " + v);
        }
    }

    protected void checkRadioButton(final int radioButtonId) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    RadioButton v = (RadioButton) getActivity().findViewById(radioButtonId);
                    v.setChecked(true);
                }
            });
        }
        catch (Throwable t) {
            fail("Failed to check radio button " + radioButtonId);
        }
    }


    protected void setText(final int textViewId, final String text) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    TextView v = (TextView) getActivity().findViewById(textViewId);
                    v.setText(text);
                    if (v instanceof EditText)
                        ((EditText)v).setSelection(v.getText().length());
                }
            });
        }
        catch (Throwable t) {
            fail("Failed to set text " + text);
        }
    }

    protected void doEditorAction(final int textViewId, final int actionCode) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    TextView v = (TextView) getActivity().findViewById(textViewId);
                    v.onEditorAction(actionCode);
                }
            });
        }
        catch (Throwable t) {
            fail("Failed do editor action " + actionCode);
        }

    }


    protected void setText(final TextView v, final String text) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    v.setText(text);
                    if (v instanceof EditText)
                        ((EditText)v).setSelection(v.getText().length());
                }
            });
        }
        catch (Throwable t) {
            fail("Failed to set text " + text);
        }
    }

    /** a runnable that requestsFocus for the specified view */
    protected static class Focuser implements Runnable {

        Focuser(View v) {
            this.view = v;
        }

        private final View view;

        @Override
        public void run() {
            view.requestFocus();
        }
    }

    protected void setFocus(View v) throws Throwable {
        runTestOnUiThread(new Focuser(v));
    }

    protected void waitSome() {
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }

    protected void waitForRender() {
        eq.waitForEvent(EventType.RenditionComplete, 5000);
    }
}
