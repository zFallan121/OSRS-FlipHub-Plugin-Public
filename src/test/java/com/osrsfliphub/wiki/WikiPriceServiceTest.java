/*
 * Copyright (c) 2026, zFallan121
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.osrsfliphub;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WikiPriceServiceTest {
    @Test
    public void getPriceEntryTriggersFetchWhenStale() {
        TestHooks hooks = new TestHooks();
        hooks.panelVisible = true;
        ImmediateSuccessFetcher fetcher = new ImmediateSuccessFetcher();
        fetcher.entries.put(4151, entry(1200, 1000));
        WikiPriceService service = new WikiPriceService(1L, 0L, hooks, fetcher);

        WikiPriceEntry first = service.getPriceEntry(4151, true);
        WikiPriceEntry second = service.getPriceEntry(4151, false);

        WikiPriceEntry effective = second != null ? second : first;
        assertNotNull(effective);
        assertEquals(Integer.valueOf(1200), effective.high);
        assertEquals(1, fetcher.calls);
    }

    @Test
    public void refreshSkipsWhenPanelHidden() {
        TestHooks hooks = new TestHooks();
        hooks.panelVisible = false;
        ImmediateSuccessFetcher fetcher = new ImmediateSuccessFetcher();
        fetcher.entries.put(100, entry(10, 9));
        WikiPriceService service = new WikiPriceService(1000L, 0L, hooks, fetcher);

        service.refreshPrices();

        assertEquals(0, fetcher.calls);
    }

    @Test
    public void refreshRespectsMinRefreshInterval() {
        TestHooks hooks = new TestHooks();
        hooks.panelVisible = true;
        ImmediateSuccessFetcher fetcher = new ImmediateSuccessFetcher();
        fetcher.entries.put(100, entry(10, 9));
        WikiPriceService service = new WikiPriceService(-1L, 60_000L, hooks, fetcher);

        service.refreshPrices();
        service.refreshPrices();

        assertEquals(1, fetcher.calls);
    }

    @Test
    public void startSchedulesAndStopCancels() {
        TestHooks hooks = new TestHooks();
        hooks.panelVisible = true;
        ImmediateSuccessFetcher fetcher = new ImmediateSuccessFetcher();
        WikiPriceService service = new WikiPriceService(1000L, 0L, hooks, fetcher);
        StubScheduler scheduler = new StubScheduler();

        service.start(scheduler);
        service.stop();

        assertEquals(1, scheduler.scheduledCount);
        assertEquals(1, scheduler.cancelledCount);
    }

    private WikiPriceEntry entry(int high, int low) {
        WikiPriceEntry entry = new WikiPriceEntry();
        entry.high = high;
        entry.low = low;
        return entry;
    }

    private static final class TestHooks implements WikiPriceService.Hooks {
        private boolean panelVisible;

        @Override
        public boolean isPanelVisible() {
            return panelVisible;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void logDebug(String message) {
        }
    }

    private static final class ImmediateSuccessFetcher implements WikiPriceService.Fetcher {
        private int calls = 0;
        private final Map<Integer, WikiPriceEntry> entries = new HashMap<>();

        @Override
        public void fetch(Callback callback) {
            calls++;
            callback.onSuccess(new HashMap<>(entries));
        }
    }

    private static final class StubScheduler implements ScheduledExecutorService {
        private int scheduledCount = 0;
        private int cancelledCount = 0;
        private ScheduledFuture<?> lastFuture;

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            scheduledCount++;
            lastFuture = new StubFuture(this);
            return lastFuture;
        }

        private static final class StubFuture implements ScheduledFuture<Object> {
            private final StubScheduler owner;
            private boolean cancelled;

            private StubFuture(StubScheduler owner) {
                this.owner = owner;
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (!cancelled) {
                    cancelled = true;
                    owner.cancelledCount++;
                }
                return true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isDone() {
                return cancelled;
            }

            @Override
            public Object get() {
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                return null;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(java.util.concurrent.Delayed o) {
                return 0;
            }
        }

        @Override public void shutdown() {}
        @Override public java.util.List<Runnable> shutdownNow() { return java.util.Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) { throw new UnsupportedOperationException(); }
        @Override public java.util.concurrent.Future<?> submit(Runnable task) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public void execute(Runnable command) { if (command != null) { command.run(); } }
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }
}
