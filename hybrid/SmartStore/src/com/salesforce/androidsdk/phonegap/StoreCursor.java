package com.salesforce.androidsdk.phonegap;

import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.store.QuerySpec;
import com.salesforce.androidsdk.store.SmartStore;

/**
 * Store Cursor
 * We don't actually keep a cursor opened, instead, we wrap the query spec and page index
 */
public class StoreCursor {
	
	private static int LAST_ID = 0;
	
	// Id / soup / query / totalPages immutable
	public  final int cursorId;
	private final String soupName;
	private final QuerySpec querySpec;
	private final int totalPages;
	
	// Current page can change - by calling moveToPageIndex
	private int currentPageIndex;
	
	/**
	 * @param soupName
	 * @param querySpec
	 * @param totalPages
	 * @param currentPageIndex
	 */
	public StoreCursor(String soupName, QuerySpec querySpec, int totalPages, int currentPageIndex) {
		this.cursorId = LAST_ID++;
		this.soupName = soupName;
		this.querySpec = querySpec;
		this.totalPages = totalPages;
		this.currentPageIndex = currentPageIndex;
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
	public JSONObject toJSON(SmartStore smartStore) throws JSONException {
		JSONObject json = new JSONObject();
		json.put(SmartStorePlugin.CURSOR_ID, cursorId);
		json.put(SmartStorePlugin.CURRENT_PAGE_INDEX, currentPageIndex);
		json.put(SmartStorePlugin.PAGE_SIZE, querySpec.pageSize);
		json.put(SmartStorePlugin.TOTAL_PAGES, totalPages);
		json.put(SmartStorePlugin.CURRENT_PAGE_ORDERED_ENTRIES, smartStore.querySoup(soupName, querySpec, currentPageIndex));
		return json;
	}
}