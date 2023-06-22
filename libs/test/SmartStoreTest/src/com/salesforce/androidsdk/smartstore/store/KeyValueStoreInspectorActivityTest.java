/*
 * Copyright (c) 2020-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;

import android.content.Intent;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.test.espresso.matcher.RootMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.smartstore.R;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.ui.KeyValueStoreInspectorActivity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for KeyValueStoreInspectorActivity
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class KeyValueStoreInspectorActivityTest {
    private final String STORE_1 = "store1";
    private final String STORE_2 = "store2";
    private final String KEY_1 = "firstKey";
    private final String KEY_2 = "secondKey";
    private final String KEY_3 = "keyThree";
    private final String VALUE_1 = "this is a value in ";
    private final String VALUE_2 = "this is a different value in ";
    private final String VALUE_3 = "this is a third value in ";
    private final String GLOBAL_STORE_TEXT = KeyValueStoreInspectorActivity.GLOBAL_STORE;

    @Rule
    public ActivityTestRule<KeyValueStoreInspectorActivity> keyValueStoreInspectorActivityTestRule = new ActivityTestRule<>(KeyValueStoreInspectorActivity.class, true, false);

    @Before
    public void setUp() throws Exception {
        EventBuilderHelper.enableDisable(false);
        SmartStoreSDKManager.getInstance().removeAllGlobalKeyValueStores();
    }

    /**
     * Test no KeyValueEncryptedFileStore
     */
    @Test
    public void testNoStore() {
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        Assert.assertEquals("Incorrect message for no store.",
                KeyValueStoreInspectorActivity.NO_STORE, getCurrentStoreName());
        Assert.assertFalse("Get Value Button should be disabled.", isGetValueButtonEnabled());
    }

    /**
     * Test single KeyValueEncryptedFileStore
     */
    @Test
    public void testSingleStore() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        Assert.assertEquals("Wrong Store name shown.",
                STORE_1 + GLOBAL_STORE_TEXT, getCurrentStoreName());
        Assert.assertTrue("Get Value Button should be enabled.", isGetValueButtonEnabled());
        writeKey(KEY_1);
        tapGetValueButton();
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1);
        writeKey(KEY_2);
        tapGetValueButton();
        checkKeyValuePairShown(KEY_2, VALUE_2 + STORE_1);
    }

    /**
     * Test changing stores.
     */
    @Test
    public void testChangingStores() {
        createKeyValueStore(STORE_1);
        createKeyValueStore(STORE_2);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        Assert.assertEquals("Wrong Store name shown.",
                STORE_1 + GLOBAL_STORE_TEXT, getCurrentStoreName());
        changeStore(STORE_2);
        Assert.assertEquals("Wrong Store name shown.",
                STORE_2 + GLOBAL_STORE_TEXT, getCurrentStoreName());
        writeKey(KEY_1);
        tapGetValueButton();
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_2);
    }

    /**
     * Test key not found.
     */
    @Test
    public void testKeyNotFound() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("badKey");
        tapGetValueButton();
        checkForKeyNotFoundDialog();
    }

    /**
     * Test * query
     */
    @Test
    public void testStarQuery() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("*");
        tapGetValueButton();
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairShown(KEY_3, VALUE_3 + STORE_1);
    }

    /**
     * Test query ending with * matching one
     */
    @Test
    public void testQueryEndingWithStarMatchingOne() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("f*");
        tapGetValueButton();
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1);
    }

    /**
     * Test query ending with * matching none
     */
    @Test
    public void testQueryEndingWithStarMatchingNone() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("x*");
        tapGetValueButton();
        checkForKeyNotFoundDialog();
        checkKeyValuePairNotShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1);
    }

    /**
     * Test query starting with * matching two
     */
    @Test
    public void testQueryStartingWithStarMatchingTwo() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("*Key");
        tapGetValueButton();
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1);
    }

    /**
     * Test query ending with * matching two
     */
    @Test
    public void testQueryEndingWithStarMatchingTwo() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("*x");
        tapGetValueButton();
        checkForKeyNotFoundDialog();
        checkKeyValuePairNotShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1);
    }

    /**
     * Test query starting and ending with * matching two
     */
    @Test
    public void testQueryStartingAndEndingWithStarMatchingTwo() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("*r*");
        tapGetValueButton();
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairShown(KEY_3, VALUE_3 + STORE_1);
    }

    /**
     * Test query starting and ending with * matching none
     */
    @Test
    public void testQueryStartingAndEndingWithStarMatchingNone() {
        createKeyValueStore(STORE_1);
        keyValueStoreInspectorActivityTestRule.launchActivity(new Intent());
        writeKey("*zz*");
        tapGetValueButton();
        checkForKeyNotFoundDialog();
        checkKeyValuePairNotShown(KEY_1, VALUE_1 + STORE_1);
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1);
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1);
    }

    private void createKeyValueStore(String storeName) {
        KeyValueEncryptedFileStore store = SmartStoreSDKManager.getInstance().getGlobalKeyValueStore(storeName);
        store.saveValue(KEY_1, VALUE_1 + storeName);
        store.saveValue(KEY_2, VALUE_2 + storeName);
        store.saveValue(KEY_3, VALUE_3 + storeName);
    }

    private String getCurrentStoreName() {
        final AutoCompleteTextView textView = keyValueStoreInspectorActivityTestRule.getActivity()
                .findViewById(R.id.sf__inspector_stores_dropdown);
        return textView.getText().toString();
    }

    private boolean isGetValueButtonEnabled() {
        final Button getValueButton = keyValueStoreInspectorActivityTestRule.getActivity()
                .findViewById(R.id.sf__inspector_get_value_button);
        return getValueButton.isEnabled();
    }

    private void writeKey(String key) {
        onView(withId(R.id.sf__inspector_key_text)).perform(replaceText(key));
    }

    private void tapGetValueButton() {
        onView(withId(R.id.sf__inspector_get_value_button)).perform(click());
    }

    private void checkKeyValuePairShown(String key, String value) {
        onView(allOf(withId(R.id.sf__inspector_key), withText(key))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.sf__inspector_value), withText(value))).check(matches(isDisplayed()));
    }

    private void checkKeyValuePairNotShown(String key, String value) {
        onView(allOf(withId(R.id.sf__inspector_key), withText(key))).check(doesNotExist());
        onView(allOf(withId(R.id.sf__inspector_value), withText(value))).check(doesNotExist());
    }

    private void changeStore(String newStore) {
        onView(withId(R.id.sf__inspector_stores_dropdown)).perform(click());
        onView(withText(newStore + GLOBAL_STORE_TEXT)).inRoot(RootMatchers.isPlatformPopup()).perform(click());
    }

    private void checkForKeyNotFoundDialog() {
        onView(withText(KeyValueStoreInspectorActivity.ERROR_DIALOG_TITLE)).check(matches(isDisplayed()));
        onView(withText(KeyValueStoreInspectorActivity.ERROR_DIALOG_MESSAGE)).check(matches(isDisplayed()));
    }
}
