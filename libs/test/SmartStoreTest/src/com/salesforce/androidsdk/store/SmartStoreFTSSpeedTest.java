/*
 * Copyright (c) 2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.store;

import java.util.ArrayList;
import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * Set of tests for the smart store full-text-search
 */
public class SmartStoreFTSSpeedTest extends InstrumentationTestCase {

    public static final String TAG = "SmartStoreFTSSpeedTest";

    public static final String[] ANIMALS = new String[]{"alligator", "ant", "bear", "bee", "bird", "camel", "cat",
            "cheetah", "chicken", "chimpanzee", "cow", "crocodile", "deer", "dog", "dolphin",
            "duck", "eagle", "elephant", "fish", "fly", "fox", "frog", "giraffe", "goat",
            "goldfish", "hamster", "hippopotamus", "horse", "kangaroo", "kitten", "lion",
            "lobster", "monkey", "octopus", "owl", "panda", "pig", "puppy", "rabbit", "rat",
            "scorpion", "seal", "shark", "sheep", "snail", "snake", "spider", "squirrel",
            "tiger", "turtle", "wolf", "zebra"};

    public static final String ANIMALS_SOUP = "animals";
    public static final String TEXT_COL = "text";

    protected Context targetContext;
    private SmartStore store;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        final SQLiteOpenHelper dbOpenHelper = DBOpenHelper.getOpenHelper(targetContext, null);
        DBHelper.getInstance(dbOpenHelper.getWritableDatabase(getPasscode())).reset(targetContext, null);
        store = new SmartStore(dbOpenHelper, getPasscode());
        store.dropAllSoups();
    }

    protected String getPasscode() {
        return "";
    }

    @Override
    protected void tearDown() throws Exception {
        final SQLiteDatabase db = DBOpenHelper.getOpenHelper(targetContext, null).getWritableDatabase(getPasscode());
        db.close();
        super.tearDown();
    }

    public void testSearch1000() throws JSONException {
        trySearch(1000, Type.string);
        store.dropAllSoups();
        trySearch(1000, Type.full_text);
    }

    private long setupData(int rowsPerAnimal, Type textFieldType) throws JSONException {
        long start = System.nanoTime();
        store.registerSoup(ANIMALS_SOUP, new IndexSpec[]{new IndexSpec(TEXT_COL, textFieldType)});
        store.beginTransaction();
        for (int i = 0; i < 26*rowsPerAnimal; i++) {
            StringBuilder text = new StringBuilder();
            int charToMatch = (i%26) + 'a';
            for (String animal : ANIMALS) {
                if (animal.charAt(0) == charToMatch) {
                    text.append(animal).append(" ");
                }
            }
            JSONObject elt =  new JSONObject();
            elt.put(TEXT_COL, text.toString());
            store.create(ANIMALS_SOUP, elt, false);
        }
        store.endTransaction();
        return System.nanoTime() - start;
    }

    private void trySearch(int rowsPerAnimal, Type textFieldType) throws JSONException {
        long setupTime = setupData(rowsPerAnimal, textFieldType);

        long totalQueryTime = 0;
        for (String animal : ANIMALS) {
            QuerySpec querySpec = textFieldType == Type.full_text
                    ? QuerySpec.buildMatchQuerySpec(ANIMALS_SOUP, TEXT_COL, animal, null, null, rowsPerAnimal)
                    : QuerySpec.buildLikeQuerySpec(ANIMALS_SOUP, TEXT_COL, "%" + animal + "%", null, null, rowsPerAnimal);
            long start = System.nanoTime();
            JSONArray results = store.query(querySpec, 0);
            totalQueryTime += System.nanoTime() - start;
            validateResults(results, animal, rowsPerAnimal);
        }

        Log.i(TAG, String.format("\nSearch Speed Test\nRows %d\nSetup time %.2f ms\nQuery time %.2f ms\n",
                rowsPerAnimal*26,
                setupTime/1000000.0, (1.0*totalQueryTime)/ANIMALS.length));
    }

    private void validateResults(JSONArray results, String animal, int rowsPerAnimal) throws JSONException {
        assertEquals("Wrong number of results", rowsPerAnimal, results.length());
        for (int i=0; i<results.length(); i++) {
            String text = results.getJSONObject(i).getString(TEXT_COL);
            assertTrue("Invalid result [" + text + "] for search on [" + animal + "]", text.contains(animal));
        }
    }

}
