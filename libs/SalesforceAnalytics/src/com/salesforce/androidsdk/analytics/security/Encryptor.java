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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
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
    private static final String AES_CBC_CIPHER = "AES/CBC/PKCS5Padding";
    private static final String AES_GCM_CIPHER = "AES/GCM/NoPadding";
    private static final String MAC_TRANSFORMATION = "HmacSHA256";
    private static final String RSA_PKCS1 = "RSA/ECB/PKCS1Padding";
    private static final String BOUNCY_CASTLE = "BC";
    private static final int READ_BUFFER_LENGTH = 1024;

    /**
     * Returns initialized cipher for encryption with an IV automatically generated.
     *
     * @param encryptionKey Encryption key.
     * @return Initialized cipher.
     */
    public static Cipher getEncryptingCipher(String encryptionKey)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        final byte[] keyBytes = Base64.decode(encryptionKey, Base64.DEFAULT);
        return getEncryptingCipher(keyBytes, generateInitVector());
    }

    private static Cipher getEncryptingCipher(byte[] keyBytes, byte[] iv)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        final Cipher cipher = getBestCipher(AES_GCM_CIPHER);
        final SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, cipher.getAlgorithm());
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
        cipher.updateAAD(ivSpec.getIV());
        return cipher;
    }

    /**
     * Returns initialized cipher for decryption.
     *
     * @param encryptionKey Encryption key.
     * @param iv Initialization vector.
     * @return Initialized cipher.
     */
    public static Cipher getDecryptingCipher(String encryptionKey, byte[] iv)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        final byte[] keyBytes = Base64.decode(encryptionKey, Base64.DEFAULT);
        return getDecryptingCipher(keyBytes, iv);
    }

    private static Cipher getDecryptingCipher(byte[] keyBytes, byte[] iv)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        final Cipher cipher = getBestCipher(AES_GCM_CIPHER);
        final SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, cipher.getAlgorithm());
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        cipher.updateAAD(ivSpec.getIV());
        return cipher;
    }

    private static Cipher getAESCBCDecryptingCipher(byte[] keyBytes, byte[] iv)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        final Cipher cipher = getBestCipher(AES_CBC_CIPHER);
        final SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, cipher.getAlgorithm());
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        return cipher;
    }

    /**
     * Decrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @return Decrypted data.
     */
    public static String decrypt(String data, String key) {
        return decrypt(data, key, new byte[12]);
    }

    /**
     * Decrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Key.
     * @return Decrypted data.
     */
    public static String decrypt(byte[] data, String key) {
        return decrypt(data, key, new byte[12]);
    }

    /**
     * Decrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
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
     * Decrypts data with key using using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Key.
     * @param iv Initialization vector.
     * @return Decrypted data.
     */
    public static String decrypt(byte[] data, String key, byte[] iv) {
        if (TextUtils.isEmpty(key)) {
            if (data != null) {
                return new String(data, StandardCharsets.UTF_8);
            } else {
                return null;
            }
        }
        try {

            // Decodes with Base64.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);

            // Decrypts with AES.
            byte[] decryptedData = decrypt(dataBytes, dataBytes.length, keyBytes, iv);
            return new String(decryptedData, 0, decryptedData.length, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during decryption", ex);
        }
        return null;
    }

    /**
     * Decrypts data with key using using AES/GCM/NoPadding. The data is not Base64 encoded.
     *
     * @param data Data.
     * @param key Key.
     * @return Decrypted data.
     */
    public static byte[] decryptWithoutBase64Encoding(byte[] data, String key) {
        if (TextUtils.isEmpty(key)) {
            return data;
        }
        try {

            // Decodes with Base64.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);

            // Decrypts with AES.
            return decrypt(data, data.length, keyBytes, new byte[12]);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during decryption", ex);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @return Encrypted data.
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
     * Encrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @param iv Initialization vector.
     * @return Encrypted data.
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
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (Exception e) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", e);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding.
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
     * Encrypts data with key using AES/GCM/NoPadding.
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
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            return Base64.encode(encrypt(dataBytes, keyBytes, iv), Base64.DEFAULT);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding. The data is not Base64 encoded.
     *
     * @param data Data.
     * @param key Key.
     * @return Encrypted data.
     */
    public static byte[] encryptWithoutBase64Encoding(byte[] data, String key) {
        if (TextUtils.isEmpty(key)) {
            return data;
        }
        try {

            // Encrypts with our preferred cipher.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            return encrypt(data, keyBytes, generateInitVector());
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
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
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
            byte[] sig = sha.doFinal(dataBytes);

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
        return encryptWithPublicKey(publicKey, data, RSA_PKCS1);
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
                decryptedData = new String(decryptedBytes, 0, decryptedBytes.length, StandardCharsets.UTF_8);
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
        return decryptWithPrivateKey(privateKey, data, RSA_PKCS1);
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
            final Cipher cipher = getAESCBCDecryptingCipher(key, iv);
            byte[] result = cipher.doFinal(data, 0, data.length);
            return new String(result, 0, result.length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during symmetric decryption using AES", e);
        }
        return null;
    }

    /**
     * Retrieves data from an InputStream.  Guaranteed to close the InputStream.
     *
     * @param stream InputStream data.
     * @return Data from the InputStream as a String.
     * @throws IOException Provide log details of this exception in a catch with specifics
     * about the operation this method was called for.
     */
    public static String getStringFromStream(InputStream stream) throws IOException {
        ByteArrayOutputStream output = getByteArrayStreamFromStream(stream);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Retrieves data from an InputStream.  Guaranteed to close the InputStream.
     *
     * @param stream InputStream data.
     * @return Data from the InputStream as a ByteArrayOutputStream
     * @throws IOException Provide log details of this exception in a catch with specifics
     * about the operation this method was called for.
     */
    public static ByteArrayOutputStream getByteArrayStreamFromStream(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_LENGTH];
        int length;
        try {
            while ((length = stream.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
        finally {
            stream.close();
        }
        return output;
    }

    /**
     * Retrieves data from a File.
     *
     * @param file File object.
     * @return Data from the input File as a String.
     * @throws IOException Provide log details of this exception in a catch with specifics
     * about the operation this method was called for.
     */
    public static String getStringFromFile(File file) throws IOException {
        FileInputStream stream = null;
        String output;
        try {
            stream = new FileInputStream(file);
            output =  getStringFromStream(stream);
        }
        finally {
            if (stream != null) {
                stream.close();
            }
        }
        return output;
    }

    private static byte[] encryptWithPublicKey(PublicKey publicKey, String data, String cipher) {
        if (publicKey == null || TextUtils.isEmpty(data)) {
            return null;
        }
        try {
            final Cipher cipherInstance = Cipher.getInstance(cipher);
            cipherInstance.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipherInstance.doFinal(data.getBytes());
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during asymmetric encryption", e);
        }
        return null;
    }

    private static byte[] decryptWithPrivateKey(PrivateKey privateKey, String data, String cipher) {
        if (privateKey == null || TextUtils.isEmpty(data)) {
            return null;
        }
        try {
            final Cipher cipherInstance = Cipher.getInstance(cipher);
            cipherInstance.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decodedBytes = Base64.decode(data.getBytes(),Base64.NO_WRAP | Base64.NO_PADDING);
            return cipherInstance.doFinal(decodedBytes);
        } catch (Exception e) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during asymmetric decryption", e);
        }
        return null;
    }

    private static byte[] generateInitVector() {
        // Create the system recommended secure random number generator provider algorithm.
        final SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        return iv;
    }

    private static byte[] encrypt(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        final Cipher cipher = getEncryptingCipher(key, iv);
        byte[] meat = cipher.doFinal(data);

        // Prepends the IV to the encoded data (first 12 bytes for GCM).
        byte[] result = new byte[iv.length + meat.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(meat, 0, result, iv.length, meat.length);
        return result;
    }

    private static byte[] decrypt(byte[] data, int length, byte[] key, byte[] iv) throws GeneralSecurityException {

        // Grabs the init vector prefix (first 12 bytes for GCM, or first 16 bytes for CBC).
        System.arraycopy(data, 0, iv, 0, iv.length);

        // Grabs the encrypted body after the init vector prefix.
        int meatLen = length - iv.length;
        int meatOffset = iv.length;
        byte[] meat = new byte[meatLen];
        System.arraycopy(data, meatOffset, meat, 0, meatLen);
        Cipher cipher;

        // AES/GCM has an IV of length 12 bytes, whereas AES/CBC has an IV of length 16 bytes.
        if (iv.length == 12) {
            cipher = getDecryptingCipher(key, iv);
        } else {
            cipher = getAESCBCDecryptingCipher(key, iv);
        }
        return cipher.doFinal(meat, 0, meatLen);
    }

    private static Cipher getBestCipher(String cipherMode) {
        Cipher cipher = null;
        try {
            if (AES_GCM_CIPHER.equals(cipherMode)) {
                cipher = Cipher.getInstance(cipherMode);
            } else {
                cipher = Cipher.getInstance(cipherMode, getLegacyEncryptionProvider());
            }
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
