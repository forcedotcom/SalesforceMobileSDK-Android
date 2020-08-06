/*
 * Copyright (c) 2020-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.smartstore.store;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import com.salesforce.androidsdk.analytics.security.DecrypterInputStream;
import com.salesforce.androidsdk.analytics.security.EncrypterOutputStream;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;
import com.salesforce.androidsdk.util.ManagedFilesHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/** Key-value store backed by file system */
public class KeyValueEncryptedFileStore  {

    private static final String TAG = KeyValueEncryptedFileStore.class.getSimpleName();
    public static final int MAX_STORE_NAME_LENGTH = 96;
    private String encryptionKey;
    private final File storeDir;

    public static final String KEY_VALUE_STORES = "keyvaluestores";

    /**
     * Constructor
     *
     * @param storeName name for key value store
     * @param encryptionKey encryption key for key value store
     */
    public KeyValueEncryptedFileStore(Context ctx,  String storeName, String encryptionKey) {
        this(computeParentDir(ctx), storeName, encryptionKey);
    }

    /**
     * Constructor
     *
     * @param parentDir parent directory for key value store
     * @param storeName name for key value store
     * @param encryptionKey encryption key for key value store
     */
    KeyValueEncryptedFileStore(File parentDir, String storeName, String encryptionKey) {
        if (!isValidStoreName(storeName)) {
            throw new IllegalArgumentException("Invalid store name: " + storeName);
        }
        storeDir = new File(parentDir, storeName);
        if (!storeDir.exists()) {
            storeDir.mkdirs();
        }
        if (!storeDir.isDirectory()) {
            throw new IllegalArgumentException("Failed to create directory for: " + storeName);
        }
        this.encryptionKey = encryptionKey;
    }


    /**
     * Return boolean indicating if a key value store with the given (full) name already exists
     * @param ctx
     * @param storeName full store name
     * @return True - if store was found
     */
    public static boolean hasKeyValueStore(Context ctx, String storeName) {
        return new File(computeParentDir(ctx), storeName).exists();
    }

    /**
     * Remove key value store with given (full) name
     * @param ctx
     * @param storeName full store name
     */
    public static void removeKeyValueStore(Context ctx, String storeName) {
        ManagedFilesHelper.deleteFile(new File(computeParentDir(ctx), storeName));
    }

    /**
     * Return parent directory for all key stores
     * @param ctx
     * @return File for parent directory
     */
    public static File computeParentDir(Context ctx) {
        return new File(ctx.getApplicationInfo().dataDir, KEY_VALUE_STORES);
    }

    /**
     * Store name can only contain letters, digits and _ and cannot exceed 96 characters
     * @param storeName
     * @return True if the name provided is valid for a store
     */
    public static boolean isValidStoreName(String storeName) {
        return storeName != null && storeName.length() > 0 && storeName.length() <= MAX_STORE_NAME_LENGTH
            && storeName.matches("^[a-zA-Z0-9_]*$");
    }

