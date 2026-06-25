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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RecentTradeDeduper {
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final Map<Integer, RecentTradeEvent> recentTradeEventsBySlot = new ConcurrentHashMap<>();

    RecentTradeDeduper(long localEventBucketMs, long duplicateTradeWindowMs) {
        this.localEventBucketMs = Math.max(1L, localEventBucketMs);
        this.duplicateTradeWindowMs = Math.max(0L, duplicateTradeWindowMs);
    }

    void clearSlot(int slot) {
        recentTradeEventsBySlot.remove(slot);
    }

    void clearAll() {
        recentTradeEventsBySlot.clear();
    }

    boolean normalizeOrSuppress(GeEvent event) {
        if (event == null || event.slot < 0 || event.item_id <= 0) {
            return false;
        }
        long ts = event.ts_client_ms > 0 ? event.ts_client_ms : System.currentTimeMillis();
        String signature = buildEventTradeSignature(event);
        String tradeKey = buildEventTradeKey(event);
        RecentTradeEvent previous = recentTradeEventsBySlot.get(event.slot);
        if (previous != null && Math.abs(ts - previous.tsClientMs) <= duplicateTradeWindowMs) {
            String previousType = previous.eventType != null ? previous.eventType : "";
            String currentType = event.event_type != null ? event.event_type : "";
            boolean strictMatch = signature.equals(previous.signature);
            boolean completionPairMatch = tradeKey.equals(previous.tradeKey)
                && LocalTradeDeltaUtils.isCompletionUpdatePair(previousType, currentType);
            boolean repeatedCompletionMatch = tradeKey.equals(previous.tradeKey)
                && "OFFER_COMPLETED".equals(previousType)
                && "OFFER_COMPLETED".equals(currentType);
            if (strictMatch || completionPairMatch || repeatedCompletionMatch) {
                if ("OFFER_COMPLETED".equals(currentType) && "OFFER_UPDATED".equals(previousType)) {
                    event.delta_qty = 0;
                    event.delta_gp = 0L;
                } else {
                    return true;
                }
            }
        }
        recentTradeEventsBySlot.put(event.slot, new RecentTradeEvent(signature, tradeKey, event.event_type, ts));
        return false;
    }

    private String buildEventTradeSignature(GeEvent event) {
        long bucket = event.ts_client_ms / localEventBucketMs;
        long normalizedValue = normalizeEventDeltaValue(event);
        return bucket + "|" + event.slot + "|" + event.item_id + "|" + event.is_buy + "|" + event.delta_qty
            + "|" + event.price + "|" + normalizedValue;
    }

    private String buildEventTradeKey(GeEvent event) {
        return event.slot + "|" + event.item_id + "|" + event.is_buy + "|" + event.price;
    }

    private long normalizeEventDeltaValue(GeEvent event) {
        int qty = event.delta_qty;
        if (qty > 0 && event.price > 0) {
            long total = (long) event.price * (long) qty;
            if (!event.is_buy) {
                long tax = ((long) event.price / 50L) * qty;
                long taxCap = (long) qty * 5_000_000L;
                tax = Math.max(0L, Math.min(tax, taxCap));
                return Math.max(0L, total - tax);
            }
            return Math.max(0L, total);
        }
        return Math.max(0L, event.delta_gp);
    }
}
