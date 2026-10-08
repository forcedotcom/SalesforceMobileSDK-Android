/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val CHROME = "com.android.chrome"
private const val EDGE = "com.microsoft.emmx"

private val chrome = InstalledBrowser("Chrome", CHROME)
private val edge = InstalledBrowser("Edge", EDGE)

@RunWith(AndroidJUnit4::class)
class CustomTabBrowserOptionsTest {

    @Test
    fun buildOptions_givenNullAppDefault_whenBuilt_thenOmitsAppDefaultRow() {
        val options = buildCustomTabBrowserOptions(null, listOf(chrome, edge))

        assertEquals(
            listOf(
                CustomTabBrowserOptionType.SystemDefault,
                CustomTabBrowserOptionType.Browser,
                CustomTabBrowserOptionType.Browser,
            ),
            options.map { it.type },
        )
    }

    @Test
    fun buildOptions_givenInstalledAppDefault_whenBuilt_thenAppDefaultIsNotRepeated() {
        val options = buildCustomTabBrowserOptions(CHROME, listOf(chrome, edge))

        assertEquals(
            listOf(
                CustomTabBrowserOptionType.AppDefault,
                CustomTabBrowserOptionType.SystemDefault,
                CustomTabBrowserOptionType.Browser,
            ),
            options.map { it.type },
        )
        assertEquals(CHROME, options[0].packageName)
        assertEquals("Chrome", options[0].label)
        assertEquals(true, options[0].installed)
        assertEquals(EDGE, options[2].packageName)
    }

    @Test
    fun buildOptions_givenAppDefaultNotFound_whenBuilt_thenAppDefaultIsMarkedNotInstalled() {
        val options = buildCustomTabBrowserOptions(CHROME, listOf(edge))

        assertEquals(CustomTabBrowserOptionType.AppDefault, options[0].type)
        assertEquals(false, options[0].installed)
        assertEquals(EDGE, options.last().packageName)
    }

    @Test
    fun selectedIndex_givenNullSelection_whenSearched_thenSelectsSystemDefault() {
        val options = buildCustomTabBrowserOptions(CHROME, listOf(chrome, edge))

        assertEquals(1, selectedCustomTabBrowserIndex(options, null))
        assertEquals(0, selectedCustomTabBrowserIndex(options, CHROME))
        assertEquals(2, selectedCustomTabBrowserIndex(options, EDGE))
        assertEquals(-1, selectedCustomTabBrowserIndex(options, "unknown.browser"))
    }
}
