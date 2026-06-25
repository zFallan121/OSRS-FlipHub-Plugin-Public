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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeLimitFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        GeLimitFactoryService factory = new GeLimitFactoryService(2);
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.lookupResults.put(100, 10);
        hooks.lookupResults.put(200, 20);
        hooks.lookupResults.put(300, 30);

        GeLimitService service = factory.create(hooks);
        service.requestGeLimits(new HashSet<>(Arrays.asList(100, 200, 300)));
        hooks.runQueuedTasks();

        assertTrue(hooks.isClientFullyReadyCalls > 0);
        assertEquals(2, hooks.lookedUpItemIds.size());
        assertEquals(Integer.valueOf(10), service.getCachedGeLimit(100));
        assertEquals(Integer.valueOf(20), service.getCachedGeLimit(200));
        assertNull(service.getCachedGeLimit(300));
        assertEquals(1, hooks.onUpdatedCalls);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        GeLimitFactoryService factory = new GeLimitFactoryService(2);
        GeLimitService service = factory.create(null);

        service.requestGeLimits(new HashSet<>(Arrays.asList(100)));

        assertNull(service.getCachedGeLimit(100));
    }

    private static final class TestRuntimeHooks implements GeLimitFactoryService.RuntimeHooks {
        private final Map<Integer, Integer> lookupResults = new HashMap<>();
        private final List<Integer> lookedUpItemIds = new ArrayList<>();
        private final List<Runnable> queuedTasks = new ArrayList<>();
        private int isClientFullyReadyCalls;
        private int onUpdatedCalls;

        @Override
        public boolean isClientFullyReady() {
            isClientFullyReadyCalls++;
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
