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
package com.salesforce.androidsdk.util;

import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Pair;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tests for LogUtil
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class LogUtilTest {

	private static final String PAIR_DELIM = "#";
	private static final String DELIM = ",";
	private static final String OTHER_DELIM = " AND ";
	private static final String OPERATOR = "=";
	private static final String OTHER_OPERATOR= "<>";

	private static final String[] EMPTY_ARRAY = new String[0];
	private static final String[] ONE_ELT_ARRAY = new String[]{"a"};
	private static final String[] TWO_ELTS_ARRAY = new String[]{"a", "b"};
	private static final String[] THREE_ELTS_ARRAY = new String[]{"a", "b", "cccc"};

	private static Set<Entry<String, Object>> buildMapValues(String[] arr) {
		Map<String, Object> map = new TreeMap<String, Object>();
		for (String s : arr) {
			map.put("k" + s, "v" + s);
		}
		return map.entrySet();
	}

	private static Set<Entry<String, Object>> buildMapIntegerValues(String[] arr) {
		Map<String, Object> map = new TreeMap<String, Object>();
		int i = 0;
		for (String s : arr) {
			map.put("k" + s, i);
			i++;
		}
		return map.entrySet();
	}

	private static final Set<Entry<String, Object>> EMPTY_SET = buildMapValues(EMPTY_ARRAY);
	private static final Set<Entry<String, Object>> ONE_ELT_SET = buildMapValues(ONE_ELT_ARRAY);
	private static final Set<Entry<String, Object>> TWO_ELTS_SET = buildMapValues(TWO_ELTS_ARRAY);
	private static final Set<Entry<String, Object>> THREE_ELTS_SET = buildMapValues(THREE_ELTS_ARRAY);
	private static final Set<Entry<String, Object>> INTEGER_VALUES_SET = buildMapIntegerValues(THREE_ELTS_ARRAY);
	
	/**
	 * Test getAsSTring(...)
	 */
    @Test
	public void testGetAsStrings() {
        Assert.assertEquals("Wrong return value for null", "#", merge(LogUtil.getAsStrings(null, DELIM)));
        Assert.assertEquals("Wrong return value for empty set", "#", merge(LogUtil.getAsStrings(EMPTY_SET, DELIM)));
        Assert.assertEquals("Wrong return value for one element set", "ka#'va'", merge(LogUtil.getAsStrings(ONE_ELT_SET, DELIM)));
        Assert.assertEquals("Wrong return value for two elements set", "ka,kb#'va','vb'", merge(LogUtil.getAsStrings(TWO_ELTS_SET, DELIM)));
        Assert.assertEquals("Wrong return value for three elements set", "ka,kb,kcccc#'va','vb','vcccc'", merge(LogUtil.getAsStrings(THREE_ELTS_SET, DELIM)));
        Assert.assertEquals("Wrong return value for other delimiter", "ka AND kb AND kcccc#'va' AND 'vb' AND 'vcccc'", merge(LogUtil.getAsStrings(THREE_ELTS_SET, OTHER_DELIM)));
        Assert.assertEquals("Wrong return value for integers values set", "ka,kb,kcccc#0,1,2", merge(LogUtil.getAsStrings(INTEGER_VALUES_SET, DELIM)));
	}
	
	/**
	 * Test zipJoin
	 */
    @Test
	public void testZipJoin() {
        Assert.assertEquals("Wrong return value for null", "", LogUtil.zipJoin(null, OPERATOR, DELIM));
        Assert.assertEquals("Wrong return value for empty set", "", LogUtil.zipJoin(EMPTY_SET, OPERATOR, DELIM));
        Assert.assertEquals("Wrong return value for one element set", "ka='va'", LogUtil.zipJoin(ONE_ELT_SET, OPERATOR, DELIM));
        Assert.assertEquals("Wrong return value for two elements set", "ka='va',kb='vb'", LogUtil.zipJoin(TWO_ELTS_SET, OPERATOR, DELIM));
        Assert.assertEquals("Wrong return value for three elements set", "ka='va',kb='vb',kcccc='vcccc'", LogUtil.zipJoin(THREE_ELTS_SET, OPERATOR, DELIM));
        Assert.assertEquals("Wrong return value for other delimiter", "ka='va' AND kb='vb' AND kcccc='vcccc'", LogUtil.zipJoin(THREE_ELTS_SET, OPERATOR, OTHER_DELIM));
        Assert.assertEquals("Wrong return value for other operator", "ka<>'va',kb<>'vb',kcccc<>'vcccc'", LogUtil.zipJoin(THREE_ELTS_SET, OTHER_OPERATOR, DELIM));
        Assert.assertEquals("Wrong return value for integers values set", "ka=0,kb=1,kcccc=2",  LogUtil.zipJoin(INTEGER_VALUES_SET, OPERATOR, DELIM));
	}
	
	/**
	 * Helper to make assertEquals easier with Pair objects
	 * @param p
	 * @return
	 */
	private String merge(Pair<String, String> p) {
		return p.first + PAIR_DELIM + p.second;
	}
}