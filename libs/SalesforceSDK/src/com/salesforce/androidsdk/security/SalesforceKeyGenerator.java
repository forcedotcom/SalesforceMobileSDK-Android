/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

/**
 * This class provides methods to generate a unique ID that can be used as an encryption
 * key. The key is derived from an AES-256 base using SecureRandom or AES-128 base using UUID.
 *
 * @author bhariharan
 */
public class SalesforceKeyGenerator {

    private static final String TAG = "SalesforceKeyGenerator";
    private static final String SHARED_PREF_FILE = "identifier.xml";
    private static final String ID_SHARED_PREF_KEY = "id_%s";
    private static final String ADDENDUM = "addendum_%s";
    private static final String UTF8 = "UTF-8";
    private static final String SHA1 = "SHA-1";
    private static final String SHA256 = "SHA-256";
    private static final String SHA1PRNG = "SHA1PRNG";
    private static final String AES = "AES";
    private static final String RSA = "RSA";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private static Map<String, String> UNIQUE_IDS = new HashMap<>();
    private static Map<String, String> CACHED_ENCRYPTION_KEYS = new HashMap<>();

    /**
     * Returns the unique ID being used. The default key length is 256 bits.
     *
     * @param name Unique name associated with this unique ID.
     * @return Unique ID.
     */
    public static String getUniqueId(String name) {
        return getUniqueId(name, 256);
    }

    /**
     * Returns the unique ID being used based on the key length.
     *
     * @param name Unique name associated with this unique ID.
     * @param length Key length.
     * @return Unique ID.
     */
    public static synchronized String getUniqueId(String name, int length) {
        if (UNIQUE_IDS.get(name) == null) {
            generateUniqueId(name, length);
        }
        return UNIQUE_IDS.get(name);
    }

    /**
     * Returns the encryption key being used.
     *
     * @param name Unique name associated with this encryption key.
     * @return Encryption key.
     */
    public static synchronized String getEncryptionKey(String name) {
        if (CACHED_ENCRYPTION_KEYS.get(name) == null) {
            generateEncryptionKey(name);
        }
        return CACHED_ENCRYPTION_KEYS.get(name);
    }

    /**
     * Returns a randomly generated 128-byte key that's URL safe.
     *
     * @return Random 128-byte key.
     */
    public static String getRandom128ByteKey() {
        final SecureRandom secureRandom = new SecureRandom();
        byte[] random = new byte[128];
        secureRandom.nextBytes(random);
        return Base64.encodeToString(random,Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    /**
     * Returns the SHA-256 hashed value of the supplied private key.
     *
     * @param privateKey Private key.
     * @return SHA-256 hash.
     */
    public static String getSHA256Hash(String privateKey) {
        String hashedString = null;
        byte[] privateKeyBytes = privateKey.getBytes(StandardCharsets.US_ASCII);
        try {
            final MessageDigest digest = MessageDigest.getInstance(SHA256);
            byte[] hash = digest.digest(privateKeyBytes);
            hashedString = Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch(Exception e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while generating SHA-256 hash", e);
        }
        return hashedString;
    }

    /**
     * Generates a keypair and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA public key.
     */
    public static synchronized PublicKey getRSAPublicKey(String name, int length) {
        PublicKey publicKey = null;
        createRSAKeysIfNecessary(name, length);
        try {
            publicKey = loadKeyStore().getCertificate(name).getPublicKey();
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Security exception thrown", e);
        }
        return publicKey;
    }

    /**
     * Generates a keypair and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA public key string.
     */
    public static synchronized String getRSAPublicString(String name, int length) {
        final PublicKey publicKey = getRSAPublicKey(name, length);
        String publicKeyBase64 = null;
        if (publicKey != null) {
            publicKeyBase64 = Base64.encodeToString(publicKey.getEncoded(),
                    Base64.NO_WRAP | Base64.NO_PADDING);
        }
        return publicKeyBase64;
    }

    /**
     * Generates a keypair and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA private key.
     */
    public static synchronized PrivateKey getRSAPrivateKey(String name, int length) {
        PrivateKey privateKey = null;
        createRSAKeysIfNecessary(name, length);
        try {
            KeyStore.Entry entry = loadKeyStore().getEntry(name, null);
            if (entry == null) {
                return null;
            }
            privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Security exception thrown", e);
        }
        return privateKey;
    }

    private static void generateEncryptionKey(String name) {
        try {
            final String keyString = getUniqueId(name);
            byte[] secretKey = keyString.getBytes(Charset.forName(UTF8));
            final MessageDigest md = MessageDigest.getInstance(SHA1);
            secretKey = md.digest(secretKey);
            byte[] dest = new byte[16];
            System.arraycopy(secretKey, 0, dest, 0, 16);
            CACHED_ENCRYPTION_KEYS.put(name, Base64.encodeToString(dest, Base64.NO_WRAP));
        } catch (Exception ex) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while getting encryption key", ex);
        }
    }

    private static void generateUniqueId(String name, int length) {
        final SharedPreferences prefs = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_FILE, 0);
        final String id = prefs.getString(getSharedPrefKey(name), null);

        // Checks if we have a unique identifier stored.
        if (id != null) {
            UNIQUE_IDS.put(name, id + getAddendum(name));
        } else {
            String uniqueId;
            try {

                // Uses SecureRandom to generate an AES-256 key.
                final int outputKeyLength = length;
                final SecureRandom secureRandom = SecureRandom.getInstance(SHA1PRNG);

                // SecureRandom does not require seeding. It's automatically seeded from system entropy.
                final KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
                keyGenerator.init(outputKeyLength, secureRandom);

                // Generates a 256-bit key.
                uniqueId = Base64.encodeToString(keyGenerator.generateKey().getEncoded(), Base64.NO_WRAP);
            } catch (NoSuchAlgorithmException e) {
                SalesforceSDKLogger.e(TAG, "Security exception thrown", e);

                // Generates a random UUID 128-bit key instead.
                uniqueId = UUID.randomUUID().toString();
            }
            prefs.edit().putString(getSharedPrefKey(name), uniqueId).commit();
            UNIQUE_IDS.put(name, uniqueId + getAddendum(name));
        }
    }

    private static String getSharedPrefKey(String name) {
        final String suffix = TextUtils.isEmpty(name) ? "" : name;
        return String.format(Locale.US, ID_SHARED_PREF_KEY, suffix);
    }

    private static String getAddendum(String name) {
        final String suffix = TextUtils.isEmpty(name) ? "" : name;
        return String.format(Locale.US, ADDENDUM, suffix);
    }

    private static void createRSAKeysIfNecessary(String name, int length) {
        try {
            KeyStore keyStore = loadKeyStore();
            if (!keyStore.containsAlias(name)) {

                // Generates a new key pair.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance(
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
                    Calendar start = Calendar.getInstance();
                    Calendar end = Calendar.getInstance();
                    end.add(Calendar.YEAR, 30);
                    KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(SalesforceSDKManager.getInstance().getAppContext())
                            .setAlias(name)
                            .setSubject(new X500Principal("CN=" + name))
                            .setSerialNumber(BigInteger.TEN)
                            .setStartDate(start.getTime())
                            .setEndDate(end.getTime())
                            .setKeySize(length)
                            .build();
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA, ANDROID_KEYSTORE);
                    kpg.initialize(spec);
                    kpg.generateKeyPair();
                }
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Security exception thrown", e);
        }
    }

    private static KeyStore loadKeyStore() throws CertificateException, NoSuchAlgorithmException,
            IOException, KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }
}
