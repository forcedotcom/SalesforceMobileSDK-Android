/*
 * Copyright 2010 Salesforce.com.
 * All Rights Reserved.
 * Company Confidential.
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
