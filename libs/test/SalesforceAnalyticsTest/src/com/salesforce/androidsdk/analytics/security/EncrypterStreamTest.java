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

package com.salesforce.androidsdk.analytics.security;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Test for {@link EncrypterOutputStream} and {@link DecrypterInputStream}
 *
 */
@RunWith(AndroidJUnit4.class)
public class EncrypterStreamTest {

    private static final String TEST_FILE = "encrypter_stream_test_file";

    private Context context;
    private String encryptionKey;

    @Before
    public void setUp() throws Exception {
        encryptionKey = Encryptor.hash("test-key", "hashing-key");
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        context.deleteFile(TEST_FILE);
    }

    /**
     * Write an encrypted file using EncrypterOutputStream and read it back using
     * DecrypterInputStream
     */
    @Test
    public void testWriteAndReadThroughStream() {
        String contentToWrite = "testWriteAndReadThroughStream";
        writeThroughStream(contentToWrite);
        String readContent = readThroughStream();
        Assert.assertEquals(contentToWrite, readContent);
    }

    /**
     * Write an encrypted file using Encryptor.encrypt and a FileOutputStream and read it back using
     * DecrypterInputStream
     *
     * <p>Note: won't work because Encryptor.encrypt base-64 encodes content
     */
    // @Test
    public void testWriteWithEncryptorAndReadThroughStream() {
        String contentToWrite = "testWriteWithEncryptorAndReadThroughStream";
        writeWithEncryptor(contentToWrite);
        String readContent = readThroughStream();
        Assert.assertEquals(contentToWrite, readContent);
    }

    /**
     * Test that writes an encrypted file using EncrypterOutputStream and reads it back using
     * FileInputStream and Encryptor.decrypt
     *
     * <p>Note: won't work because Encryptor.decrypt expect the encrypted content base 64 encoded
     */
    // @Test
    public void testWriteThroughStreamAndReadWithEncryptor() {
        String contentToWrite = "testWriteThroughStreamAndReadWithEncryptor";
        writeThroughStream(contentToWrite);
        String readContent = readWithEncryptor();
        Assert.assertEquals(contentToWrite, readContent);
    }

    /**
     * Test that writes an encrypted file using using Encryptor.encrypt and a FileOutputStream and
     * reads it back using FileInputStream and Encryptor.decrypt
     */
    @Test
    public void testWriteAndReadWithEncryptor() {
        String contentToWrite = "testWriteAndReadWithEncryptor";
        writeWithEncryptor(contentToWrite);
        String readContent = readWithEncryptor();
        Assert.assertEquals(contentToWrite, readContent);
    }

    /**
     * Test that using a DecrypterInputStream on a file with incorrect iv length fails fast
     */
    @Test
    public void testDecrypterStreamWithBadIVLength() {
        try (FileOutputStream outputStream =
            context.openFileOutput(TEST_FILE, Context.MODE_PRIVATE)) {
            outputStream.write(255); // iv length expected here
            outputStream.write("hello world".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        try (FileInputStream f = context.openFileInput(TEST_FILE);
            DecrypterInputStream i = new DecrypterInputStream(f, encryptionKey); ) {
            Assert.fail("Should have failed to create a DecrypterInputStream");
        } catch (Exception e) {
            Assert.assertEquals("Wrong exception", "Can't decrypt file: incorrect iv length found in file: 255", e.getMessage());
        }
    }

    /**
     * Helper method to write an encrypted file using EncrypterOutputStream
     *
     * @param content
     */
    private void writeThroughStream(String content) {
        try (FileOutputStream f = context.openFileOutput(TEST_FILE, Context.MODE_PRIVATE);
                EncrypterOutputStream outputStream =
                        new EncrypterOutputStream(f, encryptionKey)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Helper method to read an encrypted file using DecrypterInputStream
     *
     * @return content of file as string
     */
    private String readThroughStream() {
        try (FileInputStream f = context.openFileInput(TEST_FILE);
                DecrypterInputStream i = new DecrypterInputStream(f, encryptionKey); ) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(i));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            return out.toString();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to write an encrypted file using a FileOutputStream and Encryptor.encrypt
     *
     * @param content
     */
    private void writeWithEncryptor(String content) {
        try (FileOutputStream outputStream =
                context.openFileOutput(TEST_FILE, Context.MODE_PRIVATE)) {
            String encryptedString = Encryptor.encrypt(content, encryptionKey);
            outputStream.write(encryptedString.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Helper method to read an encrypted file using a FileInputStream and Encryptor.decrypt
     *
     * @return content of file as string
     */
    private String readWithEncryptor() {
        File file = new File(context.getFilesDir(), TEST_FILE);
        try (FileInputStream f = new FileInputStream(file);
                DataInputStream dataInputStream = new DataInputStream(f); ) {
            byte[] bytes = new byte[(int) file.length()];
            dataInputStream.readFully(bytes);
            return Encryptor.decrypt(bytes, encryptionKey);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }
}
