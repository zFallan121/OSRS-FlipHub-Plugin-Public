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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountwideSummaryUploaderTest {
    @Test
    public void syncIfNeededUploadsSummaryWhenDirty() {
        AccountwideSummaryUploader uploader = new AccountwideSummaryUploader(0L, 5 * 60_000L);
        ScriptedApiClient apiClient = new ScriptedApiClient(200);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.snapshot = snapshot(summary(100L, 1_000L, 10L, 3));

        uploader.syncIfNeeded(apiClient, config, hooks);

        assertEquals(1, apiClient.calls);
        assertFalse(uploader.isDirty());
    }

    @Test
    public void syncIfNeededSkipsUploadWhenDirtyButSummaryUnchanged() {
        AccountwideSummaryUploader uploader = new AccountwideSummaryUploader(0L, 5 * 60_000L);
        ScriptedApiClient apiClient = new ScriptedApiClient(200, 200);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.snapshot = snapshot(summary(200L, 2_000L, 20L, 4));

        uploader.syncIfNeeded(apiClient, config, hooks);
        uploader.markDirty();
        uploader.syncIfNeeded(apiClient, config, hooks);

        assertEquals(1, apiClient.calls);
        assertFalse(uploader.isDirty());
    }

    @Test
    public void syncIfNeededRefreshesAndRetriesOnUnauthorized() {
        AccountwideSummaryUploader uploader = new AccountwideSummaryUploader(0L, 5 * 60_000L);
        ScriptedApiClient apiClient = new ScriptedApiClient(401, 200);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.snapshot = snapshot(summary(300L, 3_000L, 30L, 5));
        hooks.refreshShouldSucceed = true;
        hooks.onRefresh = () -> {
            config.sessionToken = "token2";
            config.signingSecret = "secret2";
        };

        uploader.syncIfNeeded(apiClient, config, hooks);

        assertEquals(2, apiClient.calls);
        assertEquals(1, hooks.refreshAttempts);
        assertEquals(0, hooks.clearSessionCalls);
        assertFalse(uploader.isDirty());
    }

    @Test
    public void syncIfNeededClearsSessionWhenRefreshFails() {
        AccountwideSummaryUploader uploader = new AccountwideSummaryUploader(0L, 5 * 60_000L);
        ScriptedApiClient apiClient = new ScriptedApiClient(401);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.snapshot = snapshot(summary(400L, 4_000L, 40L, 6));
        hooks.refreshShouldSucceed = false;

        uploader.syncIfNeeded(apiClient, config, hooks);

        assertEquals(1, apiClient.calls);
        assertEquals(1, hooks.refreshAttempts);
        assertEquals(1, hooks.clearSessionCalls);
        assertTrue(uploader.isDirty());
    }

    @Test
    public void syncIfNeededUploadsWhenItemsChange() {
        AccountwideSummaryUploader uploader = new AccountwideSummaryUploader(0L, 5 * 60_000L);
        ScriptedApiClient apiClient = new ScriptedApiClient(200, 200);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();

        StatsSummary summary = summary(500L, 5_000L, 50L, 7);
        hooks.snapshot = snapshot(summary, itemList(4151, 500L));
        uploader.syncIfNeeded(apiClient, config, hooks);

        uploader.markDirty();
        hooks.snapshot = snapshot(summary(500L, 5_000L, 50L, 7), itemList(4151, 650L));
        uploader.syncIfNeeded(apiClient, config, hooks);

        assertEquals(2, apiClient.calls);
        assertFalse(uploader.isDirty());
    }

    private static StatsSummary summary(long profit, long cost, long tax, int flips) {
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = profit;
        summary.total_cost_gp = cost;
        summary.tax_paid_gp = tax;
        summary.fill_count = flips;
        return summary;
    }

    private static LocalStatsSnapshot snapshot(StatsSummary summary) {
        return new LocalStatsSnapshot(summary, new ArrayList<>());
    }

    private static LocalStatsSnapshot snapshot(StatsSummary summary, List<StatsItem> items) {
        return new LocalStatsSnapshot(summary, items);
    }

    private static List<StatsItem> itemList(int itemId, long profit) {
        StatsItem item = new StatsItem();
        item.item_id = itemId;
        item.total_profit_gp = profit;
        item.total_cost_gp = 1_000L;
        item.fill_count = 1;
        List<StatsItem> items = new ArrayList<>();
        items.add(item);
        return items;
    }

    private static final class ScriptedApiClient extends ApiClient {
        private final Deque<Integer> statuses = new ArrayDeque<>();
        private int calls = 0;

        private ScriptedApiClient(Integer... scriptedStatuses) {
            super(new OkHttpClient(), new Gson());
            if (scriptedStatuses != null) {
                statuses.addAll(Arrays.asList(scriptedStatuses));
            }
        }

        @Override
        public int sendAccountwideSummary(String sessionToken,
                                          String signingSecret,
                                          StatsSummary summary,
                                          List<StatsItem> items) {
            calls++;
            Integer status = statuses.pollFirst();
            return status != null ? status : 200;
        }
    }

    private static final class MutableConfig implements PluginConfig {
        private String sessionToken;
        private String signingSecret;

        private MutableConfig(String sessionToken, String signingSecret) {
            this.sessionToken = sessionToken;
            this.signingSecret = signingSecret;
        }

        @Override
        public String sessionToken() {
            return sessionToken;
        }

        @Override
        public String signingSecret() {
            return signingSecret;
        }
    }

    private static final class TestHooks implements AccountwideSummaryUploader.Hooks {
        private boolean ready = true;
        private LocalStatsSnapshot snapshot = new LocalStatsSnapshot(new StatsSummary(), new ArrayList<>());
        private boolean refreshShouldSucceed = false;
        private int refreshAttempts = 0;
        private int clearSessionCalls = 0;
        private Runnable onRefresh;

        @Override
        public boolean isClientFullyReady() {
            return ready;
        }

        @Override
        public LocalStatsSnapshot buildAccountwideSnapshot() {
            return snapshot;
        }

        @Override
        public boolean attemptRefresh(String currentToken) {
            refreshAttempts++;
            if (refreshShouldSucceed && onRefresh != null) {
                onRefresh.run();
            }
            return refreshShouldSucceed;
        }

        @Override
        public void clearSession() {
            clearSessionCalls++;
        }
    }
}
