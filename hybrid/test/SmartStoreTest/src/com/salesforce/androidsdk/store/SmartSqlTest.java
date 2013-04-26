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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper.SmartSqlException;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * Tests for "smart" sql
 *
 */
public class SmartSqlTest extends SmartStoreTestCase {

	private static final String BUDGET = "budget";
	private static final String NAME = "name";
	private static final String SALARY = "salary";
	private static final String MANAGER_ID = "managerId";
	private static final String EMPLOYEE_ID = "employeeId";
	private static final String LAST_NAME = "lastName";
	private static final String FIRST_NAME = "firstName";
	private static final String DEPT_CODE = "deptCode";
	private static final String EMPLOYEES_SOUP = "employees";
	private static final String DEPARTMENTS_SOUP = "departments";

	@Override
	protected SQLiteDatabase getWritableDatabase() {
		return DBOpenHelper.getOpenHelper(targetContext).getWritableDatabase("");
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		
		store.registerSoup(EMPLOYEES_SOUP, new IndexSpec[] {   // should be TABLE_1
				new IndexSpec(FIRST_NAME, Type.string),        // should be TABLE_1_0
				new IndexSpec(LAST_NAME, Type.string),         // should be TABLE_1_1
				new IndexSpec(DEPT_CODE, Type.string),         // should be TABLE_1_2
				new IndexSpec(EMPLOYEE_ID, Type.string),       // should be TABLE_1_3
				new IndexSpec(MANAGER_ID, Type.string),        // should be TABLE_1_4
				new IndexSpec(SALARY, Type.integer) });        // should be TABLE_1_5

		
		store.registerSoup(DEPARTMENTS_SOUP, new IndexSpec[] { // should be TABLE_2
				new IndexSpec(DEPT_CODE, Type.string),         // should be TABLE_2_0
				new IndexSpec(NAME, Type.string),              // should be TABLE_2_1
				new IndexSpec(BUDGET, Type.integer) } );       // should be TABLE_2_2

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
	
	/**
	 * Test running smart query that does a select count
	 * @throws JSONException 
	 */
	public void testSmartQueryDoingCount() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select count(*) from {employees}", 1), 0);
		assertSameJSONArray("Wrong result", new JSONArray("[[7]]"), result);
	}
	
