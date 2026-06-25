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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountwideBackfillExecutionServiceTest {
    @Test
    public void attemptIfNeededDefersWhenWithinMinInterval() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 20_000L;
        hooks.loggedIn = true;
        hooks.linked = true;
        hooks.backfillReady = true;
        hooks.cycleResult = new AccountwideBackfillCoordinator.Result(false);
        AccountwideBackfillExecutionService service = new AccountwideBackfillExecutionService(10_000L, hooks);

        service.attemptIfNeeded();
        hooks.nowMs = 21_000L;
        service.attemptIfNeeded();

        assertEquals(1, hooks.runCycleCalls.get());
        assertEquals(1, hooks.requestAttemptCalls.get());
    }

    @Test
    public void attemptIfNeededSchedulesRetryWhenCycleRequestsRetry() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 50_000L;
        hooks.loggedIn = true;
        hooks.linked = true;
        hooks.backfillReady = true;
        hooks.cycleResult = new AccountwideBackfillCoordinator.Result(true);
        AccountwideBackfillExecutionService service = new AccountwideBackfillExecutionService(0L, hooks);

        service.attemptIfNeeded();

        assertEquals(1, hooks.runCycleCalls.get());
        assertEquals(1, hooks.scheduleRetryCalls.get());
    }

    @Test
    public void attemptIfNeededSkipsWhenNotReady() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_000L;
        hooks.loggedIn = false;
        hooks.linked = true;
        hooks.backfillReady = true;
        AccountwideBackfillExecutionService service = new AccountwideBackfillExecutionService(0L, hooks);

        service.attemptIfNeeded();

        assertEquals(0, hooks.runCycleCalls.get());
        assertEquals(0, hooks.requestAttemptCalls.get());
        assertEquals(0, hooks.scheduleRetryCalls.get());
    }

    private static final class TestHooks implements AccountwideBackfillExecutionService.Hooks {
        private boolean loggedIn;
        private boolean linked;
        private boolean backfillReady;
        private long nowMs;
        private final AtomicInteger requestAttemptCalls = new AtomicInteger();
        private final AtomicInteger runCycleCalls = new AtomicInteger();
        private final AtomicInteger scheduleRetryCalls = new AtomicInteger();
        private AccountwideBackfillCoordinator.Result cycleResult;

        @Override
        public boolean isClientLoggedIn() {
            return loggedIn;
        }

        @Override
        public boolean isLinked() {
            return linked;
        }

        @Override
        public boolean isBackfillReady() {
            return backfillReady;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
            requestAttemptCalls.incrementAndGet();
        }

        @Override
        public AccountwideBackfillCoordinator.Result runBackfillCycle() {
            runCycleCalls.incrementAndGet();
            return cycleResult;
        }

        @Override
        public void scheduleBackfillRetry() {
            scheduleRetryCalls.incrementAndGet();
        }
    }
}
