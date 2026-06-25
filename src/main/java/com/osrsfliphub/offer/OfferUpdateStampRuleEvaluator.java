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

final class OfferUpdateStampRuleEvaluator {
    private final LongSupplier nowMsSupplier;
    private final BooleanSupplier loginGraceSupplier;

    OfferUpdateStampRuleEvaluator(LongSupplier nowMsSupplier, BooleanSupplier loginGraceSupplier) {
        this.nowMsSupplier = nowMsSupplier;
        this.loginGraceSupplier = loginGraceSupplier;
    }

    boolean shouldPreserveStamp(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return shouldPreserveStampInternal(stamp, snapshot.itemId, snapshot.price, snapshot.totalQty,
            snapshot.isBuy, snapshot.filledQty, snapshot.spentGp, false);
    }

    boolean shouldPreserveStamp(OfferUpdateStamp stamp, GrandExchangeOffer offer, boolean isBuy) {
        if (offer == null) {
            return false;
        }
        return shouldPreserveStampInternal(stamp, offer.getItemId(), offer.getPrice(), offer.getTotalQuantity(),
            isBuy, offer.getQuantitySold(), offer.getSpent(), false);
    }

    boolean shouldPreserveStampAfterLogin(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
        if (!isWithinLoginGrace() || snapshot == null) {
            return false;
        }
        return shouldPreserveStampInternal(stamp, snapshot.itemId, snapshot.price, snapshot.totalQty,
            snapshot.isBuy, snapshot.filledQty, snapshot.spentGp, true);
    }

    boolean shouldPreserveStampAfterLogin(OfferUpdateStamp stamp, GrandExchangeOffer offer) {
        if (!isWithinLoginGrace() || offer == null) {
            return false;
        }
        return shouldPreserveStampInternal(stamp, offer.getItemId(), offer.getPrice(), offer.getTotalQuantity(),
            OfferUpdateStampStateHelpers.isBuyOffer(offer), offer.getQuantitySold(), offer.getSpent(), true);
    }

    boolean shouldPreserveIdentityAfterLogin(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
        if (!isWithinLoginGrace() || snapshot == null) {
            return false;
        }
        return shouldPreserveIdentityAfterLoginInternal(stamp, snapshot.itemId, snapshot.price, snapshot.totalQty,
            snapshot.isBuy, snapshot.filledQty, snapshot.spentGp, OfferUpdateStampStateHelpers.isOfferComplete(snapshot));
    }

    boolean shouldPreserveIdentityAfterLogin(OfferUpdateStamp stamp, GrandExchangeOffer offer) {
        if (!isWithinLoginGrace() || offer == null) {
            return false;
        }
        return shouldPreserveIdentityAfterLoginInternal(stamp, offer.getItemId(), offer.getPrice(), offer.getTotalQuantity(),
            OfferUpdateStampStateHelpers.isBuyOffer(offer), offer.getQuantitySold(), offer.getSpent(),
            OfferUpdateStampStateHelpers.isOfferComplete(offer));
    }

    boolean shouldPreserveStampAfterEmpty(OfferSnapshot prev, OfferSnapshot next, OfferUpdateStamp stamp) {
        if (!isWithinLoginGrace()) {
            return false;
        }
        if (prev == null || next == null) {
            return false;
        }
        if (!OfferUpdateStampStateHelpers.isEmptySnapshot(prev)) {
            return false;
        }
        return shouldPreserveStampAfterEmptyInternal(stamp, next.itemId, next.price, next.totalQty, next.isBuy);
    }

    boolean shouldPreserveStampAfterEmpty(GrandExchangeOffer offer, OfferUpdateStamp stamp, boolean isBuy) {
        if (!isWithinLoginGrace() || offer == null) {
            return false;
        }
        return shouldPreserveStampAfterEmptyInternal(stamp, offer.getItemId(), offer.getPrice(), offer.getTotalQuantity(), isBuy);
    }

    boolean stampMatchesOffer(OfferUpdateStamp stamp, GrandExchangeOffer offer) {
        if (stamp == null || offer == null) {
            return false;
        }
        return stampMatches(stamp, offer.getItemId(), offer.getPrice(), offer.getTotalQuantity(),
            OfferUpdateStampStateHelpers.isBuyOffer(offer), offer.getQuantitySold(), offer.getSpent());
    }

    boolean stampMatches(OfferUpdateStamp stamp,
                         int itemId,
                         int price,
                         int totalQty,
                         boolean isBuy,
                         int filledQty,
                         long spentGp) {
        if (stamp == null) {
            return false;
        }
        if (stamp.itemId != itemId || stamp.isBuy != isBuy) {
            return false;
        }
        if (price > 0 && stamp.price > 0 && stamp.price != price) {
            return false;
        }
        boolean progressCompatible = progressMatches(stamp, filledQty, spentGp);
        if (totalQty > 0 && stamp.totalQty > 0 && stamp.totalQty != totalQty) {
            if (filledQty <= 0 && stamp.filledQty <= 0) {
                return false;
            }
            if (!progressCompatible) {
                return false;
            }
        }
        if (!progressCompatible) {
            return false;
        }
        return true;
    }

