/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.salesforce.androidsdk.util.EventsObservable.Event;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsObserver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for EventsObservable, focused on re-entrant modification of the
 * observer list while an event is being dispatched.
 *
 * <p>The observer list is iterated on the dispatching thread. When an
 * observer's onEvent callback registers or unregisters an observer, it
 * structurally modifies that list mid-iteration. With more than one observer
 * registered this bumps the backing list's modification count and the next
 * iteration step throws ConcurrentModificationException — the crash these
 * tests guard against.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class EventsObservableTest {

    private final List<EventsObserver> registered = new ArrayList<>();

    /**
     * EventsObservable is a process-wide singleton, so any observer registered
     * by a test must be removed afterward, or it leaks into later tests.
     */
    @After
    public void tearDown() {
        for (EventsObserver o : registered) {
            try {
                EventsObservable.get().unregisterObserver(o);
            } catch (IllegalStateException ignored) {
                /*
                 * Already unregistered by the observer itself during the
                 * test.
                 */
            }
        }
        registered.clear();
    }

    private void register(EventsObserver observer) {
        EventsObservable.get().registerObserver(observer);
        registered.add(observer);
    }

    /**
     * The first of two observers unregisters itself from inside its onEvent
     * callback. This mutates the observer list on the dispatching thread before
     * the iteration reaches the second observer, so dispatch must tolerate it
     * without throwing ConcurrentModificationException. This mirrors the field
     * crash, where an app registers several observers and a lifecycle callback
     * unregisters one during dispatch.
     */
    @Test
    public void testObserverUnregisteringItselfDuringDispatchDoesNotThrow() {
        final EventsObserver selfRemoving = new EventsObserver() {
            @Override
            public void onEvent(Event evt) {
                EventsObservable.get().unregisterObserver(this);
            }
        };
        final EventsObserver secondObserver = evt -> {
            /*
             * Intentionally empty; present so the list has more than one
             * entry.
             */
        };
        register(selfRemoving);
        register(secondObserver);

        EventsObservable.get().notifyEvent(EventType.Other);
    }

    /**
     * One observer unregisters a different, still-pending observer from inside
     * its onEvent callback. Dispatch must not throw
     * ConcurrentModificationException. Because the snapshot is taken before
     * dispatch begins, the unregistered observer was already captured, so it
     * still receives this in-flight event.
     */
    @Test
    public void testObserverUnregisteringAnotherDuringDispatchDoesNotThrow() {
        final boolean[] removedReceived = new boolean[1];
        final EventsObserver[] target = new EventsObserver[1];
        final EventsObserver remover = evt -> EventsObservable.get().unregisterObserver(target[0]);
        final EventsObserver removed = evt -> removedReceived[0] = true;
        target[0] = removed;
        register(remover);
        register(removed);

        EventsObservable.get().notifyEvent(EventType.Other);

        Assert.assertTrue(
                "Observer unregistered mid-dispatch was in the snapshot and "
                        + "should still receive the in-flight event",
                removedReceived[0]);
    }

    /**
     * An observer that registers a new observer from inside its onEvent
     * callback also mutates the list mid-iteration; dispatch must not throw. A
     * second observer is registered up front so the iteration continues past
     * the mutation. Because the snapshot is taken before dispatch begins, the
     * observer added mid-dispatch is not part of this event and must not
     * receive it.
     */
    @Test
    public void testObserverRegisteringAnotherDuringDispatchDoesNotThrow() {
        final boolean[] addedReceived = new boolean[1];
        final EventsObserver added = evt -> addedReceived[0] = true;
        final EventsObserver adder = evt -> register(added);
        final EventsObserver secondObserver = evt -> {
            /*
             * Intentionally empty; present so the list has more than one
             * entry.
             */
        };
        register(adder);
        register(secondObserver);

        EventsObservable.get().notifyEvent(EventType.Other);

        Assert.assertFalse(
                "Observer registered mid-dispatch was not in the snapshot and "
                        + "should not receive the in-flight event",
                addedReceived[0]);
    }

    /**
     * An observer whose onEvent callback fires a nested notifyEvent on the same
     * thread re-enters dispatch while the outer dispatch is still iterating.
     * The nested notify takes its own snapshot, so this re-entrancy must not
     * throw ConcurrentModificationException. A one-shot guard stops the nested
     * event from recursing forever, and a second observer is present so both
     * the outer and nested dispatches iterate more than one entry.
     */
    @Test
    public void testObserverFiringNestedNotifyDuringDispatchDoesNotThrow() {
        final boolean[] reentered = new boolean[1];
        final EventsObserver nestingObserver = evt -> {
            if (!reentered[0]) {
                reentered[0] = true;
                EventsObservable.get().notifyEvent(EventType.Other);
            }
        };
        final EventsObserver secondObserver = evt -> {
            /*
             * Intentionally empty; present so the list has more than one
             * entry.
             */
        };
        register(nestingObserver);
        register(secondObserver);

        EventsObservable.get().notifyEvent(EventType.Other);

        Assert.assertTrue(
                "Nested notifyEvent should have been dispatched re-entrantly",
                reentered[0]);
    }

    /**
     * Every registered observer still receives an event that no callback
     * mutates the list on, so the snapshot dispatch does not drop deliveries.
     */
    @Test
    public void testAllObserversReceiveEventWhenNoReentrantModification() {
        final boolean[] delivered = new boolean[2];
        final EventsObserver first = evt -> delivered[0] = true;
        final EventsObserver second = evt -> delivered[1] = true;
        register(first);
        register(second);

        EventsObservable.get().notifyEvent(EventType.Other);

        Assert.assertTrue("First observer should receive the event", delivered[0]);
        Assert.assertTrue("Second observer should receive the event", delivered[1]);
    }
}
