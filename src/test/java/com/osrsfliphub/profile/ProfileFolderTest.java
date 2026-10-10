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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Guice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The trade files in the folder RuneLite gives the plugin, {@code .runelite/plugin-data/osrs-fliphub}.
 *
 * <p>Every player's trades were in {@code .runelite/fliphub/profiles}. RuneLite renames that folder
 * to the new place the first time the plugin asks for its folder, so the old folder itself becomes
 * the new one and {@code profiles} arrives inside it. Nothing about the files changes, and that is
 * what is proved here the way a saving change has to be: on real folders, saved, moved, read back
 * by a store that has never seen them, and compared.
 */
public class ProfileFolderTest {
    private static final long ACCOUNTWIDE = Const.ACCOUNTWIDE_KEY;
    private static final long MAIN = 101L;
    private static final long ALT = 202L;
    private static final long NEWER = 303L;
    private static final int SHARK = 385;

    /** A record of a kind this build does not know, with a field on it and on its part that it does not know either. */
    private static final String FUTURE_RECORD = "{\"kind\":\"SOME_FUTURE_KIND\",\"name\":\"a future record\","
        + "\"inputs\":[{\"trade\":{\"tsMs\":1000,\"slot\":1,\"itemId\":4151},\"quantity\":5,\"gp\":500,\"futurePart\":7}],"
        + "\"feeGp\":0,\"recordedMs\":9000,\"toAccount\":456,\"futureField\":\"keep me\"}";
    private static final String NEWER_DOCUMENT = "{\"accountHash\":303,\"displayName\":\"Newer\",\"deltas\":["
        + "{\"tsClientMs\":1000,\"slot\":1,\"itemId\":4151,\"isBuy\":true,\"deltaQty\":5,\"deltaGp\":500,"
        + "\"eventType\":\"OFFER_COMPLETED\",\"price\":100,\"baselineSynthetic\":false}],"
        + "\"updatedMs\":1,\"recipeFlips\":[" + FUTURE_RECORD + "],\"futureMember\":{\"a\":1}}";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final Gson gson = new Gson();

    @After
    public void forgetTheInjector() {
        Bridge.set(null);
    }

    /** Where the build before this one kept everything. */
    private Path oldFolder() {
        return temp.getRoot().toPath().resolve(".runelite").resolve("fliphub");
    }

    /** Where RuneLite puts it. */
    private Path newFolder() {
        return temp.getRoot().toPath().resolve(".runelite").resolve("plugin-data").resolve("osrs-fliphub");
    }

