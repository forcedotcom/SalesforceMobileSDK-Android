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
package com.salesforce.androidsdk.util;

import java.util.Map.Entry;
import java.util.Set;

import android.util.Pair;


/**
 * Helper methods for logging 
 */
public class LogUtil {
	/**
	 * @param entries
	 * @param delim
	 * @return delim-delimited keys and comma-delimited values; string values are put between '
	 */
	public static Pair<String, String> getAsStrings(Set<Entry<String, Object>> entries, String delim) {
		if (entries == null) 
			return new Pair<String, String>("", ""); 
		
		StringBuilder keysBuilder = new StringBuilder();
		StringBuilder valuesBuilder = new StringBuilder();
		boolean first = true;
		for (Entry<String, Object> entry : entries) {
			if (first) {
				first = false;
			}
			else {
				keysBuilder.append(delim);
				valuesBuilder.append(delim);
			}
			keysBuilder.append(entry.getKey());
			Object value = entry.getValue();
			if (value instanceof String) {
				valuesBuilder.append("'").append(value).append("'");
			}
			else {
				valuesBuilder.append(value);
			}
		}
		return new Pair<String, String>(keysBuilder.toString(), valuesBuilder.toString());
	}
	
	
	/**
	 * Return arr1[0] + operator  + arr2[0] + delim + arr1[1] + operator  + arr2[1] + ... 
	 * String elements of arr2 are put between '
	 * @param arr1
	 * @param arr2
	 * @param operator
	 * @param delim
	 * @return
	 */
	public static String zipJoin(Set<Entry<String, Object>> entries, String operator, String delim) {
		if (entries == null) 
			return "";
		
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Entry<String, Object> entry : entries) {
			if (first) {
				first =  false;
			}
			else {
				sb.append(delim);
				first = false;
			}
			sb.append(entry.getKey()).append(operator);
			Object value = entry.getValue();
			if (value instanceof String) {
				sb.append("'").append(value).append("'");
			}
			else {
				sb.append(value);
			}
		}
		return sb.toString();
	}

}