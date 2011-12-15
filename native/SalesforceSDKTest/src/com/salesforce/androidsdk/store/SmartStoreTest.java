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
package com.salesforce.androidsdk.store;

import com.salesforce.androidsdk.store.SmartStore.IndexSpec;
import com.salesforce.androidsdk.store.SmartStore.Type;

import android.content.Context;
import android.database.Cursor;
import android.test.InstrumentationTestCase;

/**
 * Tests for SmartStore
 *
 */
public class SmartStoreTest extends InstrumentationTestCase {

	private static final String TEST_SOUP = "test_soup";
	
	private Context targetContext;
	private LoggingSQLiteDatabase rdb;
	private SmartStore store;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		rdb = DBOperations.getReadableDatabase("SmartStoreTest", targetContext);
		store = new SmartStore();
	}
	
	/**
	 * Check that the meta data table (soup index map) has been created
	 */
	public void testMetaDataTableCreated() {
		assertTrue("Table soup_index_map not found", hasTable("soup_index_map"));
	}
	
	/**
	 * Call register soup and check that a table is created for the soup
	 * Then call drop soup and make sure that the table created for the soup is dropped
	 */
	public void testRegisterSoupDropSoup() {
		try {
			assertFalse("Table test_soup should not exist", hasTable(TEST_SOUP));
			store.registerSoup(targetContext, TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.TEXT)});
			assertTrue("Table test_soup not found", hasTable(TEST_SOUP));
		}
		finally {
			store.dropSoup(targetContext, TEST_SOUP);
			assertFalse("Table test_soup should not exist anymore", hasTable(TEST_SOUP));
		}
	}
	
	private boolean hasTable(String tableName) {
		Cursor c = rdb.query("sqlite_master", null, "type = 'table' and name = '" + tableName + "'");
		return c.getCount() == 1;
	}
}