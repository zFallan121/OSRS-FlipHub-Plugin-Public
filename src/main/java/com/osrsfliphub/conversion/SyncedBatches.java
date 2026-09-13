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
 * Which read of the Grand Exchange history a synced trade came from.
 *
 * <p>A trade recovered from the history widget carries no timestamp of its
 * own, but one read of the widget is one batch, and the sync gives a batch a
 * shape that survives storage: every trade in it gets a synthetic slot
 * numbered upward from {@link Const#GE_HISTORY_SYNTHETIC_SLOT_START}
 * in the order the history lists them, and an invented timestamp that rises in
 * that same order. The next read starts numbering from the same slot again. So
 * walking synced trades in replay order, the slot climbs through one batch and
 * falls back at the start of the next - which is how this tells them apart
 * without anything having been stored to say so.
 *
 * <p>Two batches whose rows the sync interleaved in time - a second read
 * filling gaps a first one left in the middle of the list - can be told apart
 * only where the slots fall back. Where they do not, trades of two reads are
 * taken for one batch, and the history's order inside that stretch is still
 * what the slots say.
 *
 * <p>Nothing about this survives between replays; it is rebuilt from the
 * stored trades every time, like everything else the ledgers know.
 */
final class SyncedBatches {
    /** What {@link #batchOf} answers for a trade that was watched live. */
    static final int LIVE = 0;

    private int batch = LIVE;
    private int lastSlot = Integer.MIN_VALUE;

    /**
     * The batch this trade belongs to: {@link #LIVE} for a trade watched live,
     * else 1 upward in replay order. Must be fed every trade in the order they
     * are replayed.
     */
    int batchOf(Delta delta) {
        if (!TradeDeltaUtils.isSyncedFromHistory(delta)) {
            return LIVE;
        }
        if (batch == LIVE || delta.slot <= lastSlot) {
            batch++;
        }
        lastSlot = delta.slot;
        return batch;
    }

    void reset() {
        batch = LIVE;
        lastSlot = Integer.MIN_VALUE;
    }
}
