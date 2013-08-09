/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest.files;

import com.google.common.collect.Lists;
import com.salesforce.androidsdk.rest.RestRequest;

/**
 * 
 * @author jjiang
 */
public class FileRequestsTest extends ApiRequestsBaseTest {

    public void testBatchFileInfo() {
        RestRequest r = FileRequests.batchFileDetails(Lists.newArrayList("06930000001LkwtAAC", "06930000001LkwtAAD"));
        assertEquals(connectPath + "files/batch/06930000001LkwtAAC,06930000001LkwtAAD", r.getPath());
        doAdditionalVerifications(r);
        try {
            FileRequests.batchFileDetails(Lists.newArrayList("06930000001LkwtAAC", null));
            fail("should of thrown an exception");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.batchFileDetails(Lists.newArrayList("06930000001LkwtAAC", ""));
            fail("should of thrown an exception");
        } catch (IllegalArgumentException e) { /* expected */
        }
    }

    public void testFileContents() {
        assertEquals(connectPath + "files/" + sfdcId + "/content?versionNumber=1",
                FileRequests.fileContents(sfdcId, "1").getPath());
        try {
            FileRequests.fileContents("", "1");
            fail("empty fileId should throw an exception");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileContents(sfdcId, "1"));
    }

    public void testOwnedFilesList() {
        assertEquals(connectPath + "users/me/files", FileRequests.ownedFilesList(null, null).getPath());
        assertEquals(connectPath + "users/me/files?page=0", FileRequests.ownedFilesList(null, 0).getPath());
        assertEquals(connectPath + "users/" + userId + "/files?page=1", FileRequests.ownedFilesList(userId, 1)
                .getPath());
        try {
            FileRequests.ownedFilesList("", 1);
            fail("empty user id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.ownedFilesList(userId, 1));
    }

    public void testFilesInUsersGroups() {
        assertEquals(connectPath + "users/me/files/filter/groups", FileRequests.filesInUsersGroups(null, null)
                .getPath());
        assertEquals(connectPath + "users/me/files/filter/groups?page=0", FileRequests.filesInUsersGroups(null, 0)
                .getPath());
        assertEquals(connectPath + "users/" + userId + "/files/filter/groups?page=1",
                FileRequests.filesInUsersGroups(userId, 1).getPath());
        try {
            FileRequests.filesInUsersGroups("", 1);
            fail("empty user id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.filesInUsersGroups(userId, 1));
    }

    public void testFilesSharedWithUser() {
        assertEquals(connectPath + "users/me/files/filter/sharedwithme", FileRequests.filesSharedWithUser(null, null)
                .getPath());
        assertEquals(connectPath + "users/me/files/filter/sharedwithme?page=0",
                FileRequests.filesSharedWithUser(null, 0).getPath());
        assertEquals(connectPath + "users/" + userId + "/files/filter/sharedwithme?page=1", FileRequests
                .filesSharedWithUser(userId, 1).getPath());
        try {
            FileRequests.filesSharedWithUser("", 1);
            fail("empty user id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.filesSharedWithUser(userId, 1));
    }

    public void testFileDetails() {
        assertEquals(connectPath + "files/" + sfdcId, FileRequests.fileDetails(sfdcId, null).getPath());
        assertEquals(connectPath + "files/" + sfdcId + "?versionNumber=2", FileRequests.fileDetails(sfdcId, "2")
                .getPath());
        try {
            FileRequests.fileDetails(null, "3");
            fail("null sfdcId didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileDetails(sfdcId, "0");
            fail("invalid version id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileDetails(sfdcId, "2"));
    }

    public void testFileRendition() {
        assertEquals(connectPath + "files/" + sfdcId + "/rendition?type=PDF",
                FileRequests.fileRendition(sfdcId, null, RenditionType.PDF, null).getPath());
        assertEquals(connectPath + "files/" + sfdcId + "/rendition?type=PDF&versionNumber=2", FileRequests
                .fileRendition(sfdcId, "2", RenditionType.PDF, null).getPath());
        assertEquals(connectPath + "files/" + sfdcId + "/rendition?type=PDF",
                FileRequests.fileRendition(sfdcId, null, RenditionType.PDF, null).getPath());
        assertEquals(connectPath + "files/" + sfdcId + "/rendition?type=FLASH&page=0",
                FileRequests.fileRendition(sfdcId, null, RenditionType.FLASH, 0).getPath());
        assertEquals(connectPath + "files/" + sfdcId + "/rendition?type=THUMB120BY90&versionNumber=2&page=2",
                FileRequests.fileRendition(sfdcId, "2", RenditionType.THUMB120BY90, 2).getPath());
        try {
            FileRequests.fileRendition("", "1", RenditionType.PDF, 3);
            fail("invalid sfdcId didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileRendition(sfdcId, "2", null, 2);
            fail("null rendition type id didn't raise exception as expected");
        } catch (NullPointerException e) { /* expected */
        }
        try {
            FileRequests.fileRendition(sfdcId, "0", RenditionType.PDF, 2);
            fail("invalid verion number id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileRendition(sfdcId, "4", RenditionType.PDF, -2);
            fail("negative page number id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileRendition(sfdcId, "2", RenditionType.THUMB120BY90, 2));
    }

    public void testFileShares() {
        assertEquals(connectPath + "files/" + sfdcId + "/file-shares", FileRequests.fileShares(sfdcId, null).getPath());
        assertEquals(connectPath + "files/" + sfdcId + "/file-shares?page=4", FileRequests.fileShares(sfdcId, 4)
                .getPath());
        try {
            FileRequests.fileShares(null, 3);
            fail("null sfdcId didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileShares(sfdcId, -1);
            fail("negative page number id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileShares(sfdcId, 4));
    }

    private final String userId = "005T0000000ABCD";
    private final String sfdcId = "06930000001LkwtAAC";
}
