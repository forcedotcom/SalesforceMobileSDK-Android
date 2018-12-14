/*
 * Copyright (c) 2013-present, salesforce.com, inc.
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

import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestRequest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;

/**
 * 
 * @author jjiang
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FileRequestsTest {

    private String connectPath;

    @Before
    public void setUp() throws Exception {
        connectPath = "/services/data/" + ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext()) + "/chatter/";
    }

    @Test
    public void testBatchFileInfo() {
        RestRequest r = FileRequests.batchFileDetails(Arrays.asList("06930000001LkwtAAC", "06930000001LkwtAAD"));
        Assert.assertEquals(connectPath + "connect/files/batch/06930000001LkwtAAC,06930000001LkwtAAD", r.getPath());
        doAdditionalVerifications(r);
        try {
            FileRequests.batchFileDetails(Arrays.asList("06930000001LkwtAAC", null));
            Assert.fail("should of thrown an exception");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.batchFileDetails(Arrays.asList("06930000001LkwtAAC", ""));
            Assert.fail("should of thrown an exception");
        } catch (IllegalArgumentException e) { /* expected */
        }
    }

    @Test
    public void testFileContents() {
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/content?versionNumber=1",
                FileRequests.fileContents(sfdcId, "1").getPath());
        try {
            FileRequests.fileContents("", "1");
            Assert.fail("empty fileId should throw an exception");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileContents(sfdcId, "1"));
    }

    @Test
    public void testOwnedFilesList() {
        Assert.assertEquals(connectPath + "connect/files/users/me", FileRequests.ownedFilesList(null, null).getPath());
        Assert.assertEquals(connectPath + "connect/files/users/me?page=0", FileRequests.ownedFilesList(null, 0).getPath());
        Assert.assertEquals(connectPath + "connect/files/users/" + userId + "?page=1", FileRequests.ownedFilesList(userId, 1)
                .getPath());
        try {
            FileRequests.ownedFilesList("", 1);
            Assert.fail("empty user id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.ownedFilesList(userId, 1));
    }

    @Test
    public void testFilesInUsersGroups() {
        Assert.assertEquals(connectPath + "connect/files/users/me/filter/groups", FileRequests.filesInUsersGroups(null, null)
                .getPath());
        Assert.assertEquals(connectPath + "connect/files/users/me/filter/groups?page=0", FileRequests.filesInUsersGroups(null, 0)
                .getPath());
        Assert.assertEquals(connectPath + "connect/files/users/" + userId + "/filter/groups?page=1",
                FileRequests.filesInUsersGroups(userId, 1).getPath());
        try {
            FileRequests.filesInUsersGroups("", 1);
            Assert.fail("empty user id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.filesInUsersGroups(userId, 1));
    }

    @Test
    public void testFilesSharedWithUser() {
        Assert.assertEquals(connectPath + "connect/files/users/me/filter/sharedwithme", FileRequests.filesSharedWithUser(null, null)
                .getPath());
        Assert.assertEquals(connectPath + "connect/files/users/me/filter/sharedwithme?page=0",
                FileRequests.filesSharedWithUser(null, 0).getPath());
        Assert.assertEquals(connectPath + "connect/files/users/" + userId + "/filter/sharedwithme?page=1", FileRequests
                .filesSharedWithUser(userId, 1).getPath());
        try {
            FileRequests.filesSharedWithUser("", 1);
            Assert.fail("empty user id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.filesSharedWithUser(userId, 1));
    }

    @Test
    public void testFileDetails() {
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId, FileRequests.fileDetails(sfdcId, null).getPath());
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "?versionNumber=2", FileRequests.fileDetails(sfdcId, "2")
                .getPath());
        try {
            FileRequests.fileDetails(null, "3");
            Assert.fail("null sfdcId didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileDetails(sfdcId, "0");
            Assert.fail("invalid version id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileDetails(sfdcId, "2"));
    }

    @Test
    public void testFileRendition() {
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/rendition?type=PDF",
                FileRequests.fileRendition(sfdcId, null, RenditionType.PDF, null).getPath());
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/rendition?type=PDF&versionNumber=2", FileRequests
                .fileRendition(sfdcId, "2", RenditionType.PDF, null).getPath());
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/rendition?type=PDF",
                FileRequests.fileRendition(sfdcId, null, RenditionType.PDF, null).getPath());
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/rendition?type=FLASH&page=0",
                FileRequests.fileRendition(sfdcId, null, RenditionType.FLASH, 0).getPath());
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/rendition?type=THUMB120BY90&versionNumber=2&page=2",
                FileRequests.fileRendition(sfdcId, "2", RenditionType.THUMB120BY90, 2).getPath());
        try {
            FileRequests.fileRendition("", "1", RenditionType.PDF, 3);
            Assert.fail("invalid sfdcId didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileRendition(sfdcId, "2", null, 2);
            Assert.fail("null rendition type id didn't raise exception as expected");
        } catch (NullPointerException e) { /* expected */
        }
        try {
            FileRequests.fileRendition(sfdcId, "0", RenditionType.PDF, 2);
            Assert.fail("invalid verion number id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileRendition(sfdcId, "4", RenditionType.PDF, -2);
            Assert.fail("negative page number id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileRendition(sfdcId, "2", RenditionType.THUMB120BY90, 2));
    }

    @Test
    public void testFileShares() {
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/file-shares", FileRequests.fileShares(sfdcId, null).getPath());
        Assert.assertEquals(connectPath + "connect/files/" + sfdcId + "/file-shares?page=4", FileRequests.fileShares(sfdcId, 4)
                .getPath());
        try {
            FileRequests.fileShares(null, 3);
            Assert.fail("null sfdcId didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        try {
            FileRequests.fileShares(sfdcId, -1);
            Assert.fail("negative page number id didn't raise exception as expected");
        } catch (IllegalArgumentException e) { /* expected */
        }
        doAdditionalVerifications(FileRequests.fileShares(sfdcId, 4));
    }

    /**
     * Tests if the file upload API is working per design.
     *
     * @throws Exception
     */
    @Test
    public void testFileUpload() throws Exception {
        final File file = File.createTempFile("MyFile", "txt");
        if (!file.exists()) {
            final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
            out.write("This is a test!");
            out.close();
        }
        Assert.assertTrue("File should exist", file.exists());
        RestRequest request = FileRequests.uploadFile(file, file.getName(), "Test Title", "Test Description", "text/plain");
        Assert.assertEquals(connectPath + "connect/files/users/me", request.getPath());
        doAdditionalVerifications(RestRequest.RestMethod.POST, request);
        file.delete();
        Assert.assertFalse("File should not exist", file.exists());
    }

    private final String userId = "005T0000000ABCD";
    private final String sfdcId = "06930000001LkwtAAC";

    private void doAdditionalVerifications(RestRequest req) {
        doAdditionalVerifications(RestRequest.RestMethod.GET, req);
    }

    private void doAdditionalVerifications(RestRequest.RestMethod method, RestRequest req) {
        Assert.assertEquals(method, req.getMethod());
        Assert.assertEquals("false", req.getAdditionalHttpHeaders().get("X-Chatter-Entity-Encoding"));
    }
}
