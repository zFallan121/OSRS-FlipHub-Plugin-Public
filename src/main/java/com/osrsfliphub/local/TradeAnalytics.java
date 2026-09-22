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

import java.util.*;
import javax.inject.*;

@Singleton
final class TradeAnalytics {
    private static final long LOCAL_LIMIT_FUTURE_TOLERANCE_MS = 5L * 60L * 1000L;

    private final long limitWindowMs;
    private final long futureToleranceMs;
    private final long localEventBucketMs;

    @Inject
    TradeAnalytics() {
        this(Const.LOCAL_LIMIT_WINDOW_MS,
            LOCAL_LIMIT_FUTURE_TOLERANCE_MS,
            Const.LOCAL_EVENT_BUCKET_MS);
    }

    TradeAnalytics(long limitWindowMs, long futureToleranceMs, long localEventBucketMs) {
        this.limitWindowMs = Math.max(0L, limitWindowMs);
        this.futureToleranceMs = Math.max(0L, futureToleranceMs);
        this.localEventBucketMs = Math.max(1L, localEventBucketMs);
    }

    Map<Integer, TradeInfo> buildLocalTradeInfo(List<Delta> snapshot) {
        Map<Integer, TradeInfo> infoMap = new HashMap<>();
        if (snapshot == null || snapshot.isEmpty()) {
            return infoMap;
        }
        for (Delta delta : snapshot) {
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
            TradeInfo info = infoMap.computeIfAbsent(delta.itemId, TradeInfo::new);
            if (delta.isBuy) {
                if (info.lastBuyTs == null || delta.tsClientMs >= info.lastBuyTs) {
                    info.lastBuyTs = delta.tsClientMs;
                    info.lastBuyPrice = delta.price;
                }
            } else {
                // A sale is booked when it ended; the latest to end is the latest sale.
                long soldAtMs = delta.closedAtMs();
                if (info.lastSellTs == null || soldAtMs >= info.lastSellTs) {
                    info.lastSellTs = soldAtMs;
                    info.lastSellPrice = delta.price;
                }
            }
        }
        return infoMap;
    }

    /**
     * How much of each item's four-hour buy limit is spoken for.
     *
     * <p>The game opens the window at the first purchase made once the previous one has run
     * out, and closes it four hours later, so buys sitting in an expired window no longer
     * count at all. Measuring a plain four hours back from now instead would keep charging a
     * purchase made five hours ago against a window that has already reset.
     */
    Map<Integer, LimitInfo> buildLocalLimitInfo(List<Delta> snapshot, long nowMs) {
        Map<Integer, LimitInfo> infoMap = new HashMap<>();
        if (snapshot == null || snapshot.isEmpty()) {
            return infoMap;
        }
        Map<Integer, List<Delta>> buysByItem = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (Delta delta : snapshot) {
            if (!countsTowardBuyLimit(delta, nowMs)) {
                continue;
            }
            String signature = TradeDeltaUtils.buildLimitTradeSignature(delta, localEventBucketMs);
            if (!seen.add(signature)) {
                continue;
            }
            buysByItem.computeIfAbsent(delta.itemId, key -> new ArrayList<>()).add(delta);
        }

        for (Map.Entry<Integer, List<Delta>> entry : buysByItem.entrySet()) {
            List<Delta> buys = entry.getValue();
            buys.sort(Comparator.comparingLong(buy -> buy.tsClientMs));
            long windowStartMs = -1L;
            long windowQty = 0L;
            for (Delta buy : buys) {
                if (windowStartMs < 0L || buy.tsClientMs >= windowStartMs + limitWindowMs) {
                    windowStartMs = buy.tsClientMs;
                    windowQty = 0L;
                }
                windowQty += buy.deltaQty;
            }
            // The last window this item opened has already run its four hours, so the whole
            // limit is available again and there is nothing to report.
            if (windowStartMs < 0L || nowMs >= windowStartMs + limitWindowMs) {
                continue;
            }
            LimitInfo info = infoMap.computeIfAbsent(entry.getKey(), LimitInfo::new);
            info.buyQty += windowQty;
            info.firstBuyTs = windowStartMs;
        }
        return infoMap;
    }

    /**
     * Whether a delta is a purchase that can be placed in a buy-limit window. Trades replayed
     * from the in-game history are not: their timestamps were invented when they were imported,
     * so they say nothing about when the limit was actually spent.
     */
    private boolean countsTowardBuyLimit(Delta delta, long nowMs) {
        if (delta == null || !delta.isBuy || delta.deltaQty <= 0 || delta.baselineSynthetic) {
            return false;
        }
        if (delta.slot >= Const.GE_HISTORY_SYNTHETIC_SLOT_START) {
            return false;
        }
        return delta.tsClientMs > 0 && delta.tsClientMs <= nowMs + futureToleranceMs;
    }

    List<Delta> copySnapshot(List<Delta> deltas) {
        return deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
    }
}
