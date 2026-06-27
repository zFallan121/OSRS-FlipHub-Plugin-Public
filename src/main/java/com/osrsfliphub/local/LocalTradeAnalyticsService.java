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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalTradeAnalyticsService {
    private final long limitWindowMs;
    private final long futureToleranceMs;
    private final long localEventBucketMs;

    @Inject
    LocalTradeAnalyticsService() {
        this(GeLifecyclePluginConstants.LOCAL_LIMIT_WINDOW_MS,
            GeLifecyclePluginConstants.LOCAL_LIMIT_FUTURE_TOLERANCE_MS,
            GeLifecyclePluginConstants.LOCAL_EVENT_BUCKET_MS);
    }

    LocalTradeAnalyticsService(long limitWindowMs, long futureToleranceMs, long localEventBucketMs) {
        this.limitWindowMs = Math.max(0L, limitWindowMs);
        this.futureToleranceMs = Math.max(0L, futureToleranceMs);
        this.localEventBucketMs = Math.max(1L, localEventBucketMs);
    }

    Map<Integer, LocalTradeInfo> buildLocalTradeInfo(List<LocalTradeDelta> snapshot) {
        Map<Integer, LocalTradeInfo> infoMap = new HashMap<>();
        if (snapshot == null || snapshot.isEmpty()) {
            return infoMap;
        }
        for (LocalTradeDelta delta : snapshot) {
            if (delta == null || delta.itemId <= 0) {
                continue;
            }
            boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
            if (delta.deltaQty <= 0 && !isCompletion) {
                continue;
            }
            if (delta.price <= 0) {
                continue;
            }
            LocalTradeInfo info = infoMap.computeIfAbsent(delta.itemId, LocalTradeInfo::new);
            if (delta.isBuy) {
                if (info.lastBuyTs == null || delta.tsClientMs >= info.lastBuyTs) {
                    info.lastBuyTs = delta.tsClientMs;
                    info.lastBuyPrice = delta.price;
                }
            } else {
                if (info.lastSellTs == null || delta.tsClientMs >= info.lastSellTs) {
                    info.lastSellTs = delta.tsClientMs;
                    info.lastSellPrice = delta.price;
                }
            }
        }
        return infoMap;
    }

    Map<Integer, LocalLimitInfo> buildLocalLimitInfo(List<LocalTradeDelta> snapshot, long nowMs) {
        Map<Integer, LocalLimitInfo> infoMap = new HashMap<>();
        if (snapshot == null || snapshot.isEmpty()) {
            return infoMap;
        }
        long windowStart = nowMs - limitWindowMs;
        Set<String> seen = new HashSet<>();
        for (LocalTradeDelta delta : snapshot) {
            if (delta == null || !delta.isBuy || delta.deltaQty <= 0 || delta.baselineSynthetic) {
                continue;
            }
            if (delta.tsClientMs <= 0 || delta.tsClientMs < windowStart) {
                continue;
            }
            if (delta.tsClientMs > nowMs + futureToleranceMs) {
                continue;
            }
            String signature = LocalTradeDeltaUtils.buildLimitTradeSignature(delta, localEventBucketMs);
            if (!seen.add(signature)) {
                continue;
            }
            LocalLimitInfo info = infoMap.computeIfAbsent(delta.itemId, LocalLimitInfo::new);
            info.buyQty += delta.deltaQty;
            if (info.firstBuyTs == null || delta.tsClientMs < info.firstBuyTs) {
                info.firstBuyTs = delta.tsClientMs;
            }
        }
        return infoMap;
    }

    boolean hasRecentLocalBuy(List<LocalTradeDelta> snapshot, int itemId, long nowMs) {
        if (snapshot == null || snapshot.isEmpty() || itemId <= 0) {
            return false;
        }
        long windowStart = nowMs - limitWindowMs;
        for (LocalTradeDelta delta : snapshot) {
            if (delta == null || !delta.isBuy || delta.deltaQty <= 0) {
                continue;
            }
            if (delta.itemId != itemId) {
                continue;
            }
            if (delta.tsClientMs <= 0 || delta.tsClientMs < windowStart) {
                continue;
            }
            if (delta.tsClientMs > nowMs + futureToleranceMs) {
                continue;
            }
            return true;
        }
        return false;
    }

    List<LocalTradeDelta> copySnapshot(List<LocalTradeDelta> deltas) {
        return deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
    }
}