    /**
     * A main with 5,000 trades, three recipes and a move to its alt; the alt with 40 trades; a
     * character last saved by a newer build; and the accountwide file. Renamed as RuneLite renames
     * it, every file is byte for byte what it was, and reads, saves and reads again as what it was.
     */
    @Test
    public void theOldFolderMovedAsRuneLiteMovesItReadsBackIdentical() throws Exception {
        List<Delta> mainTrades = trades(5_000);
        List<RecipeFlip> mainRecords = records(mainTrades);
        List<Delta> altTrades = trades(40);
        ProfileStore olderBuild = Folders.store(gson, oldFolder());
        olderBuild.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", mainTrades, mainRecords);
        olderBuild.writeProfileData(ALT, ACCOUNTWIDE, "Sips Potion", altTrades, null);
        Folders.write(olderBuild.getProfileFile(NEWER, ACCOUNTWIDE), NEWER_DOCUMENT);
        olderBuild.writeProfileData(ACCOUNTWIDE, ACCOUNTWIDE, "Accountwide", mainTrades, null);
        Map<String, byte[]> before = files(oldFolder().resolve("profiles"));
        assertEquals(new TreeSet<>(Arrays.asList("accountwide.json", "hash_101.json", "hash_202.json", "hash_303.json")),
            before.keySet());

        Filepath folder = Folders.moveAsRuneLiteDoes(oldFolder(), newFolder());

        assertFalse("nothing is left at the old place", Files.exists(oldFolder()));
        Map<String, byte[]> after = files(newFolder().resolve("profiles"));
        assertEquals(before.keySet(), after.keySet());
        for (String name : before.keySet()) {
            assertArrayEquals(name + " is byte for byte what it was", before.get(name), after.get(name));
        }

        // The updated plugin, started for the first time: it has read none of these.
        ProfileStore store = new ProfileStore(gson, () -> folder);
        ProfileData main = store.readProfileData(MAIN, ACCOUNTWIDE);
        assertEquals("Zezima", main.displayName);
        assertEquals(5_000, main.deltas.size());
        assertEquals(gson.toJsonTree(mainTrades), gson.toJsonTree(main.deltas));
        assertEquals("a purchase past 2,147,483,647 coins", 2_394_000_000L, main.deltas.get(4_999).deltaGp);
        assertEquals("three recipes and a move", 4, main.recipeFlips.size());
        assertEquals(gson.toJsonTree(mainRecords), gson.toJsonTree(main.recipeFlips));
        assertEquals(Long.valueOf(ALT), main.recipeFlips.get(3).toAccount);
        ProfileData alt = store.readProfileData(ALT, ACCOUNTWIDE);
        assertEquals("Sips Potion", alt.displayName);
        assertEquals(gson.toJsonTree(altTrades), gson.toJsonTree(alt.deltas));
        ProfileData newer = store.readProfileData(NEWER, ACCOUNTWIDE);
        assertEquals(1, newer.deltas.size());
        ProfileData pool = store.readProfileData(ACCOUNTWIDE, ACCOUNTWIDE);
        assertEquals(gson.toJsonTree(mainTrades), gson.toJsonTree(pool.deltas));

        // And saved again by it, as the first trade after the update saves them.
        store.writeProfileData(MAIN, ACCOUNTWIDE, main.displayName, main.deltas, main.recipeFlips);
        store.writeProfileData(ALT, ACCOUNTWIDE, alt.displayName, alt.deltas, alt.recipeFlips);
        RecipeFlipStore newerRecords = new RecipeFlipStore();
        newerRecords.replace(NEWER, newer.recipeFlips);
        store.writeProfileData(NEWER, ACCOUNTWIDE, newer.displayName, newer.deltas, newerRecords.snapshotForFile(NEWER));
        store.writeProfileData(ACCOUNTWIDE, ACCOUNTWIDE, pool.displayName, pool.deltas, pool.recipeFlips);

        Map<String, byte[]> saved = files(newFolder().resolve("profiles"));
        assertEquals("no file more or less, and no scratch file", before.keySet(), saved.keySet());
        for (String name : Arrays.asList("accountwide.json", "hash_101.json", "hash_202.json")) {
            assertEquals(name + " holds what it held, but for when it was saved",
                document(before.get(name)), document(saved.get(name)));
        }
        JsonObject newerSaved = new JsonParser().parse(text(saved.get("hash_303.json"))).getAsJsonObject();
        assertEquals("the record this build does not know, exactly as it was written",
            new JsonParser().parse(FUTURE_RECORD), newerSaved.getAsJsonArray("recipeFlips").get(0));
        assertEquals(new JsonParser().parse("{\"a\":1}"), newerSaved.get("futureMember"));
        assertEquals(1, newerSaved.getAsJsonArray("deltas").size());
        assertEquals(gson.toJsonTree(newer.deltas),
            gson.toJsonTree(Folders.store(gson, newFolder()).readProfileData(NEWER, ACCOUNTWIDE).deltas));
        assertFalse("the plugin never makes the old folder again", Files.exists(oldFolder()));
    }

    /** RuneLite makes {@code plugin-data} and nothing in it: the first save makes the rest. */
    @Test
    public void aNewPlayersFirstSaveMakesTheProfilesFolderAndReadsBack() throws Exception {
        Files.createDirectories(newFolder().getParent());
        ProfileStore store = Folders.store(gson, newFolder());
        Filepath file = store.getProfileFile(MAIN, ACCOUNTWIDE);

        assertNull("a player with no file has no history, and that is not an error",
            store.readProfileData(MAIN, ACCOUNTWIDE));
        assertEquals(0L, store.getProfileFileModifiedMs(file));

        List<Delta> trades = trades(3);
        assertTrue("saved", store.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades) >= 0L);

