/*
 * Copyright 2015 Salesforce.com.
 * All Rights Reserved.
 * Company Confidential.
 */
package com.salesforce.androidsdk.smartstore.store;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.sqlcipher.database.SQLiteDatabase;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

/**
 * When loading sqlite library we see UnsatisfiedLinkError exceptions (https://gus.my.salesforce.com/apex/adm_bugdetail?id=a07B0000001DT0hIAG&sfdc.override=1)
 * This stack overflow post (http://stackoverflow.com/questions/18111739/why-do-some-android-phones-cause-our-app-to-throw-an-java-lang-unsatisfiedlinker) suggests the problem is
 * with unzipping the apk, and manually doing it.
 * Since we don't want to modify sql cipher's code, we will have to duplicate  SQLiteDatabase.loadLibs code in this class.
 * This class will handle loading sql cipher for us. If we get an unsatisfied error, we will unzip the application apk, extract the so files to the application's data directory,
 * and call System.load() from the application directory
 * <p/>
 * Note: SQLiteDatabase.loadLibs loads 3 native libraries in the following order
 * 1. stlport_shared
 * 2. sqlcipher_android
 * 3. database_sqlcipher.
 * <p/>
 * The code in this class assumes the first one fails, and we load all 3 from application directory.
 * We don't handle the case where the first and/or second one loaded successfully (since we haven't seen that in the wild, and makes the logic in  this class simpler)
 * <p/>
 * Created by ktanna on 3/16/15.
 */
public class SqliteLibraryLoader {

    protected static final String TAG = SqliteLibraryLoader.class.getSimpleName();
    private static final String DATABASE_SQLCIPHER = "libsqlcipher.so";
    public static final String LIB_LOAD_FAILURE = "LibLoadFailure";

    /**
     * Sql cipher needs other libraries to also load. This method ensures we load all the required libraries
     */
    public boolean loadSqlCipher(Context context) {
        try {
            loadLibs(context);
        } catch (UnsatisfiedLinkError ule) {
            // We are assuming the library loaded by sql cipher failed
            return extractAndLoadAgain(context, DATABASE_SQLCIPHER, getSupportedAbis());
        } catch (Exception ex) {
            Log.e(TAG, "Error loading native libraries for Sqlcipher", ex);
            return false;
        }

        return true;
    }

    /**
     * Extracts the given library from the apk and places in the correct path. If library still cannot be read, a flag to show a request uninstall dialog is set to be displayed on
     * the ui thread.
     *
     * @param context
     *         Context on which to query application information.
     * @param libraryName
     *         Name of the library to extract.
     *
     * @return True if the library can be read from new location, false otherwise.
     */
    private boolean extractAndLoadAgain(Context context, String libraryName, String[] supportedABIs) {
        final String METHOD_TAG = TAG + ":extractAndLoadAgain";

        ApplicationInfo appInfo = context.getApplicationInfo();
        String destPath = context.getFilesDir().toString();
        Log.i(METHOD_TAG, "Extracting to destination : " + destPath);
        try {

            String soName = destPath + File.separator + libraryName;
            new File(soName).delete();

            boolean fileCopied = false;
            for (String abi : supportedABIs) {
                Log.i(METHOD_TAG, "Using ABI : " + abi);
                UnzipUtil.extractFile(appInfo.sourceDir, "lib/" + abi + "/" + libraryName, destPath);
                if (canReadFile(new File(soName))) {
                    Log.i(METHOD_TAG, "File exists after extracting to " + abi);
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
            Log.e(METHOD_TAG, "An error occurred when extracting and loading libs", e);
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
    public void loadLibs(Context context) throws UnsatisfiedLinkError {
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
    public boolean canReadFile(File file) {
        return file.canRead();
    }

    /**
     * @return Returns a list of all supported ABIs for the platform
     */
    @SuppressLint("NewApi")
    public String[] getSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS;
        } else {
            return new String[] { Build.CPU_ABI, Build.CPU_ABI2 };
        }
    }
}

