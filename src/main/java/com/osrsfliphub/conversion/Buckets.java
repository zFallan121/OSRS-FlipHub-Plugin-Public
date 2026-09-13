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
 * The per-item inventory both ledgers already keep, seen through the four
 * operations a conversion needs.
 *
 * <p>Neither ledger stores anything new for this: they are both pure functions
 * over the persisted trade deltas, so a conversion is applied to the buckets
 * mid-replay and re-derived from scratch on the next read. Raw transaction
 * history is never written to.
 */
interface Buckets {
    long quantityOf(int itemId);

    /**
     * How much of {@link #quantityOf} was actually bought, excluding pieces
     * that came out of taking something apart.
     *
     * <p>Only bought stock may feed a recipe. A break's pieces are already
     * spoken for: the thing they came from paid for them, and the break is
     * still waiting to see them all sold before it can book that cost. Letting
     * one be consumed as an ingredient priced it at zero, because a break piece
     * carries no cost of its own, and stranded the break so it could never
     * complete. The recipe table makes that easy to hit, since a set that
     * breaks into pieces is usually matched by a recipe that builds it back.</p>
     */
    long convertibleQuantityOf(int itemId);

    /**
     * How much of {@link #quantityOf} may satisfy the sale being covered even
     * though it already happened: stock recovered from the GE history widget
     * rather than watched live, because only that stock has a timestamp the
     * plugin invented - less any of it the history itself lists after the
     * sale. One read of the history is one batch, imported in the history's
     * own order, and inside a batch that order is evidence. The buckets are
     * built knowing which sale is asking, so the answer is specific to it;
     * see {@link SyncedStock}.
     */
    long syncedQuantityOf(int itemId);

    long costOf(int itemId);

    /** Remove {@code quantity} units and the {@code cost} they carried. */
    void consume(int itemId, long quantity, long cost);

    /**
     * Add {@code quantity} units carrying {@code cost} as their basis, made by
     * {@code match}. Only a conversion that produced a single thing credits this
     * way, because only then does one item carry the whole cost.
     */
    void credit(int itemId, long quantity, long cost, Match match);

    /**
     * Add {@code quantity} units that belong to {@code pending} and carry no
     * cost of their own.
     *
     * <p>A conversion that produced several things credits every one of them
     * here. None of them is worth anything on its own - the break holds the
     * whole cost and stays open until the last piece is sold - so selling one
     * records no activity, only the coins it brought in.
     */
    void creditBreak(int itemId, long quantity, ConversionBreak pending);
}
