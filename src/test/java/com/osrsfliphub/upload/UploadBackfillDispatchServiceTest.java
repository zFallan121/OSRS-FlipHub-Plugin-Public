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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UploadBackfillDispatchServiceTest {
    @Test
    public void requestEventFlushSkipsDuplicateWhileInFlight() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger flushCalls = new AtomicInteger();

            UploadBackfillDispatchService service = new UploadBackfillDispatchService(
                new BackfillRetryScheduler(1L, 10L),
                new HooksImpl(
                    io::execute,
                    () -> {
                        flushCalls.incrementAndGet();
                        started.countDown();
                        awaitLatch(release, 2);
                        done.countDown();
                    },
                    () -> { },
                    () -> { }
                )
            );

            service.requestEventFlush();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            service.requestEventFlush();

            release.countDown();
            assertTrue(done.await(1, TimeUnit.SECONDS));
            Thread.sleep(120);
            assertEquals(1, flushCalls.get());
        } finally {
            io.shutdownNow();
        }
    }

    @Test
    public void requestAccountwideSyncSkipsDuplicateWhileInFlight() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger syncCalls = new AtomicInteger();

            UploadBackfillDispatchService service = new UploadBackfillDispatchService(
                new BackfillRetryScheduler(1L, 10L),
                new HooksImpl(
                    io::execute,
                    () -> { },
                    () -> {
                        syncCalls.incrementAndGet();
                        started.countDown();
                        awaitLatch(release, 2);
                        done.countDown();
                    },
                    () -> { }
                )
            );

            service.requestAccountwideSync();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            service.requestAccountwideSync();

            release.countDown();
            assertTrue(done.await(1, TimeUnit.SECONDS));
            Thread.sleep(120);
            assertEquals(1, syncCalls.get());
        } finally {
            io.shutdownNow();
        }
    }

    @Test
    public void requestBackfillAttemptSchedulesAttempt() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch attempted = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();

            UploadBackfillDispatchService service = new UploadBackfillDispatchService(
                new BackfillRetryScheduler(1L, 10L),
                new HooksImpl(
                    Runnable::run,
                    () -> { },
                    () -> { },
                    () -> {
                        attempts.incrementAndGet();
                        attempted.countDown();
                    }
                )
            );

            service.requestBackfillAttempt(scheduler, 0L, true);

            assertTrue(attempted.await(1, TimeUnit.SECONDS));
            assertEquals(1, attempts.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void resetBackfillRetryStateCancelsPendingAttempt() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch attempted = new CountDownLatch(1);

            UploadBackfillDispatchService service = new UploadBackfillDispatchService(
                new BackfillRetryScheduler(1L, 10L),
                new HooksImpl(
                    Runnable::run,
                    () -> { },
                    () -> { },
                    attempted::countDown
                )
            );

            service.requestBackfillAttempt(scheduler, 5L, false);
            service.resetBackfillRetryState();

            assertFalse(attempted.await(350, TimeUnit.MILLISECONDS));
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch, int seconds) {
        if (latch == null) {
            return;
        }
        try {
            latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class HooksImpl implements UploadBackfillDispatchService.Hooks {
        private final java.util.function.Consumer<Runnable> ioExecutor;
        private final Runnable flushEvents;
        private final Runnable syncAccountwideSummary;
        private final Runnable attemptBackfill;

        private HooksImpl(java.util.function.Consumer<Runnable> ioExecutor,
                          Runnable flushEvents,
                          Runnable syncAccountwideSummary,
                          Runnable attemptBackfill) {
            this.ioExecutor = ioExecutor;
            this.flushEvents = flushEvents;
            this.syncAccountwideSummary = syncAccountwideSummary;
            this.attemptBackfill = attemptBackfill;
        }

        @Override
        public void executeIo(Runnable task) {
            if (ioExecutor != null) {
                ioExecutor.accept(task);
            }
        }

        @Override
        public void flushEvents() {
            if (flushEvents != null) {
                flushEvents.run();
            }
        }

        @Override
        public void syncAccountwideSummaryIfNeeded() {
            if (syncAccountwideSummary != null) {
                syncAccountwideSummary.run();
            }
        }

        @Override
        public void attemptAccountwideBackfillIfNeeded() {
            if (attemptBackfill != null) {
                attemptBackfill.run();
            }
        }
    }
}
