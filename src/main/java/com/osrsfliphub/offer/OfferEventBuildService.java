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

@Singleton
final class OfferEventBuildService {
    interface Hooks {
        boolean stampMatchesSnapshot(OfferUpdateStamp stamp, OfferSnapshot snapshot);
        long resolveBaselineTradeTimestamp(OfferUpdateStamp stamp, long lastLoginMs);
        boolean isWithinLoginGrace();
        boolean hasRecentLocalBuy(int itemId, long nowMs);
        long nowMs();
    }

    static final class Input {
        final OfferSnapshot prev;
        final OfferSnapshot next;
        final OfferUpdateStamp stamp;
        final boolean unlinked;
        final boolean localTradesLoadedThisLogin;
        final long lastLoginMs;
        final int world;

        Input(OfferSnapshot prev,
              OfferSnapshot next,
              OfferUpdateStamp stamp,
              boolean unlinked,
              boolean localTradesLoadedThisLogin,
              long lastLoginMs,
              int world) {
            this.prev = prev;
            this.next = next;
            this.stamp = stamp;
            this.unlinked = unlinked;
            this.localTradesLoadedThisLogin = localTradesLoadedThisLogin;
            this.lastLoginMs = lastLoginMs;
            this.world = world;
        }
    }

    static final class Result {
        private final GeEvent event;
        private final boolean baselineSynthetic;
        private final boolean clearRecentSlot;
        private final boolean ignore;
        private final boolean shouldScheduleRefresh;

        private Result(GeEvent event,
                       boolean baselineSynthetic,
                       boolean clearRecentSlot,
                       boolean ignore,
                       boolean shouldScheduleRefresh) {
            this.event = event;
            this.baselineSynthetic = baselineSynthetic;
            this.clearRecentSlot = clearRecentSlot;
            this.ignore = ignore;
            this.shouldScheduleRefresh = shouldScheduleRefresh;
        }

        static Result ignore(boolean clearRecentSlot) {
            return new Result(null, false, clearRecentSlot, true, false);
        }

        static Result accepted(GeEvent event,
                               boolean baselineSynthetic,
                               boolean clearRecentSlot,
                               boolean shouldScheduleRefresh) {
            return new Result(event, baselineSynthetic, clearRecentSlot, false, shouldScheduleRefresh);
        }

        GeEvent getEvent() {
            return event;
        }

