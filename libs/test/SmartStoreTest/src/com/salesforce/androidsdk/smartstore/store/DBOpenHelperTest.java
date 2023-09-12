/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.analytics.security.Encryptor;

import net.sqlcipher.database.SQLiteDatabase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Tests for obtaining and deleting databases via the DBOpenHelper.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DBOpenHelperTest {

	// constants used for building a test user account
	private static final String TEST_USER_ID = "005123";
	private static final String TEST_ORG_ID = "00D123";
	private static final String TEST_COMMUNITY_ID = "cid123";

	private Context targetContext;
	private static final String TEST_SOUP = "test_soup";
	private static final String TEST_SOUP_2 = "test_soup_2";
	private static final String TEST_DB = "test_db";
	private static final String PASSCODE = Encryptor.hash("test_key", "hashing-key");

	@Before
	public void setUp() {
		this.targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
		EventBuilderHelper.enableDisable(false);
	}

	@After
	public void tearDown() {
        final String dbPath = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationInfo().dataDir + "/databases";
        final File fileDir = new File(dbPath);
        DBOpenHelper.deleteAllUserDatabases(InstrumentationRegistry.getInstrumentation().getTargetContext());
        DBOpenHelper.deleteDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        DBOpenHelper.removeAllFiles(fileDir);
    }

	/**
	 * Make sure database name is correct for no account and no communityId.
	 */
    @Test
	public void testGetHelperForNullAccountNullCommunityId() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
        Assert.assertEquals("Database name is not correct.","somedb.db", dbName);
	}

	/**
	 * Make sure database name is correct for account without a communityId.
	 */
    @Test
	public void testGetHelperForAccountNullCommunityId() {
		UserAccount testAcct = getTestUserAccount();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", testAcct, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
        Assert.assertTrue("Database name does not contain org id.",dbName.contains(TEST_ORG_ID));
        Assert.assertTrue("Database name does not contain user id.",dbName.contains(TEST_USER_ID));
        Assert.assertTrue("Database name does not have default internal community id.",dbName.contains(UserAccount.INTERNAL_COMMUNITY_PATH));
	}

	/**
	 * Ensure that helpers are cached.
	 */
    @Test
	public void testGetHelperIsCached() {
		DBOpenHelper helper1 = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
        Assert.assertSame("Helpers should be cached.", helper1, helper2);
	}

	/**
	 * When a database name is not given, the default name should be used.
	 */
    @Test
	public void testGetHelperUsesDefaultDatabaseName() {
		UserAccount testAcct = getTestUserAccount();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
        Assert.assertTrue("Database name is not correct.", dbName.startsWith(DBOpenHelper.DEFAULT_DB_NAME));
        Assert.assertTrue("Database name does not contain org id.",dbName.contains(TEST_ORG_ID));
        Assert.assertTrue("Database name does not contain user id.",dbName.contains(TEST_USER_ID));
        Assert.assertTrue("Database name does not have default internal community id.",dbName.contains(TEST_COMMUNITY_ID));
	}

	/**
	 * Ensures the default database is removed correctly.
	 */
    @Test
	public void testDeleteDatabaseDefault() {

		// create db
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		DBOpenHelper.deleteDatabase(targetContext, null);
        Assert.assertFalse("Database should not exist.", databaseExists(targetContext, dbName));
	}

	/**
	 * Ensures all user databases are removed correctly, and the
	 * global databases are retained.
	 */
    @Test
	public void testDeleteAllUserDatabases() {
		final UserAccount testAcct = getTestUserAccount();

		// Create a user database we want to ensure is deleted.
		final DBOpenHelper helper1 = DBOpenHelper.getOpenHelper(targetContext,
				testAcct, TEST_COMMUNITY_ID);
		final SQLiteDatabase db1 = helper1.getWritableDatabase("");
		final String dbName1 = getBaseName(db1);

		// Create another user database we want to ensure is deleted.
		final DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext,
				testAcct, "other_community_id");
		final SQLiteDatabase db2 = helper2.getWritableDatabase("");
		final String dbName2 = getBaseName(db2);

		// Create a global database we want to ensure is not deleted.
		final DBOpenHelper helper3 = DBOpenHelper.getOpenHelper(targetContext, null);
		final SQLiteDatabase db3 = helper3.getWritableDatabase("");
		final String dbName3 = getBaseName(db3);

		// Delete all user databases.
		DBOpenHelper.deleteAllUserDatabases(targetContext);
        Assert.assertFalse("Database should have been deleted.",
				databaseExists(targetContext, dbName1));
        Assert.assertFalse("Database should have been deleted.",
				databaseExists(targetContext, dbName2));
        Assert.assertTrue("Database should not have been deleted.",
				databaseExists(targetContext, dbName3));

		// 1st and 2nd helpers should not be cached, but 3rd should still be cached.
		final Map<String, DBOpenHelper> helpers = DBOpenHelper.getOpenHelpers();
        Assert.assertNotNull("List of helpers should not be null.", helpers);
		final Set<String> dbNames = helpers.keySet();
        Assert.assertNotNull("List of DB names should not be null.", dbNames);
        Assert.assertFalse("User database should not be cached.", dbNames.contains(dbName1));
        Assert.assertFalse("User database should not be cached.", dbNames.contains(dbName2));
        Assert.assertTrue("Global database should still be cached.", dbNames.contains(dbName3));
	}

	/**
	 * Ensures the database is removed from the cache.
	 */
    @Test
	public void testDeleteDatabaseRemovesFromCache() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, null);
		DBOpenHelper.deleteDatabase(targetContext, null);
		DBOpenHelper helperPostDelete = DBOpenHelper.getOpenHelper(targetContext, null);
        Assert.assertNotSame("Helpers should be different instances.", helper, helperPostDelete);
	}

	/**
	 * Ensure that only the single community-specific database is deleted.
	 */
    @Test
	public void testDeleteDatabaseWithCommunityId() {
		UserAccount testAcct = getTestUserAccount();

		// create the database we want to ensure is deleted
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		helper.getWritableDatabase("");
		
		// create another database for this account which should not be deleted.
		DBOpenHelper dontDeleteHelper = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		SQLiteDatabase dontDeleteDb = dontDeleteHelper.getWritableDatabase("");
		String dontDeleteDbName = getBaseName(dontDeleteDb);

		// now, delete the first database and make sure we didn't remove the second.
		DBOpenHelper.deleteDatabase(targetContext,  testAcct, TEST_COMMUNITY_ID);
        Assert.assertTrue("Database should not have been deleted.", databaseExists(targetContext, dontDeleteDbName));
		
		// 1st helper should not be cached, but 2nd should still be cached
		DBOpenHelper helperNew = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
        Assert.assertNotSame("Helper should have been removed from cache.", helper, helperNew);
		DBOpenHelper dontDeleteHelperCached = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
        Assert.assertSame("Helper should be same instance.", dontDeleteHelper, dontDeleteHelperCached);
	}

	/**
	 * Ensure that all databases related to the account are removed when community id is not specified.
	 */
    @Test
	public void testDeleteDatabaseWithoutCommunityId() {
		UserAccount testAcct = getTestUserAccount();

		// create the database we want to ensure is deleted
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		
		// create another database for this account which should not be deleted.
		DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		SQLiteDatabase db2 = helper2.getWritableDatabase("");
		String dbName2 = getBaseName(db2);

		// now, delete all databases related to accounts and ensure they no longer exist
		DBOpenHelper.deleteDatabase(targetContext, testAcct);
        Assert.assertFalse("Database should not exist.", databaseExists(targetContext, dbName));
        Assert.assertFalse("Database should not exist.", databaseExists(targetContext, dbName2));
		
		// also make sure references to the helpers no longer exist
		DBOpenHelper helperNew = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		DBOpenHelper helperNew2 = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
        Assert.assertNotSame("Helper should have been removed from cache.", helper, helperNew);
        Assert.assertNotSame("Helper should have been removed from cache.", helper2, helperNew2);
	}

	/**
	 * Has smart store for given account returns true.
	 */
    @Test
	public void testHasSmartStoreIsTrueForDefaultDatabase() {
		UserAccount testAcct = getTestUserAccount(); 
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct);
		helper.getWritableDatabase("");
        Assert.assertTrue("SmartStore for account should exist.",
				DBOpenHelper.smartStoreExists(targetContext, testAcct, null));
	}

	/**
	 * Has smart store for given account returns false.
	 */
    @Test
	public void testHasSmartStoreIsFalseForDefaultDatabase() {
        Assert.assertFalse("SmartStore for account should not exist.",
				DBOpenHelper.smartStoreExists(targetContext, null, null));
	}

	/**
	 * Has smart store for specified database and account returns true.
	 */
    @Test
	public void testHasSmartStoreIsTrueForSpecifiedDatabase() {
		UserAccount testAcct = getTestUserAccount(); 
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "testdb", testAcct, null);
		helper.getWritableDatabase("");
        Assert.assertTrue("SmartStore for account should exist.",
				DBOpenHelper.smartStoreExists(targetContext, "testdb", testAcct, null));
	}

	/**
	 * Has smart store for given account returns false.
	 */
    @Test
	public void testHasSmartStoreIsFalseForSpecifiedDatabase() {
        Assert.assertFalse("SmartStore for account should not exist.",
				DBOpenHelper.smartStoreExists(targetContext, "dbdne", null, null));
	}

	private boolean databaseExists(Context ctx, String dbName) {
		final String dbPath = ctx.getApplicationInfo().dataDir + "/databases/"  + dbName;
    	final File file = new File(dbPath);
		return file.exists();
	}

	private UserAccount getTestUserAccount() {
		Bundle bundle = new Bundle();
		bundle.putString(UserAccount.USER_ID, TEST_USER_ID);
		bundle.putString(UserAccount.ORG_ID, TEST_ORG_ID);
		return new UserAccount(bundle);
	}

	private String getBaseName(SQLiteDatabase db) {
		final String[] pathParts = db.getPath().split("/");
		return pathParts[pathParts.length - 1];
	}
}
