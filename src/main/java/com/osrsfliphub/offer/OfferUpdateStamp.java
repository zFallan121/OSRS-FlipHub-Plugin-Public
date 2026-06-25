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

import net.runelite.api.GrandExchangeOffer;

final class OfferUpdateStamp {
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

    OfferUpdateStamp() {
    }

    OfferUpdateStamp(int itemId,
                     int price,
                     int totalQty,
                     int filledQty,
                     boolean isBuy,
                     long spentGp,
                     long lastUpdateMs,
                     long firstSeenMs,
                     long completedMs,
                     long lastEmptyMs) {
        this.itemId = itemId;
        this.price = price;
        this.totalQty = totalQty;
        this.filledQty = filledQty;
        this.isBuy = isBuy;
        this.spentGp = spentGp;
        this.lastUpdateMs = lastUpdateMs;
        this.firstSeenMs = firstSeenMs;
        this.completedMs = completedMs;
        this.lastEmptyMs = lastEmptyMs;
    }

    static OfferUpdateStamp fromSnapshot(OfferSnapshot snapshot, long timestamp) {
        if (snapshot == null) {
            return null;
        }
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return new OfferUpdateStamp(
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

    static OfferUpdateStamp fromOffer(GrandExchangeOffer offer, long timestamp, boolean isBuy) {
        if (offer == null) {
            return null;
        }
        long safeTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return new OfferUpdateStamp(
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
