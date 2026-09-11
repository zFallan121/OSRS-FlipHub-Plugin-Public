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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The conversion table, indexed by the item a conversion produces.
 *
 * <p>The table ships as a bundled resource because the plugin works offline,
 * and it is generated - never hand-written. {@code assembly_recipes.py},
 * {@code repairables.py} and {@code item_sets.py} on the website stay the
 * single source of truth; {@code tools/export_conversions_for_plugin.py}
 * projects them into {@code conversions.json}.
 *
 * <p>Items are stored by exact Grand Exchange name and resolved against
 * {@link ItemLookupService} at runtime. A conversion whose names do not all
 * resolve is dropped, which is the same never-half-priced rule the website
 * endpoints use: a mapping rename should make a conversion disappear rather
 * than attach a wrong cost to a real trade.
 *
 * <p>Until {@link #resolve()} has run the index is empty, and an empty index
 * makes both ledgers behave exactly as they did before conversions existed.
 */
@Singleton
final class ConversionRecipeIndex {
    private static final String RESOURCE_PATH = "/com/osrsfliphub/conversions.json";

    /**
     * All five directions are indexed, and all three shapes convert. N -> 1
     * (ASSEMBLE, SET_COMBINE) sums its inputs; 1 -> 1 plus a fee (REPAIR) adds
     * the fee; 1 -> N (DISASSEMBLE, SET_BREAK) divides the one purchase between
     * the things it became, by what each of them is worth.
     *
     * <p>None of them needs a number the trade history does not contain. A
     * 1 -> N conversion is one activity filed against the thing taken apart, so
     * the pieces never need a cost of their own and there is nothing to
     * estimate - see {@link ConversionBreak}.
     *
     * <p>A recipe's {@code fee} is the NPC price, which is the only one that is
     * the same for everybody; what the player actually paid is
     * {@code ConversionFeeService}'s question, because an armour stand charges
     * by Smithing level.
     */
    private static final Set<ConversionKind> ENABLED_KINDS = EnumSet.allOf(ConversionKind.class);

    private final ToIntFunction<String> nameResolver;
    private volatile Map<Integer, List<ConversionRecipe>> byOutputItemId = Collections.emptyMap();
    private List<Definition> definitions;

    @Inject
    ConversionRecipeIndex(ItemLookupService itemLookupService) {
        this.nameResolver = itemLookupService != null ? itemLookupService::resolveItemIdFromName : null;
    }

    /** Test seam: resolve the shipped resource against a known name table. */
    ConversionRecipeIndex(ToIntFunction<String> nameResolver) {
        this.nameResolver = nameResolver;
    }

    /** Test seam: an index over recipes whose ids are already known. */
    ConversionRecipeIndex(List<ConversionRecipe> recipes) {
        this.nameResolver = null;
        this.definitions = Collections.emptyList();
        this.byOutputItemId = indexRecipes(recipes);
    }

    List<ConversionRecipe> recipesProducing(int itemId) {
        if (itemId <= 0) {
            return Collections.emptyList();
        }
        List<ConversionRecipe> recipes = byOutputItemId.get(itemId);
        return recipes != null ? recipes : Collections.emptyList();
    }

    boolean isEmpty() {
        return byOutputItemId.isEmpty();
    }

    /**
     * Resolve names to ids and rebuild the index. Called off the client thread
     * at login - it is a few hundred name lookups that touch no client state -
     * and safe to call again: resolution is cached, and a conversion that failed
     * because the item database had not loaded yet is retried next time.
     */
    synchronized boolean resolve() {
        if (nameResolver == null) {
            return false;
        }
        List<Definition> loaded = definitions();
        if (loaded.isEmpty()) {
            return false;
        }
        List<ConversionRecipe> resolved = new ArrayList<>(loaded.size());
        for (Definition definition : loaded) {
            ConversionRecipe recipe = definition.resolve(nameResolver);
            if (recipe != null && recipe.isUsable()) {
                resolved.add(recipe);
            }
        }
        Map<Integer, List<ConversionRecipe>> rebuilt = indexRecipes(resolved);
        // The caller uses this to throw away aggregates that were computed while
        // the table was still empty. The transition that matters is the first
        // one - nothing to everything - and a name that only resolves on a later
        // login repairs itself the same way.
        boolean changed = !rebuilt.keySet().equals(byOutputItemId.keySet());
        byOutputItemId = rebuilt;
        return changed;
    }

    private synchronized List<Definition> definitions() {
        if (definitions != null) {
            return definitions;
        }
        definitions = load();
        return definitions;
    }

    private static List<Definition> load() {
        try (InputStream stream = ConversionRecipeIndex.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                return Collections.emptyList();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                // The instance API, not the static JsonParser.parseReader: the
                // Gson the client ships is older than that method.
                JsonElement root = new JsonParser().parse(reader);
                if (root == null || !root.isJsonObject()) {
                    return Collections.emptyList();
                }
                JsonElement rows = root.getAsJsonObject().get("conversions");
                if (rows == null || !rows.isJsonArray()) {
                    return Collections.emptyList();
                }
                List<Definition> parsed = new ArrayList<>();
                for (JsonElement row : rows.getAsJsonArray()) {
                    Definition definition = Definition.parse(row);
                    if (definition != null && ENABLED_KINDS.contains(definition.kind)) {
                        parsed.add(definition);
                    }
                }
                return parsed;
            }
        } catch (Exception ignored) {
            // A missing or malformed resource means no conversions, which is the
            // plugin's behaviour before this feature. It is never a reason to
            // fail a trade.
            return Collections.emptyList();
        }
    }

    private static Map<Integer, List<ConversionRecipe>> indexRecipes(List<ConversionRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<ConversionRecipe>> index = new HashMap<>();
        // Two rows with the same inputs and the same outputs are one recipe
        // exported twice, whatever kind they claim to be. Indexing both would
        // give the ledger two routes with stock for both, and it claims neither
        // - so a duplicate would not attribute twice, it would never attribute
        // at all. The first row wins; the rest are the same recipe again.
        Set<String> seen = new HashSet<>();
        for (ConversionRecipe recipe : recipes) {
            if (recipe == null || !recipe.isUsable()) {
                continue;
            }
            if (!seen.add(multisetKey(recipe))) {
                continue;
            }
            for (ConversionItem output : recipe.outputs) {
                index.computeIfAbsent(output.itemId, ignored -> new ArrayList<>()).add(recipe);
            }
        }
        for (Map.Entry<Integer, List<ConversionRecipe>> entry : index.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(index);
    }

    /** Inputs and outputs as item -> total quantity, so order and splitting do not matter. */
    private static String multisetKey(ConversionRecipe recipe) {
        return multiset(recipe.inputs) + "->" + multiset(recipe.outputs);
    }

    private static String multiset(List<ConversionItem> items) {
        Map<Integer, Long> quantities = new TreeMap<>();
        for (ConversionItem item : items) {
            quantities.merge(item.itemId, (long) item.quantity, Long::sum);
        }
        return quantities.toString();
    }

    /** A row of the resource, still holding Grand Exchange names. */
    private static final class Definition {
        private final ConversionKind kind;
        private final String name;
        private final List<String[]> inputs;
        private final List<String[]> outputs;
        private final long feeGp;

        private Definition(ConversionKind kind, String name, List<String[]> inputs,
                           List<String[]> outputs, long feeGp) {
            this.kind = kind;
            this.name = name;
            this.inputs = inputs;
            this.outputs = outputs;
            this.feeGp = feeGp;
        }

        static Definition parse(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject row = element.getAsJsonObject();
            ConversionKind kind = ConversionKind.parse(optString(row, "kind"));
            if (kind == null) {
                return null;
            }
            List<String[]> inputs = parsePairs(row.get("inputs"));
            List<String[]> outputs = parsePairs(row.get("outputs"));
            if (inputs.isEmpty() || outputs.isEmpty()) {
                return null;
            }
            long fee = row.has("fee") && row.get("fee").isJsonPrimitive() ? row.get("fee").getAsLong() : 0L;
            return new Definition(kind, optString(row, "name"), inputs, outputs, fee);
        }

        ConversionRecipe resolve(ToIntFunction<String> lookup) {
            List<ConversionItem> resolvedInputs = resolveAll(inputs, lookup);
            if (resolvedInputs == null) {
                return null;
            }
            List<ConversionItem> resolvedOutputs = resolveAll(outputs, lookup);
            if (resolvedOutputs == null) {
                return null;
            }
            return new ConversionRecipe(kind, name, resolvedInputs, resolvedOutputs, feeGp);
        }

        private static List<ConversionItem> resolveAll(List<String[]> pairs, ToIntFunction<String> lookup) {
            List<ConversionItem> items = new ArrayList<>(pairs.size());
            for (String[] pair : pairs) {
                int itemId = lookup.applyAsInt(pair[0]);
                if (itemId <= 0) {
                    // One unresolvable name drops the whole conversion. A partial
                    // recipe would price a trade against fewer inputs than it had.
                    return null;
                }
                int quantity;
                try {
                    quantity = Integer.parseInt(pair[1]);
                } catch (NumberFormatException ex) {
                    return null;
                }
                if (quantity <= 0) {
                    return null;
                }
                items.add(new ConversionItem(itemId, quantity));
            }
            return items;
        }

        private static List<String[]> parsePairs(JsonElement element) {
            if (element == null || !element.isJsonArray()) {
                return Collections.emptyList();
            }
            List<String[]> pairs = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry == null || !entry.isJsonArray()) {
                    continue;
                }
                JsonArray pair = entry.getAsJsonArray();
                if (pair.size() < 2) {
                    continue;
                }
                pairs.add(new String[]{pair.get(0).getAsString(), pair.get(1).getAsString()});
            }
            return pairs;
        }

        private static String optString(JsonObject row, String key) {
            JsonElement value = row.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }
    }
}
