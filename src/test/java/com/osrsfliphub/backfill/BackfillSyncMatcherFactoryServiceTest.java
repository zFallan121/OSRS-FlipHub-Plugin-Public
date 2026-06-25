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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BackfillSyncMatcherFactoryServiceTest {
    @Test
    public void createBuildsMatcherThatDelegatesToRuntimeHooks() {
        BackfillSyncMatcherFactoryService factory = new BackfillSyncMatcherFactoryService(16, 0.05d);
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        BackfillSyncMatcher matcher = factory.create(hooks);

        Set<Long> profileKeys = new HashSet<>();
        profileKeys.add(1L);
        profileKeys.add(2L);

        Map<Long, StatsSummary> local = new HashMap<>();
        local.put(1L, summary(100L, 1_000L, 20L, 1));
        local.put(2L, summary(200L, 2_000L, 40L, 2));

        StatsSummary remote = summary(1_000_000L, 2_000_000L, 500_000L, 900_000);

        Set<Long> synced = matcher.inferLikelySyncedProfiles(profileKeys, local, remote);

        assertNull(synced);
        assertTrue(hooks.isDebugEnabledCalls > 0);
        assertEquals(1, hooks.logDebugCalls);
        assertTrue(hooks.lastDebugMessage != null && hooks.lastDebugMessage.contains("exceeds threshold"));
    }

    @Test
    public void createWithNullRuntimeHooksReturnsMatcherWithoutHooks() {
        BackfillSyncMatcherFactoryService factory = new BackfillSyncMatcherFactoryService(16, 0.45d);
        BackfillSyncMatcher matcher = factory.create(null);

        Set<Long> profileKeys = new HashSet<>();
        profileKeys.add(1L);
        profileKeys.add(2L);

        Map<Long, StatsSummary> local = new HashMap<>();
        local.put(1L, summary(100L, 1_000L, 20L, 1));
        local.put(2L, summary(200L, 2_000L, 40L, 2));

        StatsSummary remote = summary(100L, 1_000L, 20L, 1);

        Set<Long> synced = matcher.inferLikelySyncedProfiles(profileKeys, local, remote);

        assertNotNull(synced);
        assertTrue(synced.contains(1L));
    }

    private static StatsSummary summary(long profit, long cost, long tax, int flips) {
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = profit;
        summary.total_cost_gp = cost;
        summary.tax_paid_gp = tax;
        summary.fill_count = flips;
        return summary;
    }

    private static final class TestRuntimeHooks implements BackfillSyncMatcherFactoryService.RuntimeHooks {
        private int isDebugEnabledCalls;
        private int logDebugCalls;
        private String lastDebugMessage;

        @Override
        public boolean isDebugEnabled() {
            isDebugEnabledCalls++;
            return true;
        }

        @Override
        public void logDebug(String message) {
            logDebugCalls++;
            lastDebugMessage = message;
        }
    }
}
