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

import com.salesforce.androidsdk.TestForceApp;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

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

        // Output: 4*Math.Ceiling(((double)bytes.Length/3)))
        // 4*Math.Ceiling(32/3) = 44
        Assert.assertEquals("The encoded string based on an AES-256 key should have 58 characters", 44, id1.length());
		Assert.assertEquals("Unique IDs with the same name should be the same", id1Again, id1);
        Assert.assertNotSame("Unique IDs with different names should be different", id2, id1);
        final String id3 = SalesforceKeyGenerator.getUniqueId(KEY_3, 128);
        final String id3Again = SalesforceKeyGenerator.getUniqueId(KEY_3, 128);

        // 4*Math.Ceiling(16/3) = 24
        Assert.assertEquals("The encoded string based on an AES-128 key should have 38 characters", 24, id3.length());
        Assert.assertEquals("Unique IDs with the same name should be the same", id3Again, id3);
    }

	@Test
    public void testGetEncryptionKey() {
        final String id1 = SalesforceKeyGenerator.getEncryptionKey(KEY_1);
        final String id1Again = SalesforceKeyGenerator.getEncryptionKey(KEY_1);
        final String id2 = SalesforceKeyGenerator.getEncryptionKey(KEY_2);
        Assert.assertEquals("Encryption keys with the same name should be the same", id1Again, id1);
        Assert.assertNotSame("Encryption keys with different names should be different", id2, id1);
    }
}
