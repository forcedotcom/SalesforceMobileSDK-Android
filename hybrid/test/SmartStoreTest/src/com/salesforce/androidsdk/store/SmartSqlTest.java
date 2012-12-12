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

import com.salesforce.androidsdk.store.SmartSqlHelper.SmartSqlException;
import com.salesforce.androidsdk.store.SmartStore.Type;

/**
 * Tests for "smart" sql
 *
 */
public class SmartSqlTest extends InstrumentationTestCase {

	private static final String EMPLOYEES_SOUP = "employees";
	private static final String DEPARTMENTS_SOUP = "departments";
	
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
		
		store.registerSoup(EMPLOYEES_SOUP, new IndexSpec[] {   // should be TABLE_1
				new IndexSpec("firstName", Type.string),       // should be TABLE_1_0
				new IndexSpec("lastName", Type.string),        // should be TABLE_1_1
				new IndexSpec("deptCode", Type.string),        // should be TABLE_1_2
				new IndexSpec("employeeId", Type.string),      // should be TABLE_1_3
				new IndexSpec("managerId", Type.string) });    // should be TABLE_1_4

		store.registerSoup(DEPARTMENTS_SOUP, new IndexSpec[] { // should be TABLE_2
				new IndexSpec("deptCode", Type.string),        // should be TABLE_2_0
				new IndexSpec("name", Type.string) });         // should be TABLE_2_1
	}
	
	@Override
	protected void tearDown() throws Exception {
		db.close();
		// Not cleaning up after the test to make diagnosing issues easier
		super.tearDown();
	}
	
	/**
	 * Testing simple smart sql to sql conversion
	 */
	public void testSimpleConvertSmartSql() {
		assertEquals("select TABLE_1_0, TABLE_1_1 from TABLE_1 order by TABLE_1_1", 
				store.convertSmartSql("select {employees:firstName}, {employees:lastName} from {employees} order by {employees:lastName}"));

		assertEquals("select TABLE_2_1 from TABLE_2 order by TABLE_2_0", 
				store.convertSmartSql("select {departments:name} from {departments} order by {departments:deptCode}"));
	
	}

	/**
	 * Testing smart sql to sql conversion when there is a join
	 */
	public void testConvertSmartSqlWithJoin() {
		assertEquals("select TABLE_2_1, TABLE_1_0 || ' ' || TABLE_1_1 "
					+ "from TABLE_1, TABLE_2 "
				    + "where TABLE_2_0 = TABLE_1_2 "
					+ "order by TABLE_2_1, TABLE_1_1",
				store.convertSmartSql("select {departments:name}, {employees:firstName} || ' ' || {employees:lastName} "
						+ "from {employees}, {departments} "
						+ "where {departments:deptCode} = {employees:deptCode} "
						+ "order by {departments:name}, {employees:lastName}"));
	
	}

	/**
	 * Testing smart sql to sql conversion when there is a self join
	 */
	public void testConvertSmartSqlWithSelfJoin() {
		assertEquals("select mgr.TABLE_1_1, e.TABLE_1_1 "
					+ "from TABLE_1 as mgr, TABLE_1 as e "
				    + "where mgr.TABLE_1_3 = e.TABLE_1_4",
				store.convertSmartSql("select mgr.{employees:lastName}, e.{employees:lastName} "
						+ "from {employees} as mgr, {employees} as e "
						+ "where mgr.{employees:employeeId} = e.{employees:managerId}"));
	}

	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate
	 */
	public void testConvertSmartSqlWithSpecialColumns() {
		assertEquals("select TABLE_1.id, TABLE_1.lastModified, TABLE_1.soup from TABLE_1", 
				store.convertSmartSql("select {employees:_soupEntryId}, {employees:_soupLastModifiedDate}, {employees:_soup} from {employees}"));
	}
	
	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate and there is a join
	 */
	public void testConvertSmartSqlWithSpecialColumnsAndJoin() {
		assertEquals("select TABLE_1.id, TABLE_2.id from TABLE_1, TABLE_2", 
				store.convertSmartSql("select {employees:_soupEntryId}, {departments:_soupEntryId} from {employees}, {departments}"));
	}

	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate and there is a join
	 */
	public void testConvertSmartSqlWithSpecialColumnsAndSelfJoin() {
		assertEquals("select mgr.id, e.id from TABLE_1 as mgr, TABLE_1 as e", 
				store.convertSmartSql("select mgr.{employees:_soupEntryId}, e.{employees:_soupEntryId} from {employees} as mgr, {employees} as e"));
	}
	
	/**
	 * Test smart sql to sql conversation with insert/update/delete: expect exception
	 */
	public void testConvertSmartSqlWithInsertUpdateDelete() {
		for (String smartSql : new String[] { "insert into {employees}", "update {employees}", "delete from {employees}"}) {
			try {
				store.convertSmartSql(smartSql);
				fail("Should have thrown exception for " + smartSql);
			}
			catch (SmartSqlException e) {
				// Expected
			}
		}
	}
}
