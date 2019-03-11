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
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Calendar;

import javax.security.auth.x500.X500Principal;

/**
 * This class provides utilities to interact with the Android KeyStore.
 * For more information on the KeyStore, see {@link KeyStore}.
 *
 * @author bhariharan
 */
public class KeyStoreWrapper {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String RSA = "RSA";
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
                INSTANCE = null;
                SalesforceSDKLogger.e(TAG, "Could not load KeyStore", e);
            }
        }
        return INSTANCE;
    }

    /**
     * Generates an RSA keypair and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key.
     */
    public synchronized PublicKey getRSAPublicKey(String name, int length) {
        PublicKey publicKey = null;
        createRSAKeysIfNecessary(name, length);
        try {
            publicKey = keyStore.getCertificate(name).getPublicKey();
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Could not retrieve RSA public key", e);
        }
        return publicKey;
    }

    /**
     * Generates an RSA keypair and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key string.
     */
    public synchronized String getRSAPublicString(String name, int length) {
        final PublicKey publicKey = getRSAPublicKey(name, length);
        String publicKeyBase64 = null;
        if (publicKey != null) {
            publicKeyBase64 = Base64.encodeToString(publicKey.getEncoded(),
                    Base64.NO_WRAP | Base64.NO_PADDING);
        }
        return publicKeyBase64;
    }

    /**
     * Generates an RSA keypair and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA private key.
     */
    public synchronized PrivateKey getRSAPrivateKey(String name, int length) {
        PrivateKey privateKey = null;
        createRSAKeysIfNecessary(name, length);
        try {
            final KeyStore.Entry entry = keyStore.getEntry(name, null);
            if (entry == null) {
                return null;
            }
            privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Could not retrieve RSA private key", e);
        }
        return privateKey;
    }

    private KeyStore loadKeyStore() throws CertificateException, NoSuchAlgorithmException,
            IOException, KeyStoreException {
        final KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    private void createRSAKeysIfNecessary(String name, int length) {
        try {
            if (!keyStore.containsAlias(name)) {

                // Generates a new key pair.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    final KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                            KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);
                    kpg.initialize(new KeyGenParameterSpec.Builder(
                            name,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setKeySize(length)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                            .build());
                    kpg.generateKeyPair();
                } else {

                    /*
                     * TODO: Remove the 'else' block once minVersion > 23.
                     */
                    final Calendar start = Calendar.getInstance();
                    final Calendar end = Calendar.getInstance();
                    end.add(Calendar.YEAR, 30);
                    final KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(SalesforceSDKManager.getInstance().getAppContext())
                            .setAlias(name)
                            .setSubject(new X500Principal("CN=" + name))
                            .setSerialNumber(BigInteger.TEN)
                            .setStartDate(start.getTime())
                            .setEndDate(end.getTime())
                            .setKeySize(length)
                            .build();
                    final KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA, ANDROID_KEYSTORE);
                    kpg.initialize(spec);
                    kpg.generateKeyPair();
                }
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Could not generate key pair", e);
        }
    }
}
