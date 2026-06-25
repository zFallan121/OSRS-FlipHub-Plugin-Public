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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.runelite.api.GrandExchangeOfferState;

final class OfferEventBuildMathService {
    String determineEventType(OfferSnapshot prev, OfferSnapshot next) {
        if (prev == null) {
            if (next.state.equals(GrandExchangeOfferState.CANCELLED_BUY.name()) ||
                next.state.equals(GrandExchangeOfferState.CANCELLED_SELL.name())) {
                return "OFFER_ABORTED";
            }
            if (next.state.equals(GrandExchangeOfferState.BOUGHT.name()) ||
                next.state.equals(GrandExchangeOfferState.SOLD.name())) {
                return "OFFER_COMPLETED";
            }
            return "OFFER_PLACED";
        }

        if (!prev.state.equals(next.state)) {
            if (next.state.equals(GrandExchangeOfferState.CANCELLED_BUY.name()) ||
                next.state.equals(GrandExchangeOfferState.CANCELLED_SELL.name())) {
                return "OFFER_ABORTED";
            }
            if (next.state.equals(GrandExchangeOfferState.BOUGHT.name()) ||
                next.state.equals(GrandExchangeOfferState.SOLD.name())) {
                return "OFFER_COMPLETED";
            }
        }

        int deltaQty = Math.max(0, next.filledQty - prev.filledQty);
        long deltaGp = Math.max(0, next.spentGp - prev.spentGp);
        if (deltaQty > 0 || deltaGp > 0) {
            return "OFFER_UPDATED";
        }

        return null;
    }

    long computeDeltaGp(OfferSnapshot next, OfferSnapshot prev, int deltaQty) {
        long deltaGp = prev != null ? next.spentGp - prev.spentGp : next.spentGp;
        return normalizeDeltaGp(next, deltaQty, deltaGp);
    }

    long computeDeltaGpFromBaseline(OfferSnapshot next, long baselineSpentGp, int deltaQty) {
        long deltaGp = next.spentGp - baselineSpentGp;
        return normalizeDeltaGp(next, deltaQty, deltaGp);
    }

    void assignDeterministicCompletionEventId(GeEvent event, OfferEventBuildService.Input input, OfferSnapshot snapshot) {
        if (event == null || input == null || snapshot == null) {
            return;
        }
        if (!"OFFER_COMPLETED".equals(event.event_type)) {
            return;
        }
        if (input.stamp == null || input.stamp.firstSeenMs <= 0L) {
            return;
        }
        String key = input.stamp.firstSeenMs
            + "|" + snapshot.slot
            + "|" + snapshot.itemId
            + "|" + snapshot.isBuy
            + "|" + Math.max(0, snapshot.price)
            + "|" + Math.max(0, snapshot.totalQty)
            + "|" + Math.max(0, snapshot.filledQty)
            + "|" + Math.max(0L, snapshot.spentGp);
        event.event_id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private long normalizeDeltaGp(OfferSnapshot next, int deltaQty, long deltaGp) {
        if (deltaGp < 0) {
            deltaGp = 0;
        }
        if (deltaQty > 0 && !next.isBuy) {
            deltaGp = normalizeSellDeltaGp(deltaGp, deltaQty, next.price);
        }
        if (deltaQty > 0 && deltaGp <= 0) {
            long total = (long) next.price * (long) deltaQty;
            if (!next.isBuy) {
                long tax = computeSellTax(total, deltaQty, next.price);
                deltaGp = Math.max(0L, total - tax);
            } else {
                deltaGp = Math.max(0L, total);
            }
        }
        return deltaGp;
    }

    private long normalizeSellDeltaGp(long deltaGp, int deltaQty, int listedPrice) {
        long observedTotal = Math.max(0L, deltaGp);
        if (deltaQty <= 0 || observedTotal <= 0L) {
            return 0L;
        }
        long observedGrossNet = normalizeObservedGrossToNet(observedTotal, deltaQty);
        if (listedPrice <= 0) {
            return observedGrossNet;
        }
        long listedGross = (long) listedPrice * (long) deltaQty;
        long listedNet = Math.max(0L, listedGross - computeSellTax(listedGross, deltaQty, listedPrice));
        if (observedTotal == listedNet) {
            return listedNet;
        }
        if (observedTotal == listedGross) {
            return listedNet;
        }
        // RuneLite snapshots can surface either gross-like or net-like totals depending on offer transitions.
        // Choose the candidate that is closer to listed-net to avoid systematic +1/+N drift.
        long directDiff = Math.abs(observedTotal - listedNet);
        long observedDiff = Math.abs(observedGrossNet - listedNet);
        return observedDiff < directDiff ? observedGrossNet : observedTotal;
    }

    private long normalizeObservedGrossToNet(long observedTotal, int deltaQty) {
        if (observedTotal <= 0L || deltaQty <= 0) {
            return 0L;
        }
        long observedUnit = observedTotal / (long) deltaQty;
        if (observedUnit <= 0L) {
            return observedTotal;
        }
        long tax = (observedUnit / 50L) * (long) deltaQty;
        return Math.max(0L, observedTotal - tax);
    }

    long computeSellTax(long grossTotal, long qty, int listedPrice) {
        if (grossTotal <= 0L || qty <= 0L) {
            return 0L;
        }
        long taxByRate;
        if (listedPrice > 0) {
            taxByRate = ((long) listedPrice / 50L) * qty;
        } else {
            taxByRate = grossTotal / 50L;
        }
        long taxCap = qty * 5_000_000L;
        return Math.max(0L, Math.min(taxByRate, taxCap));
    }
}
