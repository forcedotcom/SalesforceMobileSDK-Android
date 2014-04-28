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
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN]
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.smartstore.store;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class LongOperation {
	
    /**
     * Enum for long operations types
     */
    public enum LongOperationType {
    	alterSoup(AlterSoupLongOperation.class);
    	
    	private Class<? extends LongOperation> operationClass;

		private LongOperationType(Class<? extends LongOperation> operationClass) {
    		this.operationClass = operationClass;
    	}
		
		public LongOperation getOperation(SmartStore store) throws IllegalAccessException, InstantiationException {
			LongOperation newInstance;
			newInstance = operationClass.newInstance();
			newInstance.setSmartStore(store);
			return newInstance;
		}
    }

	protected SmartStore store;
	protected SQLiteDatabase db;

	/**
	 * @param db
	 */
	protected void setSmartStore(SmartStore store) {
		this.store = store;
		this.db = store.getDatabase();
	}

	/**
	 * Resume long operation
	 * @param rowId in long_operations
	 * @param details
	 * @param fromStepStr
	 * @param toStepStr (used by tests - null means all remaining)
	 * @throws JSONException
	 */
	protected abstract void resume(long rowId, JSONObject details, String fromStepStr, String toStepStr) throws JSONException; 
	
}