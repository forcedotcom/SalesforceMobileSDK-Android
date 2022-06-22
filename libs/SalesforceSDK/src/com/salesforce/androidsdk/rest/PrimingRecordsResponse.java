/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest;

import com.salesforce.androidsdk.util.JSONObjectHelper;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * PrimingRecordsResponse: Class to represent response for a priming records request.
 */
public class PrimingRecordsResponse {
    public static final DateFormat TIMESTAMP_FORMAT;
    static {
        // NB can't use RestRequest.ISO8601_DATE_FORMAT it's for timestamp of the form 2001-07-04T12:08:56.235-0700
        TimeZone tz = TimeZone.getTimeZone("UTC");
        TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        TIMESTAMP_FORMAT.setTimeZone(tz);
    }

    public static final String PRIMING_RECORDS = "primingRecords";
    public static final String RELAY_TOKEN = "relayToken";
    public static final String RULE_ERRORS = "ruleErrors";
    public static final String STATS = "stats";

    public final Map<String, Map<String, List<PrimingRecord>>> primingRecords = new HashMap<>();
    public final String relayToken;
    public final List<PrimingRuleError> ruleErrors = new ArrayList<>();
    public final PrimingStats stats;

    public PrimingRecordsResponse(JSONObject responseJson) throws JSONException, ParseException {
        // Parsing priming records
        JSONObject apiNameToTypeToPrimingRecordsJson = responseJson.getJSONObject(PRIMING_RECORDS);
        for (Iterator<String> iterator = apiNameToTypeToPrimingRecordsJson.keys(); iterator.hasNext(); ) {
            String objectApiName = iterator.next();
            JSONObject typeToPrimingRecordsJson = apiNameToTypeToPrimingRecordsJson.getJSONObject(objectApiName);
            Map<String, List<PrimingRecord>> typeToPrimingRecords = new HashMap<>();
            for (Iterator<String> innerIterator = typeToPrimingRecordsJson.keys(); innerIterator.hasNext(); ) {
                String recordType = innerIterator.next();
                JSONArray primingRecordsJson = typeToPrimingRecordsJson.getJSONArray(recordType);
                List<PrimingRecord> primingRecords = new ArrayList<>();
                for (int i = 0; i < primingRecordsJson.length(); i++) {
                    primingRecords.add(new PrimingRecord(primingRecordsJson.getJSONObject(i)));
                }
                typeToPrimingRecords.put(recordType, primingRecords);
            }
            primingRecords.put(objectApiName, typeToPrimingRecords);
        }

        // Getting relay token
        relayToken = JSONObjectHelper.optString(responseJson, RELAY_TOKEN);

        // Parsing rule errors
        JSONArray ruleErrorsJson = responseJson.getJSONArray(RULE_ERRORS);
        for (int i = 0; i < ruleErrorsJson.length(); i++) {
            ruleErrors.add(new PrimingRuleError(ruleErrorsJson.getJSONObject(i)));
        }

        // Parsing stats
        stats = new PrimingStats(responseJson.getJSONObject(STATS));
    }

    public static class PrimingRecord {
        public static final String ID = "id";
        public static final String SYSTEM_MODSTAMP = "systemModstamp";

        public final String id;
        public final Date systemModstamp;

        public PrimingRecord(JSONObject json) throws JSONException, ParseException {
            id = json.getString(ID);
            systemModstamp = TIMESTAMP_FORMAT.parse(json.getString(SYSTEM_MODSTAMP));
        }
    }

    public static class PrimingRuleError {
        public static final String RULE_ID = "ruleId";

        public String ruleId;

        public PrimingRuleError(JSONObject json) throws JSONException {
            ruleId = json.getString(RULE_ID);
        }
    }

    public static class PrimingStats {
        public static final String RULE_COUNT_TOTAL = "ruleCountTotal";
        public static final String RECORD_COUNT_TOTAL = "recordCountTotal";
        public static final String RULE_COUNT_SERVED = "ruleCountServed";
        public static final String RECORD_COUNT_SERVED = "recordCountServed";

        public int ruleCountTotal;
        public int recordCountTotal;
        public int ruleCountServed;
        public int recordCountServed;

        public PrimingStats(JSONObject json) throws JSONException {
            ruleCountTotal = json.getInt(RULE_COUNT_TOTAL);
            recordCountTotal = json.getInt(RECORD_COUNT_TOTAL);
            ruleCountServed = json.getInt(RULE_COUNT_SERVED);
            recordCountServed = json.getInt(RECORD_COUNT_SERVED);
        }
    }
}