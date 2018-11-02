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

import android.app.Application;
import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.TestForceApp;
import com.salesforce.androidsdk.analytics.security.Encryptor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Tests for {@link SalesforceKeyGenerator}.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SalesforceKeyGeneratorTest {

    private static final String KEY_1 = "key_1";
    private static final String KEY_2 = "key_2";
    private static final String KEY_3 = "key_3";

    @Before
	public void setUp() throws Exception {
		final Application app = Instrumentation.newApplication(TestForceApp.class,
				InstrumentationRegistry.getInstrumentation().getTargetContext());
		InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
	}

	@Test
	public void testGetUniqueId() {
		final String id1 = SalesforceKeyGenerator.getUniqueId(KEY_1);
        final String id1Again = SalesforceKeyGenerator.getUniqueId(KEY_1);
		final String id2 = SalesforceKeyGenerator.getUniqueId(KEY_2);

        // Output: 4*Math.Ceiling(((double)bytes.Length/3))) + length of getAddendum(KEY)
        // 4*Math.Ceiling(32/3)+14 = 58
        Assert.assertEquals("The encoded string based on an AES-256 key should have 58 characters", id1.length(), 58);
		Assert.assertEquals("Unique IDs with the same name should be the same", id1, id1Again);
        Assert.assertNotSame("Unique IDs with different names should be different", id1, id2);
        final String id3 = SalesforceKeyGenerator.getUniqueId(KEY_3, 128);
        final String id3Again = SalesforceKeyGenerator.getUniqueId(KEY_3, 128);

        // 4*Math.Ceiling(16/3)+14 = 38
        Assert.assertEquals("The encoded string based on an AES-128 key should have 38 characters", id3.length(), 38);
        Assert.assertEquals("Unique IDs with the same name should be the same", id3, id3Again);
    }

	@Test
    public void testGetEncryptionKey() {
        final String id1 = SalesforceKeyGenerator.getEncryptionKey(KEY_1);
        final String id1Again = SalesforceKeyGenerator.getEncryptionKey(KEY_1);
        final String id2 = SalesforceKeyGenerator.getEncryptionKey(KEY_2);
        Assert.assertEquals("Encryption keys with the same name should be the same", id1, id1Again);
        Assert.assertNotSame("Encryption keys with different names should be different", id1, id2);
    }

    @Test
    public void testGetRSAPublicString() {
        final String key1 = SalesforceKeyGenerator.getRSAPublicString(KEY_1, 2048);
        final String key1Again = SalesforceKeyGenerator.getRSAPublicString(KEY_1, 2048);
        final String key2 = SalesforceKeyGenerator.getRSAPublicString(KEY_2, 2048);
        Assert.assertEquals("Public keys with the same name should be the same", key1, key1Again);
        Assert.assertNotSame("Public keys with different names should be different", key1, key2);
    }

    @Test
    public void testGetRSAPrivateKey() {
        final PrivateKey key1 = SalesforceKeyGenerator.getRSAPrivateKey(KEY_1, 2048);
        final PrivateKey key1Again = SalesforceKeyGenerator.getRSAPrivateKey(KEY_1, 2048);
        Assert.assertEquals("Private keys with the same name should be the same", key1, key1Again);
    }

    @Test
    public void testRSAEncryptDecrypt() throws Exception {
        final PrivateKey privateKey = SalesforceKeyGenerator.getRSAPrivateKey(KEY_1, 2048);
        final PublicKey publicKey = SalesforceKeyGenerator.getRSAPublicKey(KEY_1, 2048);
        final String data = "Test data for encryption";
        final String encryptedData = Encryptor.encryptWithRSA(publicKey, data);
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData);
        final String decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData);
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData);
    }
}
