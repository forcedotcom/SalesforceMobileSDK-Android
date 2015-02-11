/*
 * Copyright (c) 2015, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.util;

import android.util.Log;
import android.util.Xml;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;

import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * Target for sync defined by a SOQL query
 */
public class ContentSoqlSyncTarget extends SoqlSyncTarget {

    private static final String REQUEST_TEMPLATE = "<?xml version=\"1.0\"?>\n" +
            "<se:Envelope xmlns:se=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "<se:Header xmlns:sfns=\"urn:partner.soap.sforce.com\">\n" +
            "    <sfns:SessionHeader>\n" +
            "        <sessionId>%s</sessionId>\n" +
            "    </sfns:SessionHeader>\n" +
            "</se:Header>\n" +
            "<se:Body>\n" +
            "    <query xmlns=\"urn:partner.soap.sforce.com\" xmlns:ns1=\"sobject.partner.soap.sforce.com\">\n" +
            "        <queryString>%s</queryString>\n" +
            "    </query>\n" +
            "</se:Body>\n" +
            "</se:Envelope>";
    public static final String RESULT = "result";
    public static final String RECORDS = "records";


    /**
     * Build SyncTarget from json
     * @param target as json
     * @return
     * @throws JSONException
     */
    public static SyncTarget fromJSON(JSONObject target) throws JSONException {
        if (target == null)
            return null;

        String query = target.getString(QUERY);
        return new ContentSoqlSyncTarget(query);
    }

    /**
     * Build SyncTarget for soql target
     * @param soql
     * @return
     */
    public static ContentSoqlSyncTarget targetForSOQLSyncDown(String soql) {
        return new ContentSoqlSyncTarget(soql);
    }

    /**
     * Private constructor
     * @param query
     */
    public ContentSoqlSyncTarget(String query) {
        super(query);
        this.queryType = QueryType.custom;
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(ANDROID_IMPL, getClass().getName());
        return target;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        String queryToRun = maxTimeStamp > 0 ? SoqlSyncTarget.addFilterForReSync(getQuery(), maxTimeStamp) : getQuery();
        RestRequest request = buildContentSoqlRequest(syncManager.getRestClient().getAuthToken(), queryToRun);
        RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONArray records = parseContentSoqlResponse(response);

        return records;
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return null;
    }

    /**
     * @param query
     * @return rest request to run a soql query that returns content fields (it uses SOAP)
     */
    private RestRequest buildContentSoqlRequest(String sessionId, String query) throws UnsupportedEncodingException {
        Map<String, String> customHeaders = new HashMap<String, String>();
        customHeaders.put("SOAPAction", "\"\"");

        StringEntity entity = new StringEntity(String.format(REQUEST_TEMPLATE, sessionId, query), HTTP.UTF_8); // XXX session might be invalid
        entity.setContentType("text/xml");

        return new RestRequest(RestRequest.RestMethod.POST, "/services/Soap/u/32.0", entity, customHeaders);
    }

    /**
     * @param response returned by soap soql request - also sets totalSize field
     * @return
     */
    private JSONArray parseContentSoqlResponse(RestResponse response) {
        JSONArray records = null;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new ByteArrayInputStream(response.asBytes()), null);

            JSONObject record = null;
            boolean done = false;
            boolean inResults = false;
            boolean inRecord = false;

            while(!done) {
                int next = parser.next();

                if (next == XmlPullParser.START_TAG) {
                    Log.i("----> Starting TAG", parser.getName());
                }

                if (next == XmlPullParser.START_TAG && parser.getName().equals(RESULT)) {
                    inResults = true;
                    records = new JSONArray();
                }

                if (next == XmlPullParser.START_TAG && parser.getName().equals(RECORDS)) {
                    inRecord = true;
                    record = new JSONObject();
                }

                if (next == XmlPullParser.START_TAG && inRecord) {
                    record.put(parser.getName(), parser.nextText());
                }

                if (next == XmlPullParser.END_TAG && parser.getName().equals(RECORDS)) {
                    inRecord = false;
                    records.put(record);
                }

                if (next == XmlPullParser.END_TAG && parser.getName().equals(RESULT)) {
                    inResults = false;
                }

                if (next == XmlPullParser.END_DOCUMENT) {
                    done = true;
                }
            }

            totalSize = records.length();

            //  String responseStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns=\"urn:partner.soap.sforce.com\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:sf=\"urn:sobject.partner.soap.sforce.com\"><soapenv:Header><LimitInfoHeader><limitInfo><current>6</current><limit>5000</limit><type>API REQUESTS</type></limitInfo></LimitInfoHeader></soapenv:Header><soapenv:Body><queryResponse><result xsi:type=\"QueryResult\"><done>true</done><queryLocator xsi:nil=\"true\"/><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMRIA2</sf:Id><sf:Id>003R00000016BMRIA2</sf:Id><sf:FirstName>Geoff</sf:FirstName><sf:LastName>Minor</sf:LastName><sf:Title>President</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMSIA2</sf:Id><sf:Id>003R00000016BMSIA2</sf:Id><sf:FirstName>Carole</sf:FirstName><sf:LastName>White</sf:LastName><sf:Title>VP Sales</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMTIA2</sf:Id><sf:Id>003R00000016BMTIA2</sf:Id><sf:FirstName>Jon</sf:FirstName><sf:LastName>Amos</sf:LastName><sf:Title>Sales Manager</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMUIA2</sf:Id><sf:Id>003R00000016BMUIA2</sf:Id><sf:FirstName>Edward</sf:FirstName><sf:LastName>Stamos</sf:LastName><sf:Title>President and CEO</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMVIA2</sf:Id><sf:Id>003R00000016BMVIA2</sf:Id><sf:FirstName>Howard</sf:FirstName><sf:LastName>Jones</sf:LastName><sf:Title>Buyer</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMWIA2</sf:Id><sf:Id>003R00000016BMWIA2</sf:Id><sf:FirstName>Leanne</sf:FirstName><sf:LastName>Tomlin</sf:LastName><sf:Title>VP Customer Support</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><records xsi:type=\"sf:sObject\"><sf:type>Contact</sf:type><sf:Id>003R00000016BMXIA2</sf:Id><sf:Id>003R00000016BMXIA2</sf:Id><sf:FirstName>Marc</sf:FirstName><sf:LastName>Benioff</sf:LastName><sf:Title>Executive Officer</sf:Title><sf:MobilePhone xsi:nil=\"true\"/><sf:Email>info@salesforce.com</sf:Email><sf:Department xsi:nil=\"true\"/><sf:HomePhone xsi:nil=\"true\"/><sf:LastModifiedDate>2015-02-10T01:56:14.000Z</sf:LastModifiedDate></records><size>7</size></result></queryResponse></soapenv:Body></soapenv:Envelope>";

        } catch (Exception e) {
            Log.e("ContentSoqlSyncTarget:parseContentSoqlResponse", "Parsing failed", e);
        }

        return records;
    }
}

