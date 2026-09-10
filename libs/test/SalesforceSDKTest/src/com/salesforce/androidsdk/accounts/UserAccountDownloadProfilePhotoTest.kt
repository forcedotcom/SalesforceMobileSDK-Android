/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.accounts

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression coverage for [UserAccount.downloadProfilePhoto]: must not crash
 * when [PackageManager.getApplicationEnabledSetting] throws, which happens on
 * OEM/enterprise-managed devices where com.android.providers.downloads is
 * absent rather than merely disabled.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class UserAccountDownloadProfilePhotoTest {

    private lateinit var mockSdkManager: SalesforceSDKManager
    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockDownloadManager: DownloadManager

    @Before
    fun setUp() {
        mockPackageManager = mockk(relaxed = true)
        mockDownloadManager = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.getSystemService(DOWNLOAD_SERVICE) } returns mockDownloadManager
        every { mockContext.externalCacheDir } returns File("/tmp/UserAccountDownloadProfilePhotoTest")

        mockSdkManager = mockk(relaxed = true)
        mockkObject(SalesforceSDKManager)
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
        every { mockSdkManager.appContext } returns mockContext

        mockkStatic(SalesforceSDKLogger::class)
        every { SalesforceSDKLogger.w(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun buildTestAccount(): UserAccount = UserAccountBuilder.getInstance()
        .userId("test_user_id")
        .orgId("test_org_id")
        .authToken("test_auth_token")
        .photoUrl("http://some.photo.url")
        .build()

    @Test
    fun downloadProfilePhoto_whenPackageUnknown_doesNotCrashAndSkipsDownload() {
        /*
         * Given - the downloads provider package doesn't exist on this
         * device at all, so the PackageManager query itself throws rather
         * than returning a disabled state.
         */
        every {
            mockPackageManager.getApplicationEnabledSetting("com.android.providers.downloads")
        } throws IllegalArgumentException("Unknown package: com.android.providers.downloads")

        // When / Then - must return normally, not propagate the exception.
        buildTestAccount().downloadProfilePhoto()

        verify(exactly = 0) { mockDownloadManager.enqueue(any()) }
    }

    @Test
    fun downloadProfilePhoto_whenPackageDisabled_skipsDownload() {
        every {
            mockPackageManager.getApplicationEnabledSetting("com.android.providers.downloads")
        } returns COMPONENT_ENABLED_STATE_DISABLED

        buildTestAccount().downloadProfilePhoto()

        verify(exactly = 0) { mockDownloadManager.enqueue(any()) }
    }

    @Test
    fun downloadProfilePhoto_whenPackageEnabled_enqueuesDownload() {
        every {
            mockPackageManager.getApplicationEnabledSetting("com.android.providers.downloads")
        } returns COMPONENT_ENABLED_STATE_ENABLED

        buildTestAccount().downloadProfilePhoto()

        verify(exactly = 1) { mockDownloadManager.enqueue(any()) }
    }
}
