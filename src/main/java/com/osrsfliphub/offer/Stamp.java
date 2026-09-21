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

import lombok.AllArgsConstructor;
import net.runelite.api.GrandExchangeOffer;

@AllArgsConstructor
final class Stamp {
    int itemId;
    int price;
    int totalQty;
    int filledQty;
    boolean isBuy;
    long spentGp;
    long lastUpdateMs;
    long firstSeenMs;
    long completedMs;
    long lastEmptyMs;

    Stamp() {
    }

    /**
     * Independent copy. Stamps are mutated in place by the tracking rules, so a
     * caller that needs the pre-update fill level must copy before tracking.
     */
    static Stamp copyOf(Stamp stamp) {
        if (stamp == null) {
            return null;
        }
        return new Stamp(
            stamp.itemId,
            stamp.price,
            stamp.totalQty,
            stamp.filledQty,
            stamp.isBuy,
            stamp.spentGp,
            stamp.lastUpdateMs,
            stamp.firstSeenMs,
            stamp.completedMs,
            stamp.lastEmptyMs
        );
    }

    static Stamp fromSnapshot(OfferSnapshot snapshot, long timestamp) {
        if (snapshot == null) {
            return null;
        }
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return new Stamp(
            snapshot.itemId,
            snapshot.price,
            snapshot.totalQty,
            snapshot.filledQty,
            snapshot.isBuy,
            snapshot.spentGp,
            safeTimestamp,
            safeTimestamp,
            0L,
            0L
        );
    }

    static Stamp fromOffer(GrandExchangeOffer offer, long timestamp, boolean isBuy) {
        if (offer == null) {
            return null;
        }
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return new Stamp(
            offer.getItemId(),
            offer.getPrice(),
            offer.getTotalQuantity(),
            offer.getQuantitySold(),
            isBuy,
            offer.getSpent(),
            safeTimestamp,
            safeTimestamp,
            0L,
            0L
        );
    }
}
