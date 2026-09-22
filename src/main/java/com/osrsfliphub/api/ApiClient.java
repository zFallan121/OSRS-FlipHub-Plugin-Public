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

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import okhttp3.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApiClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    // The site's own front door, and the same one the panel's links open. It used to be the
    // hosting provider's address for the deployment behind it, which is a thing that moves: a
    // redeploy elsewhere would have stranded every plugin already installed, with no way to
    // tell them the new one. It also made the privacy note in the README wrong, since that
    // says trade data goes to osrsfliphub.com and it was going somewhere else.
    private static final String API_BASE_URL = Skin.DEFAULT_BASE_URL;
    private static final String PATH_LINK = "/api/plugin/link";
    private static final String PATH_REFRESH = "/api/plugin/refresh";
    private static final String PATH_EVENTS = "/api/plugin/events";
    private static final String PATH_STATS_ACCOUNTWIDE = "/api/plugin/stats/accountwide";
    private static final String PATH_STATS_WIPE = "/api/plugin/stats/wipe";
    private static final String PATH_STATS_SUMMARY = "/api/plugin/stats/summary";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PluginConfig config;
    private final ApiClientRequestFactory requestFactory =
        new ApiClientRequestFactory(API_BASE_URL, JSON);

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

    public LinkResponse linkDevice(String licenseKey, String deviceId, String pluginVersion)
        throws IOException {
        ensureSyncEnabled();
        Map<String, Object> body = new HashMap<>();
        body.put("license_key", licenseKey);
        body.put("code", licenseKey);
        body.put("device_id", deviceId);
        body.put("plugin_version", pluginVersion);

        String json = gson.toJson(body);
        Request request = requestFactory.newPostRequest(PATH_LINK, json);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = "";
                if (response.body() != null) {
                    errorBody = response.body().string();
                }
                if (Str.isBlank(errorBody)) {
                    throw new ApiRefusedException(response.code(), "Link failed: " + response.code());
                }
                throw new ApiRefusedException(response.code(),
                    "Link failed: " + response.code() + " - " + errorBody);
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, LinkResponse.class);
        }
    }

    public LinkResponse refreshSession(String sessionToken, String signingSecret, String deviceId) throws IOException {
        ensureSyncEnabled();
        Map<String, Object> payload = new HashMap<>();
        if (Str.hasText(deviceId)) {
            payload.put("device_id", deviceId.trim());
            payload.put("sent_at_ms", System.currentTimeMillis());
        }
        String json = gson.toJson(payload);
        Request.Builder requestBuilder = requestFactory.newPostBuilder(PATH_REFRESH, json);
        if (Str.hasText(sessionToken)) {
            requestBuilder.addHeader("X-Plugin-Token", sessionToken);
        }

        if (Str.hasText(signingSecret) && Str.hasText(deviceId)) {
            requestFactory.addSignedHeaders(requestBuilder, "POST", PATH_REFRESH, signingSecret, json);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException("Refresh failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, LinkResponse.class);
        }
    }

    public int sendEvents(String sessionToken, String signingSecret, List<GeEvent> events) throws IOException {
        EventUploadResponse response = sendEventsDetailed(sessionToken, signingSecret, events);
        return response != null ? response.status_code : 500;
    }

    public EventUploadResponse sendEventsDetailed(String sessionToken,
                                                     String signingSecret,
                                                     List<GeEvent> events) throws IOException {
        ensureSyncEnabled();
        EventUploadResponse result = new EventUploadResponse();
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
            if (Str.hasText(responseBody)) {
                try {
                    EventUploadResponse parsed = gson.fromJson(responseBody, EventUploadResponse.class);
                    if (parsed != null) {
                        if (parsed.status != null) {
                            result.status = parsed.status;
                        }
                        result.accepted = parsed.accepted;
                        result.duplicates = parsed.duplicates;
                        result.rejected = parsed.rejected;
                    }
                } catch (JsonParseException ignored) {
                    // The upload went through; only the counts in the reply are unreadable.
                }
            }
            return result;
        }
    }

    public int sendAccountwideSummary(String sessionToken, String signingSecret, StatsSummary summary) throws IOException {
        return sendAccountwideSummary(sessionToken, signingSecret, summary, null);
    }

    public int sendAccountwideSummary(String sessionToken,
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

    public WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws IOException {
        ensureSyncEnabled();
        Map<String, Object> payload = new HashMap<>();
        payload.put("schema_version", 1);
        payload.put("sent_at_ms", System.currentTimeMillis());

        String json = gson.toJson(payload);
        Request request = requestFactory.newSignedPostRequest(PATH_STATS_WIPE, sessionToken, signingSecret, json);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException("Website wipe failed", response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : null;
            if (Str.isBlank(responseBody)) {
                WipeStatsResponse empty = new WipeStatsResponse();
                empty.status = "ok";
                return empty;
            }
            return gson.fromJson(responseBody, WipeStatsResponse.class);
        }
    }

    public StatsSummaryResponse fetchStatsSummary(String sessionToken, Long sinceMs, Long untilMs) throws IOException {
        ensureSyncEnabled();
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(requestFactory.apiUrl(PATH_STATS_SUMMARY));
        requestFactory.appendStatsQuery(urlBuilder, sinceMs, untilMs);

        Request request = requestFactory.newGetRequest(urlBuilder.toString(), sessionToken);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException("Fetch stats summary failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, StatsSummaryResponse.class);
        }
    }

    public static class LinkResponse {
        public String session_token;
        public String session_expires_at;
        public String signing_secret;
    }

    public static class ItemsResponse {
        public List<FlipHubItem> items;
        public int page;
        public int page_size;
        public int total_items;
        public int total_pages;
        public long as_of_ms;
        public Long price_cache_ms;
    }

    public static class StatsSummaryResponse {
        public long as_of_ms;
        public StatsSummary summary;
    }

    public static class WipeStatsResponse {
        public String status;
        public Integer deleted_trade_events;
        public Integer deleted_buy_lots;
        public Integer deleted_flip_fills;
        public Integer deleted_accountwide_stats;
    }

    public static class EventUploadResponse {
        public int status_code;
        public String status;
        public Integer accepted;
        public Integer duplicates;
        public Integer rejected;
    }

    public static class ApiException extends IOException {
        public final int statusCode;

        public ApiException(String message, int statusCode) {
            super(message + ": " + statusCode);
            this.statusCode = statusCode;
        }
    }
}
