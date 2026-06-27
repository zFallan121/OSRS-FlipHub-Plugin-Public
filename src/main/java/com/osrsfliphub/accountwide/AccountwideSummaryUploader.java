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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class AccountwideSummaryUploader {
    interface Hooks {
        boolean isClientFullyReady();
        LocalStatsSnapshot buildAccountwideSnapshot();
        boolean attemptRefresh(String currentToken);
        void clearSession();
    }

    private final long minUploadIntervalMs;
    private final long resyncIntervalMs;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private volatile long lastUploadAttemptMs;
    private volatile long lastUploadSuccessMs;
    private volatile int lastSnapshotHash = Integer.MIN_VALUE;

    @Inject
    AccountwideSummaryUploader() {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_UPLOAD_MIN_INTERVAL_MS,
            GeLifecyclePluginConstants.ACCOUNTWIDE_UPLOAD_RESYNC_INTERVAL_MS);
    }

    AccountwideSummaryUploader(long minUploadIntervalMs, long resyncIntervalMs) {
        this.minUploadIntervalMs = Math.max(0L, minUploadIntervalMs);
        this.resyncIntervalMs = Math.max(0L, resyncIntervalMs);
    }

    void markDirty() {
        dirty.set(true);
    }

    void resetUploadSnapshot() {
        lastSnapshotHash = Integer.MIN_VALUE;
        lastUploadSuccessMs = 0L;
    }

    boolean isDirty() {
        return dirty.get();
    }

    void syncIfNeeded(ApiClient apiClient, PluginConfig config, Hooks hooks) {
        if (hooks == null || apiClient == null || config == null || !hooks.isClientFullyReady()) {
            return;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        if (!ApiStatusPolicy.hasCredentials(sessionToken, signingSecret)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastUploadAttemptMs < minUploadIntervalMs) {
            return;
        }

        boolean wasDirty = dirty.get();
        boolean shouldResync = lastSnapshotHash == Integer.MIN_VALUE
            || (nowMs - lastUploadSuccessMs) >= resyncIntervalMs;
        if (!wasDirty && !shouldResync) {
            return;
        }

        LocalStatsSnapshot snapshot = hooks.buildAccountwideSnapshot();
        StatsSummary summary = snapshot != null && snapshot.summary != null ? snapshot.summary : new StatsSummary();
        List<StatsItem> items = snapshot != null ? snapshot.items : null;
        if (items == null) {
            items = new ArrayList<>();
        }
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
                boolean refreshed = hooks.attemptRefresh(sessionToken);
                if (refreshed) {
                    String refreshedToken = config.sessionToken();
                    String refreshedSecret = config.signingSecret();
                    if (ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                        status = apiClient.sendAccountwideSummary(refreshedToken, refreshedSecret, summary, items);
                        if (status < 400) {
                            lastUploadSuccessMs = System.currentTimeMillis();
                            lastSnapshotHash = snapshotHash;
                            dirty.set(false);
                            return;
                        }
                        if (ApiStatusPolicy.isAuthStatus(status)) {
                            hooks.clearSession();
                        }
                    }
                } else {
                    hooks.clearSession();
                }
                if (wasDirty) {
                    markDirty();
                }
                return;
            }
            if (status < 400) {
                lastUploadSuccessMs = System.currentTimeMillis();
                lastSnapshotHash = snapshotHash;
                dirty.set(false);
                return;
            }
            if (wasDirty) {
                markDirty();
            }
        } catch (IOException | RuntimeException ex) {
            if (wasDirty) {
                markDirty();
            }
        }
    }

    private int computeSnapshotHash(StatsSummary summary, List<StatsItem> items) {
        int hash = computeSummaryHash(summary);
        if (items == null || items.isEmpty()) {
            return hash;
        }

        List<StatsItem> sortedItems = new ArrayList<>();
        for (StatsItem item : items) {
            if (item != null && item.item_id > 0) {
                sortedItems.add(item);
            }
        }
        sortedItems.sort(Comparator.comparingInt(item -> item.item_id));

        for (StatsItem item : sortedItems) {
            hash = 31 * hash + Integer.hashCode(item.item_id);
            hash = 31 * hash + hashString(item.item_name);
            hash = 31 * hash + Long.hashCode(item.total_profit_gp != null ? item.total_profit_gp : 0L);
            hash = 31 * hash + Long.hashCode(item.total_cost_gp != null ? item.total_cost_gp : 0L);
            hash = 31 * hash + Double.hashCode(item.roi_percent != null ? item.roi_percent : 0.0);
            hash = 31 * hash + Integer.hashCode(item.total_qty != null ? item.total_qty : 0);
            hash = 31 * hash + Integer.hashCode(item.fill_count != null ? item.fill_count : 0);
            hash = 31 * hash + Long.hashCode(item.last_sell_ts_ms != null ? item.last_sell_ts_ms : 0L);
        }
        return hash;
    }

    private int computeSummaryHash(StatsSummary summary) {
        int hash = 17;
        hash = 31 * hash + Long.hashCode(summary.total_profit_gp != null ? summary.total_profit_gp : 0L);
        hash = 31 * hash + Long.hashCode(summary.total_cost_gp != null ? summary.total_cost_gp : 0L);
        hash = 31 * hash + Double.hashCode(summary.roi_percent != null ? summary.roi_percent : 0.0);
        hash = 31 * hash + Double.hashCode(summary.gp_per_hour != null ? summary.gp_per_hour : 0.0);
        hash = 31 * hash + Integer.hashCode(summary.fill_count != null ? summary.fill_count : 0);
        hash = 31 * hash + Long.hashCode(summary.total_qty != null ? summary.total_qty : 0L);
        hash = 31 * hash + Long.hashCode(summary.active_ms != null ? summary.active_ms : 0L);
        hash = 31 * hash + Long.hashCode(summary.tax_paid_gp != null ? summary.tax_paid_gp : 0L);
        hash = 31 * hash + Long.hashCode(summary.first_buy_ts_ms != null ? summary.first_buy_ts_ms : 0L);
        hash = 31 * hash + Long.hashCode(summary.last_sell_ts_ms != null ? summary.last_sell_ts_ms : 0L);
        return hash;
    }

    private int hashString(String value) {
        return value != null ? value.hashCode() : 0;
    }
}
