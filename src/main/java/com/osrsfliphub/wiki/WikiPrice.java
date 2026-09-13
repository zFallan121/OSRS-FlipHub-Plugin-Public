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

import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

@Singleton
@Slf4j
@RequiredArgsConstructor
final class WikiPrice {

    interface Fetcher {
        interface Callback {
            void onSuccess(Map<Integer, WikiPriceEntry> entries);
            void onFailure(Exception error);
        }

        void fetch(Callback callback);
    }

    private final long cacheTtlMs;
    private final long minRefreshMs;
    private final PluginRuntime runtime;
    private final Fetcher fetcher;
    private final Object wikiPriceLock = new Object();
    private final Map<Integer, WikiPriceEntry> wikiLatestCache = new HashMap<>();
    private final AtomicBoolean wikiFetchInFlight = new AtomicBoolean(false);
    private final AtomicLong wikiLastAttemptMs = new AtomicLong(0L);
    private volatile long wikiLatestFetchedMs;
    private volatile ScheduledFuture<?> wikiFetchTask;

    @Inject
    WikiPrice(OkHttpClient httpClient, Gson gson, PluginRuntime runtime) {
        this(Const.WIKI_CACHE_TTL_MS,
            Const.WIKI_MIN_REFRESH_MS,
            runtime,
            httpFetcher(httpClient, gson,
                Const.WIKI_LATEST_URL,
                Const.WIKI_USER_AGENT));
    }

    private static Fetcher httpFetcher(OkHttpClient httpClient, Gson gson, String latestUrl, String userAgent) {
        return callback -> {
            if (callback == null) {
                return;
            }
            if (httpClient == null || gson == null) {
                callback.onFailure(new IllegalStateException("Wiki fetch unavailable"));
                return;
            }
            Request request = new Request.Builder()
                .url(latestUrl)
                .get()
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json")
                .build();
            httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            callback.onFailure(new IOException("HTTP " + response.code()));
                            return;
                        }
                        String body = responseBody != null ? responseBody.string() : null;
                        if (body == null || body.isEmpty()) {
                            callback.onFailure(new IOException("Empty wiki price response"));
                            return;
                        }
                        WikiLatestResponse latest = gson.fromJson(body, WikiLatestResponse.class);
                        if (latest == null || latest.data == null) {
                            callback.onFailure(new IOException("Malformed wiki price response"));
                            return;
                        }
                        Map<Integer, WikiPriceEntry> next = new HashMap<>();
                        for (Map.Entry<String, WikiPriceEntry> entry : latest.data.entrySet()) {
                            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                                continue;
                            }
                            try {
                                int itemId = Integer.parseInt(entry.getKey());
                                if (itemId > 0) {
                                    next.put(itemId, entry.getValue());
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        callback.onSuccess(next);
                    } catch (IOException | RuntimeException ex) {
                        callback.onFailure(ex);
                    }
                }
            });
        };
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
        // No initial delay. The gate below already refuses a fetch while the panel is hidden
        // and holds a floor between attempts, so waiting five seconds first only ever delayed
        // the first prices the player sees.
        wikiFetchTask = scheduler.scheduleAtFixedRate(() -> requestFetch(false), 0, 1, TimeUnit.SECONDS);
    }

    void stop() {
        if (wikiFetchTask != null) {
            wikiFetchTask.cancel(false);
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
                    notifyPricesArrived();
                } finally {
                    wikiFetchInFlight.set(false);
                }
            }

            @Override
            public void onFailure(Exception error) {
                try {
                    if (error != null && log.isDebugEnabled()) {
                        log.debug("Wiki price refresh failed: " + error.getMessage());
                    }
                } finally {
                    wikiFetchInFlight.set(false);
                }
            }
        });
    }

    /**
     * Tell the panel there are prices now.
     *
     * <p>The panel builds its cards once, when it becomes visible, and the first prices arrive
     * a moment after that. Nothing used to say so, so the cards kept whatever they had been
     * built with until some unrelated event happened to rebuild them: a batch of buy limits
     * finishing, an item name being looked up, an offer changing. On a list of any size that
     * is tens of seconds of a panel that looks broken.
     *
     * <p>The refresh is debounced by the coordinator, and a successful fetch happens at most
     * once per cache window, so this is cheap.
     */
    private void notifyPricesArrived() {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null) {
            return;
        }
        PanelRefresh coordinator = Bridge.get(PanelRefresh.class);
        if (coordinator != null) {
            coordinator.scheduleRefreshSoon(plugin.scheduler);
        }
    }

    private boolean shouldFetch(boolean allowWhenHidden) {
        long now = System.currentTimeMillis();
        if (!allowWhenHidden && runtime != null && !runtime.isPanelVisible()) {
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
