/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class for encryption/decryption/hash computations.
 */
public class Encryptor {

    private static final String TAG = "Encryptor";
    private static final String UTF8 = "UTF-8";
    private static final String US_ASCII = "US-ASCII";
    private static final String PREFER_CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String MAC_TRANSFORMATION = "HmacSHA256";
    private static final String SHA1PRNG = "SHA1PRNG";
    private static final String RSA_PKCS1 = "RSA/ECB/PKCS1Padding";
    private static final String BOUNCY_CASTLE = "BC";

    /**
     * Decrypts data with key using AES-128.
     *
     * @param data Data.
     * @param key Base64 encoded 128 bit key or null (to leave data unchanged).
     * @return Decrypted data.
     */
    public static String decrypt(String data, String key) {
        return decrypt(data, key, new byte[16]);
    }

    /**
     * Decrypts data with key using AES-128.
     *
     * @param data Data.
     * @param key Base64 encoded 128 bit key or null (to leave data unchanged).
     * @param iv Initialization vector.
     * @return Decrypted data.
     */
    public static String decrypt(String data, String key, byte[] iv) {
        if (TextUtils.isEmpty(key) || data == null) {
            return data;
        }
        return decrypt(data.getBytes(), key, iv);
    }

    /**
     * Decrypts data with key using AES-128.
     *
     * @param data Data.
     * @param key Key.
     * @return Decrypted data.
     */
    public static String decrypt(byte[] data, String key) {
        return decrypt(data, key, new byte[16]);
    }

