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

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;

/**
 * This class provides utilities to interact with the Android KeyStore.
 * For more information on the KeyStore, see {@link KeyStore}.
 *
 * @author bhariharan
 */
public class KeyStoreWrapper {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String RSA = "RSA";
    private static final String EC = "EC";
    private static final int EC_KEY_LENGTH = 256;
    private static final int RSA_KEY_LENGTH = 2048;
    private static final String TAG = "KeyStoreWrapper";

    private static KeyStoreWrapper INSTANCE;

    private KeyStore keyStore;

    /**
     * Returns an instance of this class, after initializing the KeyStore if required.
     *
     * @return Instance of this class.
     */
    public synchronized static KeyStoreWrapper getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new KeyStoreWrapper();
                INSTANCE.keyStore = INSTANCE.loadKeyStore();
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Could not load KeyStore", e);
            }
        }
        return INSTANCE;
    }

    /**
     * Generates an RSA keypair and returns the public key of length 2048.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA public key.
     */
    public PublicKey getRSAPublicKey(String name) {
        return getRSAPublicKey(name, RSA_KEY_LENGTH);
    }

    /**
     * Generates an RSA keypair and returns the encoded public key string of length 2048.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA public key string.
     */
    public String getRSAPublicString(String name) {
        return getRSAPublicString(name, RSA_KEY_LENGTH);
    }

    /**
     * Generates an RSA keypair and returns the private key of length 2048.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA private key.
     */
    public PrivateKey getRSAPrivateKey(String name) {
        return getRSAPrivateKey(name, RSA_KEY_LENGTH);
    }

    /**
     * Generates an RSA keypair and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key.
     */
    public PublicKey getRSAPublicKey(String name, int length) {
        return getPublicKey(RSA, name, length);
    }

    /**
     * Generates an RSA keypair and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key string.
     */
    public String getRSAPublicString(String name, int length) {
        return getPublicKeyString(RSA, name, length);
    }

    /**
     * Generates an RSA keypair and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA private key.
     */
    public PrivateKey getRSAPrivateKey(String name, int length) {
        return getPrivateKey(RSA, name, length);
    }

    /**
     * Generates an EC keypair of length 256, and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return EC public key.
     */
    public PublicKey getECPublicKey(String name) {
        return getPublicKey(EC, name, EC_KEY_LENGTH);
    }

    /**
     * Generates an EC keypair of length 256, and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return EC public key string.
     */
    public String getECPublicString(String name) {
        return getPublicKeyString(EC, name, EC_KEY_LENGTH);
    }

    /**
     * Generates an EC keypair of length 256, and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return EC private key.
     */
    public PrivateKey getECPrivateKey(String name) {
        return getPrivateKey(EC, name, EC_KEY_LENGTH);
    }

    private KeyStore loadKeyStore() throws CertificateException, NoSuchAlgorithmException,
            IOException, KeyStoreException {
        final KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    private PublicKey getPublicKey(String algorithm, String name, int length) {
        PublicKey publicKey = null;
        createKeysIfNecessary(algorithm, name, length);
        try {
            publicKey = keyStore.getCertificate(name).getPublicKey();
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Could not retrieve public key", e);
        }
        return publicKey;
    }

    private String getPublicKeyString(String algorithm, String name, int length) {
        final PublicKey publicKey = getPublicKey(algorithm, name, length);
        String publicKeyBase64 = null;
        if (publicKey != null) {
            publicKeyBase64 = Base64.encodeToString(publicKey.getEncoded(),
                    Base64.NO_WRAP | Base64.NO_PADDING);
        }
        return publicKeyBase64;
    }

    private PrivateKey getPrivateKey(String algorithm, String name, int length) {
        PrivateKey privateKey = null;
        createKeysIfNecessary(algorithm, name, length);
        try {
            privateKey = (PrivateKey) keyStore.getKey(name, null);
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Could not retrieve private key", e);
        }
        return privateKey;
    }

    private synchronized void createKeysIfNecessary(String algorithm, String name, int length) {
        try {
            if (!keyStore.containsAlias(name)) {

                // Generates a new key pair.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm, ANDROID_KEYSTORE);
                final KeyGenParameterSpec.Builder keyGenParameterSpecBuilder = new KeyGenParameterSpec.Builder(
                        name,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(length)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1);

                /*
                 * TODO: Remove this check once minVersion > 28.
                 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                    /*
                     * Disabling StrongBox based on Google's recommendation - it's not a good
                     * fit for this use case, since the key will need to be retrieved multiple
                     * times. Besides, StrongBox Keymaster is available only on a few devices,
                     * such as the Pixel 3 and Pixel 3 XL at this time.
                     */
                    keyGenParameterSpecBuilder.setIsStrongBoxBacked(false);
                }
                kpg.initialize(keyGenParameterSpecBuilder.build());
                kpg.generateKeyPair();
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Could not generate key pair", e);
        }
    }
}
