/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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

import java.io.File;
import java.util.Map;
import java.util.Set;

import net.sqlcipher.database.SQLiteDatabase;
import android.content.Context;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;

/**
 * Tests for obtaining and deleting databases via the DBOpenHelper.
 */
public class DBOpenHelperTest extends InstrumentationTestCase {

	// constants used for building a test user account
	private static final String TEST_USER_ID = "005123";
	private static final String TEST_ORG_ID = "00D123";
	private static final String TEST_COMMUNITY_ID = "cid123";

	private Context targetContext;

	@Override
	protected void setUp() throws Exception {
		this.targetContext = getInstrumentation().getTargetContext();
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Make sure database name is correct for no account and no communityId.
	 */
	public void testGetHelperForNullAccountNullCommunityId() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		assertEquals("Database name is not correct.","somedb.db", dbName);
	}

	/**
	 * Make sure database name is correct for account without a communityId.
	 */
	public void testGetHelperForAccountNullCommunityId() {
		UserAccount testAcct = getTestUserAccount();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", testAcct, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		assertTrue("Database name does not contain org id.",dbName.contains(TEST_ORG_ID));
		assertTrue("Database name does not contain user id.",dbName.contains(TEST_USER_ID));
		assertTrue("Database name does not have default internal community id.",dbName.contains(UserAccount.INTERNAL_COMMUNITY_PATH));
	}

	/**
	 * Ensure that helpers are cached.
	 */
	public void testGetHelperIsCached() {
		DBOpenHelper helper1 = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		assertSame("Helpers should be cached.", helper1, helper2);
	}

	/**
	 * When a database name is not given, the default name should be used.
	 */
	public void testGetHelperUsesDefaultDatabaseName() {
		UserAccount testAcct = getTestUserAccount();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		assertTrue("Database name is not correct.", dbName.startsWith(DBOpenHelper.DEFAULT_DB_NAME));
		assertTrue("Database name does not contain org id.",dbName.contains(TEST_ORG_ID));
		assertTrue("Database name does not contain user id.",dbName.contains(TEST_USER_ID));
		assertTrue("Database name does not have default internal community id.",dbName.contains(TEST_COMMUNITY_ID));
	}

	/**
	 * Ensures the default database is removed correctly.
	 */
	public void testDeleteDatabaseDefault() {

		// create db
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		DBOpenHelper.deleteDatabase(targetContext, null);
		assertFalse("Database should not exist.", databaseExists(targetContext, dbName));
	}

	/**
	 * Ensures all user databases are removed correctly, and the
	 * global databases are retained.
	 */
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
		assertFalse("Database should have been deleted.",
				databaseExists(targetContext, dbName1));
		assertFalse("Database should have been deleted.",
				databaseExists(targetContext, dbName2));
		assertTrue("Database should not have been deleted.",
				databaseExists(targetContext, dbName3));

		// 1st and 2nd helpers should not be cached, but 3rd should still be cached.
		final Map<String, DBOpenHelper> helpers = DBOpenHelper.getOpenHelpers();
		assertNotNull("List of helpers should not be null.", helpers);
		final Set<String> dbNames = helpers.keySet();
		assertNotNull("List of DB names should not be null.", dbNames);
		assertFalse("User database should not be cached.", dbNames.contains(dbName1));
		assertFalse("User database should not be cached.", dbNames.contains(dbName2));
		assertTrue("Global database should still be cached.", dbNames.contains(dbName3));
	}

	/**
	 * Ensures the database is removed from the cache.
	 */
	public void testDeleteDatabaseRemovesFromCache() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, null);
		DBOpenHelper.deleteDatabase(targetContext, null);
		DBOpenHelper helperPostDelete = DBOpenHelper.getOpenHelper(targetContext, null);
		assertNotSame("Helpers should be different instances.", helper, helperPostDelete);
	}

	/**
	 * Ensure that only the single community-specific database is deleted.
	 */
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
		assertTrue("Database should not have been deleted.", databaseExists(targetContext, dontDeleteDbName));
		
		// 1st helper should not be cached, but 2nd should still be cached
		DBOpenHelper helperNew = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		assertNotSame("Helper should have been removed from cache.", helper, helperNew);
		DBOpenHelper dontDeleteHelperCached = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		assertSame("Helper should be same instance.", dontDeleteHelper, dontDeleteHelperCached);
	}

	/**
	 * Ensure that all databases related to the account are removed when community id is not specified.
	 */
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
		DBOpenHelper.deleteDatabase(targetContext,  testAcct);
		
		assertFalse("Database should not exist.", databaseExists(targetContext, dbName));
		assertFalse("Database should not exist.", databaseExists(targetContext, dbName2));
		
		// also make sure references to the helpers no longer exist
		DBOpenHelper helperNew = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		DBOpenHelper helperNew2 = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		assertNotSame("Helper should have been removed from cache.", helper, helperNew);
		assertNotSame("Helper should have been removed from cache.", helper2, helperNew2);
	}

	/**
	 * Has smart store for given account returns true.
	 */
	public void testHasSmartStoreIsTrueForDefaultDatabase() {
		UserAccount testAcct = getTestUserAccount(); 
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct);
		helper.getWritableDatabase("");
		assertTrue("SmartStore for account should exist.",
				DBOpenHelper.smartStoreExists(targetContext, testAcct, null));
	}

	/**
	 * Has smart store for given account returns false.
	 */
	public void testHasSmartStoreIsFalseForDefaultDatabase() {
		assertFalse("SmartStore for account should not exist.",
				DBOpenHelper.smartStoreExists(targetContext, null, null));
	}

	/**
	 * Has smart store for specified database and account returns true.
	 */
	public void testHasSmartStoreIsTrueForSpecifiedDatabase() {
		UserAccount testAcct = getTestUserAccount(); 
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "testdb", testAcct, null);
		helper.getWritableDatabase("");
		assertTrue("SmartStore for account should exist.",
				DBOpenHelper.smartStoreExists(targetContext, "testdb", testAcct, null));
	}

	/**
	 * Has smart store for given account returns false.
	 */
	public void testHasSmartStoreIsFalseForSpecifiedDatabase() {
		assertFalse("SmartStore for account should not exist.",
				DBOpenHelper.smartStoreExists(targetContext, "dbdne", null, null));
	}

	/**
	 * Determines if the given database file exists or not in the database directory.
	 *
	 * @param dbName The database name.
	 * @return
	 */
	private boolean databaseExists(Context ctx, String dbName) {
		final String dbPath = ctx.getApplicationInfo().dataDir + "/databases/"  + dbName;
    	final File file = new File(dbPath);
		return file.exists();
	}

	/**
	 * Builds a user account we can use for test cases.
	 *
	 * @return A bare-bones user account.
	 */
	private UserAccount getTestUserAccount() {
		Bundle bundle = new Bundle();
		bundle.putString(UserAccount.USER_ID, TEST_USER_ID);
		bundle.putString(UserAccount.ORG_ID, TEST_ORG_ID);
		return new UserAccount(bundle);
	}

	/**
	 * Obtain the base filename from a given path.
	 *
	 * @param db The full path, including filename.
	 * @return Just the filename.
	 */
	private String getBaseName(SQLiteDatabase db) {
		final String[] pathParts = db.getPath().split("/");
		return pathParts[pathParts.length - 1];
	}
}
