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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackfillRetrySchedulerTest {
    @Test
    public void requestAttemptExecutesTask() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            BackfillRetryScheduler retryScheduler = new BackfillRetryScheduler(1L, 10L);
            CountDownLatch latch = new CountDownLatch(1);

            retryScheduler.requestAttempt(scheduler, 0L, true, latch::countDown);

            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void resetCancelsPendingAttempt() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            BackfillRetryScheduler retryScheduler = new BackfillRetryScheduler(1L, 10L);
            CountDownLatch latch = new CountDownLatch(1);

            retryScheduler.requestAttempt(scheduler, 5L, false, latch::countDown);
            retryScheduler.reset();

            assertFalse(latch.await(300, TimeUnit.MILLISECONDS));
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scheduleRetryDoesNotReplaceSoonerExistingAttempt() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            BackfillRetryScheduler retryScheduler = new BackfillRetryScheduler(1L, 4L);
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);

            retryScheduler.scheduleRetry(scheduler, () -> {
                calls.incrementAndGet();
                latch.countDown();
            });
            retryScheduler.scheduleRetry(scheduler, () -> {
                calls.incrementAndGet();
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
