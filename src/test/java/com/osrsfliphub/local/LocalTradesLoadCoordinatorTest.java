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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalTradesLoadCoordinatorTest {
    @Test
    public void ensureLocalTradesLoadedRequiresPositiveAccountKey() {
        TestHooks hooks = new TestHooks();
        LocalTradesLoadCoordinator coordinator = new LocalTradesLoadCoordinator(1_000L, hooks);

        coordinator.ensureLocalTradesLoaded(0L);
        coordinator.ensureLocalTradesLoaded(-1L);
        assertEquals(0, hooks.ensureProfileLoadedCalls);
        assertEquals(0, hooks.markLoadedCalls);

        coordinator.ensureLocalTradesLoaded(42L);
        assertEquals(1, hooks.ensureProfileLoadedCalls);
        assertEquals(1, hooks.markLoadedCalls);
        assertEquals(42L, hooks.lastLoadedAccountHash);
    }

    @Test
    public void scheduleLocalTradesLoadHonorsRetryWindow() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_000L;
        hooks.accountHash = 42L;
        LocalTradesLoadCoordinator coordinator = new LocalTradesLoadCoordinator(1_000L, hooks);
        LocalTradesLoadCoordinator.State state = new LocalTradesLoadCoordinator.State();

        coordinator.scheduleLocalTradesLoad(state, null, false);
        assertEquals(1, hooks.ensureProfileLoadedCalls);
        assertEquals(1, hooks.markLoadedCalls);

        hooks.nowMs = 1_500L;
        coordinator.scheduleLocalTradesLoad(state, null, false);
        assertEquals(1, hooks.ensureProfileLoadedCalls);
        assertEquals(1, hooks.markLoadedCalls);

        hooks.nowMs = 2_200L;
        coordinator.scheduleLocalTradesLoad(state, null, false);
        assertEquals(2, hooks.ensureProfileLoadedCalls);
        assertEquals(2, hooks.markLoadedCalls);
    }

    @Test
    public void scheduleLocalTradesLoadUsesClientThreadAndScheduler() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            TestHooks hooks = new TestHooks();
            hooks.nowMs = 1_000L;
            hooks.accountHash = 55L;
            hooks.runClientThreadImmediately = true;
            hooks.markLoadedLatch = new CountDownLatch(1);
            LocalTradesLoadCoordinator coordinator = new LocalTradesLoadCoordinator(1_000L, hooks);
            LocalTradesLoadCoordinator.State state = new LocalTradesLoadCoordinator.State();

            coordinator.scheduleLocalTradesLoad(state, scheduler, true);

            assertTrue(hooks.markLoadedLatch.await(1, TimeUnit.SECONDS));
            assertEquals(1, hooks.invokeOnClientThreadCalls);
            assertEquals(1, hooks.scheduleAsyncCalls);
            assertEquals(1, hooks.ensureProfileLoadedCalls);
            assertEquals(1, hooks.markLoadedCalls);
            assertEquals(55L, hooks.lastLoadedAccountHash);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void attemptAndLoadSkipInvalidAccountHash() {
        TestHooks hooks = new TestHooks();
        hooks.accountHash = 0L;
        LocalTradesLoadCoordinator coordinator = new LocalTradesLoadCoordinator(1_000L, hooks);

        coordinator.attemptLocalTradesLoad();
        coordinator.loadLocalTradesAsync(0L);
        assertEquals(0, hooks.ensureProfileLoadedCalls);
        assertEquals(0, hooks.markLoadedCalls);
    }

    private static final class TestHooks implements LocalTradesLoadCoordinator.Hooks {
        private long nowMs;
        private long accountHash;
        private boolean runClientThreadImmediately;
        private CountDownLatch markLoadedLatch;

        private int invokeOnClientThreadCalls;
        private int scheduleAsyncCalls;
        private int ensureProfileLoadedCalls;
        private int markLoadedCalls;
        private long lastLoadedAccountHash;

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public long resolveAccountHash() {
            return accountHash;
        }

        @Override
        public void invokeOnClientThread(Runnable task) {
            invokeOnClientThreadCalls++;
            if (runClientThreadImmediately && task != null) {
                task.run();
            }
        }

        @Override
        public void scheduleAsync(ScheduledExecutorService scheduler, Runnable task) {
            scheduleAsyncCalls++;
            if (scheduler != null && task != null) {
                scheduler.execute(task);
            }
        }

        @Override
        public void ensureProfileLoaded(long accountHash) {
            ensureProfileLoadedCalls++;
            lastLoadedAccountHash = accountHash;
        }

        @Override
        public void markLocalTradesLoaded() {
            markLoadedCalls++;
            if (markLoadedLatch != null) {
                markLoadedLatch.countDown();
            }
        }
    }
}
