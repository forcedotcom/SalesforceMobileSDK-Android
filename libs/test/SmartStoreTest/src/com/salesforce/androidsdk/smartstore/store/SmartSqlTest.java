/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper.SmartSqlException;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.util.JSONTestHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for "smart" sql
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
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
	private static final String EDUCATION = "education";
	private static final String IS_MANAGER = "isManager";
	private static final String BUILDING = "building";

	@Override
	protected String getEncryptionKey() {
		return "";
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();
		store.registerSoup(EMPLOYEES_SOUP, new IndexSpec[] {   // should be TABLE_1
				new IndexSpec(FIRST_NAME, Type.string),        // should be TABLE_1_0
				new IndexSpec(LAST_NAME, Type.string),         // should be TABLE_1_1
				new IndexSpec(DEPT_CODE, Type.string),         // should be TABLE_1_2
				new IndexSpec(EMPLOYEE_ID, Type.string),       // should be TABLE_1_3
				new IndexSpec(MANAGER_ID, Type.string),        // should be TABLE_1_4
				new IndexSpec(SALARY, Type.integer),           // should be TABLE_1_5
				new IndexSpec(EDUCATION, Type.json1),          // should be json_extract(soup, '$.education')
				new IndexSpec(IS_MANAGER, Type.json1)});       // should be json_extract(soup, '$.isManager')
		store.registerSoup(DEPARTMENTS_SOUP, new IndexSpec[] { // should be TABLE_2
				new IndexSpec(DEPT_CODE, Type.string),         // should be TABLE_2_0
				new IndexSpec(NAME, Type.string),              // should be TABLE_2_1
				new IndexSpec(BUDGET, Type.integer),           // should be TABLE_2_2
				new IndexSpec(BUILDING, Type.json1)});         // should be json_extract(soup, '$.building')
	}

	@After
    public void tearDown() throws Exception {
	    super.tearDown();
    }

	/**
	 * Testing simple smart sql to sql conversion
	 */
    @Test
	public void testSimpleConvertSmartSql() {
        Assert.assertEquals("select TABLE_1_0, TABLE_1_1 from TABLE_1 order by TABLE_1_1",
				store.convertSmartSql("select {employees:firstName}, {employees:lastName} from {employees} order by {employees:lastName}"));
        Assert.assertEquals("select TABLE_2_1 from TABLE_2 order by TABLE_2_0",
				store.convertSmartSql("select {departments:name} from {departments} order by {departments:deptCode}"));
	
	}

	/**
	 * Testing smart sql to sql conversion when there is a join
	 */
    @Test
	public void testConvertSmartSqlWithJoin() {
        Assert.assertEquals("select TABLE_2_1, TABLE_1_0 || ' ' || TABLE_1_1 "
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
    @Test
	public void testConvertSmartSqlWithSelfJoin() {
        Assert.assertEquals("select mgr.TABLE_1_1, e.TABLE_1_1 "
					+ "from TABLE_1 as mgr, TABLE_1 as e "
				    + "where mgr.TABLE_1_3 = e.TABLE_1_4",
				store.convertSmartSql("select mgr.{employees:lastName}, e.{employees:lastName} "
						+ "from {employees} as mgr, {employees} as e "
						+ "where mgr.{employees:employeeId} = e.{employees:managerId}"));
	}

	/**
	 * Testing smart sql to sql conversion when there is a self join accessing json extracted fields
	 */
	@Test
	public void testConvertSmartSqlWithSelfJoinAndJsonExtractedField() {
		Assert.assertEquals("select json_extract(mgr.soup, '$.education'), json_extract(e.soup, '$.education') "
				+ "from TABLE_1 as mgr, TABLE_1 as e "
				+ "where json_extract(mgr.soup, '$.education') = json_extract(e.soup, '$.education')",
			store.convertSmartSql("select mgr.{employees:education}, e.{employees:education} "
				+ "from {employees} as mgr, {employees} as e "
				+ "where mgr.{employees:education} = e.{employees:education}"));
	}

	/**
	 * Testing smart sql to sql conversion when there is a self join accessing json extracted fields
	 * with no spaces between referenced fields
	 */
	@Test
	public void testConvertSmartSqlWithSelfJoinAndJsonExtractedFieldNoLeadingSpace() {
		Assert.assertEquals("select json_extract(mgr.soup, '$.education'),json_extract(e.soup, '$.education') "
				+ "from TABLE_1 as mgr, TABLE_1 as e "
				+ "where not (json_extract(mgr.soup, '$.education')=json_extract(e.soup, '$.education'))",
			store.convertSmartSql("select mgr.{employees:education},e.{employees:education} "
				+ "from {employees} as mgr, {employees} as e "
				+ "where not (mgr.{employees:education}=e.{employees:education})"));
	}

	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate
	 */
    @Test
	public void testConvertSmartSqlWithSpecialColumns() {
        Assert.assertEquals("select TABLE_1.id, TABLE_1.created, TABLE_1.lastModified, TABLE_1.soup from TABLE_1",
				store.convertSmartSql("select {employees:_soupEntryId}, {employees:_soupCreatedDate}, {employees:_soupLastModifiedDate}, {employees:_soup} from {employees}"));
	}
	
	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate and there is a join
	 */
    @Test
	public void testConvertSmartSqlWithSpecialColumnsAndJoin() {
        Assert.assertEquals("select TABLE_1.id, TABLE_2.id from TABLE_1, TABLE_2",
				store.convertSmartSql("select {employees:_soupEntryId}, {departments:_soupEntryId} from {employees}, {departments}"));
	}

	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate and there is a join
	 */
    @Test
	public void testConvertSmartSqlWithSpecialColumnsAndSelfJoin() {
        Assert.assertEquals("select mgr.id, e.id from TABLE_1 as mgr, TABLE_1 as e",
				store.convertSmartSql("select mgr.{employees:_soupEntryId}, e.{employees:_soupEntryId} from {employees} as mgr, {employees} as e"));
	}
	
	/**
	 * Test smart sql to sql conversation with insert/update/delete: expect exception
	 */
    @Test
	public void testConvertSmartSqlWithInsertUpdateDelete() {
		for (String smartSql : new String[] { "insert into {employees}", "update {employees}", "delete from {employees}"}) {
			try {
				store.convertSmartSql(smartSql);
                Assert.fail("Should have thrown exception for " + smartSql);
			}
			catch (SmartSqlException e) {
				// Expected
			}
		}
	}

    @Test
	public void testConvertSmartSqlWithJSON1() {
        Assert.assertEquals("select TABLE_1_1, json_extract(soup, '$.education') from TABLE_1 where json_extract(soup, '$.education') = 'MIT'",
				store.convertSmartSql("select {employees:lastName}, {employees:education} from {employees} where {employees:education} = 'MIT'"));
	}

    @Test
	public void testConvertSmartSqlWithJSON1AndTableQualifiedColumn() {
        Assert.assertEquals("select json_extract(TABLE_1.soup, '$.education') from TABLE_1 order by json_extract(TABLE_1.soup, '$.education')",
				store.convertSmartSql("select {employees}.{employees:education} from {employees} order by {employees}.{employees:education}"));
	}

    @Test
	public void testConvertSmartSqlWithJSON1AndTableAliases() {
        Assert.assertEquals("select json_extract(e.soup, '$.education'), json_extract(soup, '$.building') from TABLE_1 as e, TABLE_2",
				store.convertSmartSql("select e.{employees:education}, {departments:building} from {employees} as e, {departments}"));

		// XXX join query with json1 will only run if all the json1 columns are qualified by table or alias
	}

	@Test
	public void testConvertSmartSqlForNonIndexedColumns() {
		Assert.assertEquals("select json_extract(soup, '$.education'), json_extract(soup, '$.address.zipcode') from TABLE_1 where json_extract(soup, '$.address.city') = 'San Francisco'",
			store.convertSmartSql("select {employees:education}, {employees:address.zipcode} from {employees} where {employees:address.city} = 'San Francisco'"));
	}

	@Test
	public void testConvertSmartSqlWithQuotedCurlyBraces() {
    	Assert.assertEquals("select json_extract(soup, '$.education') from TABLE_1 where json_extract(soup, '$.education') like 'Account(where: {Name: {eq: \"Jason\"}})'",
			store.convertSmartSql("select {employees:education} from {employees} where {employees:education} like 'Account(where: {Name: {eq: \"Jason\"}})'"));
	}

	@Test
	public void testConvertOtherComplexSmartSql() {
    	Assert.assertEquals("SELECT json_set('{}', '$.data.uiapi.query.Account.edges', ( SELECT json_group_array(json_set('{}', '$.node.Id', (json_extract('Account.JSON', '$.data.fields.Id.value')) )) FROM (SELECT 'Account'.TABLE_1_1 as 'Account.JSON' FROM TABLE_1 as 'Account' WHERE ( json_extract('Account.JSON', '$.data.apiName') = 'Account' ) ) ) ) as json",
			store.convertSmartSql("SELECT json_set('{}', '$.data.uiapi.query.Account.edges', ( SELECT json_group_array(json_set('{}', '$.node.Id', (json_extract('Account.JSON', '$.data.fields.Id.value')) )) FROM (SELECT 'Account'.TABLE_1_1 as 'Account.JSON' FROM TABLE_1 as 'Account' WHERE ( json_extract('Account.JSON', '$.data.apiName') = 'Account' ) ) ) ) as json"));
	}

	@Test
	public void testConvertSmartSqlWithMultipleQuotedCurlyBraces() {
		Assert.assertEquals("select json_extract(soup, '$.education'), '{a:b}', TABLE_1_0 from TABLE_1 where json_extract(soup, '$.address') = '{\"city\": \"San Francisco\"}' or TABLE_1_1 like 'B%'",
			store.convertSmartSql("select {employees:education}, '{a:b}', {employees:firstName} from {employees} where {employees:address} = '{\"city\": \"San Francisco\"}' or {employees:lastName} like 'B%'"));
	}

	@Test
	public void testConvertSmartSqlWithQuotedUnbalancedCurlyBraces() {
		Assert.assertEquals("select json_extract(soup, '$.education') from TABLE_1 where json_extract(soup, '$.education') like ' { { { } } '",
			store.convertSmartSql("select {employees:education} from {employees} where {employees:education} like ' { { { } } '"));
	}

	/**
	 * Test running smart query that does a select count
	 * @throws JSONException 
	 */
    @Test
	public void testSmartQueryDoingCount() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select count(*) from {employees}", 1), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[7]]"), result);
	}
	
	/**
	 * Test running smart query that does a select sum
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryDoingSum() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select sum({departments:budget}) from {departments}", 1), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[3000000]]"), result);
	}

	/**
	 * Test running smart query that return one row with one integer
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryReturningOneRowWithOneInteger() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[200000]]"), result);
	}
	
	/**
	 * Test running smart query that return one row with two integers
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryReturningOneRowWithTwoIntegers() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select mgr.{employees:salary}, e.{employees:salary} from {employees} as mgr, {employees} as e where e.{employees:lastName} = 'Thompson' and mgr.{employees:employeeId} = e.{employees:managerId}", 1), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[200000,120000]]"), result);
	}
	
	/**
	 * Test running smart query that return two rows with one integer each
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryReturningTwoRowsWithOneIntegerEach() throws JSONException {
		loadData();
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:managerId} = '00010' order by {employees:firstName}", 2), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[120000],[100000]]"), result);
	}

	/**
	 * Test running smart query that return a soup along with a string and an integer
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryReturningSoupStringAndInteger() throws JSONException {
		loadData();
		JSONObject christineJson = store.query(QuerySpec.buildExactQuerySpec(EMPLOYEES_SOUP, "employeeId", "00010", null, null, 1), 0).getJSONObject(0);
        Assert.assertEquals("Wrong elt", "Christine", christineJson.getString(FIRST_NAME));
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:_soup}, {employees:firstName}, {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1) , 0);
        Assert.assertEquals("Expected one row", 1, result.length());
		JSONTestHelper.assertSameJSON("Wrong soup", christineJson, result.getJSONArray(0).getJSONObject(0));
        Assert.assertEquals("Wrong first name", "Christine", result.getJSONArray(0).getString(1));
        Assert.assertEquals("Wrong salary", 200000, result.getJSONArray(0).getInt(2));
	}
	
	/**
	 * Test running smart query with paging
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryWithPaging() throws JSONException {
		loadData();
		QuerySpec query = QuerySpec.buildSmartQuerySpec("select {employees:firstName} from {employees} order by {employees:firstName}", 1);
        Assert.assertEquals("Expected 7 employees", 7, store.countQuery(query));
		String[] expectedResults = new String[] {"Christine", "Eileen", "Eva", "Irving", "John", "Michael", "Sally"};
		for (int i = 0; i<7; i++) {
			JSONArray result = store.query(query , i);
			JSONTestHelper.assertSameJSONArray("Wrong result at page " + i, new JSONArray("[[" + expectedResults[i] + "]]"), result);
		}
	}

	/**
	 * Test running smart query that targets _soup, _soupEntryId and _soupLastModifiedDate
	 * @throws JSONException
	 */
    @Test
	public void testSmartQueryWithSpecialFields() throws JSONException {
		loadData();
		JSONObject christineJson = store.query(QuerySpec.buildExactQuerySpec(EMPLOYEES_SOUP, "employeeId", "00010", null, null, 1), 0).getJSONObject(0);
        Assert.assertEquals("Wrong elt", "Christine", christineJson.getString(FIRST_NAME));
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:_soup}, {employees:_soupEntryId}, {employees:_soupLastModifiedDate}, {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1) , 0);
        Assert.assertEquals("Expected one row", 1, result.length());
		JSONTestHelper.assertSameJSON("Wrong soup", christineJson, result.getJSONArray(0).getJSONObject(0));
		JSONTestHelper.assertSameJSON("Wrong soupEntryId", christineJson.getString(SmartStore.SOUP_ENTRY_ID), result.getJSONArray(0).getInt(1));
		JSONTestHelper.assertSameJSON("Wrong soupLastModifiedDate", christineJson.getString(SmartStore.SOUP_LAST_MODIFIED_DATE), result.getJSONArray(0).getLong(2));
	}

	/**
	 * Test running smart queries matching null or empty fields
	 * @throws JSONException
	 */
	@Test
	public void testSmartQueryMatchingNullField() throws JSONException {
        JSONObject createdEmployee;

		// Employee with dept code
		createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"001\",\"deptCode\":\"xyz\"}");
		Assert.assertEquals("xyz", createdEmployee.get(DEPT_CODE));

		// Employee with JSONObject.NULL dept code
		createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"002\",\"deptCode\":null}");
        Assert.assertTrue(createdEmployee.isNull(DEPT_CODE));

		// Employee with "" dept code
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"003\",\"deptCode\":\"\"}");
        Assert.assertEquals("", createdEmployee.get(DEPT_CODE));

		// Employee with no dept code
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"004\"}");
        Assert.assertFalse(createdEmployee.has(DEPT_CODE));

		// Smart sql with is not null
        JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:deptCode} is not null order by {employees:employeeId}", 4), 0);
        JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"001\"],[\"003\"]]"), result);

		// Smart sql with is null
        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:deptCode} is null order by {employees:employeeId}", 4), 0);
        JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"002\"],[\"004\"]]"), result);

		// Smart sql looking for empty string
        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:deptCode} = \"\" order by {employees:employeeId}", 4), 0);
        JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"003\"]]"), result);
	}

	/**
	 * Test running smart queries matching true and false in json1 field
     * NB: SQLite does not have a separate Boolean storage class. Instead, Boolean values are stored as integers 0 (false) and 1 (true).
	 */
	@Test
	public void testSmartQueryMachingBooleanInJSON1Field() throws JSONException {
        JSONObject createdEmployee;

	    loadData();

        // Creating another employee from a json string with isManager true
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"101\",\"isManager\":true}");
        Assert.assertEquals(true, createdEmployee.get(IS_MANAGER));

        // Creating another employee from a json string with isManager false
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"102\",\"isManager\":false}");
        Assert.assertEquals(false, createdEmployee.get(IS_MANAGER));

        // Smart sql looking for isManager true
        JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:isManager} = 1 order by {employees:employeeId}", 10), 0);
        JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"00010\"],[\"00040\"],[\"00050\"],[\"101\"]]"), result);
        // Smart sql looking for isManager false
        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:isManager} = 0 order by {employees:employeeId}", 10), 0);
        JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"00020\"],[\"00060\"],[\"00070\"],[\"00310\"],[\"102\"]]"), result);
	}

	/**
	 * Test running smart queries matching non-ASCII characters in String field
	 */
	@Test
	public void testSmartQueryMachingNonAsciiInStringField() throws JSONException {

		// Creating another employee from a json string with first name using non-ascii characters (Turkish)
		createEmployeeWithJsonString("{\"employeeId\":\"101\",\"firstName\":\"Göktuğ\"}");

		// Creating another employee from a json string with first name using non-ascii characters (Korean)
		createEmployeeWithJsonString("{\"employeeId\":\"102\",\"firstName\":\"보배\"}");

		// Smart sql looking for first name containing a certain non-ASCII character (ğ)
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:firstName} like '%ğ%'", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"101\"]]"), result);

		// Smart sql looking for first name containing a certain non-ASCII character (배)
		result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:firstName} like '%배%'", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"102\"]]"), result);
	}

	/**
	 * Test running smart queries matching non-ASCII characters in Json1 field
	 */
	@Test
	public void testSmartQueryMachingNonAsciiInJSON1Field() throws JSONException {

		// Creating another employee from a json string with education using non-ascii characters (Turkish)
		createEmployeeWithJsonString("{\"employeeId\":\"101\",\"education\":\"latince uzmanı\"}");

		// Creating another employee from a json string with education using non-ascii characters (Korean)
		createEmployeeWithJsonString("{\"employeeId\":\"102\",\"education\":\"라틴어 전문가\"}");

		// Smart sql looking for education containing a certain non-ASCII character (ı)
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:education} like '%ı%'", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"101\"]]"), result);

		// Smart sql looking for education containing a certain non-ASCII character (문)
		result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:education} like '%문%'", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"102\"]]"), result);
	}

	/**
	 * Test running smart queries matching non-ASCII characters in non-indexed field
	 */
	@Test
	public void testSmartQueryMachingNonAsciiInNonIndexedField() throws JSONException {

		// Creating another employee from a json string with country using non-ascii characters (Turkish)
		createEmployeeWithJsonString("{\"employeeId\":\"101\",\"country\":\"Türkçe\"}");

		// Creating another employee from a json string with country using non-ascii characters (Korean)
		createEmployeeWithJsonString("{\"employeeId\":\"102\",\"country\":\"한국\"}");

		// Smart sql looking for country containing a certain non-ASCII character (ç)
		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:country} like '%ç%'", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"101\"]]"), result);

		// Smart sql looking for country containing a certain non-ASCII character (국)
		result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:country} like '%국%'", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"102\"]]"), result);
	}

	@Test
	public void testSmartQueryFilteringByNonIndexedField() throws JSONException {
		JSONObject employee101 = createEmployeeWithJsonString("{\"employeeId\":\"101\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94105}}");
		JSONObject employee102 = createEmployeeWithJsonString("{\"employeeId\":\"102\",\"address\":{\"city\":\"New York City\", \"zipcode\":10004}}");
		JSONObject employee103 = createEmployeeWithJsonString("{\"employeeId\":\"103\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94106}}");
		JSONObject employee104 = createEmployeeWithJsonString("{\"employeeId\":\"104\",\"address\":{\"city\":\"New York City\", \"zipcode\":10006}}");

		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:address.city} = 'San Francisco' order by {employees:employeeId}", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"101\"],[\"103\"]]"), result);

		result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:address.zipcode} = 10006", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"104\"]]"), result);
	}

	@Test
	public void testSmartQueryReturningNonIndexedField() throws JSONException {
		JSONObject employee101 = createEmployeeWithJsonString("{\"employeeId\":\"101\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94105}}");
		JSONObject employee102 = createEmployeeWithJsonString("{\"employeeId\":\"102\",\"address\":{\"city\":\"New York City\", \"zipcode\":10004}}");
		JSONObject employee103 = createEmployeeWithJsonString("{\"employeeId\":\"103\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94106}}");
		JSONObject employee104 = createEmployeeWithJsonString("{\"employeeId\":\"104\",\"address\":{\"city\":\"New York City\", \"zipcode\":10006}}");

		JSONArray result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId}, {employees:address.zipcode} from {employees} where {employees:address.city} = 'San Francisco' order by {employees:employeeId}", 10), 0);
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"101\", 94105],[\"103\", 94106]]"), result);
	}

	@Test
	public void testSmartQueryUsingWhereArgs() throws JSONException {
		JSONObject employee101 = createEmployeeWithJsonString("{\"employeeId\":\"101\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94105}}");
		JSONObject employee102 = createEmployeeWithJsonString("{\"employeeId\":\"102\",\"address\":{\"city\":\"New York City\", \"zipcode\":10004}}");
		JSONObject employee103 = createEmployeeWithJsonString("{\"employeeId\":\"103\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94106}}");
		JSONObject employee104 = createEmployeeWithJsonString("{\"employeeId\":\"104\",\"address\":{\"city\":\"New York City\", \"zipcode\":10006}}");

		QuerySpec querySpec = QuerySpec.buildSmartQuerySpec("select {employees:employeeId}, {employees:address.zipcode} from {employees} where {employees:address.city} = ? order by {employees:employeeId}", 10);
		JSONArray result = store.queryWithArgs(querySpec, 0 , "San Francisco");
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"101\", 94105],[\"103\", 94106]]"), result);
		result = store.queryWithArgs(querySpec,0 , "New York City");
		JSONTestHelper.assertSameJSONArray("Wrong result", new JSONArray("[[\"102\", 10004],[\"104\", 10006]]"), result);
	}

	@Test
	public void testNonSmartQueryUsingWhereArgs() throws JSONException {
		QuerySpec querySpec = QuerySpec.buildAllQuerySpec(EMPLOYEES_SOUP, EMPLOYEE_ID, Order.ascending, 10);
		try {
			store.queryWithArgs(querySpec, 0, "San Francisco");
			Assert.fail("SmartStoreException should have been thrown");
		} catch (SmartStoreException e) {
			Assert.assertEquals("whereArgs can only be provided for smart queries", e.getMessage());
		}
	}

	/**
	 * Making sure the "cleanup" regexp is a lot faster than the old cleanup regexp
	 * Testing a real-world query with 25k characters
	 */
	@Test
	public void testCleanupRegexpFaster() {
		String oldRegexp = "([^ ]+)\\.json_extract\\(soup";

		// At least 500 times faster than the old regexp
		Assert.assertTrue(
			timeRegexpInMs(SmartSqlHelper.TABLE_DOT_JSON_EXTRACT_REGEXP) * 500 < timeRegexpInMs(oldRegexp));
		// No more than 25ms
		Assert.assertTrue(timeRegexpInMs(SmartSqlHelper.TABLE_DOT_JSON_EXTRACT_REGEXP) <  25);
	}

	private double timeRegexpInMs(String regexp) {
		String q = "SELECT {DEFAULT:LdsSoupKey}, {DEFAULT:LdsSoupValue}\nFROM {DEFAULT}\nWHERE {DEFAULT:LdsSoupKey}\nIN (\'UiApi::BatchRepresentation(childRelationships:undefined,fields:undefined,layoutTypes:undefined,modes:undefined,optionalFields:Account.AccountSource,Account.AnnualRevenue,Account.BillingAddress,Account.BillingCity,Account.BillingCountry,Account.BillingGeocodeAccuracy,Account.BillingLatitude,Account.BillingLongitude,Account.BillingPostalCode,Account.BillingState,Account.BillingStreet,Account.ChannelProgramLevelName,Account.ChannelProgramName,Account.CreatedById,Account.CreatedDate,Account.Description,Account.Fax,Account.Id,Account.Industry,Account.IsCustomerPortal,Account.IsDeleted,Account.IsLocked,Account.IsPartner,Account.Jigsaw,Account.JigsawCompanyId,Account.LastActivityDate,Account.LastModifiedById,Account.LastModifiedDate,Account.LastReferencedDate,Account.LastViewedDate,Account.MasterRecordId,Account.MayEdit,Account.Name,Account.NumberOfEmployees,Account.OperatingHoursId,Account.OwnerId,Account.ParentId,Account.Phone,Account.PhotoUrl,Account.ShippingAddress,Account.ShippingCity,Account.ShippingCountry,Account.ShippingGeocodeAccuracy,Account.ShippingLatitude,Account.ShippingLongitude,Account.ShippingPostalCode,Account.ShippingState,Account.ShippingStreet,Account.SicDesc,Account.SystemModstamp,Account.Type,Account.Website,Asset.AccountId,Asset.AssetProvidedById,Asset.AssetServicedById,Asset.ContactId,Asset.CreatedById,Asset.CreatedDate,Asset.Description,Asset.Id,Asset.InstallDate,Asset.IsCompetitorProduct,Asset.IsDeleted,Asset.IsLocked,Asset.LastModifiedById,Asset.LastModifiedDate,Asset.LastReferencedDate,Asset.LastViewedDate,Asset.MayEdit,Asset.Name,Asset.OwnerId,Asset.ParentId,Asset.Price,Asset.Product2Id,Asset.ProductCode,Asset.PurchaseDate,Asset.Quantity,Asset.RootAssetId,Asset.SerialNumber,Asset.Status,Asset.StockKeepingUnit,Asset.SystemModstamp,Asset.UsageEndDate,Location.CloseDate,Location.ConstructionEndDate,Location.ConstructionStartDate,Location.CreatedById,Location.CreatedDate,Location.Description,Location.DrivingDirections,Location.ExternalReference,Location.Id,Location.IsDeleted,Location.IsInventoryLocation,Location.IsLocked,Location.IsMobile,Location.LastModifiedById,Location.LastModifiedDate,Location.LastReferencedDate,Location.LastViewedDate,Location.Latitude,Location.Location,Location.LocationLevel,Location.LocationType,Location.LogoId,Location.Longitude,Location.MayEdit,Location.Name,Location.OpenDate,Location.OwnerId,Location.ParentLocationId,Location.PossessionDate,Location.RemodelEndDate,Location.RemodelStartDate,Location.RootLocationId,Location.SystemModstamp,Location.TimeZone,ServiceAppointment.AccountId,ServiceAppointment.ActualDuration,ServiceAppointment.ActualEndTime,ServiceAppointment.ActualStartTime,ServiceAppointment.Address,ServiceAppointment.AppointmentNumber,ServiceAppointment.ArrivalWindowEndTime,ServiceAppointment.ArrivalWindowStartTime,ServiceAppointment.City,ServiceAppointment.ContactId,ServiceAppointment.Country,ServiceAppointment.CreatedById,ServiceAppointment.CreatedDate,ServiceAppointment.Description,ServiceAppointment.DueDate,ServiceAppointment.Duration,ServiceAppointment.DurationInMinutes,ServiceAppointment.DurationType,ServiceAppointment.EarliestStartTime,ServiceAppointment.FSL__Appointment_Grade__c,ServiceAppointment.FSL__Auto_Schedule__c,ServiceAppointment.FSL__Emergency__c,ServiceAppointment.FSL__GanttColor__c,ServiceAppointment.FSL__GanttLabel__c,ServiceAppointment.FSL__InJeopardyReason__c,ServiceAppointment.FSL__InJeopardy__c,ServiceAppointment.FSL__InternalSLRGeolocation__Latitude__s,ServiceAppointment.FSL__InternalSLRGeolocation__Longitude__s,ServiceAppointment.FSL__InternalSLRGeolocation__c,ServiceAppointment.FSL__IsFillInCandidate__c,ServiceAppointment.FSL__IsMultiDay__c,ServiceAppointment.FSL__MDS_Calculated_length__c,ServiceAppointment.FSL__MDT_Operational_Time__c,ServiceAppointment.FSL__Pinned__c,ServiceAppointment.FSL__Prevent_Geocoding_For_Chatter_Actions__c,ServiceAppointment.FSL__Related_Service__c,ServiceAppointment.FSL__Same_Day__c,ServiceAppointment.FSL__Same_Resource__c,ServiceAppointment.FSL__Schedule_Mode__c,ServiceAppointment.FSL__Schedule_over_lower_priority_appointment__c,ServiceAppointment.FSL__Time_Dependency__c,ServiceAppointment.FSL__UpdatedByOptimization__c,ServiceAppointment.FSL__Virtual_Service_For_Chatter_Action__c,ServiceAppointment.GeocodeAccuracy,ServiceAppointment.Id,ServiceAppointment.Incomplete_Status_Count__c,ServiceAppointment.IsDeleted,ServiceAppointment.IsLocked,ServiceAppointment.LastModifiedById,ServiceAppointment.LastModifiedDate,ServiceAppointment.LastReferencedDate,ServiceAppointment.LastViewedDate,ServiceAppointment.Latitude,ServiceAppointment.Longitude,ServiceAppointment.MayEdit,ServiceAppointment.OwnerId,ServiceAppointment.ParentRecordId,ServiceAppointment.ParentRecordStatusCategory,ServiceAppointment.ParentRecordType,ServiceAppointment.PostalCode,ServiceAppointment.ProductId__c,ServiceAppointment.ResourceAbsenceId__c,ServiceAppointment.SACount__c,ServiceAppointment.SchedEndTime,ServiceAppointment.SchedStartTime,ServiceAppointment.ServiceResourceId__c,ServiceAppointment.ServiceTerritoryId,ServiceAppointment.State,ServiceAppointment.Status,ServiceAppointment.Street,ServiceAppointment.Subject,ServiceAppointment.SystemModstamp,ServiceAppointment.TimeSheetEntryId__c,ServiceAppointment.TimeSheetId__c,WorkOrder.AccountId,WorkOrder.Address,WorkOrder.AssetId,WorkOrder.AssetWarrantyId,WorkOrder.BusinessHoursId,WorkOrder.CaseId,WorkOrder.City,WorkOrder.ContactId,WorkOrder.Country,WorkOrder.CreatedById,WorkOrder.CreatedDate,WorkOrder.Description,WorkOrder.Discount,WorkOrder.DurationInMinutes,WorkOrder.DurationSource,WorkOrder.EndDate,WorkOrder.EntitlementId,WorkOrder.External_Id__c,WorkOrder.FSL__IsFillInCandidate__c,WorkOrder.FSL__Prevent_Geocoding_For_Chatter_Actions__c,WorkOrder.FSL__Scheduling_Priority__c,WorkOrder.FSL__VisitingHours__c,WorkOrder.GeocodeAccuracy,WorkOrder.GrandTotal,WorkOrder.Id,WorkOrder.IsClosed,WorkOrder.IsDeleted,WorkOrder.IsGeneratedFromMaintenancePlan,WorkOrder.IsLocked,WorkOrder.IsStopped,WorkOrder.LastModifiedById,WorkOrder.LastModifiedDate,WorkOrder.LastReferencedDate,WorkOrder.LastViewedDate,WorkOrder.Latitude,WorkOrder.LineItemCount,WorkOrder.LocationId,WorkOrder.Longitude,WorkOrder.MaintenancePlanId,WorkOrder.MaintenanceWorkRuleId,WorkOrder.MayEdit,WorkOrder.MilestoneStatus,WorkOrder.OwnerId,WorkOrder.ParentWorkOrderId,WorkOrder.PostalCode,WorkOrder.Pricebook2Id,WorkOrder.Priority,WorkOrder.ProductRequiredId__c,WorkOrder.ProductServiceCampaignId,WorkOrder.ProductServiceCampaignItemId,WorkOrder.RecordTypeId,WorkOrder.RootWorkOrderId,WorkOrder.ServiceContractId,WorkOrder.ServiceCrewId__c,WorkOrder.ServiceCrewMemberId__c,WorkOrder.ServiceReportLanguage,WorkOrder.ServiceReportTemplateId,WorkOrder.ServiceTerritoryId,WorkOrder.SlaExitDate,WorkOrder.SlaStartDate,WorkOrder.StartDate,WorkOrder.State,WorkOrder.Status,WorkOrder.StopStartDate,WorkOrder.Street,WorkOrder.Subject,WorkOrder.Subtotal,WorkOrder.SuggestedMaintenanceDate,WorkOrder.SystemModstamp,WorkOrder.Tax,WorkOrder.TimeSlotId__c,WorkOrder.TotalPrice,WorkOrder.WorkOrderNumber,WorkOrder.WorkTypeId,WorkOrder.Work_Order_Count__c,pageSize:undefined,updateMru:undefined,recordIds:02ix000000CG4h1AAD,02ix000000CG4kKAAT,02ix000000CG6gKAAT,02ix000000CG4rPAAT,02ix000000CG5uRAAT,02ix000000CG3oNAAT,02ix000000CG5yyAAD,02ix000000CG4PoAAL,02ix000000CG6VRAA1,02ix000000CG4KxAAL,02ix000000CG5FbAAL,02ix000000CG5bPAAT,02ix000000CG4q8AAD,02ix000000CG61vAAD,02ix000000CG5mRAAT,02ix000000CG6BdAAL,02ix000000CG6UpAAL,02ix00000006zCzAAI,02ix000000CG6e1AAD,02ix000000CG5MqAAL,02ix000000CG6ZIAA1,02ix000000CG6OgAAL,02ix000000CG5HQAA1,02ix000000CG4ihAAD,02ix000000CG4zKAAT,02ix000000CG4VaAAL,02ix000000CG56UAAT,02ix000000CG6IvAAL,02ix000000CG6ZJAA1,02ix000000CG6bQAAT,02ix000000CG62UAAT,02ix000000CG5iCAAT,02ix000000CG6NOAA1,02ix000000CG4jrAAD,02ix000000CG4C2AAL,02ix000000CG4t6AAD,02ix000000CG4c2AAD,02ix000000CG6MLAA1,02ix000000CG6IBAA1,02ix000000CG53UAAT,02ix000000CG5FAAA1,02ix000000CG5GvAAL,02ix000000CG5YmAAL,02ix000000CG5vcAAD,02ix000000CG4SFAA1,02ix000000CG5tFAAT,02ix000000CG6GfAAL,02ix000000CG5M3AAL,02ix000000CG5CMAA1,02ix000000CG4LsAAL,131x00000000khiAAA,131x00000000khSAAQ,131x00000000jMnAAI,131x00000000khYAAQ,131x00000000khNAAQ,131x00000000khXAAQ,131x00000000jMzAAI,131x00000000khMAAQ,131x00000000jMYAAY,131x00000000jMtAAI,131x00000000khcAAA,131x00000000jMeAAI,131x00000000khZAAQ,131x00000000khOAAQ,131x00000000jN0AAI,131x00000000khJAAQ,131x00000000kheAAA,131x00000000jMkAAI,131x00000000jMvAAI,131x00000000khIAAQ,131x00000000khdAAA,131x00000000khjAAA,131x00000000jMpAAI,131x00000000khTAAQ,131x00000000khDAAQ,131x00000000khlAAA,131x00000000khKAAQ,131x00000000khfAAA,131x00000000j75AAA,131x00000000khVAAQ,131x00000000khaAAA,131x00000000jMgAAI,131x00000000khkAAA,131x00000000khUAAQ,131x00000000jN2AAI,131x00000000jMlAAI,131x00000000khPAAQ,131x00000000jMmAAI,131x00000000khbAAA,131x00000000khhAAA,131x00000000jMWAAY,131x00000000khRAAQ,131x00000000jMrAAI,131x00000000jMbAAI,131x00000000khgAAA,131x00000000jMsAAI,131x00000000khQAAQ,131x00000000jMhAAI,131x00000000khWAAQ,131x00000000khLAAQ,001x0000004ckZXAAY,001x0000004ckZcAAI,001x0000004cka2AAA,001x0000004ckZnAAI,001x0000004ckZsAAI,001x0000004cka7AAA,001x0000004ckZhAAI,001x0000004ckZxAAI,001x0000004ckZRAAY,001x0000004cka8AAA,001x0000004ckZmAAI,001x0000004ckZbAAI,001x0000004ckZWAAY,001x0000004cka6AAA,001x0000004ckZzAAI,001x0000004ckZTAAY,001x0000004ckZYAAY,001x0000004ckZdAAI,001x0000004ckZoAAI,001x0000004ckZtAAI,001x0000004ckZiAAI,001x0000004ckZNAAY,001x0000004ckaAAAQ,001x0000004ckZyAAI,001x0000004ckZSAAY,001x0000004cka1AAA,001x0000004ckZvAAI,001x0000004ckZPAAY,001x0000004ckZkAAI,001x0000004ckZUAAY,001x0000004ckZeAAI,001x0000004ckZZAAY,001x0000004cka0AAA,001x0000004ckZpAAI,001x0000004cka5AAA,001x0000004ckZuAAI,001x0000004ckZOAAY,001x0000004ckZjAAI,001x0000004ckZrAAI,001x0000004ckZgAAI,001x0000004ckZwAAI,001x0000004ckZlAAI,001x0000004ckZQAAY,001x0000004cka3AAA,001x0000004ckZaAAI,001x0000004ckZVAAY,001x0000004cka4AAA,001x0000004cka9AAA,001x0000004ckZqAAI,001x0000004ckZfAAI,0WOx0000005kEBhGAM,0WOx0000005kEBXGA2,0WOx0000005kEAAGA2,0WOx0000005kEA6GAM,0WOx0000005kEAQGA2,0WOx0000005kEA0GAM,0WOx0000005kEBSGA2,0WOx0000005kEBcGAM,0WOx0000005kEAFGA2,0WOx0000005kEAGGA2,0WOx0000005kEA1GAM,0WOx0000005kEBRGA2,0WOx0000005kEBbGAM,0WOx0000005kEALGA2,0WOx0000005kEBTGA2,0WOx0000005kEBdGAM,0WOx0000005kEAMGA2,0WOx0000005kEARGA2,0WOx0000005kEA7GAM,0WOx0000005kEABGA2,0WOx0000005kE9zGAE,0WOx0000005kECjGAM,0WOx0000005kEASGA2,0WOx0000005kEA8GAM,0WOx0000005kEACGA2,0WOx0000005kE9yGAE,0WOx0000005kEAHGA2,0WOx0000005kEA2GAM,0WOx0000005kEBYGA2,0WOx0000005kEAIGA2,0WOx0000005kEA3GAM,0WOx0000005kEANGA2,0WOx0000005kEBZGA2,0WOx0000005kEAOGA2,0WOx0000005kEBUGA2,0WOx0000005kEA9GAM,0WOx0000005kE9xGAE,0WOx0000005kEADGA2,0WOx0000005kEBeGAM,0WOx0000005kEAEGA2,0WOx0000005kEBgGAM,0WOx0000005kEBWGA2,0WOx0000005kEA4GAM,0WOx0000005kEAJGA2,0WOx0000005kEBVGA2,0WOx0000005kEBfGAM,0WOx0000005kEAKGA2,0WOx0000005kEA5GAM,0WOx0000005kEAPGA2,0WOx0000005kEBaGAM,08px000000JaaADAAZ,08px000000JaaATAAZ,08px000000JaaAuAAJ,08px000000JaaA9AAJ,08px000000JaaAYAAZ,08px000000JaaAdAAJ,08px000000JaaAIAAZ,08px000000JaaAZAAZ,08px000000JaaAeAAJ,08px000000JaaAJAAZ,08px000000JaaAOAAZ,08px000000JaaAoAAJ,08px000000JaaAjAAJ,08px000000JaaAHAAZ,08px000000JaaAcAAJ,08px000000JaaAXAAZ,08px000000JaaA8AAJ,08px000000JaaAMAAZ,08px000000JaaAhAAJ,08px000000JaaAtAAJ,08px000000JaaANAAZ,08px000000JaaAiAAJ,08px000000JaaAsAAJ,08px000000JaaACAAZ,08px000000JaaAnAAJ,08px000000JaaASAAZ,08px000000JaaALAAZ,08px000000JaaAgAAJ,08px000000JaaAAAAZ,08px000000JaaAlAAJ,08px000000JaaAQAAZ,08px000000JaaABAAZ,08px000000JaaAmAAJ,08px000000JaaARAAZ,08px000000JaaAbAAJ,08px000000JaaAGAAZ,08px000000JaaAWAAZ,08px000000JaaArAAJ,08px000000JaaAPAAZ,08px000000JaaAkAAJ,08px000000JaaAqAAJ,08px000000JaaAEAAZ,08px000000JaaAUAAZ,08px000000JaaApAAJ,08px000000JaaAaAAJ,08px000000JaaAFAAZ,08px000000JaaAVAAZ,08px000000JaaAfAAJ,08px000000JaaAvAAJ,08px000000JaaAKAAZ)\',\'UiApi::RecordRepresentation:02ix000000CG4h1AAD\',\'UiApi::RecordRepresentation:02ix000000CG4kKAAT\',\'UiApi::RecordRepresentation:02ix000000CG6gKAAT\',\'UiApi::RecordRepresentation:02ix000000CG4rPAAT\',\'UiApi::RecordRepresentation:02ix000000CG5uRAAT\',\'UiApi::RecordRepresentation:02ix000000CG3oNAAT\',\'UiApi::RecordRepresentation:02ix000000CG5yyAAD\',\'UiApi::RecordRepresentation:02ix000000CG4PoAAL\',\'UiApi::RecordRepresentation:02ix000000CG6VRAA1\',\'UiApi::RecordRepresentation:02ix000000CG4KxAAL\',\'UiApi::RecordRepresentation:02ix000000CG5FbAAL\',\'UiApi::RecordRepresentation:02ix000000CG5bPAAT\',\'UiApi::RecordRepresentation:02ix000000CG4q8AAD\',\'UiApi::RecordRepresentation:02ix000000CG61vAAD\',\'UiApi::RecordRepresentation:02ix000000CG5mRAAT\',\'UiApi::RecordRepresentation:02ix000000CG6BdAAL\',\'UiApi::RecordRepresentation:02ix000000CG6UpAAL\',\'UiApi::RecordRepresentation:02ix00000006zCzAAI\',\'UiApi::RecordRepresentation:02ix000000CG6e1AAD\',\'UiApi::RecordRepresentation:02ix000000CG5MqAAL\',\'UiApi::RecordRepresentation:02ix000000CG6ZIAA1\',\'UiApi::RecordRepresentation:02ix000000CG6OgAAL\',\'UiApi::RecordRepresentation:02ix000000CG5HQAA1\',\'UiApi::RecordRepresentation:02ix000000CG4ihAAD\',\'UiApi::RecordRepresentation:02ix000000CG4zKAAT\',\'UiApi::RecordRepresentation:02ix000000CG4VaAAL\',\'UiApi::RecordRepresentation:02ix000000CG56UAAT\',\'UiApi::RecordRepresentation:02ix000000CG6IvAAL\',\'UiApi::RecordRepresentation:02ix000000CG6ZJAA1\',\'UiApi::RecordRepresentation:02ix000000CG6bQAAT\',\'UiApi::RecordRepresentation:02ix000000CG62UAAT\',\'UiApi::RecordRepresentation:02ix000000CG5iCAAT\',\'UiApi::RecordRepresentation:02ix000000CG6NOAA1\',\'UiApi::RecordRepresentation:02ix000000CG4jrAAD\',\'UiApi::RecordRepresentation:02ix000000CG4C2AAL\',\'UiApi::RecordRepresentation:02ix000000CG4t6AAD\',\'UiApi::RecordRepresentation:02ix000000CG4c2AAD\',\'UiApi::RecordRepresentation:02ix000000CG6MLAA1\',\'UiApi::RecordRepresentation:02ix000000CG6IBAA1\',\'UiApi::RecordRepresentation:02ix000000CG53UAAT\',\'UiApi::RecordRepresentation:02ix000000CG5FAAA1\',\'UiApi::RecordRepresentation:02ix000000CG5GvAAL\',\'UiApi::RecordRepresentation:02ix000000CG5YmAAL\',\'UiApi::RecordRepresentation:02ix000000CG5vcAAD\',\'UiApi::RecordRepresentation:02ix000000CG4SFAA1\',\'UiApi::RecordRepresentation:02ix000000CG5tFAAT\',\'UiApi::RecordRepresentation:02ix000000CG6GfAAL\',\'UiApi::RecordRepresentation:02ix000000CG5M3AAL\',\'UiApi::RecordRepresentation:02ix000000CG5CMAA1\',\'UiApi::RecordRepresentation:02ix000000CG4LsAAL\',\'UiApi::RecordRepresentation:131x00000000khiAAA\',\'UiApi::RecordRepresentation:131x00000000khSAAQ\',\'UiApi::RecordRepresentation:131x00000000jMnAAI\',\'UiApi::RecordRepresentation:131x00000000khYAAQ\',\'UiApi::RecordRepresentation:131x00000000khNAAQ\',\'UiApi::RecordRepresentation:131x00000000khXAAQ\',\'UiApi::RecordRepresentation:131x00000000jMzAAI\',\'UiApi::RecordRepresentation:131x00000000khMAAQ\',\'UiApi::RecordRepresentation:131x00000000jMYAAY\',\'UiApi::RecordRepresentation:131x00000000jMtAAI\',\'UiApi::RecordRepresentation:131x00000000khcAAA\',\'UiApi::RecordRepresentation:131x00000000jMeAAI\',\'UiApi::RecordRepresentation:131x00000000khZAAQ\',\'UiApi::RecordRepresentation:131x00000000khOAAQ\',\'UiApi::RecordRepresentation:131x00000000jN0AAI\',\'UiApi::RecordRepresentation:131x00000000khJAAQ\',\'UiApi::RecordRepresentation:131x00000000kheAAA\',\'UiApi::RecordRepresentation:131x00000000jMkAAI\',\'UiApi::RecordRepresentation:131x00000000jMvAAI\',\'UiApi::RecordRepresentation:131x00000000khIAAQ\',\'UiApi::RecordRepresentation:131x00000000khdAAA\',\'UiApi::RecordRepresentation:131x00000000khjAAA\',\'UiApi::RecordRepresentation:131x00000000jMpAAI\',\'UiApi::RecordRepresentation:131x00000000khTAAQ\',\'UiApi::RecordRepresentation:131x00000000khDAAQ\',\'UiApi::RecordRepresentation:131x00000000khlAAA\',\'UiApi::RecordRepresentation:131x00000000khKAAQ\',\'UiApi::RecordRepresentation:131x00000000khfAAA\',\'UiApi::RecordRepresentation:131x00000000j75AAA\',\'UiApi::RecordRepresentation:131x00000000khVAAQ\',\'UiApi::RecordRepresentation:131x00000000khaAAA\',\'UiApi::RecordRepresentation:131x00000000jMgAAI\',\'UiApi::RecordRepresentation:131x00000000khkAAA\',\'UiApi::RecordRepresentation:131x00000000khUAAQ\',\'UiApi::RecordRepresentation:131x00000000jN2AAI\',\'UiApi::RecordRepresentation:131x00000000jMlAAI\',\'UiApi::RecordRepresentation:131x00000000khPAAQ\',\'UiApi::RecordRepresentation:131x00000000jMmAAI\',\'UiApi::RecordRepresentation:131x00000000khbAAA\',\'UiApi::RecordRepresentation:131x00000000khhAAA\',\'UiApi::RecordRepresentation:131x00000000jMWAAY\',\'UiApi::RecordRepresentation:131x00000000khRAAQ\',\'UiApi::RecordRepresentation:131x00000000jMrAAI\',\'UiApi::RecordRepresentation:131x00000000jMbAAI\',\'UiApi::RecordRepresentation:131x00000000khgAAA\',\'UiApi::RecordRepresentation:131x00000000jMsAAI\',\'UiApi::RecordRepresentation:131x00000000khQAAQ\',\'UiApi::RecordRepresentation:131x00000000jMhAAI\',\'UiApi::RecordRepresentation:131x00000000khWAAQ\',\'UiApi::RecordRepresentation:131x00000000khLAAQ\',\'UiApi::RecordRepresentation:001x0000004ckZXAAY\',\'UiApi::RecordRepresentation:001x0000004ckZcAAI\',\'UiApi::RecordRepresentation:001x0000004cka2AAA\',\'UiApi::RecordRepresentation:001x0000004ckZnAAI\',\'UiApi::RecordRepresentation:001x0000004ckZsAAI\',\'UiApi::RecordRepresentation:001x0000004cka7AAA\',\'UiApi::RecordRepresentation:001x0000004ckZhAAI\',\'UiApi::RecordRepresentation:001x0000004ckZxAAI\',\'UiApi::RecordRepresentation:001x0000004ckZRAAY\',\'UiApi::RecordRepresentation:001x0000004cka8AAA\',\'UiApi::RecordRepresentation:001x0000004ckZmAAI\',\'UiApi::RecordRepresentation:001x0000004ckZbAAI\',\'UiApi::RecordRepresentation:001x0000004ckZWAAY\',\'UiApi::RecordRepresentation:001x0000004cka6AAA\',\'UiApi::RecordRepresentation:001x0000004ckZzAAI\',\'UiApi::RecordRepresentation:001x0000004ckZTAAY\',\'UiApi::RecordRepresentation:001x0000004ckZYAAY\',\'UiApi::RecordRepresentation:001x0000004ckZdAAI\',\'UiApi::RecordRepresentation:001x0000004ckZoAAI\',\'UiApi::RecordRepresentation:001x0000004ckZtAAI\',\'UiApi::RecordRepresentation:001x0000004ckZiAAI\',\'UiApi::RecordRepresentation:001x0000004ckZNAAY\',\'UiApi::RecordRepresentation:001x0000004ckaAAAQ\',\'UiApi::RecordRepresentation:001x0000004ckZyAAI\',\'UiApi::RecordRepresentation:001x0000004ckZSAAY\',\'UiApi::RecordRepresentation:001x0000004cka1AAA\',\'UiApi::RecordRepresentation:001x0000004ckZvAAI\',\'UiApi::RecordRepresentation:001x0000004ckZPAAY\',\'UiApi::RecordRepresentation:001x0000004ckZkAAI\',\'UiApi::RecordRepresentation:001x0000004ckZUAAY\',\'UiApi::RecordRepresentation:001x0000004ckZeAAI\',\'UiApi::RecordRepresentation:001x0000004ckZZAAY\',\'UiApi::RecordRepresentation:001x0000004cka0AAA\',\'UiApi::RecordRepresentation:001x0000004ckZpAAI\',\'UiApi::RecordRepresentation:001x0000004cka5AAA\',\'UiApi::RecordRepresentation:001x0000004ckZuAAI\',\'UiApi::RecordRepresentation:001x0000004ckZOAAY\',\'UiApi::RecordRepresentation:001x0000004ckZjAAI\',\'UiApi::RecordRepresentation:001x0000004ckZrAAI\',\'UiApi::RecordRepresentation:001x0000004ckZgAAI\',\'UiApi::RecordRepresentation:001x0000004ckZwAAI\',\'UiApi::RecordRepresentation:001x0000004ckZlAAI\',\'UiApi::RecordRepresentation:001x0000004ckZQAAY\',\'UiApi::RecordRepresentation:001x0000004cka3AAA\',\'UiApi::RecordRepresentation:001x0000004ckZaAAI\',\'UiApi::RecordRepresentation:001x0000004ckZVAAY\',\'UiApi::RecordRepresentation:001x0000004cka4AAA\',\'UiApi::RecordRepresentation:001x0000004cka9AAA\',\'UiApi::RecordRepresentation:001x0000004ckZqAAI\',\'UiApi::RecordRepresentation:001x0000004ckZfAAI\',\'UiApi::RecordRepresentation:0WOx0000005kEBhGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEBXGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEAAGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA6GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAQGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA0GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEBSGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBcGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAFGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEAGGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA1GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEBRGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBbGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEALGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBTGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBdGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAMGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEARGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA7GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEABGA2\',\'UiApi::RecordRepresentation:0WOx0000005kE9zGAE\',\'UiApi::RecordRepresentation:0WOx0000005kECjGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEASGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA8GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEACGA2\',\'UiApi::RecordRepresentation:0WOx0000005kE9yGAE\',\'UiApi::RecordRepresentation:0WOx0000005kEAHGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA2GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEBYGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEAIGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA3GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEANGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBZGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEAOGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBUGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA9GAM\',\'UiApi::RecordRepresentation:0WOx0000005kE9xGAE\',\'UiApi::RecordRepresentation:0WOx0000005kEADGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBeGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAEGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBgGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEBWGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA4GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAJGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBVGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBfGAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAKGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEA5GAM\',\'UiApi::RecordRepresentation:0WOx0000005kEAPGA2\',\'UiApi::RecordRepresentation:0WOx0000005kEBaGAM\',\'UiApi::RecordRepresentation:08px000000JaaADAAZ\',\'UiApi::RecordRepresentation:08px000000JaaATAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAuAAJ\',\'UiApi::RecordRepresentation:08px000000JaaA9AAJ\',\'UiApi::RecordRepresentation:08px000000JaaAYAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAdAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAIAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAZAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAeAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAJAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAOAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAoAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAjAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAHAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAcAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAXAAZ\',\'UiApi::RecordRepresentation:08px000000JaaA8AAJ\',\'UiApi::RecordRepresentation:08px000000JaaAMAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAhAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAtAAJ\',\'UiApi::RecordRepresentation:08px000000JaaANAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAiAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAsAAJ\',\'UiApi::RecordRepresentation:08px000000JaaACAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAnAAJ\',\'UiApi::RecordRepresentation:08px000000JaaASAAZ\',\'UiApi::RecordRepresentation:08px000000JaaALAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAgAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAAAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAlAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAQAAZ\',\'UiApi::RecordRepresentation:08px000000JaaABAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAmAAJ\',\'UiApi::RecordRepresentation:08px000000JaaARAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAbAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAGAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAWAAZ\',\'UiApi::RecordRepresentation:08px000000JaaArAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAPAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAkAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAqAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAEAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAUAAZ\',\'UiApi::RecordRepresentation:08px000000JaaApAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAaAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAFAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAVAAZ\',\'UiApi::RecordRepresentation:08px000000JaaAfAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAvAAJ\',\'UiApi::RecordRepresentation:08px000000JaaAKAAZ\')";
		long start = System.nanoTime();
		q.replaceAll(regexp, "json_extract($1.soup");
		return (System.nanoTime() - start) / 1000000.0;
	}

	/**
	 * Load some datq in the smart store
	 * @throws JSONException 
	 */
	private void loadData() throws JSONException {

		// Employees
		createEmployee("Christine", "Haas", "A00", "00010", null, 200000, true);
		createEmployee("Michael", "Thompson", "A00", "00020", "00010", 120000, false);
		createEmployee("Sally", "Kwan", "A00", "00310", "00010", 100000, false);
		createEmployee("John", "Geyer", "B00", "00040", null, 102000, true);
		createEmployee("Irving", "Stern", "B00", "00050", "00040", 100000, true);
		createEmployee("Eva", "Pulaski", "B00", "00060", "00050", 80000, false);
		createEmployee("Eileen", "Henderson", "B00", "00070", "00050", 70000, false);
		
		// Departments
		createDepartment("A00", "Sales", 1000000);
		createDepartment("B00", "R&D", 2000000);
	}

	private void createEmployee(String firstName, String lastName, String deptCode, String employeeId, String managerId, int salary, boolean isManager) throws JSONException {
		JSONObject employee = new JSONObject();
		employee.put(FIRST_NAME, firstName);
		employee.put(LAST_NAME, lastName);
		employee.put(DEPT_CODE, deptCode);
		employee.put(EMPLOYEE_ID, employeeId);
		employee.put(MANAGER_ID, managerId);
		employee.put(SALARY, salary);
		employee.put(IS_MANAGER, isManager);
        store.create(EMPLOYEES_SOUP, employee);
		
	}

	private JSONObject createEmployeeWithJsonString(String json) throws JSONException {
	    JSONObject employee = new JSONObject(json);
	    return store.create(EMPLOYEES_SOUP, employee);
    }
	
	private void createDepartment(String deptCode, String name, int budget) throws JSONException {
		JSONObject department = new JSONObject();
		department.put(DEPT_CODE, deptCode);
		department.put(NAME, name);
		department.put(BUDGET, budget);
        store.create(DEPARTMENTS_SOUP, department);
	}
}
