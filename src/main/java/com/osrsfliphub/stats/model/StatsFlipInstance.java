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

/** One activity on the Profile tab: a flip, or a conversion the player recorded. */
final class StatsFlipInstance {
    final int itemId;
    final long buyPriceGp;
    final long sellPriceGp;
    final long buyCostGp;
    final long sellRevenueGp;
    final long profitGp;
    final int quantity;
    final long completionTsMs;

    /** How this came about, when it was not simply bought and sold. Null for a plain flip. */
    final ConversionKind conversionKind;
    /** What the player called the conversion. Null for a plain flip. */
    final String conversionName;

    /**
     * A sell offer that has filled part-way and is still sitting in the Grand Exchange. The
     * coins are already the player's, so the profit counts - but the flip has not happened yet,
     * so nothing that counts flips counts this.
     */
    final boolean inProgress;

    /** What the exchange took on these sales, per {@link GeTax}. */
    final long taxGp;

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs) {
        this(itemId, buyPriceGp, sellPriceGp, buyCostGp, sellRevenueGp, profitGp, quantity,
            completionTsMs, false, 0L, null, null);
    }

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs,
                      boolean inProgress,
                      long taxGp) {
        this(itemId, buyPriceGp, sellPriceGp, buyCostGp, sellRevenueGp, profitGp, quantity,
            completionTsMs, inProgress, taxGp, null, null);
    }

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs,
                      boolean inProgress,
                      long taxGp,
                      ConversionKind conversionKind,
                      String conversionName) {
        this.itemId = itemId;
        this.buyPriceGp = Math.max(0L, buyPriceGp);
        this.sellPriceGp = Math.max(0L, sellPriceGp);
        this.buyCostGp = Math.max(0L, buyCostGp);
        this.sellRevenueGp = Math.max(0L, sellRevenueGp);
        this.profitGp = profitGp;
        this.quantity = Math.max(0, quantity);
        this.completionTsMs = Math.max(0L, completionTsMs);
        this.inProgress = inProgress;
        this.taxGp = Math.max(0L, taxGp);
        this.conversionKind = conversionKind;
        this.conversionName = conversionName;
    }
}
