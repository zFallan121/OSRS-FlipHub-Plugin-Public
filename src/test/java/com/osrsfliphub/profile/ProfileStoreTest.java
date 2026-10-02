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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileStoreTest {
    @Test
    public void writeProfileDataRoundTripsAndLeavesNoTemporaryFileBehind() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-write");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            List<Delta> deltas = new ArrayList<>();
            deltas.add(new Delta(1000L, 1, 4151, true, 5, 500L, "OFFER_UPDATED", 100, false));

            long writtenMs = store.writeProfileData(123L, 0L, "Zezima", deltas);

            assertTrue(writtenMs > 0);
            ProfileData read = store.readProfileData(123L, 0L);
            assertNotNull(read);
            assertEquals(1, read.deltas.size());
            assertEquals(4151, read.deltas.get(0).itemId);
            try (java.util.stream.Stream<Path> files =
                     Files.list(baseDir.resolve("fliphub").resolve("profiles"))) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /**
     * The loader tells "this account has no trades" from "this file could not be read" using
     * exactly these two signals, and only overwrites the file in the first case.
     */
    @Test
    public void aCorruptProfileFileIsDistinguishableFromAMissingOne() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-corrupt");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Path file = store.getProfileFile(123L, 0L);
            assertEquals(0L, store.getProfileFileModifiedMs(file));

            Files.writeString(file, "{\"deltas\":[{\"itemId\"", StandardCharsets.UTF_8);

            assertNull(store.readProfileData(file));
            assertTrue(store.getProfileFileModifiedMs(file) > 0);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void parseAccountKeyFromProfileFileParsesValidHashFile() {
        ProfileStore store = new ProfileStore(new Gson(), "fliphub-dev", "fliphub");

        long parsed = store.parseAccountKeyFromProfileFile(Path.of("hash_123.json"));

        assertEquals(123L, parsed);
    }

    @Test
    public void parseAccountKeyFromProfileFileRejectsInvalidFiles() {
        ProfileStore store = new ProfileStore(new Gson(), "fliphub-dev", "fliphub");

        assertEquals(-1L, store.parseAccountKeyFromProfileFile(null));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("accountwide.json")));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("hash_invalid.json")));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("hash_0.json")));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("profile_123.json")));
    }

    @Test
    public void readProfileDataReturnsNullForInvalidJson() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-invalid-json");
        try {
            Path file = baseDir.resolve("hash_123.json");
            Files.writeString(file, "{not valid json", StandardCharsets.UTF_8);
            ProfileStore store = new ProfileStore(new Gson(), "fliphub-dev", "fliphub");

            ProfileData data = store.readProfileData(file);

            assertNull(data);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /**
     * 22 Sep 2026: the Plugin Hub build, running beside a newer development build, read a file
     * holding a recorded move it did not understand and wrote it back without it. This is the save
     * and reload that the first fix for it never tested.
     */
    @Test
    public void aRecordFromANewerVersionSurvivesASaveExactlyAsItWasWritten() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-newer");
        try {
            Gson gson = new Gson();
            ProfileStore store = new ProfileStore(gson, "fliphub", "fliphub-dev", baseDir);
            Path file = store.getProfileFile(123L, 0L);
            String future = "{\"kind\":\"SOME_FUTURE_KIND\",\"name\":\"a future record\","
                + "\"inputs\":[{\"trade\":{\"tsMs\":1000,\"slot\":1,\"itemId\":4151},\"quantity\":5,\"gp\":500,\"futurePart\":7}],"
                + "\"feeGp\":0,\"recordedMs\":9000,\"toAccount\":456,\"futureField\":\"keep me\"}";
            Files.writeString(file, "{\"accountHash\":123,\"displayName\":\"Zezima\",\"deltas\":["
                + "{\"tsClientMs\":1000,\"slot\":1,\"itemId\":4151,\"isBuy\":true,\"deltaQty\":5,\"deltaGp\":500,"
                + "\"eventType\":\"OFFER_COMPLETED\",\"price\":100,\"baselineSynthetic\":false}],"
                + "\"updatedMs\":1,\"recipeFlips\":[" + future + "],\"futureMember\":{\"a\":1}}", StandardCharsets.UTF_8);

            ProfileData read = store.readProfileData(file);
            RecipeFlipStore records = new RecipeFlipStore();
            records.replace(123L, read.recipeFlips);
            store.writeProfileData(123L, 0L, read.displayName, read.deltas, records.snapshotForFile(123L));

            JsonObject saved = new JsonParser().parse(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(new JsonParser().parse(future), saved.getAsJsonArray("recipeFlips").get(0));
            assertEquals(new JsonParser().parse("{\"a\":1}"), saved.get("futureMember"));
            assertEquals(1, saved.getAsJsonArray("deltas").size());
            assertEquals(1, store.readProfileData(file).recipeFlips.size());
        } finally {
            deleteRecursively(baseDir);
        }
    }

    private static final String ONE_TRADE = "{\"tsClientMs\":1000,\"slot\":1,\"itemId\":4151,\"isBuy\":true,"
        + "\"deltaQty\":5,\"deltaGp\":500,\"eventType\":\"OFFER_COMPLETED\",\"price\":100,\"baselineSynthetic\":false}";

    /**
     * A member this build does not know whose value is null (a repair script's, a hand edit's). It is
     * counted among what was kept but written as nothing, and what was kept is joined on by hand: the
     * file ended ",}", never read again, and every trade after it was lost when RuneLite closed.
     */
    @Test
    public void aFileHoldingAnUnknownMemberOfNullStillReadsAfterASave() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-null-member");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Path file = store.getProfileFile(123L, 0L);
            Files.writeString(file, "{\"accountHash\":123,\"displayName\":\"Zezima\",\"deltas\":[" + ONE_TRADE + "],"
                + "\"updatedMs\":1,\"futureMember\":null}", StandardCharsets.UTF_8);

            ProfileData read = store.readProfileData(file);
            store.writeProfileData(123L, 0L, read.displayName, read.deltas, null);

            ProfileData again = store.readProfileData(file);
            assertNotNull("the file as saved: " + Files.readString(file, StandardCharsets.UTF_8), again);
            assertEquals(1, again.deltas.size());
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /**
     * The model reads a file leniently (a comment a person left in it is allowed), so the second read
     * that holds on to what this build does not know must be as forgiving, or the save drops it.
     */
    @Test
    public void aFileWithACommentInItStillKeepsWhatANewerVersionSaved() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-lenient");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Path file = store.getProfileFile(123L, 0L);
            Files.writeString(file, "{\"accountHash\":123, /* edited by hand */ \"deltas\":[" + ONE_TRADE + "],"
                + "\"updatedMs\":1,\"futureMember\":{\"a\":1}}", StandardCharsets.UTF_8);

            ProfileData read = store.readProfileData(file);
            assertNotNull(read);
            store.writeProfileData(123L, 0L, read.displayName, read.deltas, null);

            JsonObject saved = new JsonParser().parse(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(new JsonParser().parse("{\"a\":1}"), saved.get("futureMember"));
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /** Two moves of one purchase, made in the same millisecond to two different alts, are two records. */
    @Test
    public void twoMovesOfOnePurchaseToTwoAltsBothSurviveASave() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-two-moves");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Delta bought = new Delta(1_000L, 1, 385, true, 10, 8_000L, "OFFER_COMPLETED", 800, false);
            RecipeFlip toFirstAlt = new RecipeFlip(ConversionKind.TRANSFER, "Shark to A",
                new ArrayList<>(List.of(new RecipeFlip.Part(TradeKey.of(bought), 4, 3_200L))), null, 0L, 5_000L, 456L, null);
            RecipeFlip toSecondAlt = new RecipeFlip(ConversionKind.TRANSFER, "Shark to B",
                new ArrayList<>(List.of(new RecipeFlip.Part(TradeKey.of(bought), 6, 4_800L))), null, 0L, 5_000L, 789L, null);
            store.writeProfileData(123L, 0L, "Zezima", new ArrayList<>(List.of(bought)), List.of(toFirstAlt, toSecondAlt));
            ProfileData read = store.readProfileData(store.getProfileFile(123L, 0L));

            store.writeProfileData(123L, 0L, "Zezima", read.deltas, read.recipeFlips);

            Set<Long> alts = new HashSet<>();
            for (RecipeFlip flip : store.readProfileData(store.getProfileFile(123L, 0L)).recipeFlips) {
                alts.add(flip.toAccount);
            }
            assertEquals(Set.of(456L, 789L), alts);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void aRecordMadeSinceIsWrittenByThisBuildAndOneForgottenIsNotBroughtBack() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-records");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Delta bought = new Delta(1_000L, 1, 385, true, 10, 8_000L, "OFFER_COMPLETED", 800, false);
            RecipeFlip kept = move(bought, 1_000L);
            RecipeFlip forgotten = move(bought, 2_000L);
            List<Delta> deltas = new ArrayList<>(List.of(bought));
            store.writeProfileData(123L, 0L, "Zezima", deltas, List.of(kept, forgotten));
            ProfileData read = store.readProfileData(store.getProfileFile(123L, 0L));

            RecipeFlip made = move(bought, 3_000L);
            store.writeProfileData(123L, 0L, "Zezima", read.deltas, List.of(read.recipeFlips.get(0), made));

            List<RecipeFlip> now = store.readProfileData(store.getProfileFile(123L, 0L)).recipeFlips;
            assertEquals(2, now.size());
            assertEquals(1_000L, now.get(0).recordedMs);
            assertEquals(3_000L, now.get(1).recordedMs);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /** A member the model has but this list lacks would be written twice. */
    @Test
    public void theKnownMembersAreExactlyTheModelsFields() {
        java.util.Set<String> fields = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : ProfileData.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!java.lang.reflect.Modifier.isStatic(modifiers) && !java.lang.reflect.Modifier.isTransient(modifiers)
                && !field.isSynthetic()) {
                fields.add(field.getName());
            }
        }
        assertEquals(fields, ProfileStore.KNOWN);
    }

    private static RecipeFlip move(Delta purchase, long recordedMs) {
        return new RecipeFlip(ConversionKind.TRANSFER, "Shark to Alt",
            new ArrayList<>(List.of(new RecipeFlip.Part(TradeKey.of(purchase), purchase.deltaQty, purchase.deltaGp))),
            null, 0L, recordedMs, 456L, null);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }
}
