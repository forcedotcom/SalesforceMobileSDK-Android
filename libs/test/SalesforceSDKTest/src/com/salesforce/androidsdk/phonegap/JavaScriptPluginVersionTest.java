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
package com.salesforce.androidsdk.phonegap;

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.app.SalesforceSDKManager;


/**
 * Tests for JavaScriptPluginVersion
 *
 */
public class JavaScriptPluginVersionTest extends InstrumentationTestCase {

	/**
	 * Test for safeParseInt
	 */
	public void testSafeParseInt() {
		assertEquals(1, JavaScriptPluginVersion.safeParseInt("1", -1));
		assertEquals(-1, JavaScriptPluginVersion.safeParseInt("not-a-number", -1));
	}
	
	/**
	 * Test for compare versions when the same versions are passed in
	 */
	public void testCompareVersionsSameVersion() {
		assertEquals(0, JavaScriptPluginVersion.compareVersions("1", "1"));
		assertEquals(0, JavaScriptPluginVersion.compareVersions("1.2", "1.2"));
		assertEquals(0, JavaScriptPluginVersion.compareVersions("2.2.3", "2.2.3"));
	}
	
	/**
	 * Test for compare versions when the same versions are passed in and one is marked as unstable
	 * Unstable version is assumed to be older
	 */
	public void testCompareVersionsSameVersionWithUnstable() {
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("1.unstable", "1"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("1", "1.unstable"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("1.2.unstable", "1.2"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("1.2", "1.2.unstable"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("2.2.3.unstable", "2.2.3"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("2.2.3", "2.2.3.unstable"));
	}

	/**
	 * Test for compare versions when one version is a patch on the other
	 * NB unstable is simply ignored
	 */
	public void testCompareVersionsWithPatch() {
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("1", "1.1"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("1.1", "1"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("2.4", "2.4.2"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("2.4.2", "2.4"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("3.5.1", "3.5.1.3"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("3.5.1.3", "3.5.1"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("3.5", "3.5.1.3"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("3.5.1.3", "3.5"));
	}

	/**
	 * Test for compare versions with versions with two digits
	 * NB unstable is simply ignored
	 */
	public void testCompareVersionsWithTwoDigits() {
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("9", "14"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("14", "9"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("2.9", "2.14"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("2.14", "2.9"));
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("1.2.9", "1.2.14"));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("1.2.14", "1.2.9"));
	}

	/**
	 * Create JavaScriptPluginVersion for empty version (always considered older)
	 */
	public void testJavaScriptPluginVersionsWithNoVersion() {
		assertEquals(-1, JavaScriptPluginVersion.compareVersions("", "2.0"));
		assertEquals(0, JavaScriptPluginVersion.compareVersions("", ""));
		assertEquals(1, JavaScriptPluginVersion.compareVersions("2.0", ""));
	}
	
	
	/**
	 * Create JavaScriptPluginVersion for old versions and make sure isCurrent/isOlder/isNewer returns the value expected
	 */
	public void testJavaScriptPluginVersionsWithOldVersions() {
		for (String version : new String[] {"1.0", "1.1", "1.2", "1.3"}) {
			assertTrue((new JavaScriptPluginVersion(version)).isOlder());
			assertFalse((new JavaScriptPluginVersion(version)).isCurrent());
			assertFalse((new JavaScriptPluginVersion(version)).isNewer());
		}
	}

	/**
	 * Create JavaScriptPluginVersion for current version and make sure isCurrent/isOlder/isNewer returns the value expected
	 */
	public void testJavaScriptPluginVersionsWithCurrentVersion() {
		assertFalse((new JavaScriptPluginVersion(SalesforceSDKManager.SDK_VERSION)).isOlder());
		assertTrue((new JavaScriptPluginVersion(SalesforceSDKManager.SDK_VERSION)).isCurrent());
		assertFalse((new JavaScriptPluginVersion(SalesforceSDKManager.SDK_VERSION)).isNewer());
	}

	
	/**
	 * Create JavaScriptPluginVersion for future versions and make sure isCurrent/isOlder/isNewer returns the value expected
	 */
	public void testJavaScriptPluginVersionsWithNewVersion() {
		for (String version : new String[] {"3.3.0", "3.4.0", "3.5.0"}) {
			assertFalse((new JavaScriptPluginVersion(version)).isOlder());
			assertFalse((new JavaScriptPluginVersion(version)).isCurrent());
			assertTrue((new JavaScriptPluginVersion(version)).isNewer());
		}
	}
}
