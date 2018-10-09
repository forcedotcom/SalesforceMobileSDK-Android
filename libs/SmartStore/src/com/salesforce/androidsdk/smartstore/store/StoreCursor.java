/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Store Cursor 
 * We don't actually keep a cursor opened, instead, we wrap the query spec and page index
 */
public class StoreCursor {

	// Keys for json
	public static final String TOTAL_ENTRIES = "totalEntries";
	public static final String TOTAL_PAGES = "totalPages";
	public static final String PAGE_SIZE = "pageSize";
	public static final String CURRENT_PAGE_INDEX = "currentPageIndex";
	public static final String CURRENT_PAGE_ORDERED_ENTRIES = "currentPageOrderedEntries";
	public static final String CURSOR_ID = "cursorId";

	
	private static int LAST_ID = 0;
	
	// Id / soup / query / totalPages immutable
	public  final int cursorId;
	private final QuerySpec querySpec;
	private final int totalPages;
	private final int totalEntries;
	
	// Current page can change - by calling moveToPageIndex
	private int currentPageIndex;
	
	/**
	 * @param smartStore
	 * @param querySpec
	 * @throws JSONException 
	 */
	public StoreCursor(SmartStore smartStore, QuerySpec querySpec) {
		int countRows = smartStore.countQuery(querySpec);
		
		this.cursorId = LAST_ID++;
		this.querySpec = querySpec;
		this.totalEntries = countRows;
		this.totalPages = (int) Math.ceil( (double) countRows / querySpec.pageSize);
		this.currentPageIndex = 0;
	}
	
	/**
	 * @param newPageIndex
	 */
	public void moveToPageIndex(int newPageIndex) {
		// Always between 0 and totalPages-1
		this.currentPageIndex = (newPageIndex < 0 ? 0 : newPageIndex >= totalPages ? totalPages - 1 : newPageIndex);
	}

	/**
	 * Returns cursor meta data (page index, size etc) and data (entries in page) as a FakeJSONObject
	 * NB: json data is never deserialized
	 * @param smartStore
	 */
	public FakeJSONObject getData(SmartStore smartStore)  {
		StringBuilder resultBuilder = new StringBuilder();
		resultBuilder.append("{")
			.append("\"").append(CURSOR_ID).append("\":").append(cursorId).append(", ")
			.append("\"").append(CURRENT_PAGE_INDEX).append("\":").append(currentPageIndex).append(", ")
			.append("\"").append(PAGE_SIZE).append("\":").append(querySpec.pageSize).append(", ")
			.append("\"").append(TOTAL_ENTRIES).append("\":").append(totalEntries).append(", ")
			.append("\"").append(TOTAL_PAGES).append("\":").append(totalPages).append(", ")
			.append("\"").append(CURRENT_PAGE_ORDERED_ENTRIES).append("\":");
		smartStore.queryAsString(resultBuilder, querySpec, currentPageIndex);
		resultBuilder.append("}");
		return new FakeJSONObject(resultBuilder.toString());
	}
}

/**
 * A subclass of JSONObject that doesn't actually parse the stringified json passed to its constructor
 * Use this class to avoid deserialization if you are calling a method that only wants to serialize the JSONObject
 */
class FakeJSONObject extends JSONObject {
	private String json;

	public FakeJSONObject(String json) {
		this.json = json;
	}

	public String toString() {
		return json;
	}
}