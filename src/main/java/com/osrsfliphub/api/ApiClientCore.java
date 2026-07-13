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
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class ApiClientCore {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String API_BASE_URL = "https://osrs-fliphub-production.up.railway.app";
    private static final String PATH_LINK = "/api/plugin/link";
    private static final String PATH_REFRESH = "/api/plugin/refresh";
    private static final String PATH_EVENTS = "/api/plugin/events";
    private static final String PATH_STATS_ACCOUNTWIDE = "/api/plugin/stats/accountwide";
    private static final String PATH_STATS_WIPE = "/api/plugin/stats/wipe";
    private static final String PATH_ITEMS = "/api/plugin/items";
    private static final String PATH_ITEM = "/api/plugin/item";
    private static final String PATH_STATS_SUMMARY = "/api/plugin/stats/summary";
    private static final String PATH_STATS_ITEMS = "/api/plugin/stats/items";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PluginConfig config;
    private final ApiClientRequestFactory requestFactory;

    ApiClientCore(OkHttpClient httpClient, Gson gson, PluginConfig config) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.config = config;
        this.requestFactory = new ApiClientRequestFactory(API_BASE_URL, JSON);
    }

    /**
     * All traffic to FlipHub's server is opt-in. Every method that performs an
     * OkHttp call to {@link #API_BASE_URL} must call this first so no request
     * can leave the client while the 'Enable FlipHub sync' config is off.
     */
    private void ensureSyncEnabled() {
        if (config == null || !config.enableFlipHubSync()) {
            throw new IllegalStateException(
                "FlipHub sync is disabled. Turn on 'Enable FlipHub sync' in the FlipHub plugin "
                    + "settings to allow connections to FlipHub's server.");
        }
    }

    ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId, String deviceName, String pluginVersion)
        throws IOException {
        ensureSyncEnabled();
        Map<String, Object> body = new HashMap<>();
        body.put("license_key", licenseKey);
        body.put("code", licenseKey);
        body.put("device_id", deviceId);
        body.put("device_name", deviceName);
        body.put("plugin_version", pluginVersion);

        String json = gson.toJson(body);
        Request request = requestFactory.newPostRequest(PATH_LINK, json);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = "";
                if (response.body() != null) {
                    errorBody = response.body().string();
                }
                if (errorBody == null || errorBody.trim().isEmpty()) {
                    throw new IllegalStateException("Link failed: " + response.code());
                }
                throw new IllegalStateException("Link failed: " + response.code() + " - " + errorBody);
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ApiClient.LinkResponse.class);
        }
    }

    ApiClient.LinkResponse refreshSession(String sessionToken) throws IOException {
        return refreshSession(sessionToken, null, null);
    }

    ApiClient.LinkResponse refreshSession(String sessionToken, String signingSecret, String deviceId) throws IOException {
        ensureSyncEnabled();
        Map<String, Object> payload = new HashMap<>();
        if (requestFactory.hasText(deviceId)) {
            payload.put("device_id", deviceId.trim());
            payload.put("sent_at_ms", System.currentTimeMillis());
        }
        String json = gson.toJson(payload);
        Request.Builder requestBuilder = requestFactory.newPostBuilder(PATH_REFRESH, json);
        if (requestFactory.hasText(sessionToken)) {
            requestBuilder.addHeader("X-Plugin-Token", sessionToken);
        }

        if (requestFactory.hasText(signingSecret) && requestFactory.hasText(deviceId)) {
            requestFactory.addSignedHeaders(requestBuilder, "POST", PATH_REFRESH, signingSecret, json);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Refresh failed: " + response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ApiClient.LinkResponse.class);
        }
    }

    int sendEvents(String sessionToken, String signingSecret, List<GeEvent> events) throws IOException {
        ApiClient.EventUploadResponse response = sendEventsDetailed(sessionToken, signingSecret, events);
        return response != null ? response.status_code : 500;
    }

    ApiClient.EventUploadResponse sendEventsDetailed(String sessionToken,
                                                     String signingSecret,
                                                     List<GeEvent> events) throws IOException {
        ensureSyncEnabled();
        ApiClient.EventUploadResponse result = new ApiClient.EventUploadResponse();
        if (events == null || events.isEmpty()) {
            result.status_code = 0;
            result.status = "ok";
            result.accepted = 0;
            result.duplicates = 0;
            result.rejected = 0;
            return result;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("schema_version", 1);
        payload.put("sent_at_ms", System.currentTimeMillis());
        payload.put("events", events);

        String json = gson.toJson(payload);
        Request request = requestFactory.newSignedPostRequest(PATH_EVENTS, sessionToken, signingSecret, json);

        try (Response response = httpClient.newCall(request).execute()) {
            result.status_code = response.code();
            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    ApiClient.EventUploadResponse parsed = gson.fromJson(responseBody, ApiClient.EventUploadResponse.class);
                    if (parsed != null) {
                        if (parsed.status != null) {
                            result.status = parsed.status;
                        }
                        result.accepted = parsed.accepted;
                        result.duplicates = parsed.duplicates;
                        result.rejected = parsed.rejected;
                    }
                } catch (JsonParseException ignored) {
                }
            }
            return result;
        }
    }

    int sendAccountwideSummary(String sessionToken, String signingSecret, StatsSummary summary) throws IOException {
        return sendAccountwideSummary(sessionToken, signingSecret, summary, null);
    }

    int sendAccountwideSummary(String sessionToken,
                               String signingSecret,
                               StatsSummary summary,
                               List<StatsItem> items) throws IOException {
        ensureSyncEnabled();
        Map<String, Object> payload = new HashMap<>();
        payload.put("schema_version", 1);
        payload.put("sent_at_ms", System.currentTimeMillis());
        payload.put("summary", summary != null ? summary : new StatsSummary());
        payload.put("items", items != null ? items : new ArrayList<>());

        String json = gson.toJson(payload);
        Request request = requestFactory.newSignedPostRequest(PATH_STATS_ACCOUNTWIDE, sessionToken, signingSecret, json);

        try (Response response = httpClient.newCall(request).execute()) {
            return response.code();
        }
    }

    ApiClient.WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws IOException {
        ensureSyncEnabled();
        Map<String, Object> payload = new HashMap<>();
        payload.put("schema_version", 1);
        payload.put("sent_at_ms", System.currentTimeMillis());

        String json = gson.toJson(payload);
        Request request = requestFactory.newSignedPostRequest(PATH_STATS_WIPE, sessionToken, signingSecret, json);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiClient.ApiException("Website wipe failed", response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null || responseBody.trim().isEmpty()) {
                ApiClient.WipeStatsResponse empty = new ApiClient.WipeStatsResponse();
                empty.status = "ok";
                return empty;
            }
            return gson.fromJson(responseBody, ApiClient.WipeStatsResponse.class);
        }
    }

    ApiClient.ItemsResponse fetchItems(String sessionToken, String query, int page, int pageSize) throws IOException {
        ensureSyncEnabled();
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(requestFactory.apiUrl(PATH_ITEMS));
        urlBuilder.append("?page=").append(page).append("&page_size=").append(pageSize);
        if (query != null && !query.trim().isEmpty()) {
            urlBuilder.append("&q=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        }

        Request request = requestFactory.newGetRequest(urlBuilder.toString(), sessionToken);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiClient.ApiException("Fetch items failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ApiClient.ItemsResponse.class);
        }
    }

    ApiClient.ItemResponse fetchItem(String sessionToken, int itemId) throws IOException {
        ensureSyncEnabled();
        String url = requestFactory.apiUrl(PATH_ITEM) + "?item_id=" + itemId;
        Request request = requestFactory.newGetRequest(url, sessionToken);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiClient.ApiException("Fetch item failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ApiClient.ItemResponse.class);
        }
    }

    ApiClient.StatsSummaryResponse fetchStatsSummary(String sessionToken, Long sinceMs, Long untilMs) throws IOException {
        ensureSyncEnabled();
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(requestFactory.apiUrl(PATH_STATS_SUMMARY));
        requestFactory.appendStatsQuery(urlBuilder, sinceMs, untilMs);

        Request request = requestFactory.newGetRequest(urlBuilder.toString(), sessionToken);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiClient.ApiException("Fetch stats summary failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ApiClient.StatsSummaryResponse.class);
        }
    }

    ApiClient.StatsItemsResponse fetchStatsItems(String sessionToken,
                                                 Long sinceMs,
                                                 Long untilMs,
                                                 Integer limit,
                                                 StatsItemSort sort) throws IOException {
        ensureSyncEnabled();
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(requestFactory.apiUrl(PATH_STATS_ITEMS));
        boolean hasQuery = requestFactory.appendStatsQuery(urlBuilder, sinceMs, untilMs);
        if (limit != null) {
            urlBuilder.append(hasQuery ? "&" : "?").append("limit=").append(limit);
            hasQuery = true;
        }
        if (sort != null) {
            urlBuilder.append(hasQuery ? "&" : "?").append("sort=").append(sort.getApiValue());
            hasQuery = true;
        }

        Request request = requestFactory.newGetRequest(urlBuilder.toString(), sessionToken);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiClient.ApiException("Fetch stats items failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ApiClient.StatsItemsResponse.class);
        }
    }

}
