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
package com.salesforce.androidsdk.auth;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.util.Log;

/**
 * Generic HTTP Access layer - used internally by RestClient and OAuth2.
 * 
 * Basically a wrapper around a AndroidHttpClient.
 * It watches network changes (e.g. wifi to 3g) and reset the HttpClient when needed.
 */
public class HttpAccess extends BroadcastReceiver {

	// Reference to app
	private Application app;
	
	// Http client
	private AndroidHttpClient http;
	
	// Fields to keep track of network 
    private boolean    hasNetwork      = true;
    private int        currentNetworkSubType = -1;
    private String     networkFailReason;

    // Connection manager
	private final ConnectivityManager conMgr;
	
    // Singleton instance
	public static HttpAccess DEFAULT;

	
	/*
	 * Initialize HttpAccess
	 * Should be called from application
	 */
	public static void init(Application app) {
		assert DEFAULT == null : "HttpAccess.init should be called once per process";
		DEFAULT = new HttpAccess(app);
	}

	/**
	 * Constructor
	 * @param ctx
	 */
	public HttpAccess(Application app) {
		// Using android http client
        http = getHttpClient();
        ((AndroidHttpClient) http).enableCurlLogging("HttpAccess", Log.DEBUG);

        // Only null in tests
        if (app == null) {
        	conMgr = null;
        }
        else {
			// Keep reference to app
			this.app = app;
	        
	        // Registering receiver to handle network changes
	        app.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	        
	        // Getting connectivity manager and current network type 
	        conMgr = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
	        currentNetworkSubType = conMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getSubtype();
        }
	}
	
	/**
	 * Build http client
	 * @param app
	 * @return
	 */
	private AndroidHttpClient getHttpClient() {
		return AndroidHttpClient.newInstance(getUserAgent(), app);
	}
	

	/**
	 * @return user agent
	 */
	private String getUserAgent() {
		return "SalesforceMobileSDK-android-nREST" + Build.VERSION.RELEASE;
	}

	/**
	 * Detects network changes and resetting network when needed 
	 * 
	 * Note: the intent info is only for this particular change, meaning it will get sent when the phone detects changes in the wireless
	 * 	state even though the user is actually using wifi.
     * 	so, don't use it, look for the current real state using the manager service.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        
        if (conMgr == null) return;
        
        // Try WIFI data connection
        NetworkInfo wifiInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo.isConnected()) {
            setHasNetwork(true, null);
            return;
        }

        // Try mobile connection
        NetworkInfo mobileInfo = conMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        
        // Reset network if type changed 
        if (currentNetworkSubType != mobileInfo.getSubtype()) {
            currentNetworkSubType = mobileInfo.getSubtype();
            resetNetwork();
        }
        
        // On 2.2 this receiver fires when we register for it, so we get the current state, not a transition, so 
        // typically in this case isFailover() is going to be true from the last failover, so we don't reset the  
        // network in that case. We also have the default HttpAccess instance created early on
        // so that this initial receive can be processed before we try and make any calls
        if (mobileInfo.isFailover()) {
            if (!mobileInfo.isConnected()) {
            	setHasNetwork(false, "No active data connection");
            	return;
            }
        }
        
        if (mobileInfo.isConnected()) {
            setHasNetwork(true, null);
            return;
        }
        
        setHasNetwork(false, mobileInfo.getReason() == null ? wifiInfo.getReason() : mobileInfo.getReason());
	}
	
	/**
	 * Shutdown existing connections.
	 * Create a new http client.
	 */
	public synchronized void resetNetwork() {
    	(new Thread(new Runnable() {
			@Override
			public void run() {
				http.close();
		        http = getHttpClient();
			}
    	})).start();
    }

	/**
	 * @return true if network is available
	 */
	public synchronized boolean hasNetwork() {
		return hasNetwork;
	}
	
	/**
	 * @param b
	 * @param reason
	 */
	private synchronized void setHasNetwork(boolean b, String reason){
        hasNetwork = b;
        networkFailReason = reason;
	}
	
    /**
     * @throws IOException
     */
    private void checkNetwork() throws IOException {
        if (!hasNetwork()) {
            throw new IOException(networkFailReason == null ? "Network not available" : networkFailReason);
        }
    }
	
    
    /**************************************************************************************************
     *
     * Http calls methods
     * 
     **************************************************************************************************/
	
	/**
	 * Wrapper around an http call and its response
	 */
	public static class Execution {

