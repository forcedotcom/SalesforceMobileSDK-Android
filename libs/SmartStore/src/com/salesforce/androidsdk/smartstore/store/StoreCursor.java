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
	 * @param smartStore
	 * @return json containing cursor meta data (page index, size etc) and data (entries in page)
	 * Note: query is run to build json
	 * @throws JSONException 
	 */
	public JSONObject getData(SmartStore smartStore) throws JSONException {
		JSONObject json = new JSONObject();
		json.put(CURSOR_ID, cursorId);
		json.put(CURRENT_PAGE_INDEX, currentPageIndex);
		json.put(PAGE_SIZE, querySpec.pageSize);
		json.put(TOTAL_ENTRIES, totalEntries);
		json.put(TOTAL_PAGES, totalPages);
		json.put(CURRENT_PAGE_ORDERED_ENTRIES, smartStore.query(querySpec, currentPageIndex));
		return json;
	}
}