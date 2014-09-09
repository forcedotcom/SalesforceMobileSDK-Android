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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import android.net.Uri;
import android.util.Log;

/**
 * This parses a Uri fragment that uses a queryString style foo=bar&bar=foo
 * parameter passing (e.g. OAuth2)
 */
public class UriFragmentParser {

	/**
	 * look for # error fragments and standard url param errors, like the
	 * user clicked deny on the auth page
	 * 
	 * @param uri
	 * @return
	 */
	public static Map<String, String> parse(Uri uri) {
		Map<String, String> retval = parse(uri.getEncodedFragment());
		if (retval.size() == 0) {
			retval = parse(uri.getEncodedQuery());
		}
		return retval;
	}

	public static Map<String, String> parse(String fragmentString) {
		Map<String, String> res = new HashMap<String, String>();
		if (fragmentString == null)
			return res;
		fragmentString = fragmentString.trim();
		if (fragmentString.length() == 0)
			return res;
		String[] params = fragmentString.split("&");
		for (String param : params) {
			String[] parts = param.split("=");
			try {
				res.put(URLDecoder.decode(parts[0], "UTF-8"),
						parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "");
			} catch (UnsupportedEncodingException e) {
				Log.e("UriFragmentParser:parse", "Unsupported encoding", e);
			}
		}
		return res;
	}

	private UriFragmentParser() {
		assert false : "don't construct me!";
	}
}