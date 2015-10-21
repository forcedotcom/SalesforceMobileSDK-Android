/*
 * Copyright (c) 2014, salesforce.com, inc.
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * This class contains commonly used constants, such as field names,
 * SObject types, attribute names, etc.
 *
 * @author bhariharan
 */
public class Constants {

	public static final String EMPTY_STRING = "";
	public static final String NULL_STRING = "null";

    public static final String ID = "Id";
    public static final String NAME = "Name";
    public static final String TYPE = "Type";
    public static final String ATTRIBUTES = "attributes";
    public static final String RECENTLY_VIEWED = "RecentlyViewed";
    public static final String RECORDS = "records";
	public static final String LID = "id"; // lower case id in create response
	public static final String SOBJECT_TYPE = "attributes.type";
	public static final String NEXT_RECORDS_URL = "nextRecordsUrl";
	public static final String TOTAL_SIZE = "totalSize";
	public static final String RECENT_ITEMS = "recentItems";
    public static final String LAST_MODIFIED_DATE = "LastModifiedDate";

    /**
     * Salesforce object types.
     */
    public static final String ACCOUNT = "Account";
    public static final String LEAD = "Lead";
    public static final String CASE = "Case";
    public static final String OPPORTUNITY = "Opportunity";
    public static final String TASK = "Task";
    public static final String CONTACT = "Contact";
    public static final String CAMPAIGN = "Campaign";
    public static final String CONTENT = "ContentDocument";
    public static final String CONTENT_VERSION = "ContentVersion";

    /**
     * Salesforce object type field constants.
     */
    public static final String KEYPREFIX_FIELD = "keyPrefix";
    public static final String NAME_FIELD = "name";
    public static final String LABEL_FIELD = "label";
    public static final String LABELPLURAL_FIELD = "labelPlural";
    public static final String FIELDS_FIELD = "fields";
    public static final String LAYOUTABLE_FIELD = "layoutable";
    public static final String SEARCHABLE_FIELD = "searchable";
    public static final String HIDDEN_FIELD = "deprecatedAndHidden";
    public static final String NAMEFIELD_FIELD = "nameField";

    /**
     * Salesforce object layout column field constants.
     */
    public static final String LAYOUT_NAME_FIELD = "name";
    public static final String LAYOUT_FIELD_FIELD = "field";
    public static final String LAYOUT_FORMAT_FIELD = "format";
    public static final String LAYOUT_LABEL_FIELD = "label";

    /**
     * Salesforce object type layout field constants.
     */
    public static final String LAYOUT_LIMITS_FIELD = "limitRows";
    public static final String LAYOUT_COLUMNS_FIELD = "searchColumns";

    /**
     * Salesforce timestamp format
     */
    public static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

}
