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
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.CustomServerUrlEditor;
import com.salesforce.androidsdk.ui.ServerPickerActivity;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

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
        checkServerAdded(label, url);
    }

    /**
     * Test an invalid valid URL is not entered or saved.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidUrl() throws Throwable {
        String label = "Invalid URL";
        String url = "";
        addCustomUrl(label, url);
        Assert.assertTrue("Custom URL dialog should not be closed",
                activity.getCustomServerUrlEditor().getDialog().isShowing());
        try {
            onView(allOf(withText(label + "\n" + url), findUiElement())).check(doesNotExist());
        } catch (Throwable t) {
            Assert.fail("Invalid Url should not be added to the server list.  Error: " + t.getLocalizedMessage());
        }
    }

    /**
     * Test that https is used if http is added.
     *
     * @throws Throwable
     */
    @Test
    public void testAddHttpUrl() throws Throwable {
        String label = "My http URL";
        String httpUrl = "http://invalid.url.com";
        String httpsUrl = "https://invalid.url.com";
        addCustomUrl(label, httpUrl);
        checkServerAdded(label, httpsUrl);
    }

    /**
     * Test that https is added is added on a url without it.
     *
     * @throws Throwable
     */
    @Test
    public void testAddNoProtocolUrl() throws Throwable {
        String label = "No Protocol URL";
        String url = "basic.url.com";
        String httpsUrl = "https://" + url;
        addCustomUrl(label, url);
        checkServerAdded(label, httpsUrl);
    }

    /**
     * Test the reset button works.
     *
     * @throws Throwable
     */
    @Test
    public void testRestButton() throws Throwable {
        String label = "Server%d";
        String url = "https://login.test.com/%d";
        String entry = label + "\n" + url;
        for (int serverNum = 0; serverNum < 3; serverNum++) {
            addCustomUrl(String.format(label, serverNum), String.format(url, serverNum));
            Thread.sleep(1000);
        }
        tapResetButton();
        for (int serverNum = 0; serverNum < 3; serverNum++) {
            try {
                onView(allOf(withText(String.format(entry, serverNum, serverNum)), findUiElement()))
                        .check(doesNotExist());
            } catch (Throwable t) {
                Assert.fail("Server entry found. Error: " + t.getLocalizedMessage());
            }
        }
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
        clickView(com.salesforce.androidsdk.R.id.sf__apply_button);
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

    private void checkServerAdded(String name, String url) {
        Assert.assertNull("Custom URL dialog should be closed",
                activity.getCustomServerUrlEditor().getDialog());
        try {
            ViewInteraction salesforceServerRadioButton = onView(allOf(withText(name + "\n" + url),
                    findUiElement()));
            salesforceServerRadioButton.check(matches(isDisplayed()));
            salesforceServerRadioButton.check(matches(isChecked()));
        } catch (Throwable t) {
            Assert.fail("Server with name: " + name + ", and server: " + url +
                    " is not displayed. Error: " + t.getLocalizedMessage());
        }
    }

    private static Matcher<View> findUiElement() {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {}

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                int childCount = ((ViewGroup) parent).getChildCount();

                for (int childNum = 0; childNum < childCount; childNum++) {
                    if (view.equals(((ViewGroup) parent).getChildAt(childNum))) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    private static void tapResetButton() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
        try {
            onView(allOf(withId(android.R.id.title), withText("Reset"), findUiElement()))
                    .perform(click());
        } catch (Throwable t) {
            Assert.fail("Unable to tap reset button.  Error: " + t.getLocalizedMessage());
        }

    }
}
