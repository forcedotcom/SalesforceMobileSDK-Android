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
package com.salesforce.androidsdk.store;

import java.util.Hashtable;
import java.util.Map;

import com.salesforce.androidsdk.store.SmartStore.IndexSpec;



/**
 * Smart store Accelerator 
 * 
 * Singleton class that caches a number of things (e.g. soup name to table name / index specs), insert helpers to speed things up 
 */
public enum SmartStoreAccelerator  {
	
	INSTANCE;
	
	// Map to speed up soup name lookup
	private Map<String, String> soupNameToTableNamesMap = new Hashtable<String, String>();
	
	// Map to speed up index specs lookup
	private Map<String, IndexSpec[]> soupNameToIndexSpecsMap = new Hashtable<String, SmartStore.IndexSpec[]>(); 
	
	/**
	 * @param soupName
	 * @return
	 */
	public String getCachedTableName(String soupName) {
		return soupNameToTableNamesMap.get(soupName);
	}

	/**
	 * @param soupName
	 * @param tableName
	 */
	public void cacheTableName(String soupName, String tableName) {
		soupNameToTableNamesMap.put(soupName, tableName);
	}
	
	/**
	 * @param soupName
	 */
	public void dropSoup(String soupName) {
		soupNameToTableNamesMap.remove(soupName);
		soupNameToIndexSpecsMap.remove(soupName);
	}

	/**
	 * @param soupName
	 * @return
	 */
	public IndexSpec[] getCachedIndexSpecs(String soupName) {
		return soupNameToIndexSpecsMap.get(soupName);
	}

	/**
	 * @param soupName
	 * @param tableName
	 */
	public void cacheIndexSpecs(String soupName, IndexSpec[] indexSpecs) {
		soupNameToIndexSpecsMap.put(soupName, indexSpecs.clone());
	}

	/**
	 * Reset cache
	 */
	public void reset() {
		soupNameToTableNamesMap.clear();
		soupNameToIndexSpecsMap.clear();
	}
	
}