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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The two pieces of bookkeeping behind the synced retry's respect for the
 * history's own order: telling one read of the history from the next, and
 * remembering which read each synced unit came from.
 */
public class ConversionSyncedStockTest {
    private static final int SYNCED = Const.GE_HISTORY_SYNTHETIC_SLOT_START;
    private static final int LIVE = SyncedBatches.LIVE;

    private static Delta delta(int slot) {
        return new Delta(1_000L, slot, 4151, true, 1, 100L, "OFFER_COMPLETED", 100, false);
    }

    @Test
    public void batchesRestartWhereTheSlotsFallBack() {
        SyncedBatches batches = new SyncedBatches();

        assertEquals(LIVE, batches.batchOf(delta(3)));
        assertEquals(1, batches.batchOf(delta(SYNCED)));
        assertEquals(1, batches.batchOf(delta(SYNCED + 1)));
        // A live trade between two synced ones does not end the read.
        assertEquals(LIVE, batches.batchOf(delta(5)));
        // A record dropped or deduped on the way leaves a gap; the read runs on.
        assertEquals(1, batches.batchOf(delta(SYNCED + 3)));
        // The next read starts numbering from the same slot again.
        assertEquals(2, batches.batchOf(delta(SYNCED)));
        assertEquals(2, batches.batchOf(delta(SYNCED + 1)));
        assertEquals(LIVE, batches.batchOf(null));

        batches.reset();
        assertEquals(1, batches.batchOf(delta(SYNCED + 7)));
    }

    @Test
    public void whatTheHistoryListsAfterASaleIsNotAvailableToIt() {
        SyncedStock stock = new SyncedStock();
        stock.add(1, SYNCED, 2);
        stock.add(2, SYNCED + 1, 1);
        stock.add(2, SYNCED + 4, 3);
        assertEquals(6L, stock.total());

        // A sale at slot SYNCED + 2 of the second read: the second read's
        // later lot is placed after it, nothing else is.
        assertEquals(3L, stock.quantityNotAfter(2, SYNCED + 2));
        // The same slot number in the first read places only the first read.
        assertEquals(4L, stock.quantityNotAfter(1, SYNCED - 1));
        // A live sale is placed by nothing.
        assertEquals(6L, stock.quantityNotAfter(LIVE, 0));

        stock.clear();
        assertEquals(0L, stock.total());
        assertEquals(0L, stock.quantityNotAfter(2, SYNCED + 2));
    }

    @Test
    public void consumingSpendsWhatTheSaleWasEntitledToFirst() {
        SyncedStock stock = new SyncedStock();
        stock.add(1, SYNCED, 1);
        stock.add(2, SYNCED + 1, 1);
        stock.add(2, SYNCED + 3, 1);

        // A sale at slot SYNCED + 2 of the second read draws two units: the
        // two it was entitled to go, and the one listed after it stays - so
        // the next sale of that read is still refused it.
        stock.consume(2L, 2, SYNCED + 2);
        assertEquals(1L, stock.total());
        assertEquals(0L, stock.quantityNotAfter(2, SYNCED + 2));
        assertEquals(1L, stock.quantityNotAfter(2, SYNCED + 5));

        // More than is held spends everything, entitled or not.
        stock.consume(5L, 2, SYNCED + 2);
        assertEquals(0L, stock.total());
        stock.consume(1L, LIVE, 0);
        assertEquals(0L, stock.total());
    }

    @Test
    public void trimmingDropsTheOldestFirst() {
        // A plain sale never says which synced units went with it, so the
        // oldest are taken to have - which is what stops units already sold
        // from answering for ones the history lists after a later sale.
        SyncedStock stock = new SyncedStock();
        stock.add(1, SYNCED, 2);
        stock.add(2, SYNCED + 1, 1);

        stock.trimTo(1L);
        assertEquals(1L, stock.total());
        assertEquals("only the second read's unit is left, and it is after the sale", 0L,
            stock.quantityNotAfter(2, SYNCED));
        assertEquals(1L, stock.quantityNotAfter(1, SYNCED));

        stock.trimTo(5L);
        assertEquals(1L, stock.total());
        stock.trimTo(0L);
        assertEquals(0L, stock.total());
        stock.trimTo(-1L);
        assertEquals(0L, stock.total());
    }
}
