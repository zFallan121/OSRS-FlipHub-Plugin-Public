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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalAccountMergeServiceTest {
    @Test
    public void mergeCombinesSourceIntoTargetWithDedupeAndSessionTransfer() {
        AccountMerge service = new AccountMerge();
        Map<Long, List<Delta>> deltasByAccount = new HashMap<>();
        Map<Long, Long> sessionStarts = new HashMap<>();

        List<Delta> target = new ArrayList<>();
        Delta a = delta(1_000L, 1, 4151, true, 1, 1_000L);
        target.add(a);
        deltasByAccount.put(100L, target);

        List<Delta> source = new ArrayList<>();
        source.add(delta(1_000L, 1, 4151, true, 1, 1_000L)); // duplicate signature
        Delta b = delta(2_000L, 2, 561, false, 2, 500L);
        source.add(b);
        deltasByAccount.put(200L, source);

        sessionStarts.put(200L, 12345L);

        AccountMerge.Result result = service.merge(deltasByAccount, sessionStarts, 100L, 200L);

        assertTrue(result.changed);
        assertNotNull(result.mergedSnapshot);
        assertEquals(2, result.mergedSnapshot.size());
        assertEquals(Long.valueOf(1_000L), Long.valueOf(result.mergedSnapshot.get(0).tsClientMs));
        assertEquals(Long.valueOf(2_000L), Long.valueOf(result.mergedSnapshot.get(1).tsClientMs));
        assertNull(deltasByAccount.get(200L));
        assertEquals(Long.valueOf(12345L), sessionStarts.get(100L));
        assertNull(sessionStarts.get(200L));
    }

    /** Nothing is ever trimmed: the oldest purchase is what a later sale is priced against. */
    @Test
    public void mergeKeepsEveryEntryOldestFirst() {
        AccountMerge service = new AccountMerge();
        Map<Long, List<Delta>> deltasByAccount = new HashMap<>();
        Map<Long, Long> sessionStarts = new HashMap<>();

        List<Delta> target = new ArrayList<>();
        target.add(delta(1_000L, 1, 1, true, 1, 100L));
        deltasByAccount.put(10L, target);

        List<Delta> source = new ArrayList<>();
        source.add(delta(2_000L, 1, 2, true, 1, 200L));
        source.add(delta(3_000L, 1, 3, true, 1, 300L));
        deltasByAccount.put(20L, source);

        AccountMerge.Result result = service.merge(deltasByAccount, sessionStarts, 10L, 20L);

        assertTrue(result.changed);
        assertEquals(3, result.mergedSnapshot.size());
        assertEquals(1, result.mergedSnapshot.get(0).itemId);
        assertEquals(2, result.mergedSnapshot.get(1).itemId);
        assertEquals(3, result.mergedSnapshot.get(2).itemId);
    }

    @Test
    public void mergeWithEmptySourceDoesNotMarkChanged() {
        AccountMerge service = new AccountMerge();
        Map<Long, List<Delta>> deltasByAccount = new HashMap<>();
        Map<Long, Long> sessionStarts = new HashMap<>();

        List<Delta> target = new ArrayList<>();
        target.add(delta(1_000L, 1, 995, true, 1, 100L));
        deltasByAccount.put(10L, target);
        deltasByAccount.put(20L, new ArrayList<>());

        AccountMerge.Result result = service.merge(deltasByAccount, sessionStarts, 10L, 20L);

        assertFalse(result.changed);
        assertEquals(1, result.mergedSnapshot.size());
        assertNull(deltasByAccount.get(20L));
    }

    private static Delta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty, long deltaGp) {
        return new Delta(
            tsClientMs,
            slot,
            itemId,
            isBuy,
            deltaQty,
            deltaGp,
            "OFFER_UPDATED",
            100,
            false
        );
    }
}