        boolean isBaselineSynthetic() {
            return baselineSynthetic;
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

    private final Hooks hooks;
    private final OfferEventBuildMathService mathService = new OfferEventBuildMathService();

    @Inject
    OfferEventBuildService(OfferUpdateStampService stampService) {
        this(productionHooks(stampService));
    }

    OfferEventBuildService(Hooks hooks) {
        this.hooks = hooks;
    }

    private static Hooks productionHooks(OfferUpdateStampService stampService) {
        return new Hooks() {
            @Override
            public boolean stampMatchesSnapshot(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
                return stampService != null && stampService.stampMatchesSnapshot(stamp, snapshot);
            }

            @Override
            public long resolveBaselineTradeTimestamp(OfferUpdateStamp stamp, long lastLoginMs) {
                return stampService != null ? stampService.resolveBaselineTradeTimestamp(stamp, lastLoginMs) : 0L;
            }

            @Override
            public boolean isWithinLoginGrace() {
                return PluginAccess.plugin().getOfferStampStateServices().isWithinLoginGrace();
            }

            @Override
            public boolean hasRecentLocalBuy(int itemId, long nowMs) {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                if (service == null) {
                    return false;
                }
                long accountKey = service.resolveAccountHash();
                return accountKey > 0 && service.hasRecentLocalBuy(accountKey, itemId, nowMs);
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }
        };
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
            || GrandExchangeOfferState.EMPTY.name().equals(prev.state);
        boolean usedBaseline = false;
        int deltaQty;
        long deltaGp;
        if (prevIsBaseline && input.stamp != null && stampMatchesSnapshot(input.stamp, next)) {
            usedBaseline = true;
            deltaQty = Math.max(0, next.filledQty - input.stamp.filledQty);
            deltaGp = mathService.computeDeltaGpFromBaseline(next, input.stamp.spentGp, deltaQty);
            if (deltaQty == 0 && deltaGp == 0) {
                return Result.ignore(nextIsEmpty);
            }
        } else {
            deltaQty = prev != null ? Math.max(0, next.filledQty - prev.filledQty) : Math.max(0, next.filledQty);
            deltaGp = mathService.computeDeltaGp(next, prev, deltaQty);
        }

        String eventType = mathService.determineEventType(prev, next);
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
            int prevFilled = prev != null ? Math.max(0, prev.filledQty) : 0;
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
                        long tax = mathService.computeSellTax(total, deltaQty, eventSnapshot.price);
                        deltaGp = Math.max(0L, total - tax);
                    } else {
                        deltaGp = Math.max(0L, total);
                    }
                }
            }
        }

        boolean baselineDelta = prevIsBaseline
            && (deltaQty > 0 || deltaGp > 0)
            && input.unlinked;
        boolean baselineSynthetic = baselineDelta && !usedBaseline;
        long baselineTimestamp = baselineDelta ? resolveBaselineTradeTimestamp(input.stamp, input.lastLoginMs) : 0L;
        if (baselineDelta && baselineTimestamp <= 0L) {
            if (isWithinLoginGrace()) {
                baselineTimestamp = nowMs();
                baselineSynthetic = true;
            } else if ("OFFER_COMPLETED".equals(eventType)) {
                if (input.lastLoginMs > 0) {
                    baselineTimestamp = Math.max(1L, input.lastLoginMs - 1L);
                    baselineSynthetic = true;
                }
            } else if (input.localTradesLoadedThisLogin) {
                return Result.ignore(nextIsEmpty);
            } else if (input.lastLoginMs > 0) {
                baselineTimestamp = Math.max(1L, input.lastLoginMs - 1L);
                baselineSynthetic = true;
            }
        }
        if (baselineDelta && input.localTradesLoadedThisLogin && next.isBuy && !baselineSynthetic) {
            if (hasRecentLocalBuy(next.itemId, nowMs())) {
                baselineSynthetic = true;
            }
        }
        if (!baselineSynthetic && baselineDelta && input.stamp != null && input.lastLoginMs > 0
            && input.stamp.firstSeenMs > 0 && input.stamp.firstSeenMs < input.lastLoginMs && isWithinLoginGrace()) {
            baselineSynthetic = true;
        }

        GeEvent geEvent = GeEvent.createBase(eventSnapshot, prev, eventType);
        mathService.assignDeterministicCompletionEventId(geEvent, input, eventSnapshot);
        geEvent.world = input.world;
        geEvent.delta_qty = deltaQty;
        geEvent.delta_gp = deltaGp;
        if (baselineTimestamp > 0L) {
            geEvent.ts_client_ms = baselineTimestamp;
        }
        boolean shouldScheduleRefresh = geEvent.delta_qty > 0 || "OFFER_COMPLETED".equals(eventType);
        return Result.accepted(geEvent, baselineSynthetic, nextIsEmpty, shouldScheduleRefresh);
    }

    private boolean stampMatchesSnapshot(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
        return hooks != null && hooks.stampMatchesSnapshot(stamp, snapshot);
    }

    private long resolveBaselineTradeTimestamp(OfferUpdateStamp stamp, long lastLoginMs) {
        return hooks != null ? hooks.resolveBaselineTradeTimestamp(stamp, lastLoginMs) : 0L;
    }

    private boolean isWithinLoginGrace() {
        return hooks != null && hooks.isWithinLoginGrace();
    }

    private boolean hasRecentLocalBuy(int itemId, long nowMs) {
        return hooks != null && hooks.hasRecentLocalBuy(itemId, nowMs);
    }

    private long nowMs() {
        return hooks != null ? hooks.nowMs() : System.currentTimeMillis();
    }
}
