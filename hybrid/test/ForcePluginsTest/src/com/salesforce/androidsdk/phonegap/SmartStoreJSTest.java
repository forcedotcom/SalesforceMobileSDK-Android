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
package com.salesforce.androidsdk.phonegap;

import java.util.Arrays;
import java.util.List;

import com.salesforce.androidsdk.util.test.JSTestCase;

/**
 * Running javascript tests for SmartStore plugin
 */
public class SmartStoreJSTest extends JSTestCase {

    public SmartStoreJSTest() {
        super("SmartStoreTestSuite");
    }
    
    @Override
    protected int getMaxRuntimeInSecondsForTest(String testName) {
        return 10;
    }

    @Override
    public List<String> getTestNames() {
        return Arrays.asList(new String[] {
                "testGetDatabaseSize",
                "testRegisterRemoveSoup",
                "testRegisterRemoveSoupGlobalStore",
                "testRegisterBogusSoup",
                "testRegisterSoupNoIndices",
                "testUpsertSoupEntries",
                "testUpsertSoupEntriesWithExternalId",
                "testUpsertToNonexistentSoup",
                "testRetrieveSoupEntries",
                "testRemoveFromSoup",
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
                "testAlterSoupWithBogusSoupName",
                "testReIndexSoup",
                "testClearSoup"
        });
    }
    

    public void testGetDatabaseSize() {
        runTest("testGetDatabaseSize");
    }

    public void testRegisterRemoveSoup()  {
        runTest("testRegisterRemoveSoup");
    }

    public void testRegisterRemoveSoupGlobalStore()  {
        runTest("testRegisterRemoveSoupGlobalStore");
    }

    public void testRegisterBogusSoup()  {
        runTest("testRegisterBogusSoup");
    }

    public void testRegisterSoupNoIndices()  {
        runTest("testRegisterSoupNoIndices");
    }

    public void testUpsertSoupEntries()  {
        runTest("testUpsertSoupEntries");
    }

    public void testUpsertSoupEntriesWithExternalId()  {
        runTest("testUpsertSoupEntriesWithExternalId");
    }
    
    public void testUpsertToNonexistentSoup()  {
        runTest("testUpsertToNonexistentSoup");
    }

    public void testRetrieveSoupEntries()  {
        runTest("testRetrieveSoupEntries");
    }

    public void testRemoveFromSoup()  {
        runTest("testRemoveFromSoup");
    }

    public void testQuerySoupWithExactQuery()  {
        runTest("testQuerySoupWithExactQuery");
    }

    public void testQuerySoupWithAllQueryDescending()  {
        runTest("testQuerySoupWithAllQueryDescending");
    }

    public void testQuerySoupWithRangeQueryWithOrderPath() {
        runTest("testQuerySoupWithRangeQueryWithOrderPath");
    }

    public void testQuerySoupBadQuerySpec()  {
        runTest("testQuerySoupBadQuerySpec");
    }

    public void testQuerySoupEndKeyNoBeginKey()  {
        runTest("testQuerySoupEndKeyNoBeginKey");
    }

    public void testQuerySoupBeginKeyNoEndKey()  {
        runTest("testQuerySoupBeginKeyNoEndKey");
    }

    public void testManipulateCursor()  {
        runTest("testManipulateCursor");
    }

    public void testMoveCursorToPreviousPageFromFirstPage() {
        runTest("testMoveCursorToPreviousPageFromFirstPage");
    }

    public void testMoveCursorToNextPageFromLastPage() {
        runTest("testMoveCursorToNextPageFromLastPage");
    }

    public void testArbitrarySoupNames()  {
        runTest("testArbitrarySoupNames");
    }

    public void testQuerySpecFactories()  {
        runTest("testQuerySpecFactories");
    }

    public void testLikeQuerySpecStartsWith()  {
        runTest("testLikeQuerySpecStartsWith");
    }

    public void testLikeQuerySpecEndsWith()  {
        runTest("testLikeQuerySpecEndsWith");
    }

    public void testLikeQueryInnerText()  {
        runTest("testLikeQueryInnerText");
    }

    public void testFullTextSearch() {
        runTest("testFullTextSearch");
    }

    public void testCompoundQueryPath()  {
        runTest("testCompoundQueryPath");
    }

    public void testEmptyQuerySpec()  {
        runTest("testEmptyQuerySpec");
    }

    public void testIntegerQuerySpec()  {
        runTest("testIntegerQuerySpec");
    }
    
    public void testSmartQueryWithCount() {
        runTest("testSmartQueryWithCount");
    }

    public void testSmartQueryWithSpecialFields() {
        runTest("testSmartQueryWithSpecialFields");
    }

    public void testSmartQueryWithIntegerCompare() {
        runTest("testSmartQueryWithIntegerCompare");
    }

    public void testSmartQueryWithMultipleFieldsAndWhereInClause() {
        runTest("testSmartQueryWithMultipleFieldsAndWhereInClause");
    }

    public void testSmartQueryWithSingleFieldAndWhereInClause() {
        runTest("testSmartQueryWithSingleFieldAndWhereInClause");
    }

    public void testSmartQueryWithWhereLikeClause() {
        runTest("testSmartQueryWithWhereLikeClause");
    }

    public void testSmartQueryWithWhereLikeClauseOrdered() {
        runTest("testSmartQueryWithWhereLikeClauseOrdered");
    }

    public void testGetSoupIndexSpecs() {
        runTest("testGetSoupIndexSpecs");
    }
    
    public void testGetSoupIndexSpecsWithBogusSoupName() {
        runTest("testGetSoupIndexSpecsWithBogusSoupName");
    }
    

    public void testAlterSoupNoReIndexing() {
        runTest("testAlterSoupNoReIndexing");
    }

    public void testAlterSoupWithReIndexing() {
        runTest("testAlterSoupWithReIndexing");
    }

    public void testAlterSoupWithBogusSoupName() {
        runTest("testAlterSoupWithBogusSoupName");
    }

    public void testReIndexSoup() {
        runTest("testReIndexSoup");
    }

    public void testClearSoup() {
        runTest("testClearSoup");
    }
}
