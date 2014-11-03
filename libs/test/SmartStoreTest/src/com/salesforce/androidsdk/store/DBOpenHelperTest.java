/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import net.sqlcipher.database.SQLiteDatabase;
import android.content.Context;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;

/**
 * Tests for obtaining and deleting databases via the DBOpenHelper.
 * 
 */
public class DBOpenHelperTest extends InstrumentationTestCase {

	// constants used for building a test user account
	private static final String TEST_USER_ID = "user123";
	private static final String TEST_ORG_ID = "org123";
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
	 * Make sure database name is correct for no account and communityId.
	 */
	public void testGetHelperForNullAccountNullCommunityId() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		SQLiteDatabase db = helper.getWritableDatabase("");

		String dbName = getBaseName(db);

		assertEquals("Database name is not correct.","somedb.db", dbName);
	}
	
	/**
	 * Make sure database name is correct for no account and communityId.
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

	public void testDeleteTests() {

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
	 * @param dbPath
	 *            The full path, including filename.
	 * @return Just the filename.
	 */
	private String getBaseName(SQLiteDatabase dbPath) {
		String[] pathParts = dbPath.getPath().split("/");
		return pathParts[pathParts.length - 1];
	}
}
