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

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

@Singleton
final class AccountwideProfileBackfillService {
    private final int maxBatchSize = GeLifecyclePluginConstants.MAX_BATCH_SIZE;
    private final int maxLocalTrades = GeLifecyclePluginConstants.MAX_LOCAL_TRADES;
    private final long localEventBucketMs = GeLifecyclePluginConstants.LOCAL_EVENT_BUCKET_MS;
    private final long duplicateTradeWindowMs = GeLifecyclePluginConstants.DUPLICATE_TRADE_WINDOW_MS;
    private final Client client;

    @Inject
    AccountwideProfileBackfillService(Client client) {
        this.client = client;
    }

    private List<LocalTradeDelta> snapshotLocalTrades(long profileKey) {
        LocalTradeSessionFacadeService service = PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        return service != null ? service.snapshotLocalTradeDeltas(profileKey) : null;
    }

    boolean backfillProfileTrades(long profileKey, ApiClient apiClient, PluginConfig config, BackfillUploader uploader) {
        if (profileKey <= 0 || apiClient == null || config == null || uploader == null) {
            return false;
        }
        List<LocalTradeDelta> source = snapshotLocalTrades(profileKey);
        List<LocalTradeDelta> snapshot = source != null ? new ArrayList<>(source) : new ArrayList<>();
        snapshot = LocalTradeDeltaUtils.dedupeLocalTrades(
            snapshot,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
        if (snapshot == null || snapshot.isEmpty()) {
            return true;
        }

        Integer world = client != null ? (Integer) client.getWorld() : null;
        List<GeEvent> currentBatch = new ArrayList<>(maxBatchSize);
        for (LocalTradeDelta delta : snapshot) {
            GeEvent event = uploader.buildBackfillEvent(profileKey, delta, world);
            if (event == null) {
                continue;
            }
            currentBatch.add(event);
            if (currentBatch.size() >= maxBatchSize) {
                if (!uploader.sendBatch(apiClient, config, currentBatch)) {
                    return false;
                }
                currentBatch.clear();
            }
        }
        if (!currentBatch.isEmpty()) {
            return uploader.sendBatch(apiClient, config, currentBatch);
        }
        return true;
    }
}
