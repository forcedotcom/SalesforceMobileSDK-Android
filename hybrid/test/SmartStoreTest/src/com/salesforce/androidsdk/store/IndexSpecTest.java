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

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import android.test.InstrumentationTestCase;

/**
 * Test class for IndexSpec
 *
 */
public class IndexSpecTest extends InstrumentationTestCase {

	private static final IndexSpec keyStringSpec = new IndexSpec("key", Type.string);
	private static final IndexSpec keyIntegerSpec = new IndexSpec("key", Type.integer);
	private static final IndexSpec keyFloatingSpec = new IndexSpec("key", Type.floating);
	private static final IndexSpec keyStringSpecWithCol = new IndexSpec("key", Type.string, "COL_1");
	private static final IndexSpec keyIntegerSpecWithCol = new IndexSpec("key", Type.integer, "COL_1");
	private static final IndexSpec keyFloatingSpecWithCol = new IndexSpec("key", Type.floating, "COL_1");
	private static final IndexSpec valueStringSpec = new IndexSpec("value", Type.string);
	private static final IndexSpec otherValueStringSpec = new IndexSpec("otherValue", Type.string);

	/**
	 * TEST for equals with same index specs
	 */
	public void testEqualsWithSame() {
		assertEquals(keyStringSpec, new IndexSpec("key", Type.string));
		assertEquals(keyIntegerSpec, new IndexSpec("key", Type.integer));
		assertEquals(keyFloatingSpec, new IndexSpec("key", Type.floating));
		assertEquals(keyStringSpecWithCol, new IndexSpec("key", Type.string, "COL_1"));
		assertEquals(keyIntegerSpecWithCol, new IndexSpec("key", Type.integer, "COL_1"));
		assertEquals(keyFloatingSpecWithCol, new IndexSpec("key", Type.floating, "COL_1"));
	}
	
	/**
	 * TEST for equals with index specs that have different paths / types or columnNames
	 */
	public void testEqualsWithDifferent() {
		assertFalse(keyStringSpec.equals(new IndexSpec("otherKey", Type.string)));
		assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.integer)));
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
		assertEquals(keyStringSpecWithCol.hashCode(), new IndexSpec("key", Type.string, "COL_1").hashCode());
		assertEquals(keyIntegerSpecWithCol.hashCode(), new IndexSpec("key", Type.integer, "COL_1").hashCode());
		assertEquals(keyFloatingSpecWithCol.hashCode(), new IndexSpec("key", Type.floating, "COL_1").hashCode());
	}
	
	/**
	 * TEST for equals with index specs that have different paths / types or columnNames
	 */
	public void testHashCodeWithDifferent() {
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("otherKey", Type.string).hashCode());
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.integer).hashCode());
		assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.string, "COL_1").hashCode());
		assertFalse(keyStringSpecWithCol.hashCode() == new IndexSpec("key", Type.string).hashCode());
		assertFalse(keyStringSpecWithCol.hashCode() == new IndexSpec("key", Type.string, "COL_2").hashCode());
	}
	
	
	/**
	 * TEST for getChangedOrNewIndexSpecs
	 */
	public void testGetChangedOrNewIndexSpecs() {
		// No old specs
		tryGetChangedOrNewIndexSpecs(new IndexSpec[] {keyStringSpec, valueStringSpec}, new IndexSpec[0], new IndexSpec[] {keyStringSpec, valueStringSpec});
		// Old specs same as new specs
		tryGetChangedOrNewIndexSpecs(new IndexSpec[0], new IndexSpec[] {keyStringSpec, valueStringSpec}, new IndexSpec[] {keyStringSpec, valueStringSpec});
		// Old specs same as new specs but in a different order
		tryGetChangedOrNewIndexSpecs(new IndexSpec[0], new IndexSpec[] {keyStringSpec, valueStringSpec}, new IndexSpec[] {valueStringSpec, keyStringSpec});
		// Old specs same as new specs but with different column
		tryGetChangedOrNewIndexSpecs(new IndexSpec[0], new IndexSpec[] {keyStringSpecWithCol}, new IndexSpec[] {keyStringSpec});
		// Old specs same as new specs but with different column
		tryGetChangedOrNewIndexSpecs(new IndexSpec[0], new IndexSpec[] {keyStringSpec}, new IndexSpec[] {keyStringSpecWithCol});
		// Old specs same path as new specs but with type change
		tryGetChangedOrNewIndexSpecs(new IndexSpec[] {keyFloatingSpec}, new IndexSpec[] {keyStringSpec}, new IndexSpec[] {keyFloatingSpec});
		// Old specs subset of new specs
		tryGetChangedOrNewIndexSpecs(new IndexSpec[] {otherValueStringSpec}, new IndexSpec[] {keyStringSpec, valueStringSpec}, new IndexSpec[] {keyStringSpec, valueStringSpec, otherValueStringSpec});
		// Old specs interesects with new specs
		tryGetChangedOrNewIndexSpecs(new IndexSpec[] {otherValueStringSpec}, new IndexSpec[] {keyStringSpec, valueStringSpec}, new IndexSpec[] {valueStringSpec, otherValueStringSpec});
		// Old specs does not intersect with new specs
		tryGetChangedOrNewIndexSpecs(new IndexSpec[] {otherValueStringSpec}, new IndexSpec[] {keyStringSpec, valueStringSpec}, new IndexSpec[] {otherValueStringSpec});
	}
	
	/**
	 * Helper method for testGetChangedOrNewIndexSpecs
	 * @param expected
	 * @param old
	 */
	private void tryGetChangedOrNewIndexSpecs(IndexSpec[] expectedSpecs, IndexSpec[] oldSpecs, IndexSpec[] newSpecs) {
		IndexSpec[] actualSpecs = IndexSpec.getChangedOrNewIndexSpecs(oldSpecs, newSpecs);
		
		assertEquals("Arrays should have same length", expectedSpecs.length, actualSpecs.length);
		for (int i = 0; i< expectedSpecs.length; i++) {
			assertEquals("Wrong index spec at position " + i, expectedSpecs[i], actualSpecs[i]);
		}
	}
}
