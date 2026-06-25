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
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

final class OfferUpdateStampService {
    interface Hooks {
        long nowMs();
        boolean isWithinLoginGrace();
        void persistOfferUpdateTimes();
    }

    private final Hooks hooks;
    private final OfferUpdateStampRuleEvaluator ruleEvaluator;

    OfferUpdateStampService(Hooks hooks) {
        this.hooks = hooks;
        this.ruleEvaluator = new OfferUpdateStampRuleEvaluator(this::nowMs, this::isWithinLoginGrace);
    }

    void trackOfferUpdate(Map<Integer, OfferUpdateStamp> stamps, int slot, OfferSnapshot prev, OfferSnapshot next) {
        if (stamps == null || next == null) {
            return;
        }
        if (next.state.equals(GrandExchangeOfferState.EMPTY.name()) || next.itemId <= 0) {
            OfferUpdateStamp existing = stamps.get(slot);
            if (OfferUpdateStampStateHelpers.shouldClearOfferStamp(prev, this::isWithinLoginGrace)) {
                if (stamps.remove(slot) != null) {
                    persistOfferUpdateTimes();
                }
            } else if (existing != null) {
                if (existing.lastEmptyMs <= 0) {
                    existing.lastEmptyMs = nowMs();
                    persistOfferUpdateTimes();
                }
            }
            return;
        }
        OfferUpdateStamp existing = stamps.get(slot);
        if (prev == null) {
            if (existing != null) {
                if (stampMatchesSnapshot(existing, next) || ruleEvaluator.shouldPreserveStamp(existing, next)
                    || ruleEvaluator.shouldPreserveStampAfterLogin(existing, next)
                    || ruleEvaluator.shouldPreserveIdentityAfterLogin(existing, next)
                    || ruleEvaluator.shouldPreserveStampAfterEmpty(prev, next, existing)) {
                    boolean changed = ruleEvaluator.maybeUpdateStampDetails(existing, next.itemId, next.price, next.totalQty, next.isBuy,
                        next.filledQty, next.spentGp);
                    if (existing.lastUpdateMs <= 0) {
                        existing.lastUpdateMs = nowMs();
                        changed = true;
                    }
                    if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(existing, next, this::nowMs)) {
                        changed = true;
                    }
                    if (changed) {
                        persistOfferUpdateTimes();
                    }
                    return;
                }
            }
            OfferUpdateStamp created = OfferUpdateStamp.fromSnapshot(next, nowMs());
            stamps.put(slot, created);
            if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(created, next, this::nowMs)) {
                persistOfferUpdateTimes();
                return;
            }
            persistOfferUpdateTimes();
            return;
        }

        if (OfferUpdateStampStateHelpers.hasOfferChanged(prev, next)) {
            if (existing != null && OfferUpdateStampStateHelpers.isEmptySnapshot(prev) && existing.lastEmptyMs <= 0) {
                existing = null;
            }
            if (existing != null) {
                if (stampMatchesSnapshot(existing, next) || ruleEvaluator.shouldPreserveStamp(existing, next)
                    || ruleEvaluator.shouldPreserveStampAfterLogin(existing, next)
                    || ruleEvaluator.shouldPreserveIdentityAfterLogin(existing, next)
                    || ruleEvaluator.shouldPreserveStampAfterEmpty(prev, next, existing)) {
                    int filledBefore = existing.filledQty;
                    long spentBefore = existing.spentGp;
                    boolean changed = ruleEvaluator.maybeUpdateStampDetails(existing, next.itemId, next.price, next.totalQty, next.isBuy,
                        next.filledQty, next.spentGp);
                    if (OfferUpdateStampStateHelpers.shouldRefreshOfferTimestamp(
                        prev,
                        next,
                        existing,
                        filledBefore,
                        spentBefore,
                        this::isWithinLoginGrace
                    )) {
                        existing.lastUpdateMs = nowMs();
                        changed = true;
                    }
                    if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(existing, next, this::nowMs)) {
                        changed = true;
                    }
                    if (changed) {
                        persistOfferUpdateTimes();
                    }
                    return;
                }
            }
            OfferUpdateStamp created = OfferUpdateStamp.fromSnapshot(next, nowMs());
            stamps.put(slot, created);
            if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(created, next, this::nowMs)) {
                persistOfferUpdateTimes();
                return;
            }
            persistOfferUpdateTimes();
            return;
        }

        if (existing == null) {
            OfferUpdateStamp created = OfferUpdateStamp.fromSnapshot(next, nowMs());
            stamps.put(slot, created);
            if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(created, next, this::nowMs)) {
                persistOfferUpdateTimes();
                return;
            }
            persistOfferUpdateTimes();
        }
    }

    long getOfferLastUpdateMs(Map<Integer, OfferUpdateStamp> stamps, int slot, GrandExchangeOffer offer) {
        if (stamps == null || offer == null) {
            return -1;
        }
        if (offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0) {
            OfferUpdateStamp existing = stamps.get(slot);
            if (existing != null && existing.lastEmptyMs <= 0) {
                existing.lastEmptyMs = nowMs();
                persistOfferUpdateTimes();
            }
            return -1;
        }
        OfferUpdateStamp existing = stamps.get(slot);
        boolean isBuy = OfferUpdateStampStateHelpers.isBuyOffer(offer);
        if (existing != null) {
            if (ruleEvaluator.stampMatchesOffer(existing, offer) || ruleEvaluator.shouldPreserveStamp(existing, offer, isBuy)
                || ruleEvaluator.shouldPreserveStampAfterLogin(existing, offer)
                || ruleEvaluator.shouldPreserveIdentityAfterLogin(existing, offer)
                || ruleEvaluator.shouldPreserveStampAfterEmpty(offer, existing, isBuy)) {
                boolean changed = ruleEvaluator.maybeUpdateStampDetails(existing, offer.getItemId(), offer.getPrice(),
                    offer.getTotalQuantity(), isBuy, offer.getQuantitySold(), offer.getSpent());
                if (existing.lastUpdateMs <= 0) {
                    existing.lastUpdateMs = nowMs();
                    changed = true;
                }
                if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(existing, offer, this::nowMs)) {
                    changed = true;
                }
                if (changed) {
                    persistOfferUpdateTimes();
                }
                if (OfferUpdateStampStateHelpers.isOfferComplete(offer)) {
                    return OfferUpdateStampStateHelpers.computeCompletedDisplayTimestamp(existing, this::nowMs);
                }
                return existing.lastUpdateMs;
            }
        }

        OfferUpdateStamp stamp = OfferUpdateStamp.fromOffer(offer, nowMs(), isBuy);
        stamps.put(slot, stamp);
        OfferUpdateStampStateHelpers.markCompletedIfNeeded(stamp, offer, this::nowMs);
        persistOfferUpdateTimes();
        if (OfferUpdateStampStateHelpers.isOfferComplete(offer)) {
            return OfferUpdateStampStateHelpers.computeCompletedDisplayTimestamp(stamp, this::nowMs);
        }
        return stamp.lastUpdateMs;
    }

    boolean stampMatchesSnapshot(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
        if (stamp == null || snapshot == null) {
            return false;
        }
        return ruleEvaluator.stampMatches(stamp, snapshot.itemId, snapshot.price, snapshot.totalQty, snapshot.isBuy,
            snapshot.filledQty, snapshot.spentGp);
    }

    long resolveBaselineTradeTimestamp(OfferUpdateStamp stamp, long lastLoginMs) {
        long stampMs = 0L;
        if (stamp != null) {
            stampMs = OfferUpdateStampStateHelpers.minPositive(stamp.firstSeenMs, stamp.lastUpdateMs, stamp.completedMs);
        }
        if (lastLoginMs > 0 && stampMs > 0 && stampMs < lastLoginMs) {
            return stampMs;
        }
        return 0L;
    }

    private long nowMs() {
        return hooks != null ? hooks.nowMs() : System.currentTimeMillis();
    }

    private boolean isWithinLoginGrace() {
        return hooks != null && hooks.isWithinLoginGrace();
    }

    private void persistOfferUpdateTimes() {
        if (hooks != null) {
            hooks.persistOfferUpdateTimes();
        }
    }
}