	/**
	 * Test running smart query that does a select sum
	 * @throws JSONException
	 */
	public void testSmartQueryDoingSum() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select sum({departments:budget}) from {departments}", 1), 0);
		assertSameJSONArray("Wrong result", new JSONArray("[[3000000]]"), result);
	}

	/**
	 * Test running smart query that return one row with one integer
	 * @throws JSONException
	 */
	public void testSmartQueryReturningOneRowWithOneInteger() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1), 0);
		assertSameJSONArray("Wrong result", new JSONArray("[[200000]]"), result);
	}
	
	/**
	 * Test running smart query that return one row with two integers
	 * @throws JSONException
	 */
	public void testSmartQueryReturningOneRowWithTwoIntegers() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select mgr.{employees:salary}, e.{employees:salary} from {employees} as mgr, {employees} as e where e.{employees:lastName} = 'Thompson'", 1), 0);
		assertSameJSONArray("Wrong result", new JSONArray("[[200000,120000]]"), result);
	}
	
	/**
	 * Test running smart query that return two rows with one integer each
	 * @throws JSONException
	 */
	public void testSmartQueryReturningTwoRowsWithOneIntegerEach() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:managerId} = '00010' order by {employees:firstName}", 2), 0);
		assertSameJSONArray("Wrong result", new JSONArray("[[120000],[100000]]"), result);
	}

	/**
	 * Test running smart query that return a soup along with a string and an integer
	 * @throws JSONException
	 */
	public void testSmartQueryReturningSoupStringAndInteger() throws JSONException {
		loadData();
		JSONObject christineJson = store.query(QuerySpec.buildExactQuerySpec(EMPLOYEES_SOUP, "employeeId", "00010", 1), 0).getJSONObject(0);
		assertEquals("Wrong elt", "Christine", christineJson.getString(FIRST_NAME));
		
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:_soup}, {employees:firstName}, {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1) , 0);
		assertEquals("Expected one row", 1, result.length());
		assertSameJSON("Wrong soup", christineJson, result.getJSONArray(0).getJSONObject(0));
		assertEquals("Wrong first name", "Christine", result.getJSONArray(0).getString(1));
		assertEquals("Wrong salary", 200000, result.getJSONArray(0).getInt(2));
	}
	
	/**
	 * Test running smart query with paging
	 * @throws JSONException
	 */
	public void testSmartQueryWithPaging() throws JSONException {
		loadData();
		QuerySpec query = QuerySpec.buildSmartQuerySpec("select {employees:firstName} from {employees} order by {employees:firstName}", 1);
		assertEquals("Expected 7 employees", 7, store.countQuery(query));
		
		String[] expectedResults = new String[] {"Christine", "Eileen", "Eva", "Irving", "John", "Michael", "Sally"};
		for (int i = 0; i<7; i++) {
			JSONArray result = store.query(query , i);
			assertSameJSONArray("Wrong result at page " + i, new JSONArray("[[" + expectedResults[i] + "]]"), result);
		}
	}

	/**
	 * Test running smart query that targets _soup, _soupEntryId and _soupLastModifiedDate
	 * @throws JSONException
	 */
	public void testSmartQueryWithSpecialFields() throws JSONException {
		loadData();

		JSONObject christineJson = store.query(QuerySpec.buildExactQuerySpec(EMPLOYEES_SOUP, "employeeId", "00010", 1), 0).getJSONObject(0);
		assertEquals("Wrong elt", "Christine", christineJson.getString(FIRST_NAME));
		
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:_soup}, {employees:_soupEntryId}, {employees:_soupLastModifiedDate}, {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1) , 0);
		assertEquals("Expected one row", 1, result.length());
		assertSameJSON("Wrong soup", christineJson, result.getJSONArray(0).getJSONObject(0));
		assertSameJSON("Wrong soupEntryId", christineJson.getString(SmartStore.SOUP_ENTRY_ID), result.getJSONArray(0).getInt(1));
		assertSameJSON("Wrong soupLastModifiedDate", christineJson.getString(SmartStore.SOUP_LAST_MODIFIED_DATE), result.getJSONArray(0).getLong(2));
	}

	
	/**
	 * Load some datq in the smart store
	 * @throws JSONException 
	 */
	private void loadData() throws JSONException {
		// Employees
		createEmployee("Christine", "Haas", "A00", "00010", null, 200000);
		createEmployee("Michael", "Thompson", "A00", "00020", "00010", 120000);
		createEmployee("Sally", "Kwan", "A00", "00310", "00010", 100000);
		createEmployee("John", "Geyer", "B00", "00040", null, 102000);
		createEmployee("Irving", "Stern", "B00", "00050", "00040", 100000);
		createEmployee("Eva", "Pulaski", "B00", "00060", "00050", 80000);
		createEmployee("Eileen", "Henderson", "B00", "00070", "00050", 70000);
		
		// Departments
		createDepartment("A00", "Sales", 1000000);
		createDepartment("B00", "R&D", 2000000);
	}

	private void createEmployee(String firstName, String lastName, String deptCode, String employeeId, String managerId, int salary) throws JSONException {
		JSONObject employee = new JSONObject();
		employee.put(FIRST_NAME, firstName);
		employee.put(LAST_NAME, lastName);
		employee.put(DEPT_CODE, deptCode);
		employee.put(EMPLOYEE_ID, employeeId);
		employee.put(MANAGER_ID, managerId);
		employee.put(SALARY, salary);
        store.create(EMPLOYEES_SOUP, employee);
		
	}
	
	private void createDepartment(String deptCode, String name, int budget) throws JSONException {
		JSONObject department = new JSONObject();
		department.put(DEPT_CODE, deptCode);
		department.put(NAME, name);
		department.put(BUDGET, budget);
        store.create(DEPARTMENTS_SOUP, department);
	}

}
