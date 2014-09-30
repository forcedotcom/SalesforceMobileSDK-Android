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
package com.salesforce.androidsdk.util.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * This tracks activity events using a queue, allowing for tests to wait for certain events to turn up.
 */
public class EventsListenerQueue implements EventsObserver {
	
    static public abstract class BlockForEvent {
    	private EventType type;
    	
    	public BlockForEvent(EventType type) {
    		this.type = type;
    	}
    	public EventType getType() {
    		return type;
    	}
    	
    	public abstract void run(Event evt);
    }
    
    private BlockingQueue<Event> events;  
    private Set<BlockForEvent> blocks = new HashSet<BlockForEvent>();

	public EventsListenerQueue() {
    	events = new ArrayBlockingQueue<Event>(10);
        EventsObservable.get().registerObserver(this);
    }

	public void onEvent(Event evt) {
    	Log.i("EventsListenerQueue", "onEvent:" + evt.getType());		
		List<BlockForEvent> matchingBlocks = new ArrayList<BlockForEvent>();
		for (BlockForEvent block : blocks) {
			if (block.getType() == evt.getType()) {
				block.run(evt);
				matchingBlocks.add(block);
			}
		}
		blocks.removeAll(matchingBlocks);
		events.offer(evt);
	}
	
	/**
	 * Register a block of code to run the next time a certain event is fired
	 * @param block
	 */
	public void registerBlock(BlockForEvent block) {
		blocks.add(block);
	}
	
    public void tearDown() {
        EventsObservable.get().unregisterObserver(this);
    }

    // remove any events in the queue
    public void clearQueue() {
        events.clear();
    }

    /** will return the next event in the queue, waiting if needed for a reasonable amount of time */
    public Event getNextEvent() {
        try {
            Event e = events.poll(30, TimeUnit.SECONDS);
            if (e == null)
                throw new RuntimeException("Failure ** Timeout waiting for an event ");
            return e;
        } catch (InterruptedException ex) {
            throw new RuntimeException("Was interupted waiting for activity event");
        }
    }

    /** will wait for expected event in the queue, waiting till the timeout specified */
    public Event waitForEvent(EventType expectedType, int timeout) {
    	Log.i("EventsListenerQueue", "waitForEvent:" + expectedType);
        long end = System.currentTimeMillis() + timeout;
        long remaining = timeout;
        while (remaining > 0) {
            try {
                Event e = events.poll(remaining, TimeUnit.MILLISECONDS);
                if (e != null && e.getType() == expectedType) {
                    return e;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Was interupted waiting for activity event");
            }
            remaining = end - System.currentTimeMillis();
        }
        throw new RuntimeException("Failure ** Timeout waiting for an event ");
    }

    public boolean peekEvent() {
        return events.peek() == null;
    }
}
