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

/**
 * One stored trade record.
 *
 * <p>While an offer is filling there is one of these per fill, holding that fill's
 * increment. When the offer completes, {@link TradeOfferCollapser} replaces
 * them with one record holding the offer's totals, so a finished offer is always
 * exactly one record however many chunks it filled in.
 */
final class Delta {
    /**
     * When the quantity here changed hands. For a collapsed offer this is its first
     * fill: the buy-limit window opens at the first purchase, and a position is held
     * from the moment it was opened, so neither may move to the completion.
     */
    long tsClientMs;
    int slot;
    int itemId;
    boolean isBuy;
    int deltaQty;
    long deltaGp;
    String eventType;
    int price;
    boolean baselineSynthetic;
    /**
     * When the offer this record belongs to was placed, as the slot's stamp saw it
     * ({@link Stamp#firstSeenMs}). Slots are reused, so this is what tells
     * two offers of the same item at the same price on one slot apart. Zero on records
     * written before it was tracked, and on trades replayed from the in-game history.
     */
    long offerStartMs;
    /**
     * When the offer ended: its completion, or - for a run of fills the slot moved on
     * from without a completion being seen - its last fill. Zero on a single fill, which
     * is one moment. Only a record the collapser wrote carries one, which is also how
     * the load-time normalisation knows to leave it alone.
     */
    long endMs;

    Delta() {
    }

    Delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty, long deltaGp,
                    String eventType, int price, boolean baselineSynthetic) {
        this(tsClientMs, slot, itemId, isBuy, deltaQty, deltaGp, eventType, price, baselineSynthetic, 0L, 0L);
    }

    Delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty, long deltaGp,
                    String eventType, int price, boolean baselineSynthetic, long offerStartMs, long endMs) {
        this.tsClientMs = tsClientMs;
        this.slot = slot;
        this.itemId = itemId;
        this.isBuy = isBuy;
        this.deltaQty = deltaQty;
        this.deltaGp = deltaGp;
        this.eventType = eventType;
        this.price = price;
        this.baselineSynthetic = baselineSynthetic;
        this.offerStartMs = offerStartMs;
        this.endMs = endMs;
    }

    /**
     * When the offer ended: {@link #endMs} when it has one, else the record's own time.
     *
     * <p>A sale is booked at this moment - replayed against the stock held by then,
     * given the flip's completion time, placed in a range, the end of the hold - because
     * a sale offer left up while more of the item is bought sells its later units out of
     * that later stock, and one record can only be matched once. Booking it when it
     * ended is the one choice that never leaves a real sale unmatched. A purchase keeps
     * {@link #tsClientMs} for its anchors: the position opens, and the buy-limit window
     * starts, at the first unit bought.
     */
    long closedAtMs() {
        return endMs > 0 ? endMs : tsClientMs;
    }
}
