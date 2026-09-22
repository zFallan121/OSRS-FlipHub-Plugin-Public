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

import java.util.function.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class OfferUpdateStampRuleEvaluator {
    private final LongSupplier nowMsSupplier;
    private final BooleanSupplier loginGraceSupplier;

    boolean shouldPreserveStamp(Stamp stamp, OfferSnapshot snapshot) {
        return snapshot != null && shouldPreserveStampInternal(stamp, snapshot, false);
    }

    boolean shouldPreserveStampAfterLogin(Stamp stamp, OfferSnapshot snapshot) {
        return isWithinLoginGrace() && snapshot != null && shouldPreserveStampInternal(stamp, snapshot, true);
    }

    boolean shouldPreserveIdentityAfterLogin(Stamp stamp, OfferSnapshot snapshot) {
        if (!isWithinLoginGrace() || snapshot == null || stamp == null) {
            return false;
        }
        if (snapshot.itemId <= 0 || stamp.itemId != snapshot.itemId || stamp.isBuy != snapshot.isBuy) {
            return false;
        }
        if (OfferUpdateStampStateHelpers.isOfferComplete(snapshot)) {
            return false;
        }
        // During login reconciliation the client can briefly report incomplete metadata.
        if (snapshot.price <= 0 || snapshot.totalQty <= 0) {
            return true;
        }
        if (!isMetadataCompatible(stamp, snapshot.price, snapshot.totalQty)) {
            return false;
        }
        boolean candidateHasProgress = snapshot.filledQty > 0 || snapshot.spentGp > 0;
        return !candidateHasProgress && !hasProgress(stamp);
    }

    /** The same offer back in a slot that showed empty for a moment while logging in. */
    boolean shouldPreserveStampAfterEmpty(OfferSnapshot next, Stamp stamp) {
        if (!isWithinLoginGrace() || next == null || stamp == null || stamp.lastEmptyMs <= 0) {
            return false;
        }
        if (next.itemId <= 0 || stamp.itemId != next.itemId) {
            return false;
        }
        boolean metadataIncomplete = next.price <= 0 || next.totalQty <= 0;
        if (!metadataIncomplete && !isMetadataCompatible(stamp, next.price, next.totalQty)) {
            return false;
        }
        return stamp.isBuy == next.isBuy;
    }

    boolean stampMatches(Stamp stamp, OfferSnapshot snapshot) {
        if (stamp.itemId != snapshot.itemId || stamp.isBuy != snapshot.isBuy) {
            return false;
        }
        if (snapshot.price > 0 && stamp.price > 0 && stamp.price != snapshot.price) {
            return false;
        }
        // A different quantity is the same offer only if something has filled against it.
        if (snapshot.totalQty > 0 && stamp.totalQty > 0 && stamp.totalQty != snapshot.totalQty
            && snapshot.filledQty <= 0 && stamp.filledQty <= 0) {
            return false;
        }
        return progressMatches(stamp, snapshot.filledQty, snapshot.spentGp);
    }

    boolean maybeUpdateStampDetails(Stamp stamp, OfferSnapshot snapshot) {
        boolean changed = false;
        if (stamp.itemId != snapshot.itemId) {
            stamp.itemId = snapshot.itemId;
            changed = true;
        }
        if (snapshot.price > 0 && stamp.price != snapshot.price) {
            stamp.price = snapshot.price;
            changed = true;
        }
        if (snapshot.totalQty > 0 && stamp.totalQty != snapshot.totalQty) {
            stamp.totalQty = snapshot.totalQty;
            changed = true;
        }
        if (stamp.isBuy != snapshot.isBuy) {
            stamp.isBuy = snapshot.isBuy;
            changed = true;
        }
        if (snapshot.filledQty > stamp.filledQty) {
            stamp.filledQty = snapshot.filledQty;
            changed = true;
        }
        if (snapshot.spentGp > stamp.spentGp) {
            stamp.spentGp = snapshot.spentGp;
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

    private boolean shouldPreserveStampInternal(Stamp stamp, OfferSnapshot snapshot, boolean forceLoginGrace) {
        if (stamp == null) {
            return false;
        }
        if (stamp.itemId != snapshot.itemId || stamp.isBuy != snapshot.isBuy) {
            return false;
        }
        boolean metadataIncomplete = snapshot.price <= 0 || snapshot.totalQty <= 0;
        if (!metadataIncomplete && !isMetadataCompatible(stamp, snapshot.price, snapshot.totalQty)) {
            return false;
        }
        if (!hasProgress(stamp)) {
            return false;
        }
        int filledQty = snapshot.filledQty;
        long spentGp = snapshot.spentGp;
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

    private boolean progressMatches(Stamp stamp, int filledQty, long spentGp) {
        if (stamp.filledQty > 0 && filledQty < stamp.filledQty) {
            return isWithinLoginGrace();
        }
        if (stamp.spentGp > 0 && spentGp < stamp.spentGp) {
            return isWithinLoginGrace();
        }
        return true;
    }

    private boolean isMetadataCompatible(Stamp stamp, int price, int totalQty) {
        if (price > 0 && stamp.price > 0 && stamp.price != price) {
            return false;
        }
        return totalQty <= 0 || stamp.totalQty <= 0 || stamp.totalQty == totalQty;
    }

    private long nowMs() {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }

    private boolean isWithinLoginGrace() {
        return loginGraceSupplier != null && loginGraceSupplier.getAsBoolean();
    }

    /** Whether anything has actually been bought or sold against this stamp yet. */
    private static boolean hasProgress(Stamp stamp) {
        return stamp.filledQty > 0 || stamp.spentGp > 0;
    }
}