    /**
     * Decrypts data with key using AES-128.
     *
     * @param data Data.
     * @param key Key.
     * @param iv Initialization vector.
     * @return Decrypted data.
     */
    public static String decrypt(byte[] data, String key, byte[] iv) {
        if (TextUtils.isEmpty(key)) {
            if (data != null) {
                return new String(data, Charset.forName(UTF8));
            } else {
                return null;
            }
        }
        try {

            // Decodes with Base64.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);

            // Decrypts with AES-128.
            byte[] decryptedData = decrypt(dataBytes, 0, dataBytes.length, keyBytes, iv);
            return new String(decryptedData, 0, decryptedData.length, UTF8);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during decryption", ex);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES-128.
     *
     * @param data Data.
     * @param key Base64 encoded 128 bit key or null (to leave data unchanged).
     * @return Base64, AES-128 encrypted data.
     */
    public static String encrypt(String data, String key) {
        try {
            return encrypt(data, key, generateInitVector());
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES-128.
     *
     * @param data Data.
     * @param key Base64 encoded 128 bit key or null (to leave data unchanged).
     * @param iv Initialization vector.
     * @return Base64, AES-128 encrypted data.
     */
    public static String encrypt(String data, String key, byte[] iv) {
        if (TextUtils.isEmpty(key) || data == null) {
            return data;
        }
        byte[] bytes = encryptBytes(data, key, iv);
        if (bytes == null) {
            return null;
        }
        try {

            // Do as Base64.encodeToString does, return US-ASCII string with the already Base64 encoded bytes.
            return new String(bytes, US_ASCII);
        } catch (UnsupportedEncodingException e) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", e);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES-128. Returns Base64 byte[] array.
     *
     * @param data Data.
     * @param key Key.
     * @return Encrypted data.
     */
    public static byte[] encryptBytes(String data, String key) {
        try {
            return encryptBytes(data, key, generateInitVector());
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES-128. Returns Base64 byte[] array.
     *
     * @param data Data.
     * @param key Key.
     * @param iv Initialization vector.
     * @return Encrypted data.
     */
    public static byte[] encryptBytes(String data, String key, byte[] iv) {
        if (TextUtils.isEmpty(key)) {
            if (data == null) {
                return null;
            } else {
                return data.getBytes();
            }
        }
        try {

            // Encrypts with our preferred cipher.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = data.getBytes(UTF8);
            return Base64.encode(encrypt(dataBytes, keyBytes, iv), Base64.DEFAULT);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex);
        }
        return null;
    }

    /**
     * Checks if the string is Base64 encoded.
     *
     * @param key String.
     * @return True - if encoded, False - otherwise.
     */
    public static boolean isBase64Encoded(String key) {
        try {
            Base64.decode(key, Base64.DEFAULT);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Return HMAC SHA-256 hash of data using key.
     *
     * @param data Data.
     * @param key Key.
     * @return Hash.
     */
    public static String hash(String data, String key) {
        try {

            // Signs with SHA-256.
            byte [] keyBytes = key.getBytes(UTF8);
            byte [] dataBytes = data.getBytes(UTF8);
            Mac sha;

            /*
             * TODO: Remove this check once minAPI >= 28.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                sha = Mac.getInstance(MAC_TRANSFORMATION);
            } else {
                sha = Mac.getInstance(MAC_TRANSFORMATION, getLegacyEncryptionProvider());
            }
            final SecretKeySpec keySpec = new SecretKeySpec(keyBytes, sha.getAlgorithm());
            sha.init(keySpec);
            byte [] sig = sha.doFinal(dataBytes);

            // Encodes with Base64.
            return Base64.encodeToString(sig, Base64.NO_WRAP);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during hashing", ex);
            return null;
        }
    }

    /**
     * Returns data encrypted with an RSA public key.
     *
     * @param publicKey RSA public key.
     * @param data Data to be encrypted.
     * @return Encrypted data.
     */
    public static String encryptWithRSA(PublicKey publicKey, String data) {
        String encryptedData = null;
        byte[] encryptedBytes = encryptWithRSABytes(publicKey, data);
        if (encryptedBytes != null) {
            encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP | Base64.NO_PADDING);
        }
        return encryptedData;
    }

    /**
     * Returns data encrypted with an RSA public key.
     *
     * @param publicKey RSA public key.
     * @param data Data to be encrypted.
     * @return Encrypted data.
     */
    public static byte[] encryptWithRSABytes(PublicKey publicKey, String data) {
        if (publicKey == null || TextUtils.isEmpty(data)) {
            return null;
        }
        try {
            final Cipher cipher = Cipher.getInstance(RSA_PKCS1);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data.getBytes());
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during asymmetric encryption using RSA", e);
        }
        return null;
    }

    /**
     * Returns data decrypted with an RSA private key.
     *
     * @param privateKey RSA private key.
     * @param data Data to be decrypted.
     * @return Decrypted data.
     */
    public static String decryptWithRSA(PrivateKey privateKey, String data) {
        String decryptedData = null;
        byte[] decryptedBytes = decryptWithRSABytes(privateKey, data);
        if (decryptedBytes != null) {
            try {
                decryptedData = new String(decryptedBytes, 0, decryptedBytes.length, UTF8);
            } catch (Exception e) {
                SalesforceAnalyticsLogger.e(null, TAG, "Error during asymmetric decryption using RSA", e);
            }
        }
        return decryptedData;
    }

    /**
     * Returns data decrypted with an RSA private key.
     *
     * @param privateKey RSA private key.
     * @param data Data to be decrypted.
     * @return Decrypted data.
     */
    public static byte[] decryptWithRSABytes(PrivateKey privateKey, String data) {
        if (privateKey == null || TextUtils.isEmpty(data)) {
            return null;
        }
        try {
            final Cipher cipher = Cipher.getInstance(RSA_PKCS1);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decodedBytes = Base64.decode(data.getBytes(),Base64.NO_WRAP | Base64.NO_PADDING);
            return cipher.doFinal(decodedBytes);
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during asymmetric decryption using RSA", e);
        }
        return null;
    }

    /**
     * Decrypts the given bytes using key and IV.
     *
     * @param data Data bytes.
     * @param key Key bytes.
     * @param iv Initialization vector bytes.
     * @return Decrypted data.
     */
    public static String decryptBytes(byte[] data, byte[] key, byte[] iv) {
        try {
            final Cipher cipher = getBestCipher();
            final SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
            final IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
            byte[] result = cipher.doFinal(data, 0, data.length);
            return new String(result, 0, result.length, UTF8);
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during symmetric decryption using AES", e);
        }
        return null;
    }

    private static byte[] generateInitVector() throws NoSuchAlgorithmException {
        final SecureRandom random = SecureRandom.getInstance(SHA1PRNG);
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }

    private static byte[] encrypt(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        final Cipher cipher = getBestCipher();
        final SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
        byte[] meat = cipher.doFinal(data);

        // Prepends the IV to the encoded data (first 16 bytes / 128 bits).
        byte[] result = new byte[iv.length + meat.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(meat, 0, result, iv.length, meat.length);
        return result;
    }

    private static byte[] decrypt(byte[] data, int offset, int length, byte[] key, byte[] iv) throws GeneralSecurityException {

        // Grabs the init vector prefix (first 16 bytes / 128 bits).
        System.arraycopy(data, offset, iv, 0, iv.length);

        // Grabs the encrypted body after the init vector prefix.
        int meatLen = length - iv.length;
        int meatOffset = offset + iv.length;
        byte[] meat = new byte[meatLen];
        System.arraycopy(data, meatOffset, meat, 0, meatLen);
        final Cipher cipher = getBestCipher();
        final SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        return cipher.doFinal(meat, 0, meatLen);
    }

    private static Cipher getBestCipher() {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance(PREFER_CIPHER_TRANSFORMATION, getLegacyEncryptionProvider());
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG,
                    "No cipher transformation available", e);
        }
        return cipher;
    }

    /*
     * TODO: Remove this method and its usages once minAPI >= 28.
     */
    private static String getLegacyEncryptionProvider() {
        return BOUNCY_CASTLE;
    }
}