        assertEquals(newFolder().resolve("profiles").resolve("hash_101.json"), Folders.path(file));
        assertTrue(Files.isRegularFile(newFolder().resolve("profiles").resolve("hash_101.json")));
        assertEquals(new TreeSet<>(Arrays.asList("hash_101.json")), files(newFolder().resolve("profiles")).keySet());
        // After a restart: a store that never wrote it.
        ProfileData read = Folders.store(gson, newFolder()).readProfileData(MAIN, ACCOUNTWIDE);
        assertEquals("Zezima", read.displayName);
        assertEquals(gson.toJsonTree(trades), gson.toJsonTree(read.deltas));
        assertTrue(store.getProfileFileModifiedMs(file) > 0L);
    }

    /**
     * While RuneLite cannot hand the folder over there is nowhere to read or save, and a store asked
     * anyway makes nothing. The moment the folder is there the same store reads what is in it: "no
     * folder yet" is never remembered as "no history".
     */
    @Test
    public void aStoreWhoseFolderIsNotReadyMakesNothingAndThenReadsTheFolderThatArrives() throws Exception {
        List<Delta> trades = trades(5_000);
        Folders.store(gson, oldFolder()).writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades);
        Map<String, byte[]> before = files(oldFolder().resolve("profiles"));
        AtomicReference<Filepath> folder = new AtomicReference<>();
        ProfileStore store = new ProfileStore(gson, folder::get);

        assertNull(store.getProfilesDir());
        assertNull(store.getProfileFile(MAIN, ACCOUNTWIDE));
        assertNull(store.readProfileData(MAIN, ACCOUNTWIDE));
        assertEquals(0L, store.getProfileFileModifiedMs(store.getProfileFile(MAIN, ACCOUNTWIDE)));
        store.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades(4));

        assertFalse("nothing was made at the new place", Files.exists(newFolder()));
        assertEquals(before.keySet(), files(oldFolder().resolve("profiles")).keySet());
        assertArrayEquals("the old file is whole", before.get("hash_101.json"),
            files(oldFolder().resolve("profiles")).get("hash_101.json"));
        for (String stray : new String[] {"profiles", "null"}) {
            assertFalse("nothing was made in the folder RuneLite runs from", Files.exists(Path.of(stray)));
        }

        folder.set(Folders.moveAsRuneLiteDoes(oldFolder(), newFolder()));

        ProfileData read = store.readProfileData(MAIN, ACCOUNTWIDE);
        assertEquals(5_000, read.deltas.size());
        assertEquals(gson.toJsonTree(trades), gson.toJsonTree(read.deltas));
        assertArrayEquals("and reading it changed nothing on disk", before.get("hash_101.json"),
            files(newFolder().resolve("profiles")).get("hash_101.json"));
    }

    /**
     * The trap in full. A window with no folder is asked to save 4 trades all the same. Answered
     * "not saved", they would stay marked unsaved and be flushed over the 5,000 the moment the folder
     * arrived. So the save is answered as it always was with nowhere to save: done, nothing written,
     * nothing left owed. After the folder arrives and a flush, the file is the 5,000, to the byte.
     */
    @Test
    public void aSaveAskedWhileThereIsNoFolderIsNotOwedAndIsNeverFlushedOverTheFolderThatArrives() throws Exception {
        List<Delta> trades = trades(5_000);
        Folders.store(gson, oldFolder()).writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades);
        byte[] before = files(oldFolder().resolve("profiles")).get("hash_101.json");
        AtomicReference<Filepath> folder = new AtomicReference<>();
        ProfileStore store = new ProfileStore(gson, folder::get);
        Bridge.set(Guice.createInjector(binder -> binder.bind(ProfileStore.class).toInstance(store)));
        // No plugin, so no pool for file work: a save is written where it is asked for.
        Access.set(null);
        PluginState state = new PluginState();
        ProfileStorage storage = new ProfileStorage(state);
        LocalTradesRuntime runtime = new LocalTradesRuntime(
            state, () -> null, () -> null, () -> storage, () -> null, () -> null, () -> null, () -> null);

        synchronized (state.getLocalStatsLock()) {
            for (Delta fill : trades(4)) {
                runtime.appendTradeDelta(MAIN, fill);
            }
        }
        runtime.persistLocalTrades(MAIN);

        assertFalse("nothing was made at the new place", Files.exists(newFolder()));
        assertArrayEquals("the old file is whole", before, files(oldFolder().resolve("profiles")).get("hash_101.json"));

        folder.set(Folders.moveAsRuneLiteDoes(oldFolder(), newFolder()));
        runtime.flushUnsavedProfiles();

        Map<String, byte[]> after = files(newFolder().resolve("profiles"));
        assertEquals(new TreeSet<>(Arrays.asList("hash_101.json")), after.keySet());
        assertArrayEquals("the file still holds the 5,000, to the byte", before, after.get("hash_101.json"));
        assertEquals(5_000, Folders.store(gson, newFolder()).readProfileData(MAIN, ACCOUNTWIDE).deltas.size());
        assertNull("the 4 were never remembered as a file the plugin wrote", state.getSelfWrittenProfileFileMs().get(MAIN));
        // The answer that keeps them from being owed: what a store with nowhere to save has always said.
        assertEquals(0L, new ProfileStore(gson, () -> null).writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades(4)));
    }

    /** "Wipe local data" for one character and for the accountwide pool: the files are emptied where they now are. */
    @Test
    public void aWipeEmptiesTheFilesInTheNewFolderAndReportsDone() throws Exception {
        List<Delta> trades = trades(40);
        ProfileStore store = Folders.store(gson, newFolder());
        store.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades, records(trades));
        store.writeProfileData(ALT, ACCOUNTWIDE, "Sips Potion", trades(7), null);
        store.writeProfileData(ACCOUNTWIDE, ACCOUNTWIDE, "Accountwide", trades, null);
        byte[] altBefore = files(newFolder().resolve("profiles")).get("hash_202.json");
        ProfileWipeDataService wipe = wipeOver(store);

        assertTrue("the character's file is cleared", wipe.clearProfileDataForWipe(MAIN, "Zezima"));
        assertTrue("the accountwide file is cleared", wipe.clearAccountwideDataForWipe());

        // As the next start reads them.
        ProfileStore restarted = Folders.store(gson, newFolder());
        ProfileData main = restarted.readProfileData(MAIN, ACCOUNTWIDE);
        assertEquals(0, main.deltas.size());
        assertTrue(main.recipeFlips == null || main.recipeFlips.isEmpty());
        assertEquals(0, restarted.readProfileData(ACCOUNTWIDE, ACCOUNTWIDE).deltas.size());
        Map<String, byte[]> after = files(newFolder().resolve("profiles"));
        assertEquals(new TreeSet<>(Arrays.asList("accountwide.json", "hash_101.json", "hash_202.json")), after.keySet());
        assertArrayEquals("the character nobody wiped is untouched", altBefore, after.get("hash_202.json"));
    }

    /** A wipe that could not reach the file must not tell the player their history is gone. */
    @Test
    public void aWipeThatCouldNotWriteTheFileDoesNotReportDone() throws Exception {
        ProfileStore store = Folders.store(gson, newFolder());
        // Something that cannot be replaced by a file sits where the file goes.
        Files.createDirectories(newFolder().resolve("profiles").resolve("hash_101.json").resolve("in-the-way"));

        assertFalse(wipeOver(store).clearProfileDataForWipe(MAIN, "Zezima"));
    }

    /**
     * {@code -Dfliphub.dataDir=dev}, which the development client sets so that it does not share
     * files with the Plugin Hub build on the same computer: a folder of its own inside the plugin's.
     */
    @Test
    public void theDataDirSwitchSavesInAFolderOfItsOwnInsideThePluginFolder() throws Exception {
        withDataDir("dev", () -> {
            ProfileStore store = Folders.store(gson, newFolder());
            List<Delta> trades = trades(3);

            assertTrue(store.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades) >= 0L);

            assertTrue(Files.isRegularFile(newFolder().resolve("dev").resolve("profiles").resolve("hash_101.json")));
            assertEquals("and nowhere else", Arrays.asList("dev"), names(newFolder()));
            assertEquals(gson.toJsonTree(trades),
                gson.toJsonTree(Folders.store(gson, newFolder()).readProfileData(MAIN, ACCOUNTWIDE).deltas));
        });
    }

    /** A reserved Windows name, a way out of the folder, a name Windows would cut short, a drive. */
    @Test
    public void aDataDirNameRuneLiteRefusesIsIgnored() throws Exception {
        for (String refused : new String[] {"nul", "..", "dev.", "x:y"}) {
            Path folder = temp.newFolder().toPath().resolve("osrs-fliphub");
            withDataDir(refused, () -> {
                ProfileStore store = Folders.store(gson, folder);

                assertTrue("saved, with the switch set to " + refused,
                    store.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades(3)) >= 0L);

                assertTrue("with the switch set to " + refused,
                    Files.isRegularFile(folder.resolve("profiles").resolve("hash_101.json")));
                assertEquals("with the switch set to " + refused, Arrays.asList("profiles"), names(folder));
                assertEquals(Arrays.asList("osrs-fliphub"), names(folder.getParent()));
            });
        }
    }

    /** With the switch unset, which is every player: straight into the plugin folder's {@code profiles}. */
    @Test
    public void withoutTheSwitchTheFilesAreInThePluginFoldersProfiles() throws Exception {
        withDataDir(null, () -> {
            ProfileStore store = Folders.store(gson, newFolder());

            store.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades(3));
            store.writeProfileData(ACCOUNTWIDE, ACCOUNTWIDE, "Accountwide", trades(3));

            assertEquals(newFolder().resolve("profiles"), Folders.path(store.getProfilesDir()));
            assertEquals(new TreeSet<>(Arrays.asList("accountwide.json", "hash_101.json")),
                files(newFolder().resolve("profiles")).keySet());
        });
    }

    // ---- what is in the files ----

    /**
     * Finished offers ten minutes apart: 100 sharks bought at 1,000 each, then sold at 1,100 each
     * (1,078 each after tax), over and over. The last is one item bought for 2,394,000,000.
     */
    static List<Delta> trades(int count) {
        List<Delta> trades = new ArrayList<>();
        for (int i = 0; i < count - 1; i++) {
            long first = 1_700_000_000_000L + i * 600_000L;
            boolean buy = i % 2 == 0;
            trades.add(new Delta(first, i % 8, SHARK, buy, 100, buy ? 100_000L : 107_800L, "OFFER_COMPLETED",
                buy ? 1_000 : 1_100, false, first - 5_000L, first + 1_000L));
        }
        long last = 1_700_000_000_000L + (count - 1) * 600_000L;
        trades.add(new Delta(last, (count - 1) % 8, 20011, true, 1, 2_394_000_000L, "OFFER_COMPLETED",
            Integer.MAX_VALUE, false, last - 5_000L, last + 1_000L));
        return trades;
    }

    /** Three recipes, each one purchase made into what one sale sold, and 60 of a fourth purchase moved to the alt. */
    static List<RecipeFlip> records(List<Delta> trades) {
        List<RecipeFlip> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Delta bought = trades.get(i * 2);
            Delta sold = trades.get(i * 2 + 1);
            records.add(new RecipeFlip(ConversionKind.ASSEMBLE, "Recipe " + (i + 1),
                new ArrayList<>(List.of(new RecipeFlip.Part(TradeKey.of(bought), 100, null))),
                new ArrayList<>(List.of(new RecipeFlip.Part(TradeKey.of(sold), 100, null))),
                250L * i, 1_800_000_000_000L + i, null, null));
        }
        Delta moved = trades.get(6);
        records.add(new RecipeFlip(ConversionKind.TRANSFER, "Shark to Sips Potion",
            new ArrayList<>(List.of(new RecipeFlip.Part(TradeKey.of(moved), 60, 60_000L))), null, 0L,
            1_800_000_000_100L, ALT, null));
        return records;
    }

    // ---- helpers ----

    private ProfileWipeDataService wipeOver(ProfileStore store) {
        PluginState state = new PluginState();
        RecipeFlipStore recipes = new RecipeFlipStore();
        Bridge.set(Guice.createInjector(binder -> {
            binder.bind(ProfileStore.class).toInstance(store);
            binder.bind(PluginState.class).toInstance(state);
            binder.bind(RecipeFlipStore.class).toInstance(recipes);
            binder.bind(Gson.class).toInstance(gson);
        }));
        return Bridge.get(ProfileWipeDataService.class);
    }

    private static void withDataDir(String name, ThrowingRunnable work) throws Exception {
        String before = System.getProperty("fliphub.dataDir");
        if (name == null) {
            System.clearProperty("fliphub.dataDir");
        } else {
            System.setProperty("fliphub.dataDir", name);
        }
        try {
            work.run();
        } finally {
            if (before == null) {
                System.clearProperty("fliphub.dataDir");
            } else {
                System.setProperty("fliphub.dataDir", before);
            }
        }
    }

    /** Every file straight inside a folder, by name. */
    private static Map<String, byte[]> files(Path dir) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> listed = Files.list(dir)) {
            for (Path file : listed.collect(Collectors.toList())) {
                files.put(file.getFileName().toString(), Files.readAllBytes(file));
            }
        }
        return files;
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> listed = Files.list(dir)) {
            return listed.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    private static String text(byte[] file) {
        return new String(file, StandardCharsets.UTF_8);
    }

    /** A file's document without the moment it was saved, which every save sets. */
    private static JsonObject document(byte[] file) {
        JsonObject document = new JsonParser().parse(text(file)).getAsJsonObject();
        document.remove("updatedMs");
        return document;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
