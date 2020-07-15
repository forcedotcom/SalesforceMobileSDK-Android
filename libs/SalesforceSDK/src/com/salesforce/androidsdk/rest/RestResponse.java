/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * RestResponse: Class to represent any REST response.
 *
 */
public class RestResponse {

    private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
	private static final String TAG = "RestResponse";

	private final Response response;

	// Populated when "consume" is called
	private boolean consumed;
	private byte[] responseAsBytes;
	private Charset responseCharSet;

	// Lazily computed
	private String responseAsString;
	private JSONObject responseAsJSONObject;
	private JSONArray responseAsJSONArray;

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
			try {
				ResponseBody body = response.body();
				if (body != null) {
					MediaType mType = body.contentType();
					responseAsBytes = body.bytes();
					responseCharSet = mType == null || mType.charset() == null ? StandardCharsets.UTF_8 : mType.charset();
					if (responseAsBytes != null && responseAsBytes.length > 0) {
						responseAsString = new String(responseAsBytes, responseCharSet);
					}
				} else {
					responseAsBytes = new byte[0];
					responseCharSet = StandardCharsets.UTF_8;
				}
			} finally {
				consumed = true;
				response.close();
			}
		}
	}

	/**
	 * Fully consume a response and swallow any exceptions thrown during the process.
	 * @see RestResponse#consume()
	 */
	public void consumeQuietly() {
		try {
			consume();
		} catch (Exception e) {
			SalesforceSDKLogger.e(TAG, "Content could not be written to a byte array", e);
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
	 * Return content type
	 * @return value of content-type header or null if header not found
	 */
	public String getContentType() {
		return response.header(CONTENT_TYPE_HEADER_KEY);
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
	 * by reading from it and calling {@link InputStream#close()},
	 * or calling {@link #consume()} to discard the contents.
	 *
	 * <p>>
	 * If the response is consumed as a stream, {@link #asBytes()} will return an empty array,
	 * {@link #asString()} will return an empty string and both {@link #asJSONArray()} and
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
			responseCharSet = StandardCharsets.UTF_8;
			InputStream stream = response.body().byteStream();
			consumed = true;
			return stream;
		}
	}

	/**
	 * Method to get the "raw" response (i.e. the underlying OkHttp3.Response object).
	 * null is return if the response was already consumed.
	 * It is the responsibility of the application to close the response after consuming it.
	 * @return raw response as {@link Response}
	 */
	public Response getRawResponse() {
		if (!consumed) {
			return response;
		} else {
			return null;
		}
	}

	@Override
	public String toString() {
		try {
			return asString();
		} catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while converting to string", e);
			return ((response == null) ? "" : response.toString());
		}
	}
}
