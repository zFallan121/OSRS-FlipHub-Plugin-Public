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

import java.util.ArrayDeque;

/**
 * One item's stock as one character holds it, purchase by purchase.
 *
 * <p>A sale takes the newest purchase made before it, then the one before that: the website's
 * rule too, so a player sees the same figure linked or not (the owner's choice, 3 Oct 2026).
 * His Forgotten brews: about 2,066 bought near 3,800 sat in a 4.1k listing, he bought 130 at
 * 3,050 and sold 2 at 3,590. The newest purchase books those 2 at +938; the average of all he
 * held booked -241, the oldest -556. A flipper buys to sell now. Stock stuck in a listing stays
 * held at what it cost, and once everything is sold the total is the same whichever way.
 *
 * <p>Both ledgers replay in time order, so whatever is held when a sale comes was bought before
 * it, and the newest is the last one added.
 */
class Lots {
    /** {quantity left, what it cost}, newest last. */
    private final ArrayDeque<long[]> held = new ArrayDeque<>();
    long qty;

    void buy(long quantity, long cost) {
        if (quantity > 0) {
            held.addLast(new long[] {quantity, Math.max(0L, cost)});
            qty += quantity;
        }
    }

    /** Takes up to {@code quantity}, newest purchase first, and says what it cost. */
    long take(long quantity) {
        long cost = 0L;
        while (quantity > 0 && !held.isEmpty()) {
            long[] lot = held.peekLast();
            long taken = Math.min(quantity, lot[0]);
            // What stays behind keeps the rest of the cost, so the last of a purchase carries what
            // division left over and no coin is ever lost.
            long share = lot[1] * taken / lot[0];
            lot[0] -= taken;
            lot[1] -= share;
            if (lot[0] == 0) {
                held.pollLast();
            }
            cost += share;
            quantity -= taken;
            qty -= taken;
        }
        return cost;
    }
}
