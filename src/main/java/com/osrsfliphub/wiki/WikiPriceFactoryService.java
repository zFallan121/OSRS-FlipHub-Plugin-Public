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
import java.util.HashMap;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class WikiPriceFactoryService {
    interface RuntimeHooks {
        boolean isPanelVisible();
        boolean isDebugEnabled();
        void logDebug(String message);
        OkHttpClient getHttpClient();
        Gson getGson();
    }

    private final long cacheTtlMs;
    private final long minRefreshMs;
    private final String latestUrl;
    private final String userAgent;

    WikiPriceFactoryService(long cacheTtlMs, long minRefreshMs, String latestUrl, String userAgent) {
        this.cacheTtlMs = cacheTtlMs;
        this.minRefreshMs = minRefreshMs;
        this.latestUrl = latestUrl;
        this.userAgent = userAgent;
    }

    WikiPriceService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new WikiPriceService(cacheTtlMs, minRefreshMs, null, null);
        }
        return new WikiPriceService(
            cacheTtlMs,
            minRefreshMs,
            new WikiPriceService.Hooks() {
                @Override
                public boolean isPanelVisible() {
                    return runtimeHooks.isPanelVisible();
                }

                @Override
                public boolean isDebugEnabled() {
                    return runtimeHooks.isDebugEnabled();
                }

                @Override
                public void logDebug(String message) {
                    runtimeHooks.logDebug(message);
                }
            },
            callback -> {
                if (callback == null) {
                    return;
                }
                OkHttpClient httpClient = runtimeHooks.getHttpClient();
                Gson gson = runtimeHooks.getGson();
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
                httpClient.newCall(request).enqueue(new Callback() {
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
            }
        );
    }
}
