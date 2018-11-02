/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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

import android.database.Cursor;
import androidx.test.filters.MediumTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for encrypted smart store with external storage
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class SmartStoreExternalStorageTest extends SmartStoreTest {

	@Override
	protected String getEncryptionKey() {
		return Encryptor.hash("test123", "hashing-key");
	}

	@Override
	protected void registerSoup(SmartStore store, String soupName, IndexSpec[] indexSpecs) {
		store.registerSoupWithSpec(new SoupSpec(soupName, SoupSpec.FEATURE_EXTERNAL_STORAGE), indexSpecs);
	}

	@Override
	protected void assertSameSoupAsDB(JSONObject soup, Cursor c, String soupTableName, Long id) throws JSONException {
		JSONTestHelper.assertSameJSON("Wrong value in external storage", soup, ((DBOpenHelper) dbOpenHelper).loadSoupBlob(soupTableName, id, getEncryptionKey()));
	}

	/**
	 * Ensure that a soup cannot be using external storage and JSON1
	 */
    @Test
	public void testRegisterSoupWithExternalStorageAndJSON1() {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP));
		try {
			registerSoup(store, OTHER_TEST_SOUP, new IndexSpec[]{new IndexSpec("lastName", Type.json1), new IndexSpec("address.city", Type.string)});
            Assert.fail("Registering soup with external storage and json1 should have thrown an exception");
		}
		catch (SmartStore.SmartStoreException e) {
            Assert.assertEquals("Wrong exception", "Can't have JSON1 index specs in externally stored soup:" + OTHER_TEST_SOUP, e.getMessage());
		}
        Assert.assertFalse("Register soup call should have failed", store.hasSoup(OTHER_TEST_SOUP));
	}

	/**
	 * Ensure data is still accessible after changing key
	 */
    @Test
	public void testChangeKey() throws JSONException {
		JSONObject soupElt = new JSONObject("{'key':'ka2', 'value':'testValue'}");
		String newPasscode = Encryptor.hash("123test", "hashing-key");

		// Use normal key to place files on external storage
		store.create(TEST_SOUP, soupElt);

		// Act
		final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getEncryptionKey());
		SmartStore.changeKey(db, getEncryptionKey(), newPasscode);
		store = new SmartStore(dbOpenHelper, newPasscode);

		// Verify that data is still accessible
		JSONArray result = store.query(QuerySpec.buildExactQuerySpec(TEST_SOUP, "key", "ka2", null, null, 10), 0);
        Assert.assertEquals("One result expected", 1, result.length());
		JSONTestHelper.assertSameJSON("Wrong result for query", soupElt, result.getJSONObject(0));
	}

	/**
	 * Test for getDatabaseSize
	 *
	 * @throws JSONException
	 */
	@Override
    @Test
	public void testGetDatabaseSize() throws JSONException {

	    // Get initial values
		int totalSizeBefore = store.getDatabaseSize();
		int dBFileSizeBefore = (int) (new File(dbOpenHelper.getWritableDatabase(getEncryptionKey()).getPath()).length());
		int dbBlobsDirSizeBefore = totalSizeBefore - dBFileSizeBefore;

		// Populate db with several entries
		for (int i = 0; i < 100; i++) {
			JSONObject soupElt = new JSONObject("{'key':'abcd" + i + "', 'value':'va" + i + "', 'otherValue':'ova" + i + "'}");
			store.create(TEST_SOUP, soupElt);
		}

		// Check new values
		int totalSizeAfter = store.getDatabaseSize();
		int dbFileSizeAfter = (int) (new File(dbOpenHelper.getWritableDatabase(getEncryptionKey()).getPath()).length());
		int dbBlobsDirSizeAfter = totalSizeAfter - dbFileSizeAfter;
        Assert.assertTrue("Database file should be larger", dbFileSizeAfter > dBFileSizeBefore);
        Assert.assertTrue("Soup blobs directory should be larger", dbBlobsDirSizeAfter > dbBlobsDirSizeBefore);
        Assert.assertTrue("Total database size should be larger than just db file", totalSizeAfter > totalSizeBefore);
	}

	@Override
    @Test
	public void testAggregateQueryOnJSON1IndexedField() throws JSONException {
		// json1 is not compatible with external storage.
	}

	@Override
    @Test
	public void testCountQueryWithGroupByUsingJSON1Indexes() throws JSONException {
		// json1 is not compatible with external storage.
	}

	@Override
    @Test
	public void testUpsertWithNullInJSON1IndexedField() throws JSONException {
		// json1 is not compatible with external storage.
	}

	@Override
    @Test
	public void testSelectWithNullInJSON1IndexedField() throws JSONException {
		// json1 is not compatible with external storage.
	}

	@Override
    @Test
	public void testDeleteAgainstChangedSoup() throws JSONException {

		//create a new soup with multiple entries
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		JSONObject soupElt4 = new JSONObject("{'key':'ka4', 'value':'va4'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		JSONObject soupElt4Created = store.create(TEST_SOUP, soupElt4);

		//CASE 1: index spec from key to value
		tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt2Created, "value",
										   new IndexSpec[]{new IndexSpec("value", Type.string)},
										   soupElt1Created, soupElt3Created, soupElt4Created);
	}

	@Override
	protected void tryAllQueryOnChangedSoupWithUpdate(String soupName, JSONObject deletedEntry, String orderPath,
			IndexSpec[] newIndexSpecs, JSONObject... expectedResults) throws JSONException {

		//alert the soup
		store.alterSoup(soupName, new SoupSpec(soupName, SoupSpec.FEATURE_EXTERNAL_STORAGE), newIndexSpecs, true);

		//delete an entry
		store.delete(soupName, idOf(deletedEntry));

		// Query all - small page
		runQueryCheckResultsAndExplainPlan(soupName,
										   QuerySpec.buildAllQuerySpec(soupName, orderPath, Order.ascending, 5),
										   0, true, "SCAN", expectedResults);
	}

	@Override
    @Test
	public void testExactQueryAgainstChangedSoup() throws JSONException {

		//create a new soup with multiple entries
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka1-', 'value':'va1*'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka1 ', 'value':'va1%'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);

		//CASE 1: index spec from key to value
		tryExactQueryOnChangedSoup(TEST_SOUP, "value", "va1",
								   new IndexSpec[]{new IndexSpec("value", Type.string)},
								   soupElt1Created);
	}

	@Override
	protected void tryExactQueryOnChangedSoup(String soupName, String orderPath, String value,
			IndexSpec[] newIndexSpecs, JSONObject expectedResult) throws JSONException {

	    //alert the soup
		store.alterSoup(soupName, new SoupSpec(soupName, SoupSpec.FEATURE_EXTERNAL_STORAGE), newIndexSpecs, true);

		// Exact Query
		runQueryCheckResultsAndExplainPlan(soupName,
										   QuerySpec.buildExactQuerySpec(soupName, orderPath, value, null, null, 5),
										   0, true, "SEARCH", expectedResult);
	}

	@Override
    @Test
	public void testUpsertAgainstChangedSoup() throws JSONException {
		//create a new soup with multiple entries
		JSONObject soupElt1 = new JSONObject("{'key':'ka1', 'value':'va1'}");
		JSONObject soupElt2 = new JSONObject("{'key':'ka2', 'value':'va2'}");
		JSONObject soupElt3 = new JSONObject("{'key':'ka3', 'value':'va3'}");
		JSONObject soupElt1Created = store.create(TEST_SOUP, soupElt1);
		JSONObject soupElt2Created = store.create(TEST_SOUP, soupElt2);
		JSONObject soupElt3Created = store.create(TEST_SOUP, soupElt3);
		JSONObject soupElt1ForUpsert = new JSONObject("{'key':'ka1u', 'value':'va1u'}");
		JSONObject soupElt2ForUpsert = new JSONObject("{'key':'ka2u', 'value':'va2u'}");
		JSONObject soupElt3ForUpsert = new JSONObject("{'key':'ka3u', 'value':'va3u'}");

		//CASE 1: index spec from key to value
		store.alterSoup(TEST_SOUP, new SoupSpec(TEST_SOUP, SoupSpec.FEATURE_EXTERNAL_STORAGE), new IndexSpec[]{new IndexSpec("value", Type.string)}, true);

		//upsert an entry
		JSONObject soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1ForUpsert);

		// Query all - small page
		runQueryCheckResultsAndExplainPlan(TEST_SOUP,
										   QuerySpec.buildAllQuerySpec(TEST_SOUP, "value", Order.ascending, 10),
										   0, true, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt3Created);
	}

    @Override
    @Test
    public void testDeleteByQuery() throws JSONException {
        List<Long> idsDeleted = new ArrayList<>();
        List<Long> idsNotDeleted = new ArrayList<>();
        tryDeleteByQuery(idsDeleted, idsNotDeleted);

        // Check file system
        checkFileSystem(TEST_SOUP, listToArray(idsDeleted), false);
        checkFileSystem(TEST_SOUP, listToArray(idsNotDeleted), true);
    }

	private long[] listToArray(List<Long> list) {
        long[] primitiveArray = new long[0];
        if (list == null) {
            return primitiveArray;
        }
		final Object[] objArray = list.toArray();
		primitiveArray = new long[objArray.length];
		for (int i = 0; i < objArray.length; i++) {
			if (objArray[i] != null) {
				primitiveArray[i] = ((Long) objArray[i]).longValue();
			}
		}
		return primitiveArray;
	}
}
