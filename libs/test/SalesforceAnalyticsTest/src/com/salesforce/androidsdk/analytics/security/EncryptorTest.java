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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

/**
 * Tests for Encryptor.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class EncryptorTest {

	private static final String[] TEST_KEYS = new String[] {
			null,
            makeKey("test1234"),
            makeKey("123456")
    };
	private static final String[] TEST_DATA = new String[] {
            "hello world",
            "fake-token"
    };

	/**
	 * Test to make sure that encrypt does nothing when given a null key.
	 */
    @Test
	public void testEncryptWithNullKey() {
		for (final String data : TEST_DATA) {
            Assert.assertEquals("Encrypt should have left the string unchanged", data,
					Encryptor.encrypt(data, null));
		}
	}

	/**
	 * Test to make sure that decrypt does nothing when given a null key.
	 */
    @Test
	public void testDecryptWithNullKey() {
		for (final String data : TEST_DATA) {
            Assert.assertEquals("Decrypt should have left the string unchanged", data,
					Encryptor.decrypt(data, null));
		}
	}

    /**
     * Test to ensure encryption and decryption work as expected.
     */
    @Test
	public void testEncryptDecrypt() {
		for (String key : TEST_KEYS) {
			for (String data : TEST_DATA) {
				String encryptedData = Encryptor.encrypt(data, key);
				String decryptedData = Encryptor.decrypt(encryptedData, key);
                Assert.assertEquals("Decrypt should restore original", data, decryptedData);
			}
		}
	}

	/**
	 * Test to make sure encrypt returns a string different from the original
     * and that decrypt restores the original.
	 */
    @Test
	public void testEncryptDecryptWithDifferentData() {
		final String key = makeKey("123456");
		for (final String data : TEST_DATA) {
            Assert.assertFalse("Encrypted string should be different from original",
					data.equals(Encryptor.encrypt(data, key)));
            Assert.assertEquals("Decrypt should restore original", data,
					Encryptor.decrypt(Encryptor.encrypt(data, key), key));
			for (final String otherData : TEST_DATA) {
                final String encryptedA = Encryptor.encrypt(data, key);
                final String decryptedA = Encryptor.decrypt(encryptedA, key);
                final String encryptedB = Encryptor.encrypt(otherData, key);
                final String decryptedB = Encryptor.decrypt(encryptedB, key);
				boolean sameDecrypted = decryptedA.equals(decryptedB);
				boolean sameData = data.equals(otherData);
                Assert.assertEquals("Decrypted strings '"
						+ decryptedA + "','" + decryptedB
						+ "'  should be different for different strings '"
						+ data +"','" + otherData + "'",
						sameDecrypted, sameData);
			}
		}
	}

	/**
	 * Test to make sure that encrypting with different keys produces different results.
	 */
    @Test
	public void testEncryptDecryptWithDifferentKeys() {
		final String data = "fake-token";
		for (final String key : TEST_KEYS) {
            Assert.assertEquals("Decrypt should restore original", data,
					Encryptor.decrypt(Encryptor.encrypt(data, key), key));
			for (final String otherKey : TEST_KEYS) {
				boolean sameKey = (key == null && otherKey == null) || (key != null && key.equals(otherKey));
				if (!sameKey) {
                    final String encryptedA = Encryptor.encrypt(data, key);
                    final String decryptedA = Encryptor.decrypt(encryptedA, key);
                    final String encryptedB = Encryptor.encrypt(data, otherKey);
                    final String decryptedB = Encryptor.decrypt(encryptedB, otherKey);
                    Assert.assertEquals("Decrypted values should be the same", decryptedA, decryptedB);
					boolean sameEncrypted = encryptedA.equals(encryptedB);
                    Assert.assertEquals("Encrypted strings '"
							+ encryptedA + "','" + encryptedB
							+ "'  should be different for different keys '"
							+ key +"','" + otherKey + "'",
							sameEncrypted, sameKey);
				}
			}
		}
	}

	/**
	 * Check cipher returned by Encryptor.getEncryptingCipher
	 */
	@Test
	public void testGetEncryptingCipher()
		throws InvalidAlgorithmParameterException, InvalidKeyException {
    	Cipher cipher = Encryptor.getEncryptingCipher(makeKey("my-key"));
    	Assert.assertEquals("Wrong algorithm", "AES/GCM/NoPadding", cipher.getAlgorithm());
		Assert.assertEquals("Wrong iv length", 12, cipher.getIV().length);
		Assert.assertEquals("Wrong mode", 16, cipher.getBlockSize());
	}

	/**
	 * Check cipher returned by Encryptor.getDecryptingCipher
	 */
	@Test
	public void testGetDecryptingCipher()
		throws InvalidAlgorithmParameterException, InvalidKeyException {
		Cipher cipher = Encryptor.getDecryptingCipher(makeKey("my-key"), new byte[12]);
		Assert.assertEquals("Wrong algorithm", "AES/GCM/NoPadding", cipher.getAlgorithm());
		Assert.assertEquals("Wrong iv length", 12, cipher.getIV().length);
		Assert.assertEquals("Wrong mode", 16, cipher.getBlockSize());
	}

	/**
	 * Encrypting/decrypting data with ciphers returned by Encryptor.getEncryptingCipher and
	 * Encryptor.getDecryptingCipher.
	 */
	@Test
	public void testEncryptDecryptWithCipher()
		throws InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {
    	String key = makeKey("test-key");
    	String originalText = "abcdefghijklmnopqrstuvwxyz";
		Cipher encryptingCipher = Encryptor.getEncryptingCipher(key);
		Cipher decryptingCipher = Encryptor.getDecryptingCipher(key, encryptingCipher.getIV());
		byte[] originalBytes = originalText.getBytes(StandardCharsets.UTF_8);
		byte[] encryptedBytes = encryptingCipher.doFinal(originalBytes);
		byte[] decryptedBytes = decryptingCipher.doFinal(encryptedBytes);
		String recoveredText = new String(decryptedBytes, StandardCharsets.UTF_8);
		Assert.assertNotEquals("Bytes should have encrypted", encryptedBytes, originalBytes);
		Assert.assertNotEquals("Bytes should have been decrypted", decryptedBytes, encryptedBytes);
		Assert.assertEquals("Recovered text should match original", originalText, recoveredText);
	}

	/**
	 * Encrypting/decrypting data with ciphers returned by Encryptor.encryptWithoutBase64Encoding and
	 * Encryptor.decryptWithoutBase64Encoding.
	 */
	@Test
	public void testEncryptDecryptWithoutBase64Encoding() {
		for (final String key : TEST_KEYS) {
			for (final String data : TEST_DATA) {
				final byte[] dataBytes = data.getBytes();
				byte[] encryptedData = Encryptor.encryptWithoutBase64Encoding(dataBytes, key);
				byte[] decryptedData = Encryptor.decryptWithoutBase64Encoding(encryptedData, key);
				Assert.assertArrayEquals("Decrypt should restore original",
						dataBytes, decryptedData);
			}
		}
	}

	private static String makeKey(String passcode) {
        return Encryptor.hash(passcode, "hashing-key");
	}
}