		public Execution(HttpRequestBase request, HttpResponse response) throws IllegalStateException, IOException {
			this.request = request;
			this.response = response;
		}

		public final HttpRequestBase request;
		public final HttpResponse response;
	}

	/**
	 * Executes an http post
	 * @param headers
	 * @param uri
	 * @param requestEntity
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Execution doPost(Map<String,String> headers, URI uri, HttpEntity requestEntity) throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(uri);
		post.setEntity(requestEntity);
		return execute(headers, post);
	}

	/**
	 * Executes an http patch
	 * @param headers
	 * @param uri
	 * @param requestEntity
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Execution doPatch(Map<String,String> headers, URI uri, HttpEntity requestEntity) throws ClientProtocolException, IOException {
		HttpPatch patch = new HttpPatch(uri);
		patch.setEntity(requestEntity);
		return execute(headers, patch);
	}

	/**
	 * Executes an http put
	 * @param headers
	 * @param uri
	 * @param requestEntity
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public Execution doPut(Map<String,String> headers, URI uri, HttpEntity requestEntity) throws ClientProtocolException, IOException {
		HttpPut put = new HttpPut(uri);
		put.setEntity(requestEntity);
		return execute(headers, put);
	}
	
	/**
	 * Executes an http get
	 * @param headers
	 * @param uri
	 * @return
	 * @throws IOException
	 */
	public Execution doGet(Map<String,String> headers, URI uri) throws IOException {
		HttpGet get = new HttpGet(uri);
		return execute(headers, get);
	}

	/**
	 * Executes an http head
	 * @param uri
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	/**
	 * @param headers
	 * @param uri
	 * @return
	 * @throws IOException
	 */
	public Execution doHead(Map<String,String> headers, URI uri) throws IOException {
		HttpHead head = new HttpHead(uri);
		return execute(headers, head);
	}
	
	/**
	 * Executes an http delete
	 * @param headers
	 * @param uri
	 * @return
	 * @throws IOException
	 */
	public Execution doDelete(Map<String, String> headers, URI uri) throws IOException {
		HttpDelete del = new HttpDelete(uri);
		return execute(headers, del);
	}
	
    /** 
	 * Updates request with the headers, and then execute it and return the results
     * @param headers
     * @param req
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	protected Execution execute(Map<String,String> headers, HttpRequestBase req) throws ClientProtocolException, IOException {
		checkNetwork();
	    try {
    		addHeaders(req, headers);
    		return execute(req);
        } catch (IOException t) {
            if (req.getMethod().equalsIgnoreCase("GET")) {
            	return execute(req);
            }
            throw t;
        }
	}
	
	/** 
	 * Executes a fully formed request, and returns the results
	 * @param req
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	protected Execution execute(HttpRequestBase req) throws ClientProtocolException, IOException {
        HttpResponse res = http.execute(req);
        return new Execution(req, res);
    }
	
	/**
	 * @param req
	 * @param headers
	 */
	private void addHeaders(HttpRequestBase req, Map<String, String> headers) {
		if (headers == null) return;
		for (Map.Entry<String, String> h : headers.entrySet()) {
			req.addHeader(h.getKey(), h.getValue());
		}
	}
	/** 
	 * This wraps an HttpEntity, and when the steam is closed, will call consumeContent to ensure
	 * that the entire response was read, and the connection can go back to the connection pool
	 */
	static class EntityClosingStream extends FilterInputStream {
		
		EntityClosingStream(HttpEntity e) throws IllegalStateException, IOException {
			super(e.getContent());
			this.entity = e;
		}
		
		private final HttpEntity entity;
		
		@Override 
		public void close() throws IOException {
			try {
				super.close();
			} finally {
				entity.consumeContent();
			}
		}
	}
	
	/**
	 * Sub class of HttpRequestBase to do a http PATH
	 *
	 */
	static class HttpPatch extends HttpEntityEnclosingRequestBase {
	
	    public final static String METHOD_NAME = "PATCH";
	    
	    public HttpPatch() {
	        super();
	    }
	    
	    public HttpPatch(final URI uri) {
	        super();
	        setURI(uri);
	    }
	
	    /**
	     * @throws IllegalArgumentException if the uri is invalid. 
	     */
	    public HttpPatch(final String uri) {
	        super();
	        setURI(URI.create(uri));
	    }
	
	    @Override
	    public String getMethod() {
	        return METHOD_NAME;
	    }
	    
	}
}
