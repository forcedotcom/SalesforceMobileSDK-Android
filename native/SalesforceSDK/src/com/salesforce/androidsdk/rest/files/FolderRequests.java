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

import java.io.File;
import java.io.UnsupportedEncodingException;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;

/**
 * This defines the HTTP requests in the connect API for folders functionality.
 */
public class FolderRequests extends ApiRequests {

    private static final String ROOT = "Root";
    private static final String FOLDERS = "folders";
    private static final String ITEMS = "items";
    private static final String PATH = "path";

    private static final String UPLOAD_TITLE = "title";
    private static final String UPLOAD_DESC = "desc";
    private static final String UPLOAD_TYPE = "type";
    private static final String UPLOAD_FILE_DATA = "fileData";
    private static final String UPLOAD_FILE = "File";
    private static final String NEW_FOLDER = "Folder";
    private static final String FOLDER_PATH = "folderPath";

    /**
     * Build a Request that can fetch the given page of files and folders that
     * belong to the current user and are children of the given parentFolder
     * 
     * @param parentId
     *            id of the parent folder. If null, root is used
     * @param pageSize
     *            number of children per page
     * @param page
     *            page we are requesting
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest ownedFilesAndFoldersList(String parentFolderId, Integer pageSize, Integer page) {
        // get /chatter/folders/folderId/items?pageSize=5
        if (parentFolderId == null) {
            parentFolderId = ROOT;
        }
        if (page == null) {
            page = 0;
        }
        if (pageSize == null || pageSize == 0) {
            return make(base(FOLDERS).appendFolderId(parentFolderId).appendPath(ITEMS).appendPageNum(page));
        }
        return make(base(FOLDERS).appendFolderId(parentFolderId).appendPath(ITEMS).appendPageSize(pageSize)
                .appendPageNum(page));
    }

    /**
     * Build a Request that can fetch information about the given folder
     * 
     * @param folderId
     *            id of the folder you want to know more about
     * @return A new RestRequest that can be used to fetch this data
     */
    public static RestRequest getFolderInfo(String folderId) {
        // get /chatter/folders/folderId
        return make(base(FOLDERS).appendFolderId(folderId));
    }

    /**
     * Build a Request that can create a new folder with the given name as a
     * child of the given folder
     * 
     * @param parentFolderId
     *            id of parent folder. If null, root is used
     * @param newFolderName
     *            Name of the new folder (cannot contain '/' character) (non
     *            null)
     * @return A new RestRequest that can be used to make this post
     */
    public static RestRequest createNewFolder(String newFolderName, String parentFolderId) {
        if (parentFolderId == null) {
            parentFolderId = ROOT;
        }
        return new RestRequest(RestMethod.POST,
                base(FOLDERS).appendFolderId(parentFolderId).appendPath(ITEMS)
                        .appendQueryParam(FOLDER_PATH, newFolderName)
                        .appendQueryParam(UPLOAD_TYPE, NEW_FOLDER)
                        .toString()
                        //spaces in the folder name should be turned into "+" post-encoding
                        .replace("%20", "+"),
                null, HTTP_HEADERS);
    }

    /**
     * Build a Request that can rename the given folder
     * 
     * @param newFolderName
     *            New name of the folder
     * @param folderId
     *            The id of the folder to be renamed
     * @return A new RestRequest that can be used to rename the folder
     */
    public static RestRequest renameFolder(String newFolderName, String folderId) {
        if (folderId == null) {
            // This is weird, but the API allows us to rename root. No idea if
            // this returns an error, though, but I'm keeping this in for now.
            folderId = ROOT;
        }
        return moveAndRenameFolder(folderId, newFolderName, null);
    }

    /**
     * Build a Request that can move the given folder under a new parent folder
     * 
     * @param folderId
     *            The id of the folder to be moved
     * @param parentFolderId
     *            The id of the folder we will move our folder to
     * @return A new RestRequest that can be used to move the folder
     */
    public static RestRequest moveFolder(String folderId, String parentFolderId) {
        if (parentFolderId == null) {
            parentFolderId = ROOT;
        }
        return moveAndRenameFolder(folderId, null, parentFolderId);
    }

    /**
     * Build a Request that can move and rename the given folder under a new
     * parent folder
     * 
     * @param folderId
     *            The id of the folder to be moved/renamed
     * @param newFolderName
     *            The new name of the folder or null if not renaming
     * @param parentFolderId
     *            The new parent of the folder, or null if not moving
     * @return
     */
    public static RestRequest moveAndRenameFolder(String folderId, String newFolderName, String parentFolderId) {
        // TODO use the following call
        // patch to
        // /chatter/folders/folderId?name=name+with+spaces&parentFolderId=folderId
        // don't fill in null fields
        return null;
    }

    /**
     * Build a Request that can delete a given folder
     * 
     * @param folderId
     *            The id of the folder to be deleted
     * @return A new RestRequest that can be used to delete the folder
     */
    public static RestRequest deleteFolder(String folderId) {
        // TODO use the following call
        // delete to /chatter/folders/folderId
        return null;
    }

    /**
     * Build a Request that can get a given folder's path
     * 
     * @param folderId
     *            The id of the folder whose path we want to know
     * @return The path of the given folder
     */
    public static RestRequest getFolderPath(String folderId) {
        return make(base(FOLDERS).appendFolderId(folderId).appendPath(PATH));
    }

    /**
     * Build a request that can upload a new file to the server, this will
     * create a new file at version 1.
     * 
     * @param theFile
     *            The path of the local file to upload to the server.
     * @param name
     *            The name/title of this file.
     * @param description
     *            A description of the file.
     * @param mimeType
     *            The mime-type of the file, if known.
     * @param folderId
     *            The ID of the parent folder
     * @return A RestRequest that can perform this upload.
     * 
     * @throws UnsupportedEncodingException
     */
    public static RestRequest uploadFile(File theFile, String name, String description, String mimeType, String folderId)
            throws UnsupportedEncodingException {
        if (folderId == null) {
            folderId = ROOT;
        }
        MultipartEntity mpe = new MultipartEntity(HttpMultipartMode.STRICT);
        FileBody bin = mimeType == null ? new FileBody(theFile) : new FileBody(theFile, mimeType);
        if (name != null)
            mpe.addPart(UPLOAD_TITLE, new StringBody(name));
        if (description != null)
            mpe.addPart(UPLOAD_DESC, new StringBody(description));
        mpe.addPart(UPLOAD_TYPE, new StringBody(UPLOAD_FILE));
        mpe.addPart(UPLOAD_FILE_DATA, bin);
        return new RestRequest(RestMethod.POST, base(FOLDERS).appendFolderId(folderId).appendPath(ITEMS).toString(),
                mpe, HTTP_HEADERS);
    }
}
