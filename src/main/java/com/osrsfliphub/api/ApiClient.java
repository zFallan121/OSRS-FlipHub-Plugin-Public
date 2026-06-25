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
import java.util.List;
import okhttp3.OkHttpClient;

public class ApiClient {
    private final ApiClientCore core;

    public ApiClient(OkHttpClient httpClient, Gson gson) {
        this.core = new ApiClientCore(httpClient, gson);
    }

    public LinkResponse linkDevice(String licenseKey, String deviceId, String deviceName, String pluginVersion)
        throws IOException {
        return core.linkDevice(licenseKey, deviceId, deviceName, pluginVersion);
    }

    public LinkResponse refreshSession(String sessionToken) throws IOException {
        return core.refreshSession(sessionToken);
    }

    public LinkResponse refreshSession(String sessionToken, String signingSecret, String deviceId) throws IOException {
        return core.refreshSession(sessionToken, signingSecret, deviceId);
    }

    public int sendEvents(String sessionToken, String signingSecret, List<GeEvent> events) throws IOException {
        return core.sendEvents(sessionToken, signingSecret, events);
    }

    public EventUploadResponse sendEventsDetailed(String sessionToken, String signingSecret, List<GeEvent> events)
        throws IOException {
        return core.sendEventsDetailed(sessionToken, signingSecret, events);
    }

    public int sendAccountwideSummary(String sessionToken, String signingSecret, StatsSummary summary) throws IOException {
        return core.sendAccountwideSummary(sessionToken, signingSecret, summary);
    }

    public int sendAccountwideSummary(String sessionToken,
                                      String signingSecret,
                                      StatsSummary summary,
                                      List<StatsItem> items) throws IOException {
        return core.sendAccountwideSummary(sessionToken, signingSecret, summary, items);
    }

    public WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws IOException {
        return core.wipeWebsiteStats(sessionToken, signingSecret);
    }

    public ItemsResponse fetchItems(String sessionToken, String query, int page, int pageSize) throws IOException {
        return core.fetchItems(sessionToken, query, page, pageSize);
    }

    public ItemResponse fetchItem(String sessionToken, int itemId) throws IOException {
        return core.fetchItem(sessionToken, itemId);
    }

    public StatsSummaryResponse fetchStatsSummary(String sessionToken, Long sinceMs, Long untilMs) throws IOException {
        return core.fetchStatsSummary(sessionToken, sinceMs, untilMs);
    }

    public StatsItemsResponse fetchStatsItems(String sessionToken, Long sinceMs, Long untilMs, Integer limit, StatsItemSort sort)
        throws IOException {
        return core.fetchStatsItems(sessionToken, sinceMs, untilMs, limit, sort);
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

    public static class ItemResponse {
        public FlipHubItem item;
        public long as_of_ms;
        public Long price_cache_ms;
    }

    public static class StatsSummaryResponse {
        public long as_of_ms;
        public StatsSummary summary;
    }

    public static class StatsItemsResponse {
        public long as_of_ms;
        public List<StatsItem> items;
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
