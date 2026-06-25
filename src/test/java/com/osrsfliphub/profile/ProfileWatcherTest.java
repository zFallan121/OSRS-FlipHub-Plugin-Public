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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileWatcherTest {
    @Test
    public void scheduleReloadDoesNotSkipWhenFileTimestampEqualsLoadedTimestamp() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger reloads = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            ProfileWatcher watcher = new ProfileWatcher(
                scheduler,
                10L,
                new TestHooks(1_000L, 1_000L, reloads, latch)
            );

            invokeScheduleReload(watcher, 42L, Path.of("hash_42.json"), true);

            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
            assertEquals(1, reloads.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scheduleReloadReschedulesToLatestEventInsteadOfDroppingNewerChange() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger reloads = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            ProfileWatcher watcher = new ProfileWatcher(
                scheduler,
                300L,
                new TestHooks(1_000L, 900L, reloads, latch)
            );

            invokeScheduleReload(watcher, 42L, Path.of("hash_42.json"), true);
            Thread.sleep(180L);
            invokeScheduleReload(watcher, 42L, Path.of("hash_42.json"), true);

            Thread.sleep(170L);
            assertEquals(0, reloads.get());
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
            assertEquals(1, reloads.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scheduleReloadFromPeriodicScanSkipsWhenTimestampIsNotNewer() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger reloads = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            ProfileWatcher watcher = new ProfileWatcher(
                scheduler,
                10L,
                new TestHooks(1_000L, 1_000L, reloads, latch)
            );

            invokeScheduleReload(watcher, 42L, Path.of("hash_42.json"), false);

            assertEquals(0, reloads.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static void invokeScheduleReload(ProfileWatcher watcher, long accountKey, Path file, boolean allowEqual)
        throws Exception {
        Method method = ProfileWatcher.class.getDeclaredMethod("scheduleReload", long.class, Path.class, boolean.class);
        method.setAccessible(true);
        method.invoke(watcher, accountKey, file, allowEqual);
    }

    private static final class TestHooks implements ProfileWatcher.Hooks {
        private final long fileMs;
        private final Long loadedMs;
        private final AtomicInteger reloads;
        private final CountDownLatch latch;

        private TestHooks(long fileMs, Long loadedMs, AtomicInteger reloads, CountDownLatch latch) {
            this.fileMs = fileMs;
            this.loadedMs = loadedMs;
            this.reloads = reloads;
            this.latch = latch;
        }

        @Override
        public Path getProfilesDir() {
            return null;
        }

        @Override
        public Path getLegacyProfilesDir() {
            return null;
        }

        @Override
        public long parseAccountKey(Path file) {
            return 42L;
        }

        @Override
        public long getProfileFileModifiedMs(Path file) {
            return fileMs;
        }

        @Override
        public Long getLoadedProfileFileMs(long accountKey) {
            return loadedMs;
        }

        @Override
        public void reloadProfile(long accountKey) {
            reloads.incrementAndGet();
            latch.countDown();
        }
    }
}
