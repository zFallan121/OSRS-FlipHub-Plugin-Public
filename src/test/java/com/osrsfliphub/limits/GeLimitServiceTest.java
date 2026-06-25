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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GeLimitServiceTest {
    @Test
    public void requestGeLimitsCachesValuesAndRespectsMaxBatch() {
        TestHooks hooks = new TestHooks();
        hooks.lookupResults.put(100, 10);
        hooks.lookupResults.put(200, 20);
        hooks.lookupResults.put(300, 30);
        GeLimitService service = new GeLimitService(2, hooks);

        service.requestGeLimits(new HashSet<>(Arrays.asList(100, 200, 300)));
        hooks.runQueuedTasks();

        assertEquals(2, hooks.lookedUpItemIds.size());
        assertEquals(Integer.valueOf(10), service.getCachedGeLimit(100));
        assertEquals(Integer.valueOf(20), service.getCachedGeLimit(200));
        assertNull(service.getCachedGeLimit(300));
        assertEquals(1, hooks.onUpdatedCalls);
    }

    @Test
    public void requestGeLimitsSkipsPendingAndCached() {
        TestHooks hooks = new TestHooks();
        hooks.lookupResults.put(100, 10);
        GeLimitService service = new GeLimitService(5, hooks);

        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));
        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));
        hooks.runQueuedTasks();
        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));
        hooks.runQueuedTasks();

        assertEquals(1, hooks.lookedUpItemIds.size());
        assertEquals(Integer.valueOf(10), service.getCachedGeLimit(100));
    }

    @Test
    public void requestGeLimitsDoesNotPublishWhenAllLimitsMissing() {
        TestHooks hooks = new TestHooks();
        hooks.lookupResults.put(100, 0);
        GeLimitService service = new GeLimitService(5, hooks);

        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));
        hooks.runQueuedTasks();

        assertNull(service.getCachedGeLimit(100));
        assertEquals(0, hooks.onUpdatedCalls);
    }

    @Test
    public void requestGeLimitsHandlesLookupRuntimeExceptionAndClearsPending() {
        TestHooks hooks = new TestHooks();
        hooks.throwOnLookup.add(100);
        GeLimitService service = new GeLimitService(5, hooks);

        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));
        hooks.runQueuedTasks();
        assertNull(service.getCachedGeLimit(100));

        hooks.throwOnLookup.clear();
        hooks.lookupResults.put(100, 10);
        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));
        hooks.runQueuedTasks();

        assertEquals(2, hooks.lookedUpItemIds.size());
        assertEquals(Integer.valueOf(10), service.getCachedGeLimit(100));
        assertEquals(1, hooks.onUpdatedCalls);
    }

    private static final class TestHooks implements GeLimitService.Hooks {
        private final Map<Integer, Integer> lookupResults = new HashMap<>();
        private final List<Integer> lookedUpItemIds = new ArrayList<>();
        private final List<Runnable> queuedTasks = new ArrayList<>();
        private final Set<Integer> throwOnLookup = new HashSet<>();
        private int onUpdatedCalls = 0;

        @Override
        public boolean isClientFullyReady() {
            return true;
        }

        @Override
        public void invokeOnClientThread(Runnable task) {
            if (task != null) {
                queuedTasks.add(task);
            }
        }

        @Override
        public Integer lookupGeLimit(int itemId) {
            lookedUpItemIds.add(itemId);
            if (throwOnLookup.contains(itemId)) {
                throw new IllegalStateException("lookup failed");
            }
            return lookupResults.get(itemId);
        }

        @Override
        public void onLimitsUpdated() {
            onUpdatedCalls++;
        }

        @Override
        public void logDebug(String message) {
        }

        private void runQueuedTasks() {
            List<Runnable> copy = new ArrayList<>(queuedTasks);
            queuedTasks.clear();
            for (Runnable task : copy) {
                task.run();
            }
        }
    }
}
