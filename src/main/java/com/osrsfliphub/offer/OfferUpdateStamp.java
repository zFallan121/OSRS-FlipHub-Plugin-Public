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
import javax.inject.*;
import net.runelite.api.*;

@Singleton
final class OfferUpdateStamp {
    private final OfferUpdateStampRuleEvaluator ruleEvaluator;

    @Inject
    OfferUpdateStamp() {
        this.ruleEvaluator = new OfferUpdateStampRuleEvaluator(this::nowMs, this::isWithinLoginGrace);
    }

    void trackOfferUpdate(Map<Integer, Stamp> stamps, int slot, OfferSnapshot prev, OfferSnapshot next) {
        if (stamps == null || next == null) {
            return;
        }
        if (next.state.equals(GrandExchangeOfferState.EMPTY.name()) || next.itemId <= 0) {
            Stamp existing = stamps.get(slot);
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
        Stamp existing = stamps.get(slot);
        if (prev == null) {
            if (existing != null && keeps(existing, next, false)) {
                boolean changed = ruleEvaluator.maybeUpdateStampDetails(existing, next);
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
            createStamp(stamps, slot, next);
            return;
        }

        if (OfferUpdateStampStateHelpers.hasOfferChanged(prev, next)) {
            boolean afterEmpty = OfferUpdateStampStateHelpers.isEmptySnapshot(prev);
            if (existing != null && afterEmpty && existing.lastEmptyMs <= 0) {
                existing = null;
            }
            if (existing != null && keeps(existing, next, afterEmpty)) {
                int filledBefore = existing.filledQty;
                long spentBefore = existing.spentGp;
                boolean changed = ruleEvaluator.maybeUpdateStampDetails(existing, next);
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
            createStamp(stamps, slot, next);
            return;
        }

        if (existing == null) {
            createStamp(stamps, slot, next);
        }
    }

    long getOfferLastUpdateMs(Map<Integer, Stamp> stamps, int slot, GrandExchangeOffer offer) {
        if (stamps == null || offer == null) {
            return -1;
        }
        if (offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0) {
            Stamp existing = stamps.get(slot);
            if (existing != null && existing.lastEmptyMs <= 0) {
                existing.lastEmptyMs = nowMs();
                persistOfferUpdateTimes();
            }
            return -1;
        }
        // Read as the snapshot the change handler would make of it, so one set of rules serves both.
        OfferSnapshot now = OfferSnapshot.fromOffer(slot, offer, null);
        Stamp existing = stamps.get(slot);
        if (existing != null && keeps(existing, now, true)) {
            boolean changed = ruleEvaluator.maybeUpdateStampDetails(existing, now);
            if (existing.lastUpdateMs <= 0) {
                existing.lastUpdateMs = nowMs();
                changed = true;
            }
            if (OfferUpdateStampStateHelpers.markCompletedIfNeeded(existing, now, this::nowMs)) {
                changed = true;
            }
            if (changed) {
                persistOfferUpdateTimes();
            }
            return displayedMs(existing, now);
        }
        return displayedMs(createStamp(stamps, slot, now), now);
    }

    /**
     * Whether the slot's stamp still describes this offer, so its timer carries on.
     *
     * @param mayFollowEmpty whether the slot may just have shown empty, which a login can make
     *                       it do for a moment without the offer having gone anywhere
     */
    private boolean keeps(Stamp existing, OfferSnapshot next, boolean mayFollowEmpty) {
        return stampMatchesSnapshot(existing, next) || ruleEvaluator.shouldPreserveStamp(existing, next)
            || ruleEvaluator.shouldPreserveStampAfterLogin(existing, next)
            || ruleEvaluator.shouldPreserveIdentityAfterLogin(existing, next)
            || mayFollowEmpty && ruleEvaluator.shouldPreserveStampAfterEmpty(next, existing);
    }

    private Stamp createStamp(Map<Integer, Stamp> stamps, int slot, OfferSnapshot next) {
        Stamp created = Stamp.fromSnapshot(next, nowMs());
        stamps.put(slot, created);
        OfferUpdateStampStateHelpers.markCompletedIfNeeded(created, next, this::nowMs);
        persistOfferUpdateTimes();
        return created;
    }

    /** A finished offer shows how long it took; one still going shows its last change. */
    private long displayedMs(Stamp stamp, OfferSnapshot offer) {
        return OfferUpdateStampStateHelpers.isOfferComplete(offer)
            ? OfferUpdateStampStateHelpers.computeCompletedDisplayTimestamp(stamp, this::nowMs)
            : stamp.lastUpdateMs;
    }

    boolean stampMatchesSnapshot(Stamp stamp, OfferSnapshot snapshot) {
        return stamp != null && snapshot != null && ruleEvaluator.stampMatches(stamp, snapshot);
    }

    private long nowMs() {
        return System.currentTimeMillis();
    }

    private boolean isWithinLoginGrace() {
        return Access.plugin().getOfferStampStateServices().isWithinLoginGrace();
    }

    private void persistOfferUpdateTimes() {
        Access.plugin().getOfferStampStateServices().persistOfferUpdateTimes();
    }
}
