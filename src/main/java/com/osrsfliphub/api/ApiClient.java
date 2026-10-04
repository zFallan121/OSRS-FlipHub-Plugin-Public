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
import java.nio.charset.StandardCharsets;
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
    // says trade data goes to fliphubosrs.com and it was going somewhere else.
    private static final String API_BASE_URL = Skin.DEFAULT_BASE_URL;
    private static final String PATH_LINK = "/api/plugin/link";
    private static final String PATH_REFRESH = "/api/plugin/refresh";
    private static final String PATH_EVENTS = "/api/plugin/events";
    private static final String PATH_STATS_ACCOUNTWIDE = "/api/plugin/stats/accountwide";
    private static final String PATH_STATS_WIPE = "/api/plugin/stats/wipe";
    private static final String PATH_FIGURES = "/api/plugin/figures";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PluginConfig config;

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
        Request request = newPostBuilder(PATH_LINK, json).build();

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
        Request.Builder requestBuilder = newPostBuilder(PATH_REFRESH, json);
        if (Str.hasText(sessionToken)) {
            requestBuilder.addHeader("X-Plugin-Token", sessionToken);
        }

        if (Str.hasText(signingSecret) && Str.hasText(deviceId)) {
            addSignedHeaders(requestBuilder, PATH_REFRESH, signingSecret, json);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException("Refresh failed", response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, LinkResponse.class);
        }
    }

    private static Request.Builder newPostBuilder(String path, String jsonBody) {
        return new Request.Builder()
            .url(API_BASE_URL + path)
            .post(RequestBody.create(JSON, jsonBody));
    }

    private static void addSignedHeaders(Request.Builder requestBuilder, String path, String signingSecret, String jsonBody) {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodyHash = Signer.sha256Hex(bodyBytes);
        String canonical = "POST\n" +
            path + "\n" +
            timestamp + "\n" +
            nonce + "\n" +
            bodyHash;
        String signature = Signer.hmacBase64(signingSecret, canonical);

        requestBuilder.addHeader("X-Nonce", nonce);
        requestBuilder.addHeader("X-Timestamp", timestamp);
        requestBuilder.addHeader("X-Signature", signature);
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("schema_version", 1);
        payload.put("sent_at_ms", System.currentTimeMillis());
        return payload;
    }

    private Response post(String path, String sessionToken, String signingSecret, Map<String, Object> payload)
        throws IOException {
        ensureSyncEnabled();
        String json = gson.toJson(payload);
        Request.Builder requestBuilder = newPostBuilder(path, json);
        requestBuilder.addHeader("X-Plugin-Token", sessionToken);
        addSignedHeaders(requestBuilder, path, signingSecret, json);
        return httpClient.newCall(requestBuilder.build()).execute();
    }

    public EventUploadResponse sendEventsDetailed(String sessionToken,
                                                     String signingSecret,
                                                     List<GeEvent> events) throws IOException {
        Map<String, Object> payload = payload();
        payload.put("events", events);
        try (Response response = post(PATH_EVENTS, sessionToken, signingSecret, payload)) {
            EventUploadResponse result = null;
            String responseBody = response.body() != null ? response.body().string() : null;
            if (Str.hasText(responseBody)) {
                try {
                    result = gson.fromJson(responseBody, EventUploadResponse.class);
                } catch (JsonParseException ignored) {
                    // The upload went through; only the counts in the reply are unreadable.
                }
            }
            if (result == null) {
                result = new EventUploadResponse();
            }
            result.status_code = response.code();
            return result;
        }
    }

    public int sendAccountwideSummary(String sessionToken,
                               String signingSecret,
                               StatsSummary summary,
                               List<StatsItem> items) throws IOException {
        Map<String, Object> payload = payload();
        payload.put("summary", summary);
        payload.put("items", items);
        try (Response response = post(PATH_STATS_ACCOUNTWIDE, sessionToken, signingSecret, payload)) {
            return response.code();
        }
    }

    public WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws IOException {
        try (Response response = post(PATH_STATS_WIPE, sessionToken, signingSecret, payload())) {
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

    /**
     * The website's own figures for one scope and range: the whole account ("account") or one
     * character, by the tag its uploads carry ({@link GeEvent#characterId}). A linked plugin shows
     * these and never a sum of its own, once the website says they are live ({@link SiteFigures}).
     * Nothing is sent but the question.
     *
     * @param sinceMs the range's start, or null for all time
     */
    public FiguresResponse fetchFigures(String sessionToken, String scope, Long sinceMs) throws IOException {
        return get(PATH_FIGURES, sessionToken, scope, null, sinceMs, FiguresResponse.class);
    }

    /** One item's flips in a scope and range, newest first: asked for when its list is opened. */
    public FlipsResponse fetchFigureFlips(String sessionToken, String scope, int itemId, Long sinceMs)
        throws IOException {
        return get(PATH_FIGURES + "/flips", sessionToken, scope, itemId, sinceMs, FlipsResponse.class);
    }

    private <T> T get(String path, String sessionToken, String scope, Integer itemId, Long sinceMs, Class<T> type)
        throws IOException {
        ensureSyncEnabled();
        HttpUrl.Builder url = HttpUrl.get(API_BASE_URL + path).newBuilder().addQueryParameter("scope", scope);
        if (itemId != null) {
            url.addQueryParameter("item_id", itemId.toString());
        }
        if (sinceMs != null) {
            url.addQueryParameter("since_ms", sinceMs.toString());
        }
        Request request = new Request.Builder()
            .url(url.toString())
            .get()
            .addHeader("X-Plugin-Token", sessionToken)
            // Never kept by the HTTP client, nor answered from what it kept: it files an answer
            // under the address alone, which is the same for every account, and the session is
            // not part of it. A new link would be shown the last link's figures.
            .addHeader("Cache-Control", "no-store")
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ApiException refused = new ApiException("Fetch figures failed", response.code());
                // A 429 says how long to wait in a header, and again in its body: read there
                // when the header did not come through.
                String wait = response.header("Retry-After");
                try {
                    refused.retryAfterSeconds = wait != null ? Long.parseLong(wait)
                        : gson.fromJson(response.peekBody(2_000_000L).string(), JsonObject.class)
                            .get("retry_after").getAsLong();
                } catch (IOException | RuntimeException ignored) {
                    // Neither says, or not in seconds: 0, and the caller picks a wait of its own.
                }
                throw refused;
            }
            // No more of it than this is read, as of a refusal above: the largest answer there is
            // comes to under a megabyte, and one cut short here cannot be read, which the caller
            // takes as none.
            return gson.fromJson(response.peekBody(2_000_000L).string(), type);
        }
    }

    /** {@code {"live": false}} and nothing else until the website's switch is on. */
    public static class FiguresResponse {
        public boolean live;
        public long as_of_ms;
        public StatsSummary summary;
        /** The scope's profit all time, whatever the range: the Merchant level reads this. */
        public long lifetime_profit_gp;
        public Map<String, KindSlice> by_kind;
        /** Only beside one character: the range's sales no character can be given. */
        public StatsSummary untagged;
        public List<StatsItem> items;
    }

    public static class KindSlice {
        public long profit_gp;
        public long cost_gp;
        public long qty;
        public int count;
    }

    public static class FlipsResponse {
        public boolean live;
        /** Whether there were more than the thousand sent. */
        public boolean more;
        public List<FigureFlip> flips;
    }

    /** One sell offer, which is one flip. The prices are per item; the revenue is what was kept after tax. */
    public static class FigureFlip {
        public long buy_price_gp;
        public long sell_price_gp;
        public long buy_cost_gp;
        public long sell_revenue_gp;
        public long profit_gp;
        public int qty;
        public long completed_ms;
        public long tax_gp;
        public String kind;
        public String name;
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
        /** Why the website refused the request, when it did and said. */
        public String error;
        /** Only when the batch held the record of a finished offer ({@link RecordSync}). */
        public Records records;
    }

    /**
     * What the website says of the records in a batch. It also lists the ones it refused, and may
     * say more of its own; only what it confirms is read, and whatever else is there is left unread.
     */
    public static class Records {
        /** The ids of the records the website now holds: stored now, or held already. */
        public List<String> confirmed;
    }

    public static class ApiException extends IOException {
        public final int statusCode;
        /** How long a 429 says to wait before asking again, or 0. */
        public long retryAfterSeconds;

        public ApiException(String message, int statusCode) {
            super(message + ": " + statusCode);
            this.statusCode = statusCode;
        }
    }
}
