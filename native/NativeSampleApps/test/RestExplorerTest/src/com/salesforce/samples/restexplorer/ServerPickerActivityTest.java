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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.EditText;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.CustomServerUrlEditor;
import com.salesforce.androidsdk.ui.ServerPickerActivity;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

/**
 * Tests for ServerPickerActivity.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ServerPickerActivityTest {

    private EventsListenerQueue eq;
    private ServerPickerActivity activity;

    @Rule
    public ActivityTestRule<ServerPickerActivity> serverPickerActivityTestRule = new ActivityTestRule<>(ServerPickerActivity.class);

    @Before
    public void setUp() throws Exception {
        eq = new EventsListenerQueue();

        // Waits for app initialization to complete.
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        activity = serverPickerActivityTestRule.getActivity();
        removeFragmentIfRequired();
        Assert.assertNotNull("Activity should not be null", activity);
    }

    @After
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
        activity.finish();
        activity = null;
    }

    /**
     * Test that the cancel button can be clicked and the URL not saved.
     *
     * @throws Throwable
     */
    @Test
    public void testCancelButton() throws Throwable {
        openCustomEditDialog();
        clickView(com.salesforce.androidsdk.R.id.sf__cancel_button);
        Assert.assertNull("Custom URL dialog should be closed",
                activity.getCustomServerUrlEditor().getDialog());
    }

    /**
     * Test a valid URL can be entered and saved.
     *
     * @throws Throwable
     */
    @Test
    public void testAddCustomInstance() throws Throwable {
        String label = "My Custom URL";
        String url = "https://valid.url.com";
        addCustomUrl(label, url);
        clickView(com.salesforce.androidsdk.R.id.sf__apply_button);
        openCustomEditDialog();
        final CustomServerUrlEditor dialog = activity.getCustomServerUrlEditor();
        Thread.sleep(3000);
        final View rootView = dialog.getRootView();
        final EditText txtLabel = rootView.findViewById(com.salesforce.androidsdk.R.id.sf__picker_custom_label);
        final EditText txtUrl = rootView.findViewById(com.salesforce.androidsdk.R.id.sf__picker_custom_url);
        Assert.assertTrue("Custom Label does not match Expected: " + label
                + " Actual: " + txtLabel.getEditableText().toString(), label
                .equalsIgnoreCase(txtLabel.getEditableText().toString()));
        Assert.assertTrue("Custom URL does not match Expected: " + url + " Actual: "
                + txtUrl.getEditableText().toString(), url
                .equalsIgnoreCase(txtUrl.getEditableText().toString()));
    }

    /**
     * Test that "https" is required.
     *
     * @throws Throwable
     */
    @Test
    public void testAddInvalidUrl() throws Throwable {
        String label = "My URL";
        String url = "http://invalid.url.com";
        addCustomUrl(label, url);
        clickView(com.salesforce.androidsdk.R.id.sf__apply_button);
        Assert.assertTrue("Custom URL dialog should still be open",
                activity.getCustomServerUrlEditor().getDialog().isShowing());
        url = "https://valid.url.com";
        addCustomUrl(label, url);
        clickView(com.salesforce.androidsdk.R.id.sf__apply_button);
        Assert.assertNull("Custom URL dialog should be closed",
                activity.getCustomServerUrlEditor().getDialog());
    }

    private void openCustomEditDialog() throws Throwable {
        clickView(com.salesforce.androidsdk.R.id.sf__show_custom_url_edit);
        final CustomServerUrlEditor dialog = activity.getCustomServerUrlEditor();
        Thread.sleep(3000);
        final View rootView = dialog.getRootView();
        Assert.assertNotNull("Root view should not be null", rootView);
        clickView(com.salesforce.androidsdk.R.id.sf__picker_custom_label);
    }

    private void addCustomUrl(String label, String url) throws Throwable {
        if (!activity.getCustomServerUrlEditor().isVisible()) {
            openCustomEditDialog();
        }
        setText(com.salesforce.androidsdk.R.id.sf__picker_custom_label, label);
        setText(com.salesforce.androidsdk.R.id.sf__picker_custom_url, url);
    }

    private void removeFragmentIfRequired() {
        final FragmentManager fm = activity.getFragmentManager();
        final Fragment dialog = activity.getFragmentManager().findFragmentByTag("custom_server_dialog");
        if (dialog != null && dialog.isAdded()) {
            final FragmentTransaction ft = fm.beginTransaction();
            ft.remove(dialog);
            ft.commit();
        }
    }

    private void setText(final int viewId, final String text) {
        try {
            onView(withId(viewId)).perform(replaceText(text), closeSoftKeyboard());
        } catch (Throwable t) {
            Assert.fail("Failed to set text " + text);
        }
    }

    private void clickView(final int resId) {
        try {
            onView(withId(resId)).perform(click());
        } catch (Throwable t) {
            Assert.fail("Failed to click view " + resId);
        }
    }
}
