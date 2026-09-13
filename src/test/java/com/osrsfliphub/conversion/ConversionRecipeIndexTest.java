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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Reads the resource the plugin actually ships, so a bad export is a failing
 * test rather than a tab that quietly gets shorter.
 */
public class ConversionRecipeIndexTest {
    private static final int BLACK_TOURMALINE_CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;
    private static final int DHAROKS_HELM = 4716;
    private static final int DHAROKS_HELM_0 = 4880;
    private static final int DHAROKS_PLATEBODY = 4720;
    private static final int DHAROKS_PLATELEGS = 4722;
    private static final int DHAROKS_GREATAXE = 4718;
    private static final int DHAROKS_ARMOUR_SET = 12881;
    private static final int TORVA_FULL_HELM = 26382;
    private static final int TORVA_FULL_HELM_DAMAGED = 26376;
    private static final int BANDOSIAN_COMPONENTS = 26394;

    private static Map<String, Integer> guardianBootsNames() {
        Map<String, Integer> ids = new HashMap<>();
        ids.put("Black tourmaline core", BLACK_TOURMALINE_CORE);
        ids.put("Bandos boots", BANDOS_BOOTS);
        ids.put("Guardian boots", GUARDIAN_BOOTS);
        return ids;
    }

    private static ConversionRecipeIndex indexResolving(Map<String, Integer> ids) {
        ConversionRecipeIndex index =
            new ConversionRecipeIndex((java.util.function.ToIntFunction<String>) name -> {
                Integer id = ids.get(name);
                return id != null ? id : -1;
            });
        index.resolve();
        return index;
    }

    private static Map<String, Integer> dharoksNames() {
        Map<String, Integer> ids = guardianBootsNames();
        ids.put("Dharok's helm", DHAROKS_HELM);
        ids.put("Dharok's platebody", DHAROKS_PLATEBODY);
        ids.put("Dharok's platelegs", DHAROKS_PLATELEGS);
        ids.put("Dharok's greataxe", DHAROKS_GREATAXE);
        ids.put("Dharok's armour set", DHAROKS_ARMOUR_SET);
        return ids;
    }

    @Test
    public void shippedResourceCarriesTheGuardianBootsRecipe() {
        ConversionRecipeIndex index = indexResolving(guardianBootsNames());

        List<ConversionRecipe> recipes = index.recipesProducing(GUARDIAN_BOOTS);
        assertEquals(1, recipes.size());
        ConversionRecipe recipe = recipes.get(0);
        assertEquals(ConversionKind.ASSEMBLE, recipe.kind);
        assertEquals("Guardian boots", recipe.displayName);
        assertEquals(0L, recipe.feeGp);
        assertEquals(2, recipe.inputs.size());
        assertEquals(1, recipe.outputQuantityOf(GUARDIAN_BOOTS));

        Map<Integer, Integer> inputs = new HashMap<>();
        for (ConversionItem input : recipe.inputs) {
            inputs.put(input.itemId, input.quantity);
        }
        assertEquals(Integer.valueOf(1), inputs.get(BLACK_TOURMALINE_CORE));
        assertEquals(Integer.valueOf(1), inputs.get(BANDOS_BOOTS));
    }

    @Test
    public void aRecipeWithAnUnresolvableNameIsDroppedWhole() {
        // The core resolves and the boots do not. Half a recipe would price the
        // trade against one ingredient, so the whole row has to go.
        Map<String, Integer> ids = guardianBootsNames();
        ids.remove("Bandos boots");

        ConversionRecipeIndex index = indexResolving(ids);

        assertTrue(index.recipesProducing(GUARDIAN_BOOTS).isEmpty());
    }

    @Test
    public void repairIsIndexedWithItsFee() {
        // A Barrows piece bought broken and sold whole is a repair, and the fee
        // is part of what it cost to make - without it the flip reads as 60k
        // more profit than it was.
        Map<String, Integer> ids = guardianBootsNames();
        ids.put("Dharok's helm", DHAROKS_HELM);
        ids.put("Dharok's helm 0", DHAROKS_HELM_0);

        ConversionRecipeIndex index = indexResolving(ids);

        List<ConversionRecipe> recipes = index.recipesProducing(DHAROKS_HELM);
        assertEquals(1, recipes.size());
        ConversionRecipe recipe = recipes.get(0);
        assertEquals(ConversionKind.REPAIR, recipe.kind);
        assertEquals(60_000L, recipe.feeGp);
        assertEquals(1, recipe.inputs.size());
        assertEquals(DHAROKS_HELM_0, recipe.inputs.get(0).itemId);
        assertEquals(1, recipe.outputQuantityOf(DHAROKS_HELM));
    }

    @Test
    public void bothSetDirectionsAreIndexed() {
        // Both directions are real trades and both are carried: the combine
        // against the set it makes, the break against every piece it makes, so
        // that a sale of any piece can find it.
        ConversionRecipeIndex index = indexResolving(dharoksNames());

        List<ConversionRecipe> combine = index.recipesProducing(DHAROKS_ARMOUR_SET);
        assertEquals(1, combine.size());
        assertEquals(ConversionKind.SET_COMBINE, combine.get(0).kind);
        assertEquals(4, combine.get(0).inputs.size());

        ConversionRecipe setBreak = null;
        for (ConversionRecipe recipe : index.recipesProducing(DHAROKS_HELM)) {
            if (recipe.kind == ConversionKind.SET_BREAK) {
                setBreak = recipe;
            }
        }
        assertNotNull("the break is carried against the helm it produces", setBreak);
        assertEquals(4, setBreak.outputs.size());
        assertEquals(DHAROKS_ARMOUR_SET, setBreak.inputs.get(0).itemId);
        assertTrue(setBreak.isUsable());
        assertFalse(index.recipesProducing(GUARDIAN_BOOTS).isEmpty());
    }

