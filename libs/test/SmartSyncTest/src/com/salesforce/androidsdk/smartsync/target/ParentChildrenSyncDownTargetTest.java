package com.salesforce.androidsdk.smartsync.target;

/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.smartsync.util.ChildrenInfo;
import com.salesforce.androidsdk.smartsync.util.ParentInfo;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Test class for ParentChildrenSyncDownTarget
 *
 */
public class ParentChildrenSyncDownTargetTest extends TestCase {
    /**
     * Test getQuery for ParentChildrenSyncDownTarget
     */
    public void testGetQueryForParentChildrenSyncDownTarget() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "ChildId", "ChildLastModifiedDate", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                ParentChildrenSyncDownTarget.RelationshipType.LOOKUP);

        assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children) from Parent where School = 'MIT'", target.getQuery());

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                ParentChildrenSyncDownTarget.RelationshipType.LOOKUP);


        assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children) from Parent where School = 'MIT'", target.getQuery());
    }

    /**
     * Test getSoqlForRemoteIds for ParentChildrenSyncDownTarget
     */
    public void testGetSoqlForRemoteIdsForParentChildrenSyncDownTarget() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "ChildId", "ChildLastModifiedDate", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                ParentChildrenSyncDownTarget.RelationshipType.LOOKUP);

        assertEquals("select ParentId, (select ChildId from Children) from Parent where School = 'MIT'", target.getSoqlForRemoteIds());

        // With default id and modification date fields
        target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                ParentChildrenSyncDownTarget.RelationshipType.LOOKUP);

        assertEquals("select Id, (select Id from Children) from Parent where School = 'MIT'", target.getSoqlForRemoteIds());
    }

    /**
     * Test getDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    public void testGetDirtyRecordIdsSqlForParentChildrenSyncDownTarget() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "ChildId", "ChildLastModifiedDate", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                ParentChildrenSyncDownTarget.RelationshipType.LOOKUP);

        assertEquals("SELECT DISTINCT {ParentSoup:IdForQuery} FROM {childrenSoup},{ParentSoup} WHERE {childrenSoup:parentLocalId} = {ParentSoup:_soupEntryId} AND ({ParentSoup:__local__} = 'true' OR {childrenSoup:__local__} = 'true')", target.getDirtyRecordIdsSql("ParentSoup", "IdForQuery"));
    }


    /**
     * Test getNonDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    public void testGetNonDirtyRecordIdsSqlForParentChildrenSyncDownTarget() {
        ParentChildrenSyncDownTarget target = new ParentChildrenSyncDownTarget(
                new ParentInfo("Parent", "ParentId", "ParentModifiedDate"),
                Arrays.asList("ParentName", "Title"),
                "School = 'MIT'",
                new ChildrenInfo("Child", "Children", "ChildId", "ChildLastModifiedDate", "childrenSoup", "parentId", "parentLocalId"),
                Arrays.asList("ChildName", "School"),
                ParentChildrenSyncDownTarget.RelationshipType.LOOKUP);

        assertEquals("SELECT DISTINCT {ParentSoup:IdForQuery} FROM {childrenSoup},{ParentSoup} WHERE {childrenSoup:parentLocalId} = {ParentSoup:_soupEntryId} AND ({ParentSoup:__local__} = 'false' OR {childrenSoup:__local__} = 'false')", target.getNonDirtyRecordIdsSql("ParentSoup", "IdForQuery"));

    }


}
