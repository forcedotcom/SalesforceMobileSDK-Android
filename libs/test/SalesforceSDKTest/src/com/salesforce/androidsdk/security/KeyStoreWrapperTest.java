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

import java.nio.charset.StandardCharsets;
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
    public void testRSAPKCS1EncryptDecrypt() {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        final PrivateKey privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        final PublicKey publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH);
        final String data = "Test data for encryption";
        final String encryptedData = Encryptor.encryptWithRSA(publicKey, data, Encryptor.CipherMode.RSA_PKCS1);
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData);
        final String decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData, Encryptor.CipherMode.RSA_PKCS1);
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData);
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
    //
    // In 12.0 client will use a new RSA cipher mode
    // In 250 server will use the new RSA cipher mode

    /**
     * New client against new server
     * 1. Client generates key pair with new code
     * 2. Server encrypts push notification using new RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    public void testDecryptDataEncryptedWithNewRSACipher() {
        tryNewOrUpgradedClientAgainstNewOrOldServer(true, true, false);
    }

    /**
     * New client against old server
     * 1. Client generates key pair with new code
     * 2. Server has not been upgraded yet and encrypts push notification using old RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    public void testDecryptDataEncryptedWithLegacyRSACipher() {
        tryNewOrUpgradedClientAgainstNewOrOldServer(true, false, false);
    }

    /**
     * Upgraded client against new server
     * 1. Client generated key pair before upgrading to 11.1.1
     * 2. Server encrypts push notification using new RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    public void testDecryptDataEncryptedWithNewRSACipherForKeyCreatedBeforeUpgrade() {
        // NB: only works with upgrade step (which regenerates the key)
        tryNewOrUpgradedClientAgainstNewOrOldServer(false, true, true);
    }

    /**
     * Upgraded client against old server
     * 1. Client generates key pair before upgrading to 11.1.1
     * 2. Server has not been upgraded yet and encrypts push notification using old RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    public void testDecryptDataEncryptedWithLegacyRSACipherForKeyCreatedBeforeUpgrade() {
        // With upgrade step (which regenerates the key)
        tryNewOrUpgradedClientAgainstNewOrOldServer(false, false, true);

        // Also works without the upgrade step
        tryNewOrUpgradedClientAgainstNewOrOldServer(false, false, false);
    }

    /**
     * Helper method for tests for RSA cipher mode change
     * @param newClient true means new client (key generated with new code), false means upgraded client (key generated the old way)
     * @param newServer true means new server (using new cipher mode), false means old server (using old cipher mode)
     * @param simulateUpgradeStep true means run the code that SalesforceSDKUpgradeManager would run if coming from an older version (one with the old cipher mode)
     */
    private void tryNewOrUpgradedClientAgainstNewOrOldServer(boolean newClient, boolean newServer, boolean simulateUpgradeStep) {
        final KeyStoreWrapper keyStoreWrapper = KeyStoreWrapper.getInstance();
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper);
        if (!newClient) {
            // Simulating upgraded client / generating key the old way
            keyStoreWrapper.legacyCreateKeysIfNecessary("RSA", KEY_1, RSA_LENGTH);
            if (simulateUpgradeStep) {
                // Simulating the upgrade step which should run when app first run
                KeyStoreWrapper.getInstance().deleteKey(KEY_1);
            }
        }
        final PrivateKey privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH);
        final PublicKey publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH);
        final String data = "Test data for encryption";
        // Simulating server
        final byte[] encryptedBytes = Encryptor.encryptWithPublicKey(publicKey, data,
                newServer
                        ? Encryptor.CipherMode.RSA_OAEP_SHA256 // new server / cipher mode
                        : Encryptor.CipherMode.RSA_PKCS1       // old server / cipher mode
        );
        final String encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP | Base64.NO_PADDING);
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData);
        final String decryptedData = new String(Encryptor.decryptWithRSAMultiCipherNodes(privateKey, encryptedData), StandardCharsets.UTF_8);
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData);
    }
}
