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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOfferState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Singleton
final class OfferEventBuild {
    @RequiredArgsConstructor
    static final class Input {
        final OfferSnapshot prev;
        final OfferSnapshot next;
        final Stamp stamp;
        final boolean unlinked;
        final boolean localTradesLoadedThisLogin;
        final long lastLoginMs;
        final int world;
    }

    @RequiredArgsConstructor
    static final class Result {
        @Getter
        private final GeEvent event;
        @Getter
        private final boolean baselineSynthetic;
        private final boolean clearRecentSlot;
        private final boolean ignore;
        private final boolean shouldScheduleRefresh;

        static Result ignore(boolean clearRecentSlot) {
            return new Result(null, false, clearRecentSlot, true, false);
        }

        static Result accepted(GeEvent event,
                               boolean baselineSynthetic,
                               boolean clearRecentSlot,
                               boolean shouldScheduleRefresh) {
            return new Result(event, baselineSynthetic, clearRecentSlot, false, shouldScheduleRefresh);
        }

        boolean shouldClearRecentSlot() {
            return clearRecentSlot;
        }

        boolean shouldIgnore() {
            return ignore;
        }

        boolean shouldScheduleRefresh() {
            return shouldScheduleRefresh;
        }
    }

    private final OfferUpdateStamp stampService;
    private final OfferEventBuildMath mathService = new OfferEventBuildMath();

    @Inject
    OfferEventBuild(OfferUpdateStamp stampService) {
        this.stampService = stampService;
    }

    Result derive(Input input) {
        if (input == null || input.next == null) {
            return Result.ignore(false);
        }
        OfferSnapshot prev = input.prev;
        OfferSnapshot next = input.next;
        boolean nextIsEmpty = GrandExchangeOfferState.EMPTY.name().equals(next.state);
        boolean prevWasEmpty = prev == null
            || prev.itemId <= 0
            || GrandExchangeOfferState.EMPTY.name().equals(prev.state);
        if (nextIsEmpty && prevWasEmpty) {
            return Result.ignore(true);
        }

        boolean prevIsBaseline = prev == null
            || prev.itemId <= 0
            || GrandExchangeOfferState.EMPTY.name().equals(prev.state)
            || isSlotReused(prev, next);
        // A baseline prev holds no progress for this offer, so progress and event type are
        // derived as if nothing had been seen before (an EMPTY prev is the same as none).
        OfferSnapshot progressBase = prevIsBaseline ? null : prev;
        boolean usedBaseline = false;
        int deltaQty;
        long deltaGp;
        if (prevIsBaseline && input.stamp != null && (stampService != null && stampService.stampMatchesSnapshot(input.stamp, next))) {
            usedBaseline = true;
            deltaQty = Math.max(0, next.filledQty - input.stamp.filledQty);
            deltaGp = mathService.computeDeltaGpFromBaseline(next, input.stamp.spentGp, deltaQty);
            if (deltaQty == 0 && deltaGp == 0) {
                return Result.ignore(nextIsEmpty);
            }
        } else {
            deltaQty = progressBase != null
                ? Math.max(0, next.filledQty - progressBase.filledQty)
                : Math.max(0, next.filledQty);
            deltaGp = mathService.computeDeltaGp(next, progressBase, deltaQty);
        }

        String eventType = mathService.determineEventType(progressBase, next);
        OfferSnapshot eventSnapshot = next;
        if (eventType == null) {
            boolean prevCompletedState = prev != null
                && (GrandExchangeOfferState.BOUGHT.name().equals(prev.state)
                    || GrandExchangeOfferState.SOLD.name().equals(prev.state));
            boolean prevLikelyCompletedBeforeEmpty = prev != null
                && (GrandExchangeOfferState.BUYING.name().equals(prev.state)
                    || GrandExchangeOfferState.SELLING.name().equals(prev.state))
                && prev.totalQty > 0
                && prev.filledQty >= prev.totalQty;
            if (prev != null && nextIsEmpty && (prevCompletedState || prevLikelyCompletedBeforeEmpty)) {
                eventType = "OFFER_COMPLETED";
                eventSnapshot = prev;
            } else {
                return Result.ignore(nextIsEmpty);
            }
        }
        if (usedBaseline && "OFFER_PLACED".equals(eventType)) {
            eventType = "OFFER_UPDATED";
        }

        if ("OFFER_COMPLETED".equals(eventType) && deltaQty == 0) {
            int prevFilled = progressBase != null ? Math.max(0, progressBase.filledQty) : 0;
            int remaining = 0;
            if (eventSnapshot.totalQty > 0) {
                remaining = Math.max(0, eventSnapshot.totalQty - prevFilled);
            }
            // Only fall back to full filled quantity when no prior progress snapshot exists.
            if (remaining == 0 && prevFilled <= 0 && eventSnapshot.filledQty > 0) {
                remaining = eventSnapshot.filledQty;
            }
            if (remaining > 0) {
                deltaQty = remaining;
                if (deltaGp == 0) {
                    long total = eventSnapshot.spentGp > 0 ? eventSnapshot.spentGp
                        : (long) eventSnapshot.price * (long) deltaQty;
                    if (!eventSnapshot.isBuy) {
                        long tax = mathService.computeSellTax(eventSnapshot.itemId, total, deltaQty, eventSnapshot.price);
                        deltaGp = Math.max(0L, total - tax);
                    } else {
                        deltaGp = Math.max(0L, total);
                    }
                }
            }
        }

        // A fill level is evidence of new progress only when there is a stored position to
        // measure it against. Without one, this event says where the offer stands now, not what
        // happened since it was last seen, and counting it would re-count fills already
        // recorded in an earlier session. The caller has already advanced the stamp to this
        // snapshot, so the position is adopted here and the next fill measures from it.
        //
        // Guessing is the only thing given up. A stored position still yields its offline
        // progress, an offer placed from scratch is still reported, and a trade skipped here is
        // recoverable from the in-game Grand Exchange history, which the plugin imports.
        if (prevIsBaseline && !usedBaseline && (deltaQty > 0 || deltaGp > 0)) {
            return Result.ignore(nextIsEmpty);
        }

        GeEvent geEvent = GeEvent.createBase(eventSnapshot, prev, eventType);
        mathService.assignDeterministicCompletionEventId(geEvent, input, eventSnapshot);
        geEvent.world = input.world;
        geEvent.delta_qty = deltaQty;
        geEvent.delta_gp = deltaGp;
        boolean shouldScheduleRefresh = geEvent.delta_qty > 0 || "OFFER_COMPLETED".equals(eventType);
        return Result.accepted(geEvent, false, nextIsEmpty, shouldScheduleRefresh);
    }

    /**
     * The slot now holds a different offer than the one last seen, with no EMPTY observed in
     * between (a missed event), so {@code prev}'s progress does not belong to {@code next}.
     * Identity is item, side, price and total; a zero price or total is metadata the client
     * has not reported yet, not a change.
     */
    private static boolean isSlotReused(OfferSnapshot prev, OfferSnapshot next) {
        if (next.itemId <= 0 || GrandExchangeOfferState.EMPTY.name().equals(next.state)) {
            return false;
        }
        if (prev.itemId != next.itemId || prev.isBuy != next.isBuy) {
            return true;
        }
        if (prev.price > 0 && next.price > 0 && prev.price != next.price) {
            return true;
        }
        return prev.totalQty > 0 && next.totalQty > 0 && prev.totalQty != next.totalQty;
    }

    private boolean isWithinLoginGrace() {
        return Access.plugin().getOfferStampStateServices().isWithinLoginGrace();
    }
}
