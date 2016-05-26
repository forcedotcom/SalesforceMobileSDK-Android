/*
 * Copyright (c) 2016, salesforce.com, inc.
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

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.smartstore.store.QuerySpec;

/**
 * Test class for QuerySpecTest
 *
 */
public class QuerySpecTest extends InstrumentationTestCase {

    public void testAllQuerySmartSql() {
    QuerySpec querySpec = QuerySpec.buildAllQuerySpec("employees", "lastName", QuerySpec.Order.descending, 1);
    assertEquals("Wrong smart sql for all query spec", "SELECT {employees:_soup} FROM {employees} ORDER BY {employees}.{employees:lastName} DESC ", querySpec.smartSql);
    }

    public void testAllQueryCountSmartSql() {
        QuerySpec querySpec = QuerySpec.buildAllQuerySpec("employees", "lastName", QuerySpec.Order.descending, 1);
        assertEquals("Wrong count smart sql for all query spec", "SELECT count(*) FROM {employees} ", querySpec.countSmartSql);
    }

    public void testAllQueryIdsSmartSql() {
        QuerySpec querySpec = QuerySpec.buildAllQuerySpec("employees", "lastName", QuerySpec.Order.descending, 1);
        assertEquals("Wrong ids smart sql for all query spec", "SELECT id FROM {employees} ORDER BY {employees}.{employees:lastName} DESC ", querySpec.idsSmartSql);
    }

    public void testRangeQuerySmartSql() {
        QuerySpec querySpec = QuerySpec.buildRangeQuerySpec("employees", "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong smart sql for range query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ORDER BY {employees}.{employees:lastName} ASC ", querySpec.smartSql);
    }

    public void testRangeQueryCountSmartSql() {
        QuerySpec querySpec = QuerySpec.buildRangeQuerySpec("employees", "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong count smart sql for range query spec", "SELECT count(*) FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ", querySpec.countSmartSql);
    }

    public void testRangeQueryIdsSmartSql() {
        QuerySpec querySpec = QuerySpec.buildRangeQuerySpec("employees", "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong ids smart sql for range query spec", "SELECT id FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ORDER BY {employees}.{employees:lastName} ASC ", querySpec.idsSmartSql);
    }

    public void testExactQuerySmartSql()
    {
        QuerySpec querySpec = QuerySpec.buildExactQuerySpec("employees", "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong smart sql for exact query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:lastName} = ? ORDER BY {employees}.{employees:lastName} ASC ", querySpec.smartSql);
    }

    public void testExactQueryCountSmartSql() {
        QuerySpec querySpec = QuerySpec.buildExactQuerySpec("employees", "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong count smart sql for exact query spec", "SELECT count(*) FROM {employees} WHERE {employees:lastName} = ? ", querySpec.countSmartSql);
    }

    public void testExactQueryIdsSmartSql() {
        QuerySpec querySpec = QuerySpec.buildExactQuerySpec("employees", "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong ids smart sql for exact query spec", "SELECT id FROM {employees} WHERE {employees:lastName} = ? ORDER BY {employees}.{employees:lastName} ASC ", querySpec.idsSmartSql);
    }

    public void testMatchQuerySmartSql() {
        QuerySpec querySpec = QuerySpec.buildMatchQuerySpec("employees", "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong smart sql for match query spec", "SELECT {employees:_soup} FROM {employees}, {employees}_fts WHERE {employees}_fts.docid = {employees:_soupEntryId} AND {employees}_fts.{employees:lastName} MATCH 'Bond' ORDER BY {employees}.{employees:firstName} ASC ", querySpec.smartSql);
    }

    public void testMatchQueryCountSmartSql() {
        QuerySpec querySpec = QuerySpec.buildMatchQuerySpec("employees", "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong count smart sql for match query spec", "SELECT count(*) FROM {employees}, {employees}_fts WHERE {employees}_fts.docid = {employees:_soupEntryId} AND {employees}_fts.{employees:lastName} MATCH 'Bond' ", querySpec.countSmartSql);
    }

    public void testMatchQueryIdsSmartSql() {
        QuerySpec querySpec = QuerySpec.buildMatchQuerySpec("employees", "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong ids smart sql for match query spec", "SELECT id FROM {employees}, {employees}_fts WHERE {employees}_fts.docid = {employees:_soupEntryId} AND {employees}_fts.{employees:lastName} MATCH 'Bond' ORDER BY {employees}.{employees:firstName} ASC ", querySpec.idsSmartSql);
    }

    public void testLikeQuerySmartSql() {
        QuerySpec querySpec = QuerySpec.buildLikeQuerySpec("employees", "lastName", "Bon%" , "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong smart sql for like query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:lastName} LIKE ? ORDER BY {employees}.{employees:lastName} ASC ", querySpec.smartSql);
    }

    public void testLikeQueryCountSmartSql() {
        QuerySpec querySpec = QuerySpec.buildLikeQuerySpec("employees", "lastName", "Bon%" , "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong count smart sql for like query spec", "SELECT count(*) FROM {employees} WHERE {employees:lastName} LIKE ? ", querySpec.countSmartSql);
    }

    public void testLikeQueryIdsSmartSql() {
        QuerySpec querySpec = QuerySpec.buildLikeQuerySpec("employees", "lastName", "Bon%" , "lastName", QuerySpec.Order.ascending, 1);
        assertEquals("Wrong ids smart sql for like query spec", "SELECT id FROM {employees} WHERE {employees:lastName} LIKE ? ORDER BY {employees}.{employees:lastName} ASC ", querySpec.idsSmartSql);
    }

    public void testSmartQueryCountSmartSql() {
        QuerySpec querySpec = QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1);
        assertEquals("Wrong count smart sql", "SELECT count(*) FROM (select {employees:salary} from {employees} where {employees:lastName} = 'Haas')", querySpec.countSmartSql);
    }

    public void testSmartQueryIdsSmartSql() {
        QuerySpec querySpec = QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1);
        assertEquals("Wrong ids smart sql", "SELECT id FROM (select {employees:salary} from {employees} where {employees:lastName} = 'Haas')", querySpec.idsSmartSql);
    }
}