    /**
     * Save value for the given key.
     *
     * @param key Unique identifier.
     * @param value Value to be persisted.
     * @return True - if successful, False - otherwise.
     */
    public boolean saveValue(String key, String value) {
        if (!isKeyValid(key, "saveValue")) {
            return false;
        }
        if (value == null) {
            SmartStoreLogger.w(TAG, "saveValue: Invalid value supplied: " + value);
            return false;
        }

        long startNanoTime = System.nanoTime();
        try (FileOutputStream f = new FileOutputStream(getFileForKey(key));
                EncrypterOutputStream outputStream = new EncrypterOutputStream(f, encryptionKey)) {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "IOException occurred while saving value to filesystem", e);
            return false;
        }
    }

    /**
     * Save value given as an input stream for the given key.
     * NB: does not close provided input stream
     *
     * @param key Unique identifier.
     * @param stream Stream to be persisted.
     * @return True - if successful, False - otherwise.
     */
    public boolean saveStream(String key, InputStream stream) {
        if (!isKeyValid(key, "saveStream")) {
            return false;
        }
        if (stream == null) {
            SmartStoreLogger.w(TAG, "saveStream: Invalid value supplied: " + stream);
            return false;
        }

        try {
            saveStream(getFileForKey(key), stream, encryptionKey);
            return true;
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "Exception occurred while saving value from stream to filesystem", e);
            return false;
        }
    }

    /**
     * Returns value stored for given key.
     *
     * @param key Unique identifier.
     * @return value for given key or null if key not found.
     */
    public String getValue(String key) {
        try (InputStream inputStream = getStream(key)) {
            if (inputStream == null) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            String result = out.toString();

            return result;
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "getValue(): Threw exception for key: " + key, e);
            return null;
        }
    }

    /**
     * Returns stream for value of given key.
     *
     * @param key Unique identifier.
     * @return stream to value for given key or null if key not found.
     */
    public InputStream getStream(String key) {
        long startNanoTime = System.nanoTime();

        if (!isKeyValid(key, "getStream")) {
            return null;
        }

        final File file = getFileForKey(key);

        if (file == null || !file.exists()) {
            SmartStoreLogger.w(TAG, "getStream: File does not exist for key: " + key);
            return null;
        }

        try {
            return getStream(file, encryptionKey);
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "getStream: Threw exception for key: " + key, e);
            return null;
        }
    }

    /**
     * Deletes stored value for given key.
     *
     * @param key Unique identifier.
     * @return True - if successful, False - otherwise.
     */
    public synchronized boolean deleteValue(String key) {
        if (!isKeyValid(key, "deleteValue")) {
            return false;
        }
        return getFileForKey(key).delete();
    }

    /** Deletes all stored values. */
    public void deleteAll() {
        for (File file : safeListFiles()) {
            SmartStoreLogger.i(TAG, "deleting file :" + file.getName());
            file.delete();
        }
    }

    /** @return number of entries in the store. */
    public int count() {
        return safeListFiles().length;
    }

    /** @return True if store is empty. */
    public boolean isEmpty() {
        return safeListFiles().length == 0;
    }

    /**
     * @return store directory
     */
    public File getStoreDir() {
        return storeDir;
    }

    /**
     * @return store name
     */
    public String getStoreName() {
        return storeDir.getName();
    }

    /**
     * Change encryption key
     * All files are read/decrypted with old key and encrypted/written back with new key
     * @param newEncryptionKey
     * @return true if successful
     */
    public boolean changeEncryptionKey(String newEncryptionKey) {
        File originalStoreDir = storeDir;
        String storeName = getStoreName();
        File tmpDir = new File(storeDir.getParent(), storeName + "-tmp");
        tmpDir.mkdirs();
        if (!tmpDir.isDirectory()) {
            SmartStoreLogger.e(TAG, "changeKey: Failed to create tmp directory: " + tmpDir);
            return false;
        }
        // NB: - not allowed for store name so no chances of hitting colliding with existing store
        File[] originalFiles = originalStoreDir.listFiles();
        for (File originalFile : originalFiles) {
            try {
                saveStream(
                    new File(tmpDir, originalFile.getName()), // tmp file
                    getStream(originalFile, encryptionKey),   // reading original file
                    newEncryptionKey);                        // encrypting with new encryption key
            } catch (Exception e) {
                SmartStoreLogger.e(TAG, "changeKey: Threw exception for file: " + originalFile, e);
                //Failed
                return false;
            }
        }
        // Removing old store dir - renaming tmp dir
        ManagedFilesHelper.deleteFile(originalStoreDir);
        tmpDir.renameTo(originalStoreDir);

        // Updating encryption key
        encryptionKey = newEncryptionKey;

        // Successful
        return true;
    }


    private String encodeKey(String key) {
        return SalesforceKeyGenerator.getSHA256Hash(key);
    }

    private File getFileForKey(String key) {
        return new File(storeDir, encodeKey(key));
    }

    private boolean isKeyValid(String key, String operation) {
        if (TextUtils.isEmpty(key)) {
            SmartStoreLogger.w(TAG, operation + ": Invalid key supplied: " + key);
            return false;
        }
        return true;
    }

    /**
     * @return array of files in storeDir won't return null even if storeDir has been deleted
     */
    private File[] safeListFiles() {
        File[] files = storeDir == null ? null : storeDir.listFiles();
        return files == null ? new File[0] : files;
    }

    InputStream getStream(File file, String encryptionKey) throws IOException, GeneralSecurityException {
        FileInputStream f = new FileInputStream(file);
        DecrypterInputStream inputStream = new DecrypterInputStream(f, encryptionKey);
        return inputStream;
    }

    void saveStream(File file, InputStream stream, String encryptionKey)
        throws IOException, GeneralSecurityException {
        try (FileOutputStream f = new FileOutputStream(file);
            EncrypterOutputStream outputStream = new EncrypterOutputStream(f, encryptionKey)) {
            byte[] buffer = new byte[1024];
            int len;
            while((len=stream.read(buffer))>0){
                outputStream.write(buffer,0,len);
            }
        }
    }
}
