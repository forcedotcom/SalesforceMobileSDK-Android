/*
 * Copyright (c) 2011, salesforce.com, inc.
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
package com.salesforce.samples.restexplorer;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.IntentFilter;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.util.BaseActivityInstrumentationTestCase;
import com.salesforce.androidsdk.util.EventsListenerQueue;

/**
 * Tests for ExplorerActivity
 *
 * NB: we are not actually talking to a live server
 *     instead we inject a mock http accessor in the RestClient
 *     and make sure that the http requests coming through are as expected (method/path/body)
 */
public class ExplorerActivityTest extends
        BaseActivityInstrumentationTestCase<ExplorerActivity> {

    private static final String TEST_ORG_ID = "test_org_id";
    private static final String TEST_USER_ID = "test_user_id";
    private static final String TEST_CLIENT_ID = "test_client_d";
    private static final String TEST_LOGIN_URL = "https://test.salesforce.com";
    private static final String TEST_INSTANCE_URL = "https://tapp0.salesforce.com";
    private static final String TEST_IDENTITY_URL = "https://test.salesforce.com";
    private static final String TEST_ACCESS_TOKEN = "test_access_token";
    private static final String TEST_REFRESH_TOKEN = "test_refresh_token";
    private static final String TEST_USERNAME = "test_username";
    private static final String TEST_ACCOUNT_NAME = "test_account_name";


    private static final int VERSIONS_TAB = 0;
    private static final int RESOURCES_TAB = 1;
    private static final int DESCRIBE_GLOBAL_TAB = 2;
    private static final int METADATA_TAB = 3;
    private static final int DESCRIBE_TAB = 4;
    private static final int CREATE_TAB = 5;
    private static final int RETRIEVE_TAB = 6;
    private static final int UPDATE_TAB = 7;
    private static final int UPSERT_TAB = 8;
    private static final int DELETE_TAB = 9;
    private static final int QUERY_TAB = 10;
    private static final int SEARCH_TAB = 11;
    private static final int MANUAL_REQUEST_TAB = 12;

    private Context targetContext;
    private ClientManager clientManager;
    private MockHttpAccess mockHttpAccessor;
    EventsListenerQueue eq;


    public ExplorerActivityTest() {
        super("com.salesforce.samples.restexplorer", ExplorerActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        targetContext = getInstrumentation().getTargetContext();
        clientManager = new ClientManager(targetContext, targetContext.getString(R.string.account_type), null, ForceApp.APP.shouldLogoutWhenTokenRevoked());
        clientManager.createNewAccount(TEST_ACCOUNT_NAME, TEST_USERNAME, TEST_REFRESH_TOKEN,
                TEST_ACCESS_TOKEN, TEST_INSTANCE_URL, TEST_LOGIN_URL, TEST_IDENTITY_URL, TEST_CLIENT_ID, TEST_ORG_ID, TEST_USER_ID, null);
        mockHttpAccessor = new MockHttpAccess(RestExplorerApp.APP);
        ForceApp.APP.getPasscodeManager().setTimeoutMs(0 /* disabled */);
    }

    /**
     * Test clicking clear.
     */
    public void testClickClear() {
        TextView resultText = (TextView) getActivity().findViewById(R.id.result_text);

        // Putting some text in the result text area
        setText(R.id.result_text, "dummy-text");
        assertFalse("Result text area should not be empty", resultText.getText().length() == 0);

        // Click on clear
        clickView(getActivity().findViewById(R.id.clear_button));

        // Check that result text has been cleared
        assertEquals("Result text area should habe been cleared", 0, resultText.getText().length());
    }

    /**
     * Test clicking logout and then canceling out.
     */
    public void _testClickLogoutThenCancel() {
        // Click on logout
        clickView(getActivity().findViewById(R.id.logout_button));

        // Check that confirmation dialog is shown
        assertTrue("Logout confirmation dialog showing", getActivity().logoutConfirmationDialog.isShowing());

        // Click no
        clickView(getActivity().logoutConfirmationDialog.getButton(AlertDialog.BUTTON_NEGATIVE));

        // Wait for dialog to go
        waitSome();

        // Check that confirmation dialog is no longer shown
        assertFalse("Logout confirmation dialog should no longer be showing", getActivity().logoutConfirmationDialog.isShowing());
    }

    /**
     * Test clicking logout and then clicking yes - make sure we end up in login screen.
     *
     * FIXME after logout subsequent tests fail
     */
    public void _testClickLogoutThenConfirm() {
        // Click on logout
        clickView(getActivity().findViewById(R.id.logout_button));

        // Check that confirmation dialog is shown
        assertTrue("Logout confirmation dialog showing", getActivity().logoutConfirmationDialog.isShowing());

        // Setup activity monitor
        ActivityMonitor monitor = getInstrumentation().addMonitor(new IntentFilter(LoginActivity.class.getName()), null, false);

        // Click yes
        clickView(getActivity().logoutConfirmationDialog.getButton(AlertDialog.BUTTON_POSITIVE));

        // Wait for login screen
        Activity loginActivity = monitor.waitForActivityWithTimeout(10000);
        assertTrue("Login should have been launched", loginActivity instanceof LoginActivity);
        loginActivity.finish();
    }

    /**
     * Test going to versions tab - check UI and click get versions.
     */
    public void testGetVersions() {
        gotoTabAndRunAction(VERSIONS_TAB, R.id.versions_button, "Get Versions", null, "[GET " + TEST_INSTANCE_URL + "/services/data/]");
    }

    /**
     * Test going to resources tab - check UI and click get resources.
     */
    public void testGetResources() {
        gotoTabAndRunAction(RESOURCES_TAB, R.id.resources_button, "Get Resources", null, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/]");
    }

    /**
     * Test going to describe global tab - check UI and click describe global.
     */
    public void testDescribeGlobal() {
        gotoTabAndRunAction(DESCRIBE_GLOBAL_TAB, R.id.describe_global_button, "Describe Global", null, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/]");
    }

    /**
     * Test going to metadata tab - check UI and click get metadata.
     */
    public void testGetMetadata() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.metadata_object_type_text, "objTypeMetadata");
            }
        };
        gotoTabAndRunAction(METADATA_TAB, R.id.metadata_button, "Get Metadata", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeMetadata/]");
    }

    /**
     * Test going to describe tab - check UI and click describe.
     */
    public void testDescribe() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.describe_object_type_text, "objTypeDescribe");
            }
        };
        gotoTabAndRunAction(DESCRIBE_TAB, R.id.describe_button, "Describe", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeDescribe/describe/]");
    }

    /**
     * Test going to create tab - check UI and click create.
     */
    public void testCreate() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.create_object_type_text, "objTypeCreate");
                setText(R.id.create_fields_text, "{\"field1\":\"create1\",\"field2\":\"create2\"}");
            }
        };
        gotoTabAndRunAction(CREATE_TAB, R.id.create_button, "Create", extraSetup, "[POST " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeCreate {\"field1\":\"create1\",\"field2\":\"create2\"}]");
    }

    /**
     * Test going to retrieve tab - check UI and click retrieve.
     */
    public void testRetrieve() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.retrieve_object_type_text, "objTypeRetrieve");
                setText(R.id.retrieve_object_id_text, "objIdRetrieve");
                setText(R.id.retrieve_field_list_text, "field1,field2");
            }
        };
        gotoTabAndRunAction(RETRIEVE_TAB, R.id.retrieve_button, "Retrieve", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeRetrieve/objIdRetrieve?fields=field1%2Cfield2]");
    }


    /**
     * Test going to update tab - check UI and click update.
     */
    public void testUpdate() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.update_object_type_text, "objTypeUpdate");
                setText(R.id.update_object_id_text, "objIdUpdate");
                setText(R.id.update_fields_text, "{\"field1\":\"update1\",\"field2\":\"update2\"}");
            }
        };
        gotoTabAndRunAction(UPDATE_TAB, R.id.update_button, "Update", extraSetup, "[PATCH " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeUpdate/objIdUpdate {\"field1\":\"update1\",\"field2\":\"update2\"}]");
    }

    /**
     * Test going to upsert tab - check UI and click upsert.
     */
    public void testUpsert() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.upsert_object_type_text, "objTypeUpsert");
                setText(R.id.upsert_external_id_field_text, "extIdField");
                setText(R.id.upsert_external_id_text, "extId");
                setText(R.id.upsert_fields_text, "{\"field1\":\"upsert1\",\"field2\":\"upsert2\"}");
            }
        };
        gotoTabAndRunAction(UPSERT_TAB, R.id.upsert_button, "Upsert", extraSetup, "[PATCH " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeUpsert/extIdField/extId {\"field1\":\"upsert1\",\"field2\":\"upsert2\"}]");
    }

    /**
     * Test going to delete tab - check UI and click delete.
     */
    public void testDelete() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.delete_object_type_text, "objTypeDelete");
                setText(R.id.delete_object_id_text, "objIdDelete");
            }
        };
        gotoTabAndRunAction(DELETE_TAB, R.id.delete_button, "Delete", extraSetup, "[DELETE " + TEST_INSTANCE_URL + "/services/data/v23.0/sobjects/objTypeDelete/objIdDelete]");
    }


    /**
     * Test going to query tab - check UI and click query.
     */
    public void testQuery() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.query_soql_text, "fake query");
            }
        };
        gotoTabAndRunAction(QUERY_TAB, R.id.query_button, "Query", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/query?q=fake+query]");
    }


    /**
     * Test going to search tab - check UI and click search.
     */
    public void testSearch() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.search_sosl_text, "fake search");
            }
        };
        gotoTabAndRunAction(SEARCH_TAB, R.id.search_button, "Search", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/v23.0/search?q=fake+search]");
    }


    /**
     * Test going to manual request tab - check UI and click run.
     */
    public void testManualRequest() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.manual_request_path_text, "/manualRequestPath");
                setText(R.id.manual_request_params_text, "{\"field1\":\"manual1\",\"field2\":\"manual2\"}");
                checkRadioButton(R.id.manual_request_put_radio);
            }
        };
        gotoTabAndRunAction(MANUAL_REQUEST_TAB, R.id.manual_request_button, "Run Manual Request", extraSetup, "[PUT " + TEST_INSTANCE_URL + "/manualRequestPath field1=manual1&field2=manual2]");
    }


    /**
     * Go to tab (tabId), check button (goButtonId) label is as expected (goButtonLabel)
     * Do any extra setup (extraSetup)
     * Click on button and check response is as expected (expectedResponse)
     *
     * @param tabIndex
     * @param goButtonId
     * @param goButtonLabel
     * @param extraSetup
     * @param expectedResponse
     */
    private void gotoTabAndRunAction(int tabIndex, int goButtonId, String goButtonLabel, Runnable extraSetup, String expectedResponse) {
        // Go to tab
        TabHost tabHost = (TabHost) getActivity().findViewById(android.R.id.tabhost);
        clickTab(tabHost, tabIndex);

        // Check UI
        Button runButton = (Button) getActivity().findViewById(goButtonId);
        assertEquals(goButtonLabel + " button has wrong label", goButtonLabel, runButton.getText());

        // Plug our mock access
        getActivity().getClient().setHttpAccessor(mockHttpAccessor);

        // Do any extra setup
        if (extraSetup != null) {
            extraSetup.run();
        }

        // Click resources button
        clickView(runButton);

        // Wait for call to complete
        String mockResponse = null;
        try {
            mockResponse = mockHttpAccessor.q.poll(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            fail("Test interrupted");
        }
        assertEquals("Wrong request executed", expectedResponse, mockResponse);


        // Check result area
        waitForRender();
        TextView resultText = (TextView) getActivity().findViewById(R.id.result_text);
        assertTrue("Response not found in text area", resultText.getText().toString().indexOf(expectedResponse) > 0);
    }

    /**
     * Mock http access
     */
    private static class MockHttpAccess extends HttpAccess {
        protected MockHttpAccess(Application app) {
            super(app, null);
        }

        public final BlockingQueue<String> q = new ArrayBlockingQueue<String>(1);

        protected Execution execute(HttpRequestBase req) throws ClientProtocolException, IOException {
            HttpResponse res = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("http", 1, 1), HttpStatus.SC_OK, null), null, null);
            String body = "";
            if (req instanceof HttpEntityEnclosingRequestBase) {
                body = " " + EntityUtils.toString(((HttpEntityEnclosingRequestBase) req).getEntity());
            }
            String mockResponse = "[" + req.getMethod() + " " + req.getURI() + body + "]";
            q.add(mockResponse);
            res.setEntity(new StringEntity(mockResponse));
            return new Execution(req, res);
        }
    }
}
