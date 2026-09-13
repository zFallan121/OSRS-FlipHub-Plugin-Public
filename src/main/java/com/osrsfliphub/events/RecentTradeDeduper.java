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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class RecentTradeDeduper {
    private final long duplicateTradeWindowMs;
    private final Map<Integer, RecentTradeEvent> recentTradeEventsBySlot = new ConcurrentHashMap<>();

    @Inject
    RecentTradeDeduper() {
        this(Const.DUPLICATE_TRADE_WINDOW_MS);
    }

    RecentTradeDeduper(long duplicateTradeWindowMs) {
        this.duplicateTradeWindowMs = Math.max(0L, duplicateTradeWindowMs);
    }

    void clearSlot(int slot) {
        recentTradeEventsBySlot.remove(slot);
    }

    void clearAll() {
        recentTradeEventsBySlot.clear();
    }

    /**
     * A repeat is the same offer reporting the same cumulative fill again within the window.
     * Two chunks of one offer share slot, item, side and price and can even be the same size,
     * so only the cumulative {@code filled_qty}/{@code spent_gp} tells a repeat from a new fill.
     */
    boolean normalizeOrSuppress(GeEvent event) {
        if (event == null || event.slot < 0 || event.item_id <= 0) {
            return false;
        }
        long ts = event.ts_client_ms > 0 ? event.ts_client_ms : System.currentTimeMillis();
        String tradeKey = event.slot + "|" + event.item_id + "|" + event.is_buy + "|" + event.price;
        RecentTradeEvent previous = recentTradeEventsBySlot.get(event.slot);
        if (previous != null
            && Math.abs(ts - previous.tsClientMs) <= duplicateTradeWindowMs
            && tradeKey.equals(previous.tradeKey)
            && event.filled_qty == previous.filledQty
            && event.spent_gp == previous.spentGp) {
            String previousType = previous.eventType != null ? previous.eventType : "";
            String currentType = event.event_type != null ? event.event_type : "";
            if ("OFFER_COMPLETED".equals(currentType) && "OFFER_UPDATED".equals(previousType)) {
                // The state change is new information; the quantity and coins were already reported.
                event.delta_qty = 0;
                event.delta_gp = 0L;
            } else if (currentType.equals(previousType)) {
                return true;
            }
        }
        recentTradeEventsBySlot.put(event.slot,
            new RecentTradeEvent(tradeKey, event.event_type, event.filled_qty, event.spent_gp, ts));
        return false;
    }

}
