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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the website is told about a recorded recipe.
 *
 * <p>The website finds the trades a recipe names by their slot, item and first-fill time, costs
 * the recipe from the coins sent with each part, and tells one recipe from another - and a
 * repeat of one it already has - by ids. Every one of those is a promise this suite holds the
 * plugin to; the website's half is {@code test_recipe_pairing.py} in the website repository.
 */
public class RecipeUploadTest {
    private static final long ACCOUNT = 77L;
    private static final int HELM = 4716;
    private static final int BODY = 4720;
    private static final int DH_SET = 12877;

    private static Delta bought(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", 0, false, tsMs - 50L, tsMs + 900L);
    }

    private static Delta sold(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, false, qty, gp, "OFFER_COMPLETED", 0, false, tsMs - 50L, tsMs + 900L);
    }

    private static RecipeFlip.Part all(Delta delta) {
        return new RecipeFlip.Part(TradeKey.of(delta), delta.deltaQty, null);
    }

    private final Delta helm = bought(1_000L, 0, HELM, 1, 1_000_000L);
    private final Delta body = bought(2_000L, 1, BODY, 1, 1_200_000L);
    private final Delta set = sold(9_000L, 4, DH_SET, 1, 2_310_000L);
    private final List<Delta> stored = Arrays.asList(helm, body, set);

    private RecipeFlip recipe(long recordedMs) {
        return new RecipeFlip(ConversionKind.SET_COMBINE, "Dharok's set",
            Arrays.asList(all(helm), all(body)), Collections.singletonList(all(set)), 0L, recordedMs, null, null);
    }

    @Test
    public void thePurchasesComeFirstThenTheSalesOneEventToATrade() {
        List<GeEvent> events = RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore());

        assertEquals(3, events.size());
        assertEquals(Arrays.asList("RECIPE_IN", "RECIPE_IN", "RECIPE_OUT"),
            Arrays.asList(events.get(0).event_type, events.get(1).event_type, events.get(2).event_type));
        assertEquals(Arrays.asList(HELM, BODY, DH_SET),
            Arrays.asList(events.get(0).item_id, events.get(1).item_id, events.get(2).item_id));
    }

    @Test
    public void aPartNamesItsTradeTheWayTheWebsiteStoredIt() {
        GeEvent sale = RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore()).get(2);

        // Slot, item and first-fill time are how the website finds the sale's own events.
        assertEquals(4, sale.slot);
        assertEquals(DH_SET, sale.item_id);
        assertEquals(9_000L, sale.ts_client_ms);
        assertEquals(Long.valueOf(9_900L), sale.recipe_trade_end_ms);
        assertFalse(sale.is_buy);
        assertEquals(1, sale.delta_qty);
        assertEquals(2_310_000L, sale.delta_gp);
    }

    @Test
    public void everyPartSaysWhichRecipeItBelongsToAndHowManyPartsThereAre() {
        List<GeEvent> events = RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore());

        Set<String> recipeIds = new HashSet<>();
        Set<String> eventIds = new HashSet<>();
        for (GeEvent event : events) {
            recipeIds.add(event.recipe_id);
            eventIds.add(event.event_id);
            assertEquals(Integer.valueOf(3), event.recipe_parts);
            assertEquals("SET_COMBINE", event.recipe_kind);
        }
        assertEquals(1, recipeIds.size());
        assertEquals(3, eventIds.size());
    }

    @Test
    public void everyPartSaysWhichCharacterRecordedIt() {
        // Stored recipes of every character go up when their profiles load, whoever is logged in:
        // the tag has to come from the recipe's own account, not from the character on screen.
        for (GeEvent event : RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore())) {
            assertEquals(GeEvent.characterId(ACCOUNT), event.character_id);
        }
    }

    @Test
    public void onlyTheQuantityUsedAndItsShareOfTheCoinsIsSent() {
        Delta helms = bought(1_000L, 0, HELM, 4, 4_000_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "x",
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(helms), 1, null)),
            Collections.singletonList(all(set)), 60_000L, 50_000L, null, null);

        GeEvent part = RecipeUpload.parts(ACCOUNT, flip, Arrays.asList(helms, set), new RecipeFlipStore()).get(0);

        assertEquals(1, part.delta_qty);
        assertEquals(1_000_000L, part.delta_gp);
        assertEquals(Long.valueOf(60_000L), part.recipe_fee_gp);
    }

    @Test
    public void sendingARecipeAgainSendsTheSameIds() {
        List<GeEvent> first = RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore());
        List<GeEvent> again = RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore());

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).event_id, again.get(i).event_id);
            assertEquals(first.get(i).recipe_id, again.get(i).recipe_id);
        }
    }

    @Test
    public void aRecipeRecordedAgainAfterBeingDeletedIsANewRecipe() {
        // The website keeps a deleted recipe deleted. The same trades recorded again must not be mistaken for it.
        assertNotEquals(
            RecipeUpload.recipeId(ACCOUNT, recipe(50_000L)), RecipeUpload.recipeId(ACCOUNT, recipe(60_000L)));
    }

    @Test
    public void twoAccountsNeverShareARecipeId() {
        assertNotEquals(RecipeUpload.recipeId(ACCOUNT, recipe(50_000L)), RecipeUpload.recipeId(78L, recipe(50_000L)));
    }

    @Test
    public void aRecipeNamingATradeThatIsGoneSendsNothingRatherThanHalf() {
        assertTrue(RecipeUpload.parts(ACCOUNT, recipe(50_000L), Arrays.asList(helm, set), new RecipeFlipStore()).isEmpty());
        assertTrue(RecipeUpload.parts(ACCOUNT, recipe(50_000L), new ArrayList<>(), new RecipeFlipStore()).isEmpty());
    }

    @Test
    public void deletingNamesTheSameRecipeWithAnIdOfItsOwn() {
        GeEvent deleted = RecipeUpload.deleted(ACCOUNT, recipe(50_000L));
        List<GeEvent> parts = RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore());

        assertEquals("RECIPE_VOID", deleted.event_type);
        assertEquals(parts.get(0).recipe_id, deleted.recipe_id);
        for (GeEvent part : parts) {
            assertNotEquals(part.event_id, deleted.event_id);
        }
        assertEquals(deleted.event_id, RecipeUpload.deleted(ACCOUNT, recipe(50_000L)).event_id);
    }

    @Test
    public void aTradeIsStillSentExactlyAsBefore() {
        // The recipe fields are null on a trade, and Gson leaves a null out.
        JsonObject trade = new Gson().toJsonTree(new GeEvent()).getAsJsonObject();

        for (String key : trade.keySet()) {
            assertFalse(key, key.startsWith("recipe_"));
        }
    }

    @Test
    public void aPartCarriesEveryFieldTheWebsiteReads() {
        JsonObject part = new Gson().toJsonTree(RecipeUpload.parts(ACCOUNT, recipe(50_000L), stored, new RecipeFlipStore()).get(0))
            .getAsJsonObject();

        for (String key : Arrays.asList("event_id", "event_type", "recipe_id", "recipe_kind", "recipe_parts",
            "recipe_fee_gp", "recipe_trade_end_ms", "ts_client_ms", "slot", "item_id", "delta_qty", "delta_gp")) {
            assertTrue(key, part.has(key));
        }
    }
}
