/*
 * Copyright (c) 2011-2016, salesforce.com, inc.
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

import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TabHost;
import android.widget.TextView;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Tests for ExplorerActivity
 *
 * NB: we are not actually talking to a live server
 *     instead we inject a mock http accessor in the RestClient
 *     and make sure that the http requests coming through are as expected (method/path/body)
 */
public class ExplorerActivityTest extends
        ActivityInstrumentationTestCase2<ExplorerActivity> {

    private static final String TEST_ORG_ID = "test_org_id";
    private static final String TEST_USER_ID = "test_user_id";
    private static final String TEST_CLIENT_ID = "test_client_d";
    private static final String TEST_LOGIN_URL = "https://test.salesforce.com";
    private static final String TEST_INSTANCE_URL = "https://cs1.salesforce.com";
    private static final String TEST_IDENTITY_URL = "https://test.salesforce.com";
    private static final String TEST_ACCESS_TOKEN = "test_access_token";
    private static final String TEST_REFRESH_TOKEN = "test_refresh_token";
    private static final String TEST_USERNAME = "test_username";
    private static final String TEST_ACCOUNT_NAME = "test_account_name (https://cs1.salesforce.com) (RestExplorerTest)";

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
    private static final int SEARCH_SCOPE_AND_ORDER_TAB = 13;
    private static final int SEARCH_RESULT_LAYOUT_TAB = 14;
    private static final int OWNED_FILES_LIST_TAB = 15;
    private static final int FILES_IN_USERS_GROUPS_TAB = 16;
    private static final int FILES_SHARED_WITH_USER_TAB = 17;
    private static final int FILE_DETAILS_TAB = 18;
    private static final int BATCH_FILE_DETAILS_TAB = 19;
    private static final int FILE_SHARES_TAB = 20;
    private static final int ADD_FILE_SHARE_TAB = 21;
    private static final int DELETE_FILE_SHARE_TAB = 22;

    public static final MediaType MEDIA_TYPE_PLAIN = okhttp3.MediaType.parse("text/plain; charset=utf-8");


    private EventsListenerQueue eq;
    private Context targetContext;
    private ClientManager clientManager;
    public static volatile String RESPONSE = null;

    public ExplorerActivityTest() {
        super(ExplorerActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        eq = new EventsListenerQueue();

        // Waits for app initialization to complete.
        if (!SalesforceSDKManager.hasInstance()) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        targetContext = getInstrumentation().getTargetContext();
        clientManager = new ClientManager(targetContext, targetContext.getString(R.string.account_type), null, SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked());
        clientManager.createNewAccount(TEST_ACCOUNT_NAME, TEST_USERNAME, TEST_REFRESH_TOKEN,
                TEST_ACCESS_TOKEN, TEST_INSTANCE_URL, TEST_LOGIN_URL, TEST_IDENTITY_URL, TEST_CLIENT_ID, TEST_ORG_ID, TEST_USER_ID, null);
        SalesforceSDKManager.getInstance().getPasscodeManager().setTimeoutMs(0);
        final AccountManager accountManager = AccountManager.get(targetContext);

        /*
         * Since we are using bogus credentials, we need to explicitly set the auth token value to
         * prevent ClientManager from attempting a refresh with the bogus refresh token.
         */
        accountManager.setAuthToken(clientManager.getAccount(), AccountManager.KEY_AUTHTOKEN, TEST_ACCESS_TOKEN);

        // Plug a modified OkHttpClient that doesn't actually go to the server.
        final ExplorerActivity activity = getActivity();
        assertNotNull("Activity should not be null", activity);
        final RestClient client = activity.getClient();
        assertNotNull("Rest client should not be null", client);
        final OkHttpClient mockOkHttpClient = buildMockOkHttpClient();
        client.setOkHttpClient(mockOkHttpClient);
    }

    private OkHttpClient buildMockOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        final String requestBody;
                        if (request.body() != null) {
                            final Buffer buffer = new Buffer();
                            request.body().writeTo(buffer);
                            requestBody = " " + buffer.readUtf8();
                        }
                        else {
                            requestBody = "";
                        }

                        RESPONSE = "[" + request.method() + " " + request.url() + requestBody + "]";
                        Response response = new Response.Builder()
                                .request(request)
                                .code(HttpURLConnection.HTTP_OK)
                                .protocol(Protocol.HTTP_2)
                                .body(ResponseBody.create(MEDIA_TYPE_PLAIN, RESPONSE))
                                .build();
                        return response;
                    }
                });
        return builder.build();
    }

    @Override
	public void tearDown() throws Exception {
		if (eq != null) {
            eq.tearDown();
            eq = null;
        }
		RESPONSE = null;
		super.tearDown();
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
    public void testClickLogoutThenCancel() {

        // Click on logout
        clickView(getActivity().findViewById(R.id.logout_button));
        waitSome();
        waitSome(); // wait more to avoid flapping

        // Check that confirmation dialog is shown
        final ExplorerActivity activity = getActivity();
        assertNotNull("Activity should not be null", activity);
        final LogoutDialogFragment logoutFrag = activity.getLogoutConfirmationDialog();
        assertNotNull("Logout dialog fragment should not be null", logoutFrag);
        final AlertDialog dialog = (AlertDialog) logoutFrag.getDialog();
        assertNotNull("Logout dialog should not be null", dialog);
        // FLAPPING // assertTrue("Logout confirmation dialog should be showing", dialog.isShowing());

        // Click no
        clickView(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));

        // Wait for dialog to go
        waitSome();

        // Check that confirmation dialog is no longer shown
        // FLAPPING // assertFalse("Logout confirmation dialog should no longer be showing", dialog.isShowing());
    }

    /**
     * Test clicking logout and then clicking yes - make sure we end up removing
     * the account.
     */
    public void testClickLogoutThenConfirm() {

        // Click on logout
        clickView(getActivity().findViewById(R.id.logout_button));
        waitSome();

        // Check that confirmation dialog is shown
        final ExplorerActivity activity = getActivity();
        assertNotNull("Activity should not be null", activity);
        final LogoutDialogFragment logoutFrag = activity.getLogoutConfirmationDialog();
        assertNotNull("Logout dialog fragment should not be null", logoutFrag);
        final AlertDialog dialog = (AlertDialog) logoutFrag.getDialog();
        assertNotNull("Logout dialog should not be null", dialog);
        // FLAPPING // assertTrue("Logout confirmation dialog should be showing", dialog.isShowing());
        final UserAccountManager userAccMgr = SalesforceSDKManager.getInstance().getUserAccountManager();
        UserAccount curUser = userAccMgr.getCurrentUser();
        assertNotNull("Current user should not be null", curUser);

        // Click yes
        clickView(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        final EventsListenerQueue eq = new EventsListenerQueue();
        eq.waitForEvent(EventType.LogoutComplete, 30000);
        curUser = userAccMgr.getCurrentUser();
        assertNull("Current user should be null", curUser);
    }

    /**
     * Test going to versions tab - check UI and click "Go".
     */
    public void testGetVersions() {
        gotoTabAndRunAction(VERSIONS_TAB, R.id.versions_button, "Go", null, "[GET " + TEST_INSTANCE_URL + "/services/data/]");
    }

    /**
     * Test going to resources tab - check UI and click "Go".
     */
    public void testGetResources() {
        gotoTabAndRunAction(RESOURCES_TAB, R.id.resources_button, "Go", null, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/]");
    }

    /**
     * Test going to describe global tab - check UI and click "Go".
     */
    public void testDescribeGlobal() {
        gotoTabAndRunAction(DESCRIBE_GLOBAL_TAB, R.id.describe_global_button, "Go", null, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/]");
    }

    /**
     * Test going to metadata tab - check UI and click "Go".
     */
    public void testGetMetadata() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.metadata_object_type_text, "objTypeMetadata");
            }
        };
        gotoTabAndRunAction(METADATA_TAB, R.id.metadata_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeMetadata/]");
    }

    /**
     * Test going to describe tab - check UI and click "Go".
     */
    public void testDescribe() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.describe_object_type_text, "objTypeDescribe");
            }
        };
        gotoTabAndRunAction(DESCRIBE_TAB, R.id.describe_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeDescribe/describe/]");
    }

    /**
     * Test going to create tab - check UI and click "Go".
     */
    public void testCreate() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.create_object_type_text, "objTypeCreate");
                setText(R.id.create_fields_text, "{\"field1\":\"create1\",\"field2\":\"create2\"}");
            }
        };
        gotoTabAndRunAction(CREATE_TAB, R.id.create_button, "Go", extraSetup, "[POST " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeCreate {\"field1\":\"create1\",\"field2\":\"create2\"}]");
    }

    /**
     * Test going to retrieve tab - check UI and click "Go".
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
        gotoTabAndRunAction(RETRIEVE_TAB, R.id.retrieve_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeRetrieve/objIdRetrieve?fields=field1%2Cfield2]");
    }


    /**
     * Test going to update tab - check UI and click "Go".
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
        gotoTabAndRunAction(UPDATE_TAB, R.id.update_button, "Go", extraSetup, "[PATCH " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeUpdate/objIdUpdate {\"field1\":\"update1\",\"field2\":\"update2\"}]");
    }

    /**
     * Test going to upsert tab - check UI and click "Go".
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
        gotoTabAndRunAction(UPSERT_TAB, R.id.upsert_button, "Go", extraSetup, "[PATCH " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeUpsert/extIdField/extId {\"field1\":\"upsert1\",\"field2\":\"upsert2\"}]");
    }

    /**
     * Test going to delete tab - check UI and click "Go".
     */
    public void testDelete() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.delete_object_type_text, "objTypeDelete");
                setText(R.id.delete_object_id_text, "objIdDelete");
            }
        };
        gotoTabAndRunAction(DELETE_TAB, R.id.delete_button, "Go", extraSetup, "[DELETE " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/objTypeDelete/objIdDelete]");
    }


    /**
     * Test going to query tab - check UI and click "Go".
     */
    public void testQuery() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.query_soql_text, "fake query");
            }
        };
        gotoTabAndRunAction(QUERY_TAB, R.id.query_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/query?q=fake+query]");
    }


    /**
     * Test going to search tab - check UI and click "Go".
     */
    public void testSearch() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.search_sosl_text, "fake search");
            }
        };
        gotoTabAndRunAction(SEARCH_TAB, R.id.search_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/search?q=fake+search]");
    }


    /**
     * Test going to manual request tab - check UI and click "Go".
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
        gotoTabAndRunAction(MANUAL_REQUEST_TAB, R.id.manual_request_button, "Go", extraSetup, "[PUT " + TEST_INSTANCE_URL + "/manualRequestPath field1=manual1&field2=manual2]");
    }

    /**
     * Test going to search scope and order tab - check UI and click "Go".
     */
    public void testSearchScopeAndOrderTab() {
        gotoTabAndRunAction(SEARCH_SCOPE_AND_ORDER_TAB, R.id.search_scope_and_order_button, "Go", null, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/search/scopeOrder]");
    }

    /**
     * Test going to search result layout tab - check UI and click "Go".
     */
    public void testSearchResultLayout() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.search_result_layout_object_list_text, "Account,Contact");
            }
        };
        gotoTabAndRunAction(SEARCH_RESULT_LAYOUT_TAB, R.id.search_result_layout_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/search/layout?q=Account%2CContact]");
    }

    /**
     * Test going to owned files list tab - check UI and click "Go".
     */
    public void testOwnedFilesList() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.owned_files_list_user_id_text, "filesOwnedByUserId");
                setText(R.id.owned_files_list_page_text, "0");
            }
        };
        gotoTabAndRunAction(OWNED_FILES_LIST_TAB, R.id.owned_files_list_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/chatter/connect/files/users/filesOwnedByUserId?page=0]");
    }

    /**
     * Test going to files in users groups tab - check UI and click "Go".
     */
    public void testFilesInUsersGroups() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.files_in_users_groups_user_or_group_id_text, "filesInUsersGroupsId");
                setText(R.id.files_in_users_groups_page_text, "0");
            }
        };
        gotoTabAndRunAction(FILES_IN_USERS_GROUPS_TAB, R.id.files_in_users_groups_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/chatter/connect/files/users/filesInUsersGroupsId/filter/groups?page=0]");
    }

    /**
     * Test going to files shared with user tab - check UI and click "Go".
     */
    public void testFilesSharedWithUser() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.files_shared_with_user_user_id_text, "fileSharedWithUserId");
                setText(R.id.files_shared_with_user_page_text, "0");
            }
        };
        gotoTabAndRunAction(FILES_SHARED_WITH_USER_TAB, R.id.files_shared_with_user_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/chatter/connect/files/users/fileSharedWithUserId/filter/sharedwithme?page=0]");
    }

    /**
     * Test going to file details tab - check UI and click "Go".
     */
    public void testFileDetails() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.file_details_document_id_text, "detailsForFileId");
                setText(R.id.file_details_version_text, "1");
            }
        };
        gotoTabAndRunAction(FILE_DETAILS_TAB, R.id.file_details_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/chatter/connect/files/detailsForFileId?versionNumber=1]");
    }

    /**
     * Test going to batch file details tab - check UI and click "Go".
     */
    public void testBatchFileDetails() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.batch_file_details_document_id_list_text, "fileId1,fileId2");
            }
        };
        gotoTabAndRunAction(BATCH_FILE_DETAILS_TAB, R.id.batch_file_details_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/chatter/connect/files/batch/fileId1,fileId2]");
    }

    /**
     * Test going to file shares tab - check UI and click "Go".
     */
    public void testFileShares() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.file_shares_document_id_text, "sharesForFileId");
                setText(R.id.file_shares_page_text, "0");
            }
        };
        gotoTabAndRunAction(FILE_SHARES_TAB, R.id.file_shares_button, "Go", extraSetup, "[GET " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/chatter/connect/files/sharesForFileId/file-shares?page=0]");
    }

    /**
     * Test going to add file share tab - check UI and click "Go".
     */
    public void testAddFileShare() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.add_file_share_document_id_text, "objectIdForAdd");
                setText(R.id.add_file_share_entity_id_text, "entityIdForAdd");
                setText(R.id.add_file_share_share_type_text, "shareType");
            }
        };
        gotoTabAndRunAction(ADD_FILE_SHARE_TAB, R.id.add_file_share_button, "Go", extraSetup, "[POST " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/ContentDocumentLink {\"ContentDocumentId\":\"objectIdForAdd\",\"ShareType\":\"shareType\",\"LinkedEntityId\":\"entityIdForAdd\"}]");
    }

    /**
     * Test going to delete file share tab - check UI and click "Go".
     */
    public void testDeleteFileShare() {
        Runnable extraSetup = new Runnable() {
            @Override
            public void run() {
                setText(R.id.delete_file_share_share_id_text, "shareIdToDelete");
           }
        };
        gotoTabAndRunAction(DELETE_FILE_SHARE_TAB, R.id.delete_file_share_button, "Go", extraSetup, "[DELETE " + TEST_INSTANCE_URL + "/services/data/" + ApiVersionStrings.getVersionNumber(targetContext) + "/sobjects/ContentDocumentLink/shareIdToDelete]");
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
        final ExplorerActivity activity = getActivity();
        assertNotNull("Activity should not be null", activity);
        TabHost tabHost = (TabHost) activity.findViewById(android.R.id.tabhost);
        clickTab(tabHost, tabIndex);

        // Check UI
        Button runButton = (Button) activity.findViewById(goButtonId);
        assertEquals(goButtonLabel + " button has wrong label", goButtonLabel, runButton.getText());

        // Do any extra setup
        if (extraSetup != null) {
            extraSetup.run();
        }

        // Click resources button
        clickView(runButton);

        // Wait for call to complete
        long curTime = System.currentTimeMillis();
        while (RESPONSE == null) {
            waitSome();
            if (System.currentTimeMillis() - curTime > 5000) {
        		break;
        	}
        }
        assertEquals("Wrong request executed", expectedResponse, RESPONSE);

        // Check result area
        waitForRender();
        TextView resultText = (TextView) activity.findViewById(R.id.result_text);
        assertTrue("Response not found in text area", resultText.getText().toString().indexOf(expectedResponse) > 0);
    }

    private void setText(final int textViewId, final String text) {
        try {
            runTestOnUiThread(new Runnable() {
                @Override public void run() {
                    TextView v = (TextView) getActivity().findViewById(textViewId);
                    v.setText(text);
                    if (v instanceof EditText)
                        ((EditText) v).setSelection(v.getText().length());
                }
            });
        } catch (Throwable t) {
            fail("Failed to set text " + text);
        }
    }

    private void waitForRender() {
        eq.waitForEvent(EventType.RenditionComplete, 5000);
    }

    private void clickTab(final TabHost tabHost, final int tabIndex) {
        try {
            runTestOnUiThread(new Runnable() {

                @Override
                public void run() {
                    tabHost.setCurrentTab(tabIndex);
                } 
            });
        } catch (Throwable t) {
            fail("Failed to click tab " + tabIndex);
        }
    }

    private void waitSome() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }

    private void clickView(final View v) {
        try {
            runTestOnUiThread(new Runnable() {

                @Override
                public void run() {
                    v.performClick();
                }
            });
        } catch (Throwable t) {
            fail("Failed to click view " + v);
        }
    }

    private void checkRadioButton(final int radioButtonId) {
        try {
            runTestOnUiThread(new Runnable() {

                @Override
                public void run() {
                    RadioButton v = (RadioButton) getActivity().findViewById(radioButtonId);
                    v.setChecked(true);
                }
            });
        } catch (Throwable t) {
            fail("Failed to check radio button " + radioButtonId);
        }
    }
}
