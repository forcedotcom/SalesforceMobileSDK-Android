/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * When loading sqlite library we see UnsatisfiedLinkError exceptions
 * This stack overflow post (http://stackoverflow.com/questions/18111739/why-do-some-android-phones-cause-our-app-to-throw-an-java-lang-unsatisfiedlinker) suggests the problem is
 * with unzipping the apk, and manually doing it.
 * Since we don't want to modify sql cipher's code, we will have to duplicate  SQLiteDatabase.loadLibs code in this class.
 * This class will handle loading sql cipher for us. If we get an unsatisfied error, we will unzip the application apk, extract the so files to the application's data directory,
 * and call System.load() from the application directory
 * <p/>
 */
public class SqliteLibraryLoader {

    private static final String TAG = SqliteLibraryLoader.class.getSimpleName();
    private static final String DATABASE_SQLCIPHER = "libsqlcipher.so";

    /**
     * Sql cipher needs other libraries to also load. This method ensures we load all the required libraries
     */
    public static boolean loadSqlCipher(Context context) {
        try {
            loadLibs(context);
        } catch (UnsatisfiedLinkError ule) {
            // We are assuming the library loaded by sql cipher failed
            return extractAndLoadAgain(context, DATABASE_SQLCIPHER, getSupportedAbis());
        } catch (Exception ex) {
            SmartStoreLogger.e(TAG, "Error occurred while loading native libs for SQLCipher", ex);
            return false;
        }
        return true;
    }

    /**
     * Extracts the given library from the apk and places in the correct path. If library still cannot be read, return false to let caller decide appropriate error handling
     * the ui thread.
     *
     * @param context
     *         Context on which to query application information.
     * @param libraryName
     *         Name of the library to extract.
     *
     * @return True if the library can be read from new location, false otherwise.
     */
    private static boolean extractAndLoadAgain(Context context, String libraryName, String[] supportedABIs) {
        ApplicationInfo appInfo = context.getApplicationInfo();
        String destPath = context.getFilesDir().toString();
        SmartStoreLogger.i(TAG, "Extracting to destination: " + destPath);
        try {
            String soName = destPath + File.separator + libraryName;
            new File(soName).delete();
            boolean fileCopied = false;
            for (String abi : supportedABIs) {
                SmartStoreLogger.i(TAG, "Using ABI: " + abi);
                UnzipUtil.extractFile(appInfo.sourceDir, "lib/" + abi + "/" + libraryName, destPath);
                if (canReadFile(new File(soName))) {
                    SmartStoreLogger.i(TAG, "File exists after extracting to " + abi);
                    fileCopied = true;
                    break;
                }
            }
            if (fileCopied) {
                System.load(soName);
                return true;
            }
        } catch (IOException | UnsatisfiedLinkError e) {
            // extractFile to app files dir did not work. Not enough space? Uninstall/Reinstall dialog should be shown.
            SmartStoreLogger.e(TAG, "Error occurred while extracting and loading libs", e);
        }
        return false;
    }


    public static class UnzipUtil {
        /**
         * Size of the buffer to read/write data
         */
        private static final int BUFFER_SIZE = 4096;

        /**
         * Extracts a file from a zip to specified destination directory.
         * The path of the file inside the zip is discarded, the file is
         * copied directly to the destDirectory.
         *
         * @param zipFilePath
         *         - path and file name of a zip file
         * @param inZipFilePath
         *         - path and file name inside the zip
         * @param destDirectory
         *         - directory to which the file from zip should be extracted, the path part is discarded.
         *
         * @throws java.io.IOException
         */
        public static void extractFile(String zipFilePath, String inZipFilePath, String destDirectory) throws IOException {
            FileInputStream fileInputStream = new FileInputStream(zipFilePath);
            if (fileInputStream != null) {
                ZipInputStream zipIn = new ZipInputStream(fileInputStream);
                ZipEntry entry = zipIn.getNextEntry();
                // iterates over entries in the zip file
                try {
                    while (entry != null) {
                        if (!entry.isDirectory() && inZipFilePath.equals(entry.getName())) {
                            String filePath = entry.getName();
                            int separatorIndex = filePath.lastIndexOf(File.separator);
                            if (separatorIndex > -1) {
                                filePath = filePath.substring(separatorIndex + 1, filePath.length());
                            }
                            filePath = destDirectory + File.separator + filePath;
                            extractFile(zipIn, filePath);
                            break;
                        }
                        zipIn.closeEntry();
                        entry = zipIn.getNextEntry();
                    }
                } finally {
                    if (zipIn != null) {
                        zipIn.close();
                    }
                    fileInputStream.close();
                }
            }
        }

        /**
         * Extracts a zip entry (file entry)
         *
         * @param zipIn
         * @param filePath
         *
         * @throws java.io.IOException
         */
        private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
            try {
                byte[] bytesIn = new byte[BUFFER_SIZE];
                int read = 0;
                while ((read = zipIn.read(bytesIn)) != -1) {
                    bos.write(bytesIn, 0, read);
                }
            } finally {
                bos.close();
            }
        }
    }

    /**
     * Attempts to load sql related libs.
     *
     * @param context
     *         Context to assist in lib loading.
     *
     * @throws UnsatisfiedLinkError
     */
    public static void loadLibs(Context context) throws UnsatisfiedLinkError {
        SQLiteDatabase.loadLibs(context);
    }

    /**
     * Checks if file can be read.
     *
     * @param file
     *         File to be checked.
     *
     * @return True if file can be read, false otherwise.
     */
    public static boolean canReadFile(File file) {
        return file.canRead();
    }

    /**
     * @return Returns a list of all supported ABIs for the platform
     */
    @SuppressLint("NewApi")
    public static String[] getSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS;
        } else {
            return new String[] { Build.CPU_ABI, Build.CPU_ABI2 };
        }
    }
}

