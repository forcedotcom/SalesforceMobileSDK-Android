/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;

import java.net.URI;
/**
 * Do org data cleanup
 *
 */
public class TestDataCleanupHelper extends InstrumentationTestCase {

    private ClientInfo clientInfo;
    private HttpAccess httpAccess;
    private RestClient restClient;
    private String authToken;
    public static final String TEST_FIRST_NAME = "firstName";
    public static final String TEST_LAST_NAME = "lastName";
    public static final String TEST_DISPLAY_NAME = "displayName";
    public static final String TEST_EMAIL = "test@email.com";
    public static final String TEST_PHOTO_URL = "http://some.photo.url";
    public static final String TEST_THUMBNAIL_URL = "http://some.thumbnail.url";
    private static final String EXPECTED_DELETE = "ExpectedDeletes";


    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestCredentials.init(getInstrumentation().getContext());
        httpAccess = new HttpAccess(null, "dummy-agent");
        TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess, new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
        authToken = refreshResponse.authToken;
        clientInfo = new ClientInfo(TestCredentials.CLIENT_ID,
                new URI(TestCredentials.INSTANCE_URL),
                new URI(TestCredentials.LOGIN_URL),
                new URI(TestCredentials.IDENTITY_URL),
                TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
                TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null,
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_DISPLAY_NAME, TEST_EMAIL, TEST_PHOTO_URL, TEST_THUMBNAIL_URL);
        restClient = new RestClient(clientInfo, authToken, httpAccess, null);
    }

    /**
     * Cleanup all records that created by test account.
     *
     * @throws Exception
     */
    public void testCleanupOrgData() throws Exception {
        int stop = 0;
        int expectedCount = 0;
        String requestFormat = "/services/apexrest/Cleanup?start=%d&stop=%d";
        int maxTransactCount = 100;
        do {
            int start = stop;
            stop = stop + maxTransactCount;

            RestResponse response = restClient.sendSync(new RestRequest(RestMethod.DELETE, String.format(requestFormat, start, stop), null));
            expectedCount = response.asJSONObject().getInt(EXPECTED_DELETE);
        } while (expectedCount >= maxTransactCount);
    }
}
