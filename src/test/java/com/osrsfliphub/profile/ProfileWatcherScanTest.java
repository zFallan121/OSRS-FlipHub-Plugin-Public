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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.osrsfliphub.RecordSyncWorld.bought;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Two RuneLite windows on one computer share the trade files, and each has to notice what the
 * other saved. A thread used to sit on the folder waiting to be told; while it did, Windows would
 * not let the folder be renamed, which is how RuneLite moves it. What is left is the look every
 * two seconds, and its two rules: the plugin's own save is not news, and a file is read again only
 * when it is newer than the last time it was read.
 */
public class ProfileWatcherScanTest {
    private static final long MAIN = 101L;
    private static final long ALT = 202L;
    private static final long MINUTE = 60_000L;
    /** The wait before the first look, and before a changed file is read: 20 ms here, a second in the client. */
    private static final long DEBOUNCE_MS = 20L;

    private RecordSyncWorld world;
    private ScheduledThreadPoolExecutor scheduler;
    private ProfileWatcher watcher;

    @Before
    public void setUp() throws Exception {
        world = RecordSyncWorld.fresh();
        // Not linked: a file read again sends nothing anywhere.
        world.linked = false;
        scheduler = new ScheduledThreadPoolExecutor(1);
        watcher = new ProfileWatcher(scheduler, DEBOUNCE_MS);
    }

    @After
    public void tearDown() throws Exception {
        watcher.stop();
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);
        RecordSyncWorld.close(world);
    }

    /**
     * The main is logged in here with 10 trades loaded. Its other window saves 12. Within one look
     * this window holds the 12.
     */
    @Test
    public void anotherWindowsSaveIsSeenByTheScanAndThatCharacterIsReadAgain() throws Exception {
        world.store(MAIN, purchases(10));
        world.login(MAIN);
        runtime().loadLocalTradesForAccount(MAIN, false);
        assertEquals(10, inMemory(MAIN).size());

        world.store(MAIN, purchases(12));
        savedLaterThanItWasRead(MAIN);
        watcher.start();

        await("the other window's 12 trades", () -> inMemory(MAIN).size() == 12);
        assertEquals("read, not written: the file is the other window's", 12, world.stored(MAIN).size());
    }

    /**
     * This window saves its 10 trades and records an 11th before anything else happens. Reading its
     * own save back would put the 10 in place of the 11. The alt's file, saved by the other window,
     * is read in the same look, which is how the test knows the look happened.
     */
    @Test
    public void thePluginsOwnSaveIsNotReadBackOverTradesRecordedSince() throws Exception {
        world.store(MAIN, purchases(9));
        world.login(MAIN);
        runtime().loadLocalTradesForAccount(MAIN, false);
        Delta[] more = purchases(11);
        synchronized (world.state.getLocalStatsLock()) {
            inMemoryList(MAIN).add(more[9]);
        }
        assertTrue(world.injector.getInstance(ProfileStorage.class).writeProfileData(MAIN, inMemory(MAIN)));
        synchronized (world.state.getLocalStatsLock()) {
            inMemoryList(MAIN).add(more[10]);
        }
        world.store(ALT, purchases(4));
        watcher.start();

        await("the alt's file, which the other window saved", () -> inMemory(ALT).size() == 4);
        settle();

        assertEquals("the trade recorded after the save is still here", 11, inMemory(MAIN).size());
        assertEquals(10, world.stored(MAIN).size());
    }

    /** A file nobody has touched since it was read is not read again, look after look. */
    @Test
    public void aFileThatHasNotChangedSinceItWasReadIsLeftAlone() throws Exception {
        world.store(MAIN, purchases(10));
        world.login(MAIN);
        runtime().loadLocalTradesForAccount(MAIN, false);
        // What a second read would wipe out: a trade in memory that the file does not hold.
        synchronized (world.state.getLocalStatsLock()) {
            inMemoryList(MAIN).add(purchases(11)[10]);
        }
        world.store(ALT, purchases(4));
        watcher.start();

        await("the alt's file", () -> inMemory(ALT).size() == 4);
        settle();

        assertEquals(11, inMemory(MAIN).size());
    }

    /**
     * No thread of the watcher's own, and nothing held open on the folder: with the watcher running
     * the folder can be renamed, which Windows refused while the old watch was on it.
     */
    @Test
    public void aRunningWatcherHasNoThreadOfItsOwnAndLeavesTheFolderFreeToBeRenamed() throws Exception {
        world.store(MAIN, purchases(3));
        watcher.start();
        await("the first look", () -> inMemory(MAIN).size() == 3);
        settle();

        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            assertFalse("a thread is watching the folder: " + thread.getName(),
                thread.getName().contains("profile-watch"));
        }
        Path renamed = world.dir.resolveSibling(world.dir.getFileName() + "-renamed");
        Files.move(world.dir, renamed);
        try {
            assertTrue(Files.isRegularFile(renamed.resolve("profiles").resolve("hash_" + MAIN + ".json")));
        } finally {
            Files.move(renamed, world.dir);
        }
    }

    // ---- helpers ----

    private LocalTradesRuntime runtime() {
        return world.injector.getInstance(LocalTradesRuntime.class);
    }

    /** Finished purchases an hour apart, the oldest first. */
    private static Delta[] purchases(int count) {
        Delta[] trades = new Delta[count];
        for (int i = 0; i < count; i++) {
            trades[i] = bought((count - i) * 60 * MINUTE + 24 * 60 * MINUTE, i % 8, 10);
        }
        return trades;
    }

    private List<Delta> inMemory(long key) {
        synchronized (world.state.getLocalStatsLock()) {
            List<Delta> trades = world.state.getLocalTradeDeltasByAccount().get(key);
            return trades != null ? new ArrayList<>(trades) : new ArrayList<>();
        }
    }

    private List<Delta> inMemoryList(long key) {
        return world.state.getLocalTradeDeltasByAccount().get(key);
    }

    /** Two saves can land in the same millisecond on a fast disk; a real second window's is seconds later. */
    private void savedLaterThanItWasRead(long key) throws Exception {
        Long readAt = world.state.getLoadedProfileFileMs().get(key);
        assertTrue("the file was read first", readAt != null && readAt > 0L);
        Files.setLastModifiedTime(world.file(key), FileTime.fromMillis(readAt + 5_000L));
    }

    /** Lets the first look, and whatever it asked to be read, finish. */
    private void settle() throws Exception {
        Thread.sleep(DEBOUNCE_MS * 4);
        scheduler.schedule(() -> { }, DEBOUNCE_MS * 2, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);
        scheduler.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    private static void await(String what, BooleanSupplier done) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (!done.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("never happened: " + what);
            }
            Thread.sleep(10L);
        }
    }
}
