/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * Test class for IndexSpec
 *
 */
public class IndexSpecTest extends InstrumentationTestCase {

	private static final IndexSpec keyStringSpec = new IndexSpec("key", Type.string);
	private static final IndexSpec keyIntegerSpec = new IndexSpec("key", Type.integer);
	private static final IndexSpec keyFloatingSpec = new IndexSpec("key", Type.floating);
	private static final IndexSpec keyFullTextSpec = new IndexSpec("key", Type.full_text);

	private static final IndexSpec keyStringSpecWithCol = new IndexSpec("key", Type.string, "COL_1");
	private static final IndexSpec keyIntegerSpecWithCol = new IndexSpec("key", Type.integer, "COL_1");
	private static final IndexSpec keyFloatingSpecWithCol = new IndexSpec("key", Type.floating, "COL_1");
	private static final IndexSpec keyFullTextSpecWithCol = new IndexSpec("key", Type.full_text, "COL_1");

	/**
	 * TEST for equals with same index specs
	 */
	public void testEqualsWithSame() {
		assertEquals(keyStringSpec, new IndexSpec("key", Type.string));
		assertEquals(keyIntegerSpec, new IndexSpec("key", Type.integer));
		assertEquals(keyFloatingSpec, new IndexSpec("key", Type.floating));
		assertEquals(keyFullTextSpec, new IndexSpec("key", Type.full_text));
		assertEquals(keyStringSpecWithCol, new IndexSpec("key", Type.string, "COL_1"));
		assertEquals(keyIntegerSpecWithCol, new IndexSpec("key", Type.integer, "COL_1"));
		assertEquals(keyFloatingSpecWithCol, new IndexSpec("key", Type.floating, "COL_1"));
		assertEquals(keyFullTextSpecWithCol, new IndexSpec("key", Type.full_text, "COL_1"));
	}

	/**
	 * TEST for equals with index specs that have different paths / types or columnNames
	 */
	public void testEqualsWithDifferent() {
		// Different path
		assertFalse(keyStringSpec.equals(new IndexSpec("otherKey", Type.string)));

		// Different type
		assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.integer)));
		assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.floating)));
		assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.full_text)));

		// Different columnName
		assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.string, "COL_1")));
		assertFalse(keyStringSpecWithCol.equals(new IndexSpec("key", Type.string)));
		assertFalse(keyStringSpecWithCol.equals(new IndexSpec("key", Type.string, "COL_2")));
	}

	/**
	 * TEST for equals with same index specs
	 */
	public void testHashCodeWithSame() {
		assertEquals(keyStringSpec.hashCode(), new IndexSpec("key", Type.string).hashCode());
		assertEquals(keyIntegerSpec.hashCode(), new IndexSpec("key", Type.integer).hashCode());
		assertEquals(keyFloatingSpec.hashCode(), new IndexSpec("key", Type.floating).hashCode());
		assertEquals(keyFullTextSpec.hashCode(), new IndexSpec("key", Type.full_text).hashCode());

		assertEquals(keyStringSpecWithCol.hashCode(), new IndexSpec("key", Type.string, "COL_1").hashCode());
		assertEquals(keyIntegerSpecWithCol.hashCode(), new IndexSpec("key", Type.integer, "COL_1").hashCode());
		assertEquals(keyFloatingSpecWithCol.hashCode(), new IndexSpec("key", Type.floating, "COL_1").hashCode());
		assertEquals(keyFullTextSpecWithCol.hashCode(), new IndexSpec("key", Type.full_text, "COL_1").hashCode());
	}
	
	/**
	 * TEST for equals with index specs that have different paths / types or columnNames
	 */
	public void testHashCodeWithDifferent() {
		// Different path
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("otherKey", Type.string).hashCode());

		// Different type
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.integer).hashCode());
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.floating).hashCode());
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.full_text).hashCode());

		// Different columnName
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.string, "COL_1").hashCode());
		assertFalse(keyStringSpecWithCol.hashCode() == new IndexSpec("key", Type.string).hashCode());
		assertFalse(keyStringSpecWithCol.hashCode() == new IndexSpec("key", Type.string, "COL_2").hashCode());
	}
}
