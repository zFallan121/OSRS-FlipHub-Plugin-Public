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

import java.util.List;

final class GeHistoryAutoSyncFactoryService {
    interface RuntimeHooks {
        void ensureProfileLoaded(long accountKey);
        void ensureLocalSessionStart(long accountKey, long tsClientMs);
        List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey);
        void cacheItemName(int itemId);
        void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta);
        void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta);
        Integer resolveWorld();
        void enqueueUploadEvent(GeEvent event);
        void requestEventFlush();
        void persistLocalTrades(long accountKey);
        void triggerStatsRefresh();
        void triggerPanelRefresh();
        long nowMs();
    }

    private final BackfillUploader backfillUploader;

    GeHistoryAutoSyncFactoryService(BackfillUploader backfillUploader) {
        this.backfillUploader = backfillUploader;
    }

    GeHistoryAutoSyncService create(long accountwideKey, RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new GeHistoryAutoSyncService(accountwideKey, null);
        }
        return new GeHistoryAutoSyncService(
            accountwideKey,
            new GeHistoryAutoSyncService.Hooks() {
                @Override
                public void ensureProfileLoaded(long accountKey) {
                    runtimeHooks.ensureProfileLoaded(accountKey);
                }

                @Override
                public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
                    runtimeHooks.ensureLocalSessionStart(accountKey, tsClientMs);
                }

                @Override
                public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
                    return runtimeHooks.snapshotLocalTradeDeltas(accountKey);
                }

                @Override
                public void cacheItemName(int itemId) {
                    runtimeHooks.cacheItemName(itemId);
                }

                @Override
                public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
                    runtimeHooks.appendTradeDeltaPair(accountKey, accountwideKey, delta);
                }

                @Override
                public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
                    runtimeHooks.applyDeltaToStatsCache(accountKey, delta);
                }

                @Override
                public GeEvent buildUploadEvent(long profileKey, LocalTradeDelta delta) {
                    if (backfillUploader == null || delta == null) {
                        return null;
                    }
                    return backfillUploader.buildBackfillEvent(profileKey, delta, runtimeHooks.resolveWorld());
                }

                @Override
                public void enqueueUploadEvent(GeEvent event) {
                    if (event != null) {
                        runtimeHooks.enqueueUploadEvent(event);
                    }
                }

                @Override
                public void requestEventFlush() {
                    runtimeHooks.requestEventFlush();
                }

                @Override
                public void persistLocalTrades(long accountKey) {
                    runtimeHooks.persistLocalTrades(accountKey);
                }

                @Override
                public void triggerStatsRefresh() {
                    runtimeHooks.triggerStatsRefresh();
                }

                @Override
                public void triggerPanelRefresh() {
                    runtimeHooks.triggerPanelRefresh();
                }

                @Override
                public long nowMs() {
                    return runtimeHooks.nowMs();
                }
            }
        );
    }
}
