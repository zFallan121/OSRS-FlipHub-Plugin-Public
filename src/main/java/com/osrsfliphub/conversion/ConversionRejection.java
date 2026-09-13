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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A recipe the plugin guessed and the player said was wrong.
 *
 * <p>The guess is keyed on the sales it drew on: for something made, the sale
 * of the thing made; for something taken apart, every sale of a piece the
 * break handed out. Any of those sales coming up in a replay is refused a
 * conversion, so the parts stay as bought and the sale stands on its own -
 * exactly what the ledger does when no recipe fits. Rejecting one sale of a
 * break and not its siblings would only move the guess onto the next piece
 * sold, which is why a break's rejection covers the lot.
 *
 * <p>The rest is what the entry showed when it was dismissed - the item it was
 * filed against, which way it read, where it sat in time - so the correction
 * can be shown where the guess used to be, and undone from there.
 */
final class ConversionRejection {
    /** The item the guess was filed against: the card it appeared on. */
    int itemId;
    /** {@link ConversionKind#name()}; a string so an unknown value loads as null rather than failing. */
    String kind;
    String name;
    long completionTsMs;
    long rejectedAtMs;
    List<TradeKey> trades;

    ConversionRejection() {
    }

    ConversionRejection(int itemId, String kind, String name, long completionTsMs, long rejectedAtMs,
                        List<TradeKey> trades) {
        this.itemId = itemId;
        this.kind = kind;
        this.name = name;
        this.completionTsMs = completionTsMs;
        this.rejectedAtMs = rejectedAtMs;
        this.trades = trades != null ? new ArrayList<>(trades) : new ArrayList<>();
    }

    /** The correction for an entry the panel is showing, or null if it has nothing to correct. */
    static ConversionRejection of(StatsFlipInstance instance, long nowMs) {
        if (instance == null || instance.conversionKind == null || instance.conversionTrades.isEmpty()) {
            return null;
        }
        return new ConversionRejection(
            instance.itemId,
            instance.conversionKind.name(),
            instance.conversionName,
            instance.completionTsMs,
            nowMs,
            instance.conversionTrades);
    }

    ConversionKind kindOrNull() {
        return ConversionKind.parse(kind);
    }

    List<TradeKey> trades() {
        return trades != null ? Collections.unmodifiableList(trades) : Collections.emptyList();
    }

    boolean covers(TradeKey key) {
        return key != null && trades != null && trades.contains(key);
    }

    /** Whether any of the sales this covers is among {@code sales}. */
    boolean touches(Set<TradeKey> sales) {
        if (sales == null || sales.isEmpty() || trades == null) {
            return false;
        }
        for (TradeKey key : trades) {
            if (sales.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Two corrections of the same guess: the same sales, in any order. */
    boolean sameTrades(ConversionRejection other) {
        if (other == null || trades == null || other.trades == null) {
            return false;
        }
        return new HashSet<>(trades).equals(new HashSet<>(other.trades));
    }
}
