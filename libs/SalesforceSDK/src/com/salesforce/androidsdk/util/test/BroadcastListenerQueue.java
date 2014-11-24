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
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.util.test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This tracks broadcasts using a queue, allowing for tests to wait for certain broadcast to turn up.
 */
public class BroadcastListenerQueue extends BroadcastReceiver {

	private BlockingQueue<Intent> broadcasts; 
	
	public BroadcastListenerQueue() {
		broadcasts = new ArrayBlockingQueue<Intent>(10);
	}
	
	@Override
	public void onReceive(Context context, final Intent intent) {
		broadcasts.offer(intent);
	}
	
    // remove any events in the queue
    public void clearQueue() {
    	broadcasts.clear();
    }

    /** will return the next event in the queue, waiting if needed for a reasonable amount of time */
    public Intent getNextBroadcast() {
        try {
        	Intent broadcast = broadcasts.poll(30, TimeUnit.SECONDS);
            if (broadcast == null)
                throw new RuntimeException("Failure ** Timeout waiting for a broadcast ");
            return broadcast;
        } catch (InterruptedException ex) {
            throw new RuntimeException("Was interrupted waiting for broadcast");
        }
    }
}
