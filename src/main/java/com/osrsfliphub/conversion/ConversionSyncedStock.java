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
import java.util.Deque;
import java.util.Iterator;

/**
 * The history-synced units of one item, each remembered with the import it
 * came from and its place in that import.
 *
 * <p>Only synced stock may satisfy a sale that already happened, because only
 * synced stock has a timestamp the plugin invented. But the history's own
 * order is not invented, and inside one import the sync preserves it: a
 * higher synthetic slot is a later row. So a synced part the history lists
 * after a synced sale was bought after it, as surely as a live buy with a
 * later timestamp, and is no evidence the sale was made from it. Across two
 * imports nothing relates the slots, and the retry keeps its original
 * reasoning.
 *
 * <p>Units leave oldest first, except that a conversion drawing on this stock
 * spends the units it was entitled to before any it was not - so what is left
 * behind is what the next sale may still be refused.
 */
final class ConversionSyncedStock {
    private static final class Lot {
        private final int batch;
        private final int slot;
        private long quantity;

        private Lot(int batch, int slot, long quantity) {
            this.batch = batch;
            this.slot = slot;
            this.quantity = quantity;
        }

        /** Whether the batch's own order puts this lot after the given sale. */
        private boolean after(int saleBatch, int saleSlot) {
            return batch != ConversionSyncedBatches.LIVE && batch == saleBatch && slot > saleSlot;
        }
    }

    /** Oldest first. Null until the first synced unit arrives. */
    private Deque<Lot> lots;
    private long total;

    void add(int batch, int slot, long quantity) {
        if (quantity <= 0L) {
            return;
        }
        if (lots == null) {
            lots = new ArrayDeque<>();
        }
        lots.addLast(new Lot(batch, slot, quantity));
        total += quantity;
    }

    long total() {
        return total;
    }

    /**
     * How much of this stock the history does not place after the sale at
     * {@code saleSlot} of {@code saleBatch}: every other import's units, and
     * this import's up to the sale. A live sale - {@link ConversionSyncedBatches#LIVE}
     * - is placed by nothing, so nothing is excluded.
     */
    long quantityNotAfter(int saleBatch, int saleSlot) {
        if (lots == null || saleBatch == ConversionSyncedBatches.LIVE) {
            return total;
        }
        long eligible = 0L;
        for (Lot lot : lots) {
            if (!lot.after(saleBatch, saleSlot)) {
                eligible += lot.quantity;
            }
        }
        return eligible;
    }

    /**
     * Spend {@code quantity} units on the sale at {@code saleSlot} of
     * {@code saleBatch}: the units the sale was entitled to first, oldest
     * first within each kind. More than is held spends everything.
     */
    void consume(long quantity, int saleBatch, int saleSlot) {
        if (lots == null || quantity <= 0L) {
            return;
        }
        long remaining = take(quantity, saleBatch, saleSlot, false);
        if (remaining > 0L) {
            take(remaining, saleBatch, saleSlot, true);
        }
    }

    /** Keep at most {@code maxQuantity} units, dropping the oldest first. */
    void trimTo(long maxQuantity) {
        if (lots == null || total <= Math.max(0L, maxQuantity)) {
            return;
        }
        // Nothing is placed after a live sale, so every lot is eligible and
        // they go in age order.
        take(total - Math.max(0L, maxQuantity), ConversionSyncedBatches.LIVE, 0, false);
    }

    void clear() {
        lots = null;
        total = 0L;
    }

    /**
     * Remove up to {@code quantity} units from the lots that are - or, when
     * {@code fromAfter}, are not - the sale's own entitlement, oldest first.
     * Returns what could not be removed.
     */
    private long take(long quantity, int saleBatch, int saleSlot, boolean fromAfter) {
        long remaining = quantity;
        if (lots == null) {
            return remaining;
        }
        Iterator<Lot> iterator = lots.iterator();
        while (remaining > 0L && iterator.hasNext()) {
            Lot lot = iterator.next();
            if (lot.after(saleBatch, saleSlot) != fromAfter) {
                continue;
            }
            long taken = Math.min(remaining, lot.quantity);
            lot.quantity -= taken;
            total -= taken;
            remaining -= taken;
            if (lot.quantity <= 0L) {
                iterator.remove();
            }
        }
        if (lots.isEmpty()) {
            lots = null;
        }
        return remaining;
    }
}
