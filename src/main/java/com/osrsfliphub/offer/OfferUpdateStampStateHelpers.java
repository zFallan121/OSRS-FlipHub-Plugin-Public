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

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

final class OfferUpdateStampStateHelpers {
    private OfferUpdateStampStateHelpers() {
    }

    static boolean hasOfferChanged(OfferSnapshot prev, OfferSnapshot next) {
        if (prev == null || next == null) {
            return true;
        }
        return prev.itemId != next.itemId
            || prev.price != next.price
            || prev.totalQty != next.totalQty
            || prev.filledQty != next.filledQty
            || prev.spentGp != next.spentGp
            || !prev.state.equals(next.state);
    }

    static boolean isEmptySnapshot(OfferSnapshot snapshot) {
        return snapshot != null
            && (snapshot.itemId <= 0 || GrandExchangeOfferState.EMPTY.name().equals(snapshot.state));
    }

    static boolean shouldRefreshOfferTimestamp(OfferSnapshot prev, OfferSnapshot next) {
        if (prev == null || next == null) {
            return false;
        }
        if (prev.itemId <= 0 || GrandExchangeOfferState.EMPTY.name().equals(prev.state)) {
            return false;
        }
        if (next.filledQty > prev.filledQty) {
            return true;
        }
        if (next.spentGp > prev.spentGp) {
            return true;
        }
        if (!prev.state.equals(next.state)) {
            return next.state.equals(GrandExchangeOfferState.BOUGHT.name())
                || next.state.equals(GrandExchangeOfferState.SOLD.name());
        }
        return false;
    }

    static boolean shouldRefreshOfferTimestamp(
        OfferSnapshot prev,
        OfferSnapshot next,
        OfferUpdateStamp stamp,
        int filledBefore,
        long spentBefore,
        BooleanSupplier isWithinLoginGraceSupplier
    ) {
        if (!shouldRefreshOfferTimestamp(prev, next)) {
            return false;
        }
        if (stamp == null) {
            return true;
        }

        boolean withinLoginGrace = isWithinLoginGraceSupplier != null && isWithinLoginGraceSupplier.getAsBoolean();
        // Prevent login-time offer reconciliation from resetting timers when the "new" progress was already
        // captured in the persisted stamp from a previous session.
        if (withinLoginGrace) {
            boolean progressedSinceStamp = next.filledQty > filledBefore || next.spentGp > spentBefore;
            if (!progressedSinceStamp) {
                boolean progressedSincePrev = next.filledQty > prev.filledQty || next.spentGp > prev.spentGp;
                if (progressedSincePrev) {
                    return false;
                }

                boolean terminalTransition = !prev.state.equals(next.state)
                    && (next.state.equals(GrandExchangeOfferState.BOUGHT.name())
                        || next.state.equals(GrandExchangeOfferState.SOLD.name()));
                if (terminalTransition && stamp.completedMs > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean markCompletedIfNeeded(OfferUpdateStamp stamp, OfferSnapshot snapshot, LongSupplier nowMsSupplier) {
        if (stamp == null || snapshot == null) {
            return false;
        }
        if (!isOfferComplete(snapshot)) {
            return false;
        }
        if (stamp.completedMs > 0) {
            return false;
        }
        long now = nowMs(nowMsSupplier);
        stamp.completedMs = now;
        if (stamp.firstSeenMs <= 0) {
            stamp.firstSeenMs = stamp.lastUpdateMs > 0 ? stamp.lastUpdateMs : now;
        }
        return true;
    }

    static boolean markCompletedIfNeeded(OfferUpdateStamp stamp, GrandExchangeOffer offer, LongSupplier nowMsSupplier) {
        if (stamp == null || offer == null) {
            return false;
        }
        if (!isOfferComplete(offer)) {
            return false;
        }
        if (stamp.completedMs > 0) {
            return false;
        }
        long now = nowMs(nowMsSupplier);
        stamp.completedMs = now;
        if (stamp.firstSeenMs <= 0) {
            stamp.firstSeenMs = stamp.lastUpdateMs > 0 ? stamp.lastUpdateMs : now;
        }
        return true;
    }

    static boolean isOfferComplete(OfferSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        boolean terminal = GrandExchangeOfferState.BOUGHT.name().equals(snapshot.state)
            || GrandExchangeOfferState.SOLD.name().equals(snapshot.state);
        if (!terminal) {
            return false;
        }
        return snapshot.totalQty <= 0 || snapshot.filledQty >= snapshot.totalQty;
    }

    static boolean isOfferComplete(GrandExchangeOffer offer) {
        if (offer == null) {
            return false;
        }
        GrandExchangeOfferState state = offer.getState();
        boolean terminal = state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD;
        if (!terminal) {
            return false;
        }
        return offer.getTotalQuantity() <= 0 || offer.getQuantitySold() >= offer.getTotalQuantity();
    }

    static long computeCompletedDisplayTimestamp(OfferUpdateStamp stamp, LongSupplier nowMsSupplier) {
        if (stamp == null) {
            return -1;
        }
        long now = nowMs(nowMsSupplier);
        long completionMs = stamp.completedMs > 0 ? stamp.completedMs
            : (stamp.lastUpdateMs > 0 ? stamp.lastUpdateMs : now);
        long startMs = stamp.firstSeenMs > 0 ? stamp.firstSeenMs : completionMs;
        long duration = Math.max(0L, completionMs - startMs);
        return now - duration;
    }

    static long minPositive(long a, long b, long c) {
        long min = 0L;
        if (a > 0) {
            min = a;
        }
        if (b > 0 && (min == 0L || b < min)) {
            min = b;
        }
        if (c > 0 && (min == 0L || c < min)) {
            min = c;
        }
        return min;
    }

    static boolean shouldClearOfferStamp(OfferSnapshot prev, BooleanSupplier isWithinLoginGraceSupplier) {
        if (prev == null) {
            return false;
        }
        if (prev.itemId <= 0) {
            return false;
        }
        boolean isCancelled = GrandExchangeOfferState.CANCELLED_BUY.name().equals(prev.state)
            || GrandExchangeOfferState.CANCELLED_SELL.name().equals(prev.state);
        boolean isCompleted = GrandExchangeOfferState.BOUGHT.name().equals(prev.state)
            || GrandExchangeOfferState.SOLD.name().equals(prev.state);
        if (!isCancelled && !isCompleted) {
            return false;
        }

        // Some login/logout transitions transiently report terminal states even for partially-filled offers.
        // Preserve these stamps only during login reconciliation to avoid stale timers during normal runtime.
        boolean withinLoginGrace = isWithinLoginGraceSupplier != null && isWithinLoginGraceSupplier.getAsBoolean();
        if (withinLoginGrace
            && prev.totalQty > 0
            && prev.filledQty > 0
            && prev.filledQty < prev.totalQty) {
            return false;
        }
        return true;
    }

    static boolean isBuyOffer(GrandExchangeOffer offer) {
        if (offer == null) {
            return false;
        }
        GrandExchangeOfferState state = offer.getState();
        return state == GrandExchangeOfferState.BUYING
            || state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private static long nowMs(LongSupplier nowMsSupplier) {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }
}
