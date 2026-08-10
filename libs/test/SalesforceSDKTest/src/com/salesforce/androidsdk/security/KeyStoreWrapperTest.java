/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security;

import android.app.Application;
import android.app.Instrumentation;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.analytics.security.Encryptor;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Tests for {@link KeyStoreWrapper}.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class KeyStoreWrapperTest {

    private static final String KEY_1 = "key_1";
    private static final String KEY_2 = "key_2";
    private static final String KEY_OAEP_TEST = "key_oaep_test";
    private static final int RSA_LENGTH = 2048;

    @Before
    public void setUp() throws Exception {
        final Application app = Instrumentation.newApplication(TestForceApp.class,
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
    }

    @After
    public void tearDown() throws Exception {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        keyStoreWrapper.deleteKey(KEY_1);
        keyStoreWrapper.deleteKey(KEY_2);
        keyStoreWrapper.deleteKey(KEY_OAEP_TEST);
    }

    @Test
    public void testGetRSAPublicString() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final String key1 = keyStoreWrapper.getRSAPublicString(KEY_1, RSA_LENGTH);
        final String key1Again = keyStoreWrapper.getRSAPublicString(KEY_1, RSA_LENGTH);
        final String key2 = keyStoreWrapper.getRSAPublicString(KEY_2, RSA_LENGTH);
        Assert.assertEquals("Public keys with the same name should be the same", key1, key1Again);
        Assert.assertNotSame("Public keys with different names should be different", key1, key2);
    }

    @Test
    public void testGetRSAPrivateKey() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final PrivateKey key1 = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        final PrivateKey key1Again = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        Assert.assertEquals("Private keys with the same name should be the same", key1, key1Again);
    }

    @Test
    public void testRSAOAEPSHA256EncryptDecrypt() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final PrivateKey privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        final PublicKey publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH);
        final String data = "Test data for encryption";
        final String encryptedData = Encryptor.encryptWithRSA(publicKey, data, Encryptor.CipherMode.RSA_OAEP_SHA256);
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData);
        final String decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData, Encryptor.CipherMode.RSA_OAEP_SHA256);
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData);
    }


    @Test
    public void testGetECPublicString() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final String key1 = keyStoreWrapper.getECPublicString(KEY_1);
        final String key1Again = keyStoreWrapper.getECPublicString(KEY_1);
        final String key2 = keyStoreWrapper.getECPublicString(KEY_2);
        Assert.assertEquals("Public keys with the same name should be the same", key1, key1Again);
        Assert.assertNotSame("Public keys with different names should be different", key1, key2);
    }

    @Test
    public void testGetECPrivateKey() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final PrivateKey key1 = keyStoreWrapper.getECPrivateKey(KEY_1);
        final PrivateKey key1Again = keyStoreWrapper.getECPrivateKey(KEY_1);
        Assert.assertEquals("Private keys with the same name should be the same", key1, key1Again);
    }

    // RSA is used to encrypt push notifications.
    // 1. Client generates key pair, stores keys to key store and sends public key to server.
    // 2. Server uses public key to encrypt part of push notification.
    // 3. Client uses private key stored in key store to decrypt push notification.

    /**
     * New client against new server:
     * 1. Client generates key pair with current code
     * 2. Server encrypts push notification using RSA_OAEP_SHA256
     * 3. Client decrypts successfully
     */
    @Test
    public void testDecryptDataEncryptedWithNewRSACipher() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final PrivateKey privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        final PublicKey publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH);
        final String data = "Test data for encryption";
        final byte[] encryptedBytes = Encryptor.encryptWithPublicKey(publicKey, data, Encryptor.CipherMode.RSA_OAEP_SHA256);
        final String encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP | Base64.NO_PADDING);
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData);
        final String decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData, Encryptor.CipherMode.RSA_OAEP_SHA256);
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData);
    }

    /**
     * Upgraded client against new server:
     * 1. Old key is deleted (simulating upgrade step that regenerates the key with modern spec)
     * 2. New key pair is generated with modern code (supports OAEP)
     * 3. Server encrypts push notification using RSA_OAEP_SHA256
     * 4. Client decrypts successfully
     */
    @Test
    public void testDecryptDataEncryptedWithNewRSACipherForKeyCreatedBeforeUpgrade() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        // Simulate the upgrade step that deletes and regenerates the key with modern spec
        keyStoreWrapper.deleteKey(KEY_1);
        final PrivateKey privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        final PublicKey publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH);
        final String data = "Test data for encryption";
        final byte[] encryptedBytes = Encryptor.encryptWithPublicKey(publicKey, data, Encryptor.CipherMode.RSA_OAEP_SHA256);
        final String encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP | Base64.NO_PADDING);
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData);
        final String decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData, Encryptor.CipherMode.RSA_OAEP_SHA256);
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData);
    }

    @Test
    public void testKeySupportsOAEPPadding() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);

        // Create a modern key pair
        keyStoreWrapper.getRSAPublicKey(KEY_OAEP_TEST, RSA_LENGTH);

        // Verify the modern key supports OAEP padding
        Assert.assertTrue("Modern key should support OAEP padding",
                keyStoreWrapper.keySupportsOAEPPadding(KEY_OAEP_TEST));
    }
}
