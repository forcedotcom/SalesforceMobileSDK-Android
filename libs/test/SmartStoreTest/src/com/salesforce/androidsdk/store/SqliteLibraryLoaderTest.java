/*
 * Copyright 2015 Salesforce.com.
 * All Rights Reserved.
 * Company Confidential.
 */
package com.salesforce.androidsdk.store;

import java.io.File;

import org.mockito.Mockito;

import com.salesforce.androidsdk.smartstore.store.SqliteLibraryLoader;

import android.content.Context;
import android.test.AndroidTestCase;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Tests for SqliteLibraryLoader
 *
 * @author eromero
 */
public class SqliteLibraryLoaderTest extends AndroidTestCase {

    SqliteLibraryLoader sqlLibLoader;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sqlLibLoader = new SqliteLibraryLoader();
    }

    public void testLoadSqlCipher() {
        // Test positive case
        assertTrue("Load libs should succeed, request uninstall dialog is not needed.", sqlLibLoader.loadSqlCipher(getContext()));
    }

    public void testLoadSqlCipherThrowsException() {
        // Setup to fire UnsatisfiedLinkError
        SqliteLibraryLoader libLoaderSpy = Mockito.spy(sqlLibLoader);
        doThrow(new UnsatisfiedLinkError()).when(libLoaderSpy).loadLibs(any(Context.class));
        doReturn(true).when(libLoaderSpy).canReadFile(any(File.class));

        // Test - Method should catch unsatisfied link error and successfully extract libs manually from apk.
        assertTrue("Extraction should succeed, request uninstall dialog is not needed.", libLoaderSpy.loadSqlCipher(getContext()));
    }

    public void testLoadSqlCipherThrowsExceptionAndWorkaroundFails() {
        // Setup to fire UnsatisfiedLinkError and fail extraction
        SqliteLibraryLoader libLoaderSpy = Mockito.spy(sqlLibLoader);
        doThrow(new UnsatisfiedLinkError()).when(libLoaderSpy).loadLibs(any(Context.class));
        doReturn(false).when(libLoaderSpy).canReadFile(any(File.class));

        // Test - Method should catch unsatisfied link error and fail during lib extraction
        // Check - Since lib loading has completely failed, request uninstall dialog flag should be set
        assertFalse("Lib loading should completely fail and show request uninstall dialog.", libLoaderSpy.loadSqlCipher(getContext()));
    }
}
