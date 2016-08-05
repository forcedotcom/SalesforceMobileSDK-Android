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

import android.util.Log;

import com.google.common.base.Charsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import okhttp3.Response;
import okhttp3.ResponseBody;


/**
 * RestResponse: Class to represent any REST response.
 * 
 */
public class RestResponse {
	
	private final Response response;

	// Populated when "consume" is called
	private boolean consumed;
	private byte[] responseAsBytes;
	private Charset responseCharSet;

	// Lazily computed
	private String responseAsString;
	private JSONObject responseAsJSONObject;
	private JSONArray responseAsJSONArray;
	private Map<String, String> headers;

	/**
	 * Constructor
	 *
	 * @param response okHttp Response object.
	 */
	public RestResponse(Response response) {
		this.response = response;
	}

	/**
	 * Returns all headers associated with this response.
	 *
	 * @return Map containing all headers.
	 */
	public Map<String, List<String>> getAllHeaders() {
		return response.headers().toMultimap();
	}

	/**
	 * @return HTTP status code of the response
	 */
	public int getStatusCode() {
		return response.code();
	}

	/**
	 * @return true for response with 2xx status codes
	 */
	public boolean isSuccess() {
		return response.isSuccessful();
	}

	/**
	 * @return true for response with 2xx status codes
	 */
	public static boolean isSuccess(int statusCode) {
		return statusCode / 100 == 2;
	}

	/**
	 * Fully consume response entity content and closes content stream
	 * Must be called before returning control to the UI thread
	 * @throws IOException 
	 */
	public void consume() throws IOException {
		if (!consumed && response != null) {
			ResponseBody body = response.body();
			if (body != null) {
				responseAsBytes = body.bytes();
				responseCharSet = body.contentType() == null || body.contentType().charset() == null ? Charsets.UTF_8 : body.contentType().charset();
				body.close();
			}
			else {
				responseAsBytes = new byte[0];
				responseCharSet = Charsets.UTF_8;
			}

			consumed = true;
		}
	}

	/**
	 * Fully consume a response and swallow any exceptions thrown during the process.
	 * @see RestResponse#consume()
	 */
	public void consumeQuietly() {
		try {
			consume();
		} catch (IOException e) {
			Log.e("RestResponse: consume()", "Content could not be written to byte array", e);
		}
	}

	/**
	 * @return byte[] for entire response
	 * @throws IOException
	 */
	public byte[] asBytes() throws IOException {
		if (responseAsBytes == null) {
			consume();
		}
		return responseAsBytes;
	}

	/**
	 * String is built the first time the method is called.
	 *
	 * @return string for entire response
	 * @throws IOException
	 */
	public String asString() throws IOException {
		if (responseAsString == null) {
			byte[] bytes = asBytes(); // will also compute responseCharSet
			responseAsString = new String(bytes, responseCharSet);
		}
		return responseAsString;
	}

	/**
	 * JSONObject is built the first time the method is called.
	 *
	 * @return JSONObject for response
	 * @throws JSONException
	 * @throws IOException
	 */
	public JSONObject asJSONObject() throws JSONException, IOException {
		if (responseAsJSONObject == null) {
			responseAsJSONObject = new JSONObject(asString());
		}
		return responseAsJSONObject;
	}

	/**
	 * JSONArray is built the first time the method is called.
	 *
	 * @return JSONObject for response
	 * @throws JSONException
	 * @throws IOException
	 */
	public JSONArray asJSONArray() throws JSONException, IOException {
		if (responseAsJSONArray == null) {
			responseAsJSONArray = new JSONArray(asString());
		}
		return responseAsJSONArray;
	}

	/**
	 * Streams the response content. This stream <strong>must</strong> be consumed either
	 * by reading from it, calling a method like {@link com.google.common.io.Closeables#closeQuietly(InputStream)}
	 * or calling {@link #consume()} to discard the contents.
	 *
	 * <p>>
	 * If the response is consumed as a stream, {@link #asBytes()} will return an empty array,
	 * {@link #asString()} will return an emtpy string and both {@link #asJSONArray()} and
	 * {@link #asJSONObject()} will throw exceptions.
	 * </p>
	 *
	 * @return an {@link InputStream} from the response content
	 * @throws IOException if the stream could not be created or has already been consumed
	 */
	public InputStream asInputStream() throws IOException {
		if (consumed) {
			throw new IOException("Content has been consumed");
		}
		else {
			responseAsBytes = new byte[0];
			responseCharSet = Charsets.UTF_8;
			InputStream stream = response.body().byteStream();
			consumed = true;
			return stream;
		}
	}

	@Override
	public String toString() {
		try {
			return asString();
		} catch (Exception e) {
			Log.e("RestResponse:toString()", "Exception caught while calling asString()", e);
			return ((response == null) ? "" : response.toString());
		}
	}
}
