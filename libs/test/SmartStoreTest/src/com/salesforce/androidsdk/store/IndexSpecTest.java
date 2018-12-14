/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class for IndexSpec
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class IndexSpecTest {

	private static final IndexSpec keyStringSpec = new IndexSpec("key", Type.string);
	private static final IndexSpec keyIntegerSpec = new IndexSpec("key", Type.integer);
	private static final IndexSpec keyFloatingSpec = new IndexSpec("key", Type.floating);
	private static final IndexSpec keyFullTextSpec = new IndexSpec("key", Type.full_text);
	private static final IndexSpec keyJSON1Spec = new IndexSpec("key", Type.json1);

	private static final IndexSpec keyStringSpecWithCol = new IndexSpec("key", Type.string, "COL_1");
	private static final IndexSpec keyIntegerSpecWithCol = new IndexSpec("key", Type.integer, "COL_1");
	private static final IndexSpec keyFloatingSpecWithCol = new IndexSpec("key", Type.floating, "COL_1");
	private static final IndexSpec keyFullTextSpecWithCol = new IndexSpec("key", Type.full_text, "COL_1");
	private static final IndexSpec keyJSON1SpecWithCol = new IndexSpec("key", Type.json1, "COL_1");

	/**
	 * TEST for equals with same index specs
	 */
    @Test
	public void testEqualsWithSame() {
        Assert.assertEquals(keyStringSpec, new IndexSpec("key", Type.string));
        Assert.assertEquals(keyIntegerSpec, new IndexSpec("key", Type.integer));
        Assert.assertEquals(keyFloatingSpec, new IndexSpec("key", Type.floating));
        Assert.assertEquals(keyFullTextSpec, new IndexSpec("key", Type.full_text));
        Assert.assertEquals(keyJSON1Spec, new IndexSpec("key", Type.json1));
        Assert.assertEquals(keyStringSpecWithCol, new IndexSpec("key", Type.string, "COL_1"));
        Assert.assertEquals(keyIntegerSpecWithCol, new IndexSpec("key", Type.integer, "COL_1"));
        Assert.assertEquals(keyFloatingSpecWithCol, new IndexSpec("key", Type.floating, "COL_1"));
        Assert.assertEquals(keyFullTextSpecWithCol, new IndexSpec("key", Type.full_text, "COL_1"));
        Assert.assertEquals(keyJSON1SpecWithCol, new IndexSpec("key", Type.json1, "COL_1"));
	}

	/**
	 * TEST for equals with index specs that have different paths / types or columnNames
	 */
    @Test
	public void testEqualsWithDifferent() {

		// Different path
        Assert.assertFalse(keyStringSpec.equals(new IndexSpec("otherKey", Type.string)));

		// Different type
        Assert.assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.integer)));
        Assert.assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.floating)));
        Assert.assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.full_text)));
        Assert.assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.json1)));

		// Different columnName
        Assert.assertFalse(keyStringSpec.equals(new IndexSpec("key", Type.string, "COL_1")));
        Assert.assertFalse(keyStringSpecWithCol.equals(new IndexSpec("key", Type.string)));
        Assert.assertFalse(keyStringSpecWithCol.equals(new IndexSpec("key", Type.string, "COL_2")));
	}

	/**
	 * TEST for equals with same index specs
	 */
    @Test
	public void testHashCodeWithSame() {
        Assert.assertEquals(keyStringSpec.hashCode(), new IndexSpec("key", Type.string).hashCode());
        Assert.assertEquals(keyIntegerSpec.hashCode(), new IndexSpec("key", Type.integer).hashCode());
        Assert.assertEquals(keyFloatingSpec.hashCode(), new IndexSpec("key", Type.floating).hashCode());
        Assert.assertEquals(keyFullTextSpec.hashCode(), new IndexSpec("key", Type.full_text).hashCode());
        Assert.assertEquals(keyJSON1Spec.hashCode(), new IndexSpec("key", Type.json1).hashCode());
        Assert.assertEquals(keyStringSpecWithCol.hashCode(), new IndexSpec("key", Type.string, "COL_1").hashCode());
        Assert.assertEquals(keyIntegerSpecWithCol.hashCode(), new IndexSpec("key", Type.integer, "COL_1").hashCode());
        Assert.assertEquals(keyFloatingSpecWithCol.hashCode(), new IndexSpec("key", Type.floating, "COL_1").hashCode());
        Assert.assertEquals(keyFullTextSpecWithCol.hashCode(), new IndexSpec("key", Type.full_text, "COL_1").hashCode());
        Assert.assertEquals(keyJSON1SpecWithCol.hashCode(), new IndexSpec("key", Type.json1, "COL_1").hashCode());
	}
	
	/**
	 * TEST for equals with index specs that have different paths / types or columnNames
	 */
    @Test
	public void testHashCodeWithDifferent() {

		// Different path
        Assert.assertFalse(keyStringSpec.hashCode() == new IndexSpec("otherKey", Type.string).hashCode());

		// Different type
        Assert.assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.integer).hashCode());
        Assert.assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.floating).hashCode());
        Assert.assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.full_text).hashCode());
        Assert.assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.json1).hashCode());

		// Different columnName
        Assert.assertFalse(keyStringSpec.hashCode() == new IndexSpec("key", Type.string, "COL_1").hashCode());
        Assert.assertFalse(keyStringSpecWithCol.hashCode() == new IndexSpec("key", Type.string).hashCode());
        Assert.assertFalse(keyStringSpecWithCol.hashCode() == new IndexSpec("key", Type.string, "COL_2").hashCode());
	}
}
