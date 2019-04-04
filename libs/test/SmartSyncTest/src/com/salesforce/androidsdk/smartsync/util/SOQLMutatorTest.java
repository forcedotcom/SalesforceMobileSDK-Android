/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Test class for SyncState.
 */
@RunWith(AndroidJUnit4.class)
public class SOQLMutatorTest {

    @Test
    public void testMutatorNoChange() {
        String soql = "select Id, Name from Account where Id in (select Id from Account) and Name like 'Mad Max' limit 1000";
        Assert.assertEquals(soql, new SOQLMutator(soql).asBuilder().build());
    }

    @Test
    public void testSelectFieldPresenceWhenPresent() {
        String soql = "SELECT Id, Name FROM Account";
        Assert.assertTrue(new SOQLMutator(soql).isSelectingField("Id"));
        Assert.assertTrue(new SOQLMutator(soql).isSelectingField("Name"));
    }

    @Test
    public void testSelectFieldPresenceWhenAbsent() {
        String soql = "SELECT Id, Name FROM Account";
        Assert.assertFalse(new SOQLMutator(soql).isSelectingField("Description"));
    }

    @Test
    public void testSelectFieldPresenceWhenPresentInWhereClause() {
        String soql = "SELECT Id FROM Account WHERE Name like 'James%'";
        Assert.assertFalse(new SOQLMutator(soql).isSelectingField("Name"));
    }

    @Test
    public void testSelectFieldPresenceWhenPresentInSubquery() {
        Assert.assertFalse(new SOQLMutator("SELECT Name, (SELECT LastName FROM Contacts) FROM Account").isSelectingField("LastName"));
    }

    @Test
    public void testSelectFieldPresenceWhenPresentAsSubstring() {
        Assert.assertFalse(new SOQLMutator("SELECT LastName FROM Account").isSelectingField("Name"));
    }

    @Test
    public void testOrderByPresenceWhenPresent() {
        Assert.assertTrue(new SOQLMutator("SELECT LastName FROM Account ORDER BY LastModifiedDate").isOrderingBy("LastModifiedDate"));
    }

    @Test
    public void testOrderByPresenceWhenPresentInSubquery() {
        Assert.assertFalse(new SOQLMutator("SELECT LastName FROM Account WHERE Id IN (SELECT Id FROM Account ORDER BY LastModifiedDate)").isOrderingBy("LastModifiedDate"));
    }

    @Test
    public void testOrderByPresenceWhenAbsent() {
        Assert.assertFalse(new SOQLMutator("SELECT LastName FROM Account").isOrderingBy("LastModifiedDate"));
    }

    @Test
    public void testOrderByPresenceWhenOrderingBySomethingElse() {
        Assert.assertFalse(new SOQLMutator("SELECT LastName FROM Account ORDER BY FirstName").isOrderingBy("LastModifiedDate"));
    }

    @Test
    public void testAddSelectField() {
        String soql = "SELECT Description FROM Account";
        Assert.assertEquals("select Id,Name,Description from Account", new SOQLMutator(soql).addSelectFields("Name").addSelectFields("Id").asBuilder().build());
    }

    @Test
    public void testReplaceSelectField() {
        String soql = "SELECT Description FROM Account";
        Assert.assertEquals("select Id from Account", new SOQLMutator(soql).replaceSelectFields("Id").asBuilder().build());
    }

    @Test
    public void testAddWherePredicateWhenWhereClausePresent() {
        String soql = "SELECT Description FROM Account WHERE FirstName = 'James'";
        Assert.assertEquals("select Description from Account where LastModifiedDate > 123 and FirstName = 'James'", new SOQLMutator(soql).addWherePredicates("LastModifiedDate > 123").asBuilder().build());
    }

    @Test
    public void testAddWherePredicateWhenWhereClauseAbsent() {
        String soql = "SELECT Description FROM Account";
        Assert.assertEquals("select Description from Account where LastModifiedDate > 123", new SOQLMutator(soql).addWherePredicates("LastModifiedDate > 123").asBuilder().build());
    }

    @Test
    public void testReplaceOrderBy() {
        String soql = "SELECT Description FROM Account";
        Assert.assertEquals("select Description from Account order by LastModifiedDate", new SOQLMutator(soql).replaceOrderBy("LastModifiedDate").asBuilder().build());
    }

    @Test
    public void testReplaceOrderByWhenLimit() {
        String soql = "SELECT Description FROM Account LIMIT 1000";
        Assert.assertEquals("select Description from Account order by LastModifiedDate limit 1000", new SOQLMutator(soql).replaceOrderBy("LastModifiedDate").asBuilder().build());
    }


    @Test
    public void testDropOrderBy() {
        String soql = "SELECT Description FROM Account ORDER BY FirstName";
        Assert.assertEquals("select Description from Account", new SOQLMutator(soql).replaceOrderBy("").asBuilder().build());
    }

    @Test
    public void testDropOrderByWhenLimit() {
        String soql = "SELECT Description FROM Account ORDER BY FirstName LIMIT 1000";
        Assert.assertEquals("select Description from Account limit 1000", new SOQLMutator(soql).replaceOrderBy("").asBuilder().build());
    }

    @Test
    public void testHasOrderByWhenPresent() {
        Assert.assertTrue(new SOQLMutator("SELECT Description FROM Account ORDER BY FirstName LIMIT 1000").hasOrderBy());
    }

    @Test
    public void testHasOrderByWhenPresentInSubquery() {
        Assert.assertFalse(new SOQLMutator("SELECT Description FROM Account WHERE Id IN (SELECT Id FROM Account ORDER BY FirstName) LIMIT 1000").hasOrderBy());
    }

    @Test
    public void testHasOrderByWhenPresentInValue() {
        Assert.assertFalse(new SOQLMutator("SELECT Description FROM Account WHERE Name = ' order by \' order by \''").hasOrderBy());
    }

    @Test
    public void testHasOrderByWhenAbsent() {
        Assert.assertFalse(new SOQLMutator("SELECT Description FROM Account LIMIT 1000").hasOrderBy());
    }


}
