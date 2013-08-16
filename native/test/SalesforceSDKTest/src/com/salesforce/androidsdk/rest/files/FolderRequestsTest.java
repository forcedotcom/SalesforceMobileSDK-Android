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

import com.salesforce.androidsdk.rest.RestRequest.RestMethod;

public class FolderRequestsTest extends ApiRequestsBaseTest {

    public void testOwnedFilesAndFoldersList() {
        assertEquals(connectPath + "folders/Root/items?page=0",
                FolderRequests.ownedFilesAndFoldersList(null, null, null).getPath());
        assertEquals(connectPath + "folders/FOOBAR/items?page=0",
                FolderRequests.ownedFilesAndFoldersList("FOOBAR", null, null).getPath());

        assertEquals(connectPath + "folders/Root/items?page=0",
                FolderRequests.ownedFilesAndFoldersList("Root", null, null).getPath());
        assertEquals(connectPath + "folders/Root/items?page=0",
                FolderRequests.ownedFilesAndFoldersList(null, 0, null).getPath());
        assertEquals(connectPath + "folders/Root/items?pageSize=1&page=0",
                FolderRequests.ownedFilesAndFoldersList(null, 1, null).getPath());
        assertEquals(connectPath + "folders/Root/items?page=1",
                FolderRequests.ownedFilesAndFoldersList(null, null, 1).getPath());

        assertEquals(connectPath + "folders/Root/items?pageSize=1&page=1",
                FolderRequests.ownedFilesAndFoldersList("Root", 1, 1).getPath());

        doAdditionalVerifications(FolderRequests.ownedFilesAndFoldersList(null, null, null));
    }

    public void testGetFolderInfo() {
        assertEquals(connectPath + "folders/FOOBAR",
                FolderRequests.getFolderInfo("FOOBAR").getPath());

        doAdditionalVerifications(FolderRequests.getFolderInfo("FOOBAR"));
    }

    public void testGetFolderPath() {
        assertEquals(connectPath + "folders/FOOBAR/path",
                FolderRequests.getFolderPath("FOOBAR").getPath());

        doAdditionalVerifications(FolderRequests.getFolderPath("FOOBAR"));
    }

    public void testCreateNewFolder() {
        assertEquals(connectPath + "folders/Root/items?folderPath=FOOBAR&type=Folder",
                FolderRequests.createNewFolder("FOOBAR", null).getPath());
        assertEquals(connectPath + "folders/BAR/items?folderPath=FOO&type=Folder",
                FolderRequests.createNewFolder("FOO", "BAR").getPath());
        assertEquals(connectPath + "folders/Root/items?folderPath=FOO+BAR&type=Folder",
                FolderRequests.createNewFolder("FOO BAR", null).getPath());

        doAdditionalVerifications(RestMethod.POST, FolderRequests.createNewFolder("FOOBAR", null));
    }
}
