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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class WikiPriceService {
    interface Hooks {
        boolean isPanelVisible();
        boolean isDebugEnabled();
        void logDebug(String message);
    }

    interface Fetcher {
        interface Callback {
            void onSuccess(Map<Integer, WikiPriceEntry> entries);
            void onFailure(Exception error);
        }

        void fetch(Callback callback);
    }

    private final long cacheTtlMs;
    private final long minRefreshMs;
    private final Hooks hooks;
    private final Fetcher fetcher;
    private final Object wikiPriceLock = new Object();
    private final Map<Integer, WikiPriceEntry> wikiLatestCache = new HashMap<>();
    private final AtomicBoolean wikiFetchInFlight = new AtomicBoolean(false);
    private final AtomicLong wikiLastAttemptMs = new AtomicLong(0L);
    private volatile long wikiLatestFetchedMs;
    private volatile ScheduledFuture<?> wikiFetchTask;

    WikiPriceService(long cacheTtlMs, long minRefreshMs, Hooks hooks, Fetcher fetcher) {
        this.cacheTtlMs = cacheTtlMs;
        this.minRefreshMs = minRefreshMs;
        this.hooks = hooks;
        this.fetcher = fetcher;
    }

    WikiPriceEntry getPriceEntry(int itemId, boolean allowRefresh) {
        long now = System.currentTimeMillis();
        if (allowRefresh && (wikiLatestFetchedMs <= 0 || now - wikiLatestFetchedMs > cacheTtlMs)) {
            requestFetch(true);
        }
        synchronized (wikiPriceLock) {
            return wikiLatestCache.get(itemId);
        }
    }

    void refreshPrices() {
        requestFetch(false);
    }

    void start(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            return;
        }
        if (wikiFetchTask != null && !wikiFetchTask.isCancelled()) {
            return;
        }
        wikiFetchTask = scheduler.scheduleAtFixedRate(() -> requestFetch(false), 5, 1, TimeUnit.SECONDS);
    }

    void stop() {
        if (wikiFetchTask != null) {
            wikiFetchTask.cancel(true);
            wikiFetchTask = null;
        }
        wikiFetchInFlight.set(false);
    }

    private void requestFetch(boolean allowWhenHidden) {
        if (!shouldFetch(allowWhenHidden)) {
            return;
        }
        if (!wikiFetchInFlight.compareAndSet(false, true)) {
            return;
        }
        wikiLastAttemptMs.set(System.currentTimeMillis());
        if (fetcher == null) {
            wikiFetchInFlight.set(false);
            return;
        }
        fetcher.fetch(new Fetcher.Callback() {
            @Override
            public void onSuccess(Map<Integer, WikiPriceEntry> entries) {
                try {
                    if (entries == null) {
                        return;
                    }
                    synchronized (wikiPriceLock) {
                        wikiLatestCache.clear();
                        wikiLatestCache.putAll(entries);
                        wikiLatestFetchedMs = System.currentTimeMillis();
                    }
                } finally {
                    wikiFetchInFlight.set(false);
                }
            }

            @Override
            public void onFailure(Exception error) {
                try {
                    if (error != null && hooks != null && hooks.isDebugEnabled()) {
                        hooks.logDebug("Wiki price refresh failed: " + error.getMessage());
                    }
                } finally {
                    wikiFetchInFlight.set(false);
                }
            }
        });
    }

    private boolean shouldFetch(boolean allowWhenHidden) {
        long now = System.currentTimeMillis();
        if (!allowWhenHidden && hooks != null && !hooks.isPanelVisible()) {
            return false;
        }
        if (wikiFetchInFlight.get()) {
            return false;
        }
        if (wikiLatestFetchedMs > 0 && now - wikiLatestFetchedMs <= cacheTtlMs) {
            return false;
        }
        long lastAttempt = wikiLastAttemptMs.get();
        if (lastAttempt > 0 && now - lastAttempt < minRefreshMs) {
            return false;
        }
        return true;
    }
}