    @Test
    public void shippedResourceHasNoTwoRecipesWithTheSameInputsAndOutputs() throws Exception {
        // Two rows with identical inputs and outputs are one recipe exported
        // twice, and the ledger refuses to choose between two routes that both
        // have stock - so a duplicate does not attribute twice, it never
        // attributes at all. Read the file itself rather than the index, which
        // collapses duplicates and would hide the export error this catches.
        String[] names = shippedNames();
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new java.util.ArrayList<>();
        for (JsonElement row : shippedConversions()) {
            JsonArray conversion = row.getAsJsonArray();
            String key = multisetKey(conversion.get(2).getAsJsonArray(), names)
                + " -> " + multisetKey(conversion.get(3).getAsJsonArray(), names);
            if (!seen.add(key)) {
                duplicates.add(conversion.get(0).getAsString() + " " + key);
            }
        }
        assertTrue("recipes exported twice: " + duplicates, duplicates.isEmpty());
    }

    @Test
    public void shippedResourceRepairsTorvaByExactlyOneRoute() {
        // A damaged piece plus Bandosian components is a repair, and it has to
        // be the only recipe producing the piece: a second identical route made
        // every Torva sale ambiguous and dropped it.
        Map<String, Integer> ids = new HashMap<>();
        ids.put("Torva full helm", TORVA_FULL_HELM);
        ids.put("Torva full helm (damaged)", TORVA_FULL_HELM_DAMAGED);
        ids.put("Bandosian components", BANDOSIAN_COMPONENTS);

        ConversionRecipeIndex index = indexResolving(ids);

        List<ConversionRecipe> recipes = index.recipesProducing(TORVA_FULL_HELM);
        assertEquals(1, recipes.size());
        assertEquals(ConversionKind.REPAIR, recipes.get(0).kind);
        assertEquals(0L, recipes.get(0).feeGp);
    }

    @Test
    public void twoRecipesWithTheSameInputsAndOutputsCollapseToOne() {
        // The runtime guard for the same mistake. A duplicate must not make
        // the recipe unusable, so the index keeps the first and drops the rest
        // - by multiset, so listing the inputs in another order is still the
        // same recipe.
        ConversionRecipe repair = new ConversionRecipe(
            ConversionKind.REPAIR,
            "Torva full helm",
            Arrays.asList(new ConversionItem(TORVA_FULL_HELM_DAMAGED, 1), new ConversionItem(BANDOSIAN_COMPONENTS, 1)),
            Collections.singletonList(new ConversionItem(TORVA_FULL_HELM, 1)),
            0L);
        ConversionRecipe sameAgain = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Torva full helm",
            Arrays.asList(new ConversionItem(BANDOSIAN_COMPONENTS, 1), new ConversionItem(TORVA_FULL_HELM_DAMAGED, 1)),
            Collections.singletonList(new ConversionItem(TORVA_FULL_HELM, 1)),
            0L);

        ConversionRecipeIndex index = new ConversionRecipeIndex(Arrays.asList(repair, sameAgain));

        List<ConversionRecipe> recipes = index.recipesProducing(TORVA_FULL_HELM);
        assertEquals(1, recipes.size());
        assertEquals(ConversionKind.REPAIR, recipes.get(0).kind);
    }

    private static JsonObject shippedRoot() throws Exception {
        try (InputStream stream = ConversionRecipeIndex.class.getResourceAsStream("/com/osrsfliphub/conversions.json");
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static JsonArray shippedConversions() throws Exception {
        return shippedRoot().getAsJsonArray("conversions");
    }

    /** The file's name table. Rows refer to a name by its position in this. */
    private static String[] shippedNames() throws Exception {
        JsonArray array = shippedRoot().getAsJsonArray("names");
        String[] names = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
            names[i] = array.get(i).getAsString();
        }
        return names;
    }

    /** Names to summed quantities, in name order: the same however a row lists them. */
    private static String multisetKey(JsonArray pairs, String[] names) {
        Map<String, Long> quantities = new TreeMap<>();
        for (JsonElement pair : pairs) {
            if (pair.isJsonPrimitive()) {
                quantities.merge(names[pair.getAsInt()], 1L, Long::sum);
                continue;
            }
            JsonArray entry = pair.getAsJsonArray();
            quantities.merge(names[entry.get(0).getAsInt()], entry.get(1).getAsLong(), Long::sum);
        }
        return quantities.toString();
    }

    @Test
    public void anIndexWithNothingResolvedProducesNothing() {
        ConversionRecipeIndex index = indexResolving(new HashMap<>());

        assertTrue(index.isEmpty());
        assertNotNull(index.recipesProducing(GUARDIAN_BOOTS));
        assertTrue(index.recipesProducing(GUARDIAN_BOOTS).isEmpty());
    }
}
