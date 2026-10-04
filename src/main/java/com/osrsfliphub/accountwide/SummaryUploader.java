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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.*;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class SummaryUploader {
    private static final long ACCOUNTWIDE_UPLOAD_MIN_INTERVAL_MS = 4_000L;
    private static final long ACCOUNTWIDE_UPLOAD_RESYNC_INTERVAL_MS = 5 * 60_000L;

    private final PluginRuntime pluginRuntime;

    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private volatile long lastUploadAttemptMs;
    private volatile long lastUploadSuccessMs;
    private volatile int lastSnapshotHash = Integer.MIN_VALUE;

    /**
     * Reads the readiness flag the client thread photographs each tick, because this runs on the
     * upload pool and {@code client.getLocalPlayer()} must not be called from there.
     */
    private boolean isClientFullyReady() {
        return pluginRuntime.isClientFullyReady();
    }

    void markDirty() {
        dirty.set(true);
    }

    void resetUploadSnapshot() {
        lastSnapshotHash = Integer.MIN_VALUE;
        lastUploadSuccessMs = 0L;
    }

    void syncIfNeeded(ApiClient apiClient, PluginConfig config) {
        if (apiClient == null || config == null || !isClientFullyReady()) {
            return;
        }
        if (!config.enableFlipHubSync()) {
            return;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        if (!ApiStatusPolicy.hasCredentials(sessionToken, signingSecret)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastUploadAttemptMs < ACCOUNTWIDE_UPLOAD_MIN_INTERVAL_MS) {
            return;
        }

        boolean wasDirty = dirty.get();
        boolean shouldResync = lastSnapshotHash == Integer.MIN_VALUE
            || (nowMs - lastUploadSuccessMs) >= ACCOUNTWIDE_UPLOAD_RESYNC_INTERVAL_MS;
        if (!wasDirty && !shouldResync) {
            return;
        }

        StatsSnapshot snapshot = Access.plugin().getProfileWorkflowService().buildReconciledAccountwideSnapshot();
        StatsSummary summary = snapshot.summary;
        List<StatsItem> items = snapshot.items;
        int snapshotHash = computeSnapshotHash(summary, items);

        if (wasDirty && lastSnapshotHash != Integer.MIN_VALUE && snapshotHash == lastSnapshotHash) {
            dirty.set(false);
            return;
        }
        if (!shouldResync && snapshotHash == lastSnapshotHash) {
            return;
        }

        lastUploadAttemptMs = nowMs;
        try {
            int status = apiClient.sendAccountwideSummary(sessionToken, signingSecret, summary, items);
            if (ApiStatusPolicy.isAuthStatus(status)) {
                if (SessionRefresh.refreshOrUnavailable(sessionToken) != SessionRefresh.Outcome.REFRESHED) {
                    return;
                }
                String refreshedToken = config.sessionToken();
                String refreshedSecret = config.signingSecret();
                if (!ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                    return;
                }
                status = apiClient.sendAccountwideSummary(refreshedToken, refreshedSecret, summary, items);
                if (ApiStatusPolicy.isAuthStatus(status)) {
                    SessionRefresh.clearIfRunning();
                }
            }
            if (status < 400) {
                lastUploadSuccessMs = System.currentTimeMillis();
                lastSnapshotHash = snapshotHash;
                dirty.set(false);
            }
        } catch (IOException | RuntimeException ex) {
            // Only a refusal ends the link. Anything else waits for the next sync.
        }
    }

    private int computeSnapshotHash(StatsSummary summary, List<StatsItem> items) {
        int hash = Objects.hash(summary.total_profit_gp, summary.total_cost_gp, summary.roi_percent,
            summary.gp_per_hour, summary.fill_count, summary.total_qty, summary.active_ms, summary.tax_paid_gp,
            summary.first_buy_ts_ms, summary.last_sell_ts_ms);
        List<StatsItem> sortedItems = new ArrayList<>();
        for (StatsItem item : items) {
            if (item != null && item.item_id > 0) {
                sortedItems.add(item);
            }
        }
        sortedItems.sort(Comparator.comparingInt(item -> item.item_id));
        for (StatsItem item : sortedItems) {
            hash = 31 * hash + Objects.hash(item.item_id, item.item_name, item.total_profit_gp, item.total_cost_gp,
                item.roi_percent, item.total_qty, item.fill_count, item.last_sell_ts_ms);
        }
        return hash;
    }
}
