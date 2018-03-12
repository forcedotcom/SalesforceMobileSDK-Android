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

import android.support.test.filters.MediumTest;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

/**
 * Running javascript tests for SmartStore plugin.
 */
@RunWith(Parameterized.class)
@MediumTest
public class SmartStoreJSTest extends JSTestCase {

    private static final String JS_SUITE = "SmartStoreTestSuite";

    @Parameterized.Parameter
    public String testName;

    @Parameterized.Parameters(name = "{0}")
    public static List<String> data() {
        return Arrays.asList(new String[]{
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

    @BeforeClass
    public static void runJSTestSuite() throws InterruptedException {
        JSTestCase.runJSTestSuite(JS_SUITE, data(), 30);
    }

    @Test
    public void test() {
        runTest(JS_SUITE, testName);
    }
}
