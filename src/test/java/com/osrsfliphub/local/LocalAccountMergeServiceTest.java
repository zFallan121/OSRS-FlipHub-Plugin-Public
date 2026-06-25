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
        LocalAccountMergeService service = new LocalAccountMergeService();
        Map<Long, List<LocalTradeDelta>> deltasByAccount = new HashMap<>();
        Map<Long, Long> sessionStarts = new HashMap<>();

        List<LocalTradeDelta> target = new ArrayList<>();
        LocalTradeDelta a = delta(1_000L, 1, 4151, true, 1, 1_000L);
        target.add(a);
        deltasByAccount.put(100L, target);

        List<LocalTradeDelta> source = new ArrayList<>();
        source.add(delta(1_000L, 1, 4151, true, 1, 1_000L)); // duplicate signature
        LocalTradeDelta b = delta(2_000L, 2, 561, false, 2, 500L);
        source.add(b);
        deltasByAccount.put(200L, source);

        sessionStarts.put(200L, 12345L);

        LocalAccountMergeService.Result result = service.merge(deltasByAccount, sessionStarts, 100L, 200L, 5000);

        assertTrue(result.changed);
        assertNotNull(result.mergedSnapshot);
        assertEquals(2, result.mergedSnapshot.size());
        assertEquals(Long.valueOf(1_000L), Long.valueOf(result.mergedSnapshot.get(0).tsClientMs));
        assertEquals(Long.valueOf(2_000L), Long.valueOf(result.mergedSnapshot.get(1).tsClientMs));
        assertNull(deltasByAccount.get(200L));
        assertEquals(Long.valueOf(12345L), sessionStarts.get(100L));
        assertNull(sessionStarts.get(200L));
    }

    @Test
    public void mergeTrimsOldestEntriesToMaxLocalTrades() {
        LocalAccountMergeService service = new LocalAccountMergeService();
        Map<Long, List<LocalTradeDelta>> deltasByAccount = new HashMap<>();
        Map<Long, Long> sessionStarts = new HashMap<>();

        List<LocalTradeDelta> target = new ArrayList<>();
        target.add(delta(1_000L, 1, 1, true, 1, 100L));
        deltasByAccount.put(10L, target);

        List<LocalTradeDelta> source = new ArrayList<>();
        source.add(delta(2_000L, 1, 2, true, 1, 200L));
        source.add(delta(3_000L, 1, 3, true, 1, 300L));
        deltasByAccount.put(20L, source);

        LocalAccountMergeService.Result result = service.merge(deltasByAccount, sessionStarts, 10L, 20L, 2);

        assertTrue(result.changed);
        assertEquals(2, result.mergedSnapshot.size());
        assertEquals(2, result.mergedSnapshot.get(0).itemId);
        assertEquals(3, result.mergedSnapshot.get(1).itemId);
    }

    @Test
    public void mergeWithEmptySourceDoesNotMarkChanged() {
        LocalAccountMergeService service = new LocalAccountMergeService();
        Map<Long, List<LocalTradeDelta>> deltasByAccount = new HashMap<>();
        Map<Long, Long> sessionStarts = new HashMap<>();

        List<LocalTradeDelta> target = new ArrayList<>();
        target.add(delta(1_000L, 1, 995, true, 1, 100L));
        deltasByAccount.put(10L, target);
        deltasByAccount.put(20L, new ArrayList<>());

        LocalAccountMergeService.Result result = service.merge(deltasByAccount, sessionStarts, 10L, 20L, 5000);

        assertFalse(result.changed);
        assertEquals(1, result.mergedSnapshot.size());
        assertNull(deltasByAccount.get(20L));
    }

    private static LocalTradeDelta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty, long deltaGp) {
        return new LocalTradeDelta(
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
