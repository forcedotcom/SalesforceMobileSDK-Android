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

import com.salesforce.androidsdk.util.JSTestCase;

/**
 * Running javascript tests for SmartStore plugin
 */
public class SmartStoreJSTest extends JSTestCase {

    public SmartStoreJSTest() {
        super("SmartStoreTestSuite");
    }

    @Override
    public List<String> getTestNames() {
    	return Arrays.asList(new String[] {"testSmartQueryWithCount", "testSmartQueryWithSpecialFields", "testArbitrarySoupNames", "testCompoundQueryPath", "testEmptyQuerySpec", "testIntegerQuerySpec", "testLikeQueryInnerText", "testLikeQuerySpecEndsWith", "testLikeQuerySpecStartsWith", "testManipulateCursor", "testMoveCursorToNextPageFromLastPage", "testMoveCursorToPreviousPageFromFirstPage", "testQuerySoup", "testQuerySoupBadQuerySpec", "testQuerySoupBeginKeyNoEndKey", "testQuerySoupDescending", "testQuerySoupEndKeyNoBeginKey", "testQuerySpecFactories", "testRegisterBogusSoup", "testRegisterRemoveSoup", "testRegisterSoupNoIndices", "testRemoveFromSoup", "testRetrieveSoupEntries", "testUpsertSoupEntries", "testUpsertSoupEntriesWithExternalId", "testUpsertToNonexistentSoup"});
    }
    
    public void testRegisterRemoveSoup()  {
        runTest("testRegisterRemoveSoup");
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

    public void testQuerySoup()  {
        runTest("testQuerySoup");
    }

    public void testQuerySoupDescending()  {
        runTest("testQuerySoupDescending");
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
}
