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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecipeFlipStoreTest {
    private static final int BLADE = 11690;
    private static final int HILT = 11810;
    private static final int GODSWORD = 11802;

    private static RecipeFlip godswordAssemble() {
        return new RecipeFlip(
            ConversionKind.ASSEMBLE,
            "Armadyl godsword",
            Arrays.asList(
                new RecipeFlip.Part(new TradeKey(1_000L, 1, BLADE), 1),
                new RecipeFlip.Part(new TradeKey(2_000L, 2, HILT), 1)),
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(3_000L, 3, GODSWORD), 1)),
            0L,
            4_000L);
    }

    @Test
    public void aRecordIsKeptAgainstTheAccountThatMadeIt() {
        RecipeFlipStore store = new RecipeFlipStore();

        assertTrue(store.add(7L, godswordAssemble()));

        assertEquals(1, store.applicable(7L).size());
        assertTrue("another character must not see it", store.applicable(8L).isEmpty());
    }

    /** The accountwide replay covers every character's trades, so it sees every record. */
    @Test
    public void theAccountwideViewSeesEveryAccountsRecords() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(7L, godswordAssemble());
        store.add(8L, godswordAssemble());

        assertEquals(2, store.applicable(Const.ACCOUNTWIDE_KEY).size());
    }

    @Test
    public void anUnusableRecordIsRefused() {
        RecipeFlipStore store = new RecipeFlipStore();

        assertFalse("no kind", store.add(7L, new RecipeFlip(
            null, "x",
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(1L, 1, BLADE), 1)),
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(2L, 2, GODSWORD), 1)),
            0L, 0L)));
        assertFalse("nothing bought", store.add(7L, new RecipeFlip(
            ConversionKind.ASSEMBLE, "x",
            Collections.emptyList(),
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(2L, 2, GODSWORD), 1)),
            0L, 0L)));
        assertFalse("a part with no quantity", store.add(7L, new RecipeFlip(
            ConversionKind.ASSEMBLE, "x",
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(1L, 1, BLADE), 0)),
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(2L, 2, GODSWORD), 1)),
            0L, 0L)));
        assertTrue(store.applicable(7L).isEmpty());
    }

    @Test
    public void aRecordCanBeTakenBack() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(7L, godswordAssemble());

        assertTrue(store.remove(7L, godswordAssemble()));
        assertTrue(store.applicable(7L).isEmpty());
    }

    /**
     * The record has to survive the round trip through the profile file, because it is the only
     * place the conversion exists - nothing can re-derive it once guessing is gone.
     */
    @Test
    public void aRecordSurvivesAWriteAndReloadOfTheProfileFile() {
        Gson gson = new Gson();
        ProfileData written = new ProfileData();
        written.accountHash = 7L;
        written.recipeFlips = Collections.singletonList(godswordAssemble());

        ProfileData reloaded = gson.fromJson(gson.toJson(written), ProfileData.class);

        RecipeFlipStore store = new RecipeFlipStore();
        store.replace(7L, reloaded.recipeFlips);

        List<RecipeFlip> back = store.applicable(7L);
        assertEquals(1, back.size());
        RecipeFlip flip = back.get(0);
        assertEquals(ConversionKind.ASSEMBLE, flip.kind);
        assertEquals("Armadyl godsword", flip.name);
        assertEquals(2, flip.inputParts().size());
        assertEquals(1, flip.outputParts().size());
        assertEquals(GODSWORD, flip.subjectItemId());
        assertEquals(new TradeKey(1_000L, 1, BLADE), flip.inputParts().get(0).trade);
    }

    /** A file from an older build has no recorded conversions, and must load as none. */
    @Test
    public void aFileWithoutRecordedConversionsLoadsAsNone() {
        Gson gson = new Gson();
        ProfileData old = gson.fromJson("{\"accountHash\":7,\"deltas\":[]}", ProfileData.class);

        RecipeFlipStore store = new RecipeFlipStore();
        store.replace(7L, old.recipeFlips);

        assertTrue(store.applicable(7L).isEmpty());
    }
}
