/*
 * Copyright (c) 2012, salesforce.com, inc.
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
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.store.SmartStore.IndexSpec;
import com.salesforce.androidsdk.store.SmartStore.Type;

/**
 * Tests for "smart" sql
 *
 */
public class SmartSqlTest extends InstrumentationTestCase {

	private static final String TEST_SOUP = "test_soup";
//	private static final String OTHER_TEST_SOUP = "other_test_soup";
//	private static final String THIRD_TEST_SOUP = "third_test_soup";
	
	protected Context targetContext;
	private SQLiteDatabase db;
	private SmartStore store;
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		DBHelper.INSTANCE.reset(targetContext); // start clean
		db = DBOpenHelper.getOpenHelper(targetContext).getWritableDatabase("");
		store = new SmartStore(db);
		
		store.registerSoup(TEST_SOUP, new IndexSpec[] {new IndexSpec("key", Type.string)});
	}
	
	@Override
	protected void tearDown() throws Exception {
		db.close();
		// Not cleaning up after the test to make diagnosing issues easier
		super.tearDown();
	}
	
	/**
	 * Testing smart sql to sql conversion
	 */
	public void testConvertSimpleSql() {
		assertEquals("select TABLE_1_0 from TABLE_1 order by TABLE_1_0", store.convertSmartSql("select {test_soup:key} from {test_soup} order by {test_soup:key}"));
	}
}
