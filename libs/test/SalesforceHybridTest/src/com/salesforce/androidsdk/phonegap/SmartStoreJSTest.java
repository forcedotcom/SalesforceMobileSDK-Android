/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap;

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Running javascript tests for SmartStore plugin.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SmartStoreJSTest extends JSTestCase {

    public SmartStoreJSTest() {
        super("SmartStoreTestSuite");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected int getMaxRuntimeInSecondsForTest(String testName) {
        return 30;
    }

    @Override
    public List<String> getTestNames() {
        return Arrays.asList(new String[] {
                "testGetDatabaseSize",
                "testRegisterRemoveSoup",
                "testRegisterWithSpec",
                "testRegisterRemoveSoupGlobalStore",
                "testRegisterBogusSoup",
                "testRegisterSoupNoIndices",
                "testUpsertSoupEntries",
                "testUpsertSoupEntriesWithExternalId",
                "testUpsertToNonexistentSoup",
                "testRetrieveSoupEntries",
                "testRemoveFromSoup",
                "testRemoveFromSoupByQuery",
                "testQuerySoupWithExactQuery",
                "testQuerySoupWithAllQueryDescending",
                "testQuerySoupWithRangeQueryWithOrderPath",
                "testQuerySoupBadQuerySpec",
                "testQuerySoupEndKeyNoBeginKey",
                "testQuerySoupBeginKeyNoEndKey",
                "testManipulateCursor",
                "testMoveCursorToPreviousPageFromFirstPage",
                "testMoveCursorToNextPageFromLastPage",
                "testArbitrarySoupNames",
                "testQuerySpecFactories",
                "testLikeQuerySpecStartsWith",
                "testLikeQuerySpecEndsWith",
                "testLikeQueryInnerText",
                "testFullTextSearch",
                "testCompoundQueryPath",
                "testEmptyQuerySpec",
                "testIntegerQuerySpec",
                "testSmartQueryWithCount",
                "testSmartQueryWithSpecialFields",
                "testSmartQueryWithIntegerCompare",
                "testSmartQueryWithMultipleFieldsAndWhereInClause",
                "testSmartQueryWithSingleFieldAndWhereInClause",
                "testSmartQueryWithWhereLikeClause",
                "testSmartQueryWithWhereLikeClauseOrdered",
                "testGetSoupIndexSpecs",
                "testGetSoupIndexSpecsWithBogusSoupName",
                "testAlterSoupNoReIndexing",
                "testAlterSoupWithReIndexing",
                "testAlterSoupWithSpecNoReIndexing",
                "testAlterSoupWithSpecWithReIndexing",
                "testAlterSoupWithBogusSoupName",
                "testReIndexSoup",
                "testClearSoup",
                "testFullTextSearchAgainstArrayNode",
                "testLikeQueryAgainstArrayNode",
                "testExactQueryAgainstArrayNode",
                "testSmartQueryAgainstArrayNode",
                "testCreateMultipleGlobalStores",
                "testCreateMultipleUserStores"
        });
    }

    @Test
    public void testGetDatabaseSize() {
        runTest("testGetDatabaseSize");
    }

    @Test
    public void testRegisterRemoveSoup()  {
        runTest("testRegisterRemoveSoup");
    }

    @Test
    public void testRegisterWithSpec()  {
        runTest("testRegisterRemoveSoup");
    }

    @Test
    public void testRegisterRemoveSoupGlobalStore() {
        runTest("testRegisterRemoveSoupGlobalStore");
    }

    @Test
    public void testRegisterBogusSoup()  {
        runTest("testRegisterBogusSoup");
    }

    @Test
    public void testRegisterSoupNoIndices()  {
        runTest("testRegisterSoupNoIndices");
    }

    @Test
    public void testUpsertSoupEntries()  {
        runTest("testUpsertSoupEntries");
    }

    @Test
    public void testUpsertSoupEntriesWithExternalId() {
        runTest("testUpsertSoupEntriesWithExternalId");
    }

    @Test
    public void testUpsertToNonexistentSoup()  {
        runTest("testUpsertToNonexistentSoup");
    }

    @Test
    public void testRetrieveSoupEntries()  {
        runTest("testRetrieveSoupEntries");
    }

    @Test
    public void testRemoveFromSoup()  {
        runTest("testRemoveFromSoup");
    }

    @Test
    public void testRemoveFromSoupByQuery()  {
        runTest("testRemoveFromSoupByQuery");
    }

    @Test
    public void testQuerySoupWithExactQuery()  {
        runTest("testQuerySoupWithExactQuery");
    }

    @Test
    public void testQuerySoupWithAllQueryDescending() {
        runTest("testQuerySoupWithAllQueryDescending");
    }

    @Test
    public void testQuerySoupWithRangeQueryWithOrderPath() {
        runTest("testQuerySoupWithRangeQueryWithOrderPath");
    }

    @Test
    public void testQuerySoupBadQuerySpec()  {
        runTest("testQuerySoupBadQuerySpec");
    }

    @Test
    public void testQuerySoupEndKeyNoBeginKey()  {
        runTest("testQuerySoupEndKeyNoBeginKey");
    }

    @Test
    public void testQuerySoupBeginKeyNoEndKey()  {
        runTest("testQuerySoupBeginKeyNoEndKey");
    }

    @Test
    public void testManipulateCursor()  {
        runTest("testManipulateCursor");
    }

    @Test
    public void testMoveCursorToPreviousPageFromFirstPage() {
        runTest("testMoveCursorToPreviousPageFromFirstPage");
    }

    @Test
    public void testMoveCursorToNextPageFromLastPage() {
        runTest("testMoveCursorToNextPageFromLastPage");
    }

    @Test
    public void testArbitrarySoupNames()  {
        runTest("testArbitrarySoupNames");
    }

    @Test
    public void testQuerySpecFactories()  {
        runTest("testQuerySpecFactories");
    }

    @Test
    public void testLikeQuerySpecStartsWith()  {
        runTest("testLikeQuerySpecStartsWith");
    }

    @Test
    public void testLikeQuerySpecEndsWith()  {
        runTest("testLikeQuerySpecEndsWith");
    }

    @Test
    public void testLikeQueryInnerText()  {
        runTest("testLikeQueryInnerText");
    }

    @Test
    public void testFullTextSearch() {
        runTest("testFullTextSearch");
    }

    @Test
    public void testCompoundQueryPath()  {
        runTest("testCompoundQueryPath");
    }

    @Test
    public void testEmptyQuerySpec()  {
        runTest("testEmptyQuerySpec");
    }

    @Test
    public void testIntegerQuerySpec()  {
        runTest("testIntegerQuerySpec");
    }

    @Test
    public void testSmartQueryWithCount() {
        runTest("testSmartQueryWithCount");
    }

    @Test
    public void testSmartQueryWithSpecialFields() {
        runTest("testSmartQueryWithSpecialFields");
    }

    @Test
    public void testSmartQueryWithIntegerCompare() {
        runTest("testSmartQueryWithIntegerCompare");
    }

    @Test
    public void testSmartQueryWithMultipleFieldsAndWhereInClause() {
        runTest("testSmartQueryWithMultipleFieldsAndWhereInClause");
    }

    @Test
    public void testSmartQueryWithSingleFieldAndWhereInClause() {
        runTest("testSmartQueryWithSingleFieldAndWhereInClause");
    }

    @Test
    public void testSmartQueryWithWhereLikeClause() {
        runTest("testSmartQueryWithWhereLikeClause");
    }

    @Test
    public void testSmartQueryWithWhereLikeClauseOrdered() {
        runTest("testSmartQueryWithWhereLikeClauseOrdered");
    }

    @Test
    public void testGetSoupIndexSpecs() {
        runTest("testGetSoupIndexSpecs");
    }

    @Test
    public void testGetSoupIndexSpecsWithBogusSoupName() {
        runTest("testGetSoupIndexSpecsWithBogusSoupName");
    }

    @Test
    public void testAlterSoupNoReIndexing() {
        runTest("testAlterSoupNoReIndexing");
    }

    @Test
    public void testAlterSoupWithReIndexing() {
        runTest("testAlterSoupWithReIndexing");
    }

    @Test
    public void testAlterSoupWithSpecNoReIndexing() {
        runTest("testAlterSoupWithSpecNoReIndexing");
    }

    @Test
    public void testAlterSoupWithSpecWithReIndexing() {
        runTest("testAlterSoupWithSpecWithReIndexing");
    }

    @Test
    public void testAlterSoupWithBogusSoupName() {
        runTest("testAlterSoupWithBogusSoupName");
    }

    @Test
    public void testReIndexSoup() {
        runTest("testReIndexSoup");
    }

    @Test
    public void testClearSoup() {
        runTest("testClearSoup");
    }

    @Test
    public void testFullTextSearchAgainstArrayNode() {
        runTest("testFullTextSearchAgainstArrayNode");
    }

    @Test
    public void testLikeQueryAgainstArrayNode() {
        runTest("testLikeQueryAgainstArrayNode");
    }

    @Test
    public void testExactQueryAgainstArrayNode() {
        runTest("testExactQueryAgainstArrayNode");
    }

    @Test
    public void testSmartQueryAgainstArrayNode() {
        runTest("testSmartQueryAgainstArrayNode");
    }

    @Test
    public void testCreateMultipleGlobalStores() {
        runTest("testCreateMultipleGlobalStores");
    }

    @Test
    public void testCreateMultipleUserStores() {
        runTest("testCreateMultipleUserStores");
    }
}