    boolean maybeUpdateStampDetails(OfferUpdateStamp stamp,
                                    int itemId,
                                    int price,
                                    int totalQty,
                                    boolean isBuy,
                                    int filledQty,
                                    long spentGp) {
        if (stamp == null) {
            return false;
        }
        boolean changed = false;
        if (stamp.itemId != itemId) {
            stamp.itemId = itemId;
            changed = true;
        }
        if (price > 0 && stamp.price != price) {
            stamp.price = price;
            changed = true;
        }
        if (totalQty > 0 && stamp.totalQty != totalQty) {
            stamp.totalQty = totalQty;
            changed = true;
        }
        if (stamp.isBuy != isBuy) {
            stamp.isBuy = isBuy;
            changed = true;
        }
        if (filledQty > stamp.filledQty) {
            stamp.filledQty = filledQty;
            changed = true;
        }
        if (spentGp > stamp.spentGp) {
            stamp.spentGp = spentGp;
            changed = true;
        }
        if (stamp.lastEmptyMs != 0) {
            stamp.lastEmptyMs = 0;
            changed = true;
        }
        if (stamp.firstSeenMs <= 0) {
            stamp.firstSeenMs = stamp.lastUpdateMs > 0 ? stamp.lastUpdateMs : nowMs();
            changed = true;
        }
        return changed;
    }

    private boolean shouldPreserveStampInternal(
        OfferUpdateStamp stamp,
        int itemId,
        int price,
        int totalQty,
        boolean isBuy,
        int filledQty,
        long spentGp,
        boolean forceLoginGrace
    ) {
        if (stamp == null) {
            return false;
        }
        if (stamp.itemId != itemId || stamp.isBuy != isBuy) {
            return false;
        }
        boolean metadataIncomplete = hasIncompleteMetadata(price, totalQty);
        if (!metadataIncomplete && !isMetadataCompatible(stamp, price, totalQty)) {
            return false;
        }
        if (!stampHasProgress(stamp)) {
            return false;
        }
        boolean candidateHasProgress = filledQty > 0 || spentGp > 0;
        boolean withinGrace = forceLoginGrace || isWithinLoginGrace();
        if (!candidateHasProgress) {
            return metadataIncomplete || withinGrace;
        }
        if (filledQty > 0 && stamp.filledQty > 0 && filledQty < stamp.filledQty) {
            return withinGrace;
        }
        if (spentGp > 0 && stamp.spentGp > 0 && spentGp < stamp.spentGp) {
            return withinGrace;
        }
        return false;
    }

    private boolean shouldPreserveIdentityAfterLoginInternal(
        OfferUpdateStamp stamp,
        int itemId,
        int price,
        int totalQty,
        boolean isBuy,
        int filledQty,
        long spentGp,
        boolean offerComplete
    ) {
        if (stamp == null) {
            return false;
        }
        if (itemId <= 0 || stamp.itemId != itemId || stamp.isBuy != isBuy) {
            return false;
        }
        if (offerComplete) {
            return false;
        }
        // During login reconciliation the client can briefly report incomplete metadata.
        if (price <= 0 || totalQty <= 0) {
            return true;
        }
        if (!isMetadataCompatible(stamp, price, totalQty)) {
            return false;
        }
        boolean candidateHasProgress = filledQty > 0 || spentGp > 0;
        return !candidateHasProgress && !stampHasProgress(stamp);
    }

    private boolean shouldPreserveStampAfterEmptyInternal(
        OfferUpdateStamp stamp,
        int itemId,
        int price,
        int totalQty,
        boolean isBuy
    ) {
        if (stamp == null || stamp.lastEmptyMs <= 0) {
            return false;
        }
        if (itemId <= 0 || stamp.itemId != itemId) {
            return false;
        }
        boolean metadataIncomplete = hasIncompleteMetadata(price, totalQty);
        if (!metadataIncomplete && !isMetadataCompatible(stamp, price, totalQty)) {
            return false;
        }
        return stamp.isBuy == isBuy;
    }

    private boolean progressMatches(OfferUpdateStamp stamp, int filledQty, long spentGp) {
        if (stamp == null) {
            return false;
        }
        if (stamp.filledQty > 0 && filledQty < stamp.filledQty) {
            return isWithinLoginGrace();
        }
        if (stamp.spentGp > 0 && spentGp < stamp.spentGp) {
            return isWithinLoginGrace();
        }
        return true;
    }

    private boolean hasIncompleteMetadata(int price, int totalQty) {
        return price <= 0 || totalQty <= 0;
    }

    private boolean isMetadataCompatible(OfferUpdateStamp stamp, int price, int totalQty) {
        if (stamp == null) {
            return false;
        }
        if (price > 0 && stamp.price > 0 && stamp.price != price) {
            return false;
        }
        if (totalQty > 0 && stamp.totalQty > 0 && stamp.totalQty != totalQty) {
            return false;
        }
        return true;
    }

    private boolean stampHasProgress(OfferUpdateStamp stamp) {
        return stamp != null && (stamp.filledQty > 0 || stamp.spentGp > 0);
    }

    private long nowMs() {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }

    private boolean isWithinLoginGrace() {
        return loginGraceSupplier != null && loginGraceSupplier.getAsBoolean();
    }
}
