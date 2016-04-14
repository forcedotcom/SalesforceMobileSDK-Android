/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.store;

import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SoupSpec;

/**
 * Tests for "smart" sql
 *
 */
public class SmartSqlExternalStorageTest extends SmartSqlTest {

	@Override
	protected String getPasscode() {
		return Encryptor.hash("test123", "hashing-key");
	}

	@Override
	protected void registerSoup(SmartStore store, String soupName, IndexSpec[] indexSpecs) {
		store.registerSoupWithSpec(new SoupSpec(soupName, SoupSpec.FEATURE_EXTERNAL_STORAGE), indexSpecs);
	}

	/**
	 * Testing smart sql to sql conversion when path is: _soup, _soupEntryId or _soupLastModifiedDate
	 * Since this has external storage enabled, _soup column is replaced with value of soup name and soup entry id
	 */
	@Override
	public void testConvertSmartSqlWithSpecialColumns() {
		assertEquals("select TABLE_1.id, TABLE_1.lastModified, 'employees:' || TABLE_1.id as 'externalStorage' from TABLE_1",
					 store.convertSmartSql("select {employees:_soupEntryId}, {employees:_soupLastModifiedDate}, {employees:_soup} from {employees}"));
	}
}
