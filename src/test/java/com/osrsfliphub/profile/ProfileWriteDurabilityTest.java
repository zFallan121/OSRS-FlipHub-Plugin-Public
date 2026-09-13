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

import com.google.gson.Gson;
import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Trade history reaching disk, under the two conditions that used to lose it.
 *
 * <p>Several parts of the plugin write a profile file and they do not share a queue: the
 * coalescing writer on the IO pool, the flush when the client closes, the display-name stamp
 * on the game thread, and a wipe. Two of them writing one file at the same moment used to go
 * through one shared scratch file, which can publish half a document; half a document reads
 * back as an account with no trades at all, and the loader then saves that emptiness over the
 * top. Separately, an account was marked saved before the write was attempted, so a write that
 * failed threw the trades away with nothing said.
 */
public class ProfileWriteDurabilityTest {
    private static final long ACCOUNT = 123L;
    private static final long ACCOUNTWIDE = 0L;

    /**
     * Four threads writing the same profile at once. Whatever order they land in, the file has
     * to read back as one of the documents somebody actually wrote, never a mixture or a stump.
     */
    @Test
    public void writersRacingTheSameProfileNeverPublishAHalfWrittenFile() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-race");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            int[] sizes = {1, 400, 7, 900};
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            for (int round = 0; round < 40; round++) {
                CountDownLatch go = new CountDownLatch(1);
                List<Thread> writers = new ArrayList<>();
                for (int size : sizes) {
                    List<Delta> document = trades(size);
                    writers.add(new Thread(() -> {
                        try {
                            go.await();
                            long result = store.writeProfileData(ACCOUNT, ACCOUNTWIDE, "Zezima", document);
                            if (result < 0L) {
                                thrown.compareAndSet(null, new IllegalStateException(
                                    "a write of " + document.size() + " records failed"));
                            }
                        } catch (Throwable error) {
                            thrown.compareAndSet(null, error);
                        }
                    }));
                }
                writers.forEach(Thread::start);
                go.countDown();
                for (Thread writer : writers) {
                    writer.join();
                }
                if (thrown.get() != null) {
                    throw new AssertionError("round " + round, thrown.get());
                }

                ProfileData read = store.readProfileData(ACCOUNT, ACCOUNTWIDE);
                if (read == null || read.deltas == null) {
                    fail("round " + round + ": the profile no longer reads as a document, "
                        + "which the loader would treat as an account with no history");
                }
                int size = read.deltas.size();
                boolean expected = false;
                for (int candidate : sizes) {
                    expected |= size == candidate;
                }
                if (!expected) {
                    fail("round " + round + ": the profile holds " + size
                        + " records, which is none of the documents that were written");
                }
            }

            assertNoScratchFilesLeftBehind(baseDir);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /** A write that could not happen has to leave the account marked unsaved, not saved. */
    @Test
    public void aFailedSaveLeavesTheTradesQueuedForTheNextAttempt() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-failed-save");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Bridge.set(Guice.createInjector(
                binder -> binder.bind(ProfileStore.class).toInstance(store)));
            PluginState state = new PluginState();
            // Nothing to write through at first, standing in for storage that is not there yet.
            AtomicReference<ProfileStorage> storage = new AtomicReference<>();
            LocalTradesRuntime runtime = runtimeService(state, storage::get);

            synchronized (state.getLocalStatsLock()) {
                runtime.appendTradeDelta(ACCOUNT, trades(1).get(0));
            }
            runtime.persistLocalTrades(ACCOUNT);

            assertNull("nothing should have reached disk yet", store.readProfileData(ACCOUNT, ACCOUNTWIDE));

            // Storage comes back. The account must still be known to be unsaved.
            storage.set(new ProfileStorage(state));
            runtime.flushUnsavedProfiles();

            ProfileData read = store.readProfileData(ACCOUNT, ACCOUNTWIDE);
            assertNotNull("the trades were dropped when the first save failed", read);
            assertEquals(1, read.deltas.size());
            assertEquals(4151, read.deltas.get(0).itemId);
        } finally {
            Bridge.set(null);
            deleteRecursively(baseDir);
        }
    }

    /** And once it has been saved, the flush has nothing left to do. */
    @Test
    public void aSavedProfileIsNotWrittenAgainByTheFlush() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-clean-flush");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Bridge.set(Guice.createInjector(
                binder -> binder.bind(ProfileStore.class).toInstance(store)));
            PluginState state = new PluginState();
            AtomicReference<ProfileStorage> storage =
                new AtomicReference<>(new ProfileStorage(state));
            LocalTradesRuntime runtime = runtimeService(state, storage::get);

            synchronized (state.getLocalStatsLock()) {
                runtime.appendTradeDelta(ACCOUNT, trades(1).get(0));
            }
            runtime.persistLocalTrades(ACCOUNT);
            assertTrue(store.getProfileFileModifiedMs(store.getProfileFile(ACCOUNT, ACCOUNTWIDE)) > 0L);

            storage.set(null);
            // Nothing is unsaved, so the flush must not reach for storage it no longer has.
            runtime.flushUnsavedProfiles();

            ProfileData read = store.readProfileData(ACCOUNT, ACCOUNTWIDE);
            assertNotNull(read);
            assertEquals(1, read.deltas.size());
        } finally {
            Bridge.set(null);
            deleteRecursively(baseDir);
        }
    }

    private static LocalTradesRuntime runtimeService(
        PluginState state, Supplier<ProfileStorage> storageSupplier) {
        return new LocalTradesRuntime(
            ACCOUNTWIDE,
            Const.LOCAL_EVENT_BUCKET_MS,
            Const.DUPLICATE_TRADE_WINDOW_MS,
            state.getLocalStatsLock(),
            state.getLocalTradeDeltasByAccount(),
            state.getLoadedProfiles(),
            state.getLocalTradesLoadState(),
            () -> null,
            () -> null,
            () -> false,
            () -> null,
            storageSupplier,
            () -> { },
            () -> null,
            () -> null,
            () -> null,
            () -> { },
            () -> { }
        );
    }

    private static List<Delta> trades(int count) {
        List<Delta> deltas = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            deltas.add(new Delta(1000L + index, index % 8, 4151, true, 5, 500L, "BOUGHT", 100, true));
        }
        return deltas;
    }

    private static void assertNoScratchFilesLeftBehind(Path baseDir) throws IOException {
        try (Stream<Path> files = Files.list(baseDir.resolve("fliphub").resolve("profiles"))) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(path);
            }
        }
    }
}
