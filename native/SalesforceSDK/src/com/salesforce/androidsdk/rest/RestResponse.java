/*
 * Copyright (c) 2012, salesforce.com, inc.
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

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * RestResponse: Class to represent any REST response.
 * 
 */
public class RestResponse {
	
	private final int statusCode;
	private HttpResponse response;
	
	// Lazily computed
	private String responseAsString;
	private JSONObject responseAsJSONObject;
	private JSONArray responseAsJSONArray;

	/**
	 * Constructor
	 * @param response
	 */
	public RestResponse(HttpResponse response) {
		this.response = response;
		this.statusCode = response.getStatusLine().getStatusCode();
	}
	
	/**
	 * @return HTTP status code of response
	 */
	public int getStatusCode() {
		return statusCode; 
	}
	
	/**
	 * @return true for response with 2xx status codes
	 */
	public boolean isSuccess() {
		// 2xx success
		return (statusCode >= HttpStatus.SC_OK && statusCode < HttpStatus.SC_MULTIPLE_CHOICES);	
	}
	
	/**
	 * String is built the first time the method is called.
	 * Don't call this method for large response or binary responses.
	 * 
	 * @return string for entire response
	 * @throws ParseException
	 * @throws IOException
	 */
	public String asString() throws ParseException, IOException {
		if (responseAsString == null) {
			responseAsString = (response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), HTTP.UTF_8));
		}
		return responseAsString;
	}
	
	/**
	 * JSONObject is built the first time the method is called.
	 * 
	 * @return JSONObject for response
	 * @throws ParseException
	 * @throws JSONException
	 * @throws IOException
	 */
	public JSONObject asJSONObject() throws ParseException, JSONException, IOException {
		if (responseAsJSONObject == null) {
			responseAsJSONObject = new JSONObject(asString());
		}
		return responseAsJSONObject;
	}

	/**
	 * JSONArray is built the first time the method is called.
	 * 
	 * @return JSONObject for response
	 * @throws ParseException
	 * @throws JSONException
	 * @throws IOException
	 */
	public JSONArray asJSONArray() throws ParseException, JSONException, IOException {
		if (responseAsJSONArray == null) {
			responseAsJSONArray = new JSONArray(asString());
		}
		return responseAsJSONArray;
	}
	
	
	@Override
	public String toString() {
		try {
			return asString();
		} catch (Exception e) {
			return response.toString();
		}
	}
}
