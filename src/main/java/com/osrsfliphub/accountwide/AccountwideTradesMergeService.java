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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class AccountwideTradesMergeService {
    interface Hooks {
        Path getProfilesDir();
        Path getLegacyProfilesDir();
        ProfileData readProfileData(Path file);
        Map<String, String> getLegacyLocalTradesCache();
    }

    private final Gson gson;
    private final int maxLocalTrades;
    private final Hooks hooks;

    @Inject
    AccountwideTradesMergeService(Gson gson) {
        this(gson, GeLifecyclePluginConstants.MAX_LOCAL_TRADES, productionHooks());
    }

    AccountwideTradesMergeService(Gson gson, int maxLocalTrades, Hooks hooks) {
        this.gson = gson;
        this.maxLocalTrades = maxLocalTrades;
        this.hooks = hooks;
    }

    private static ProfileStorageFacadeService profileStorage() {
        return PluginAccess.plugin().getProfileSelectionServices().getProfileStorageFacadeService();
    }

    private static Hooks productionHooks() {
        return new Hooks() {
            @Override
            public Path getProfilesDir() {
                ProfileStorageFacadeService service = profileStorage();
                return service != null ? service.getProfilesDir() : null;
            }

            @Override
            public Path getLegacyProfilesDir() {
                ProfileStorageFacadeService service = profileStorage();
                return service != null ? service.getLegacyProfilesDir() : null;
            }

            @Override
            public ProfileData readProfileData(Path file) {
                ProfileStorageFacadeService service = profileStorage();
                return service != null ? service.readProfileData(file) : null;
            }

            @Override
            public Map<String, String> getLegacyLocalTradesCache() {
                LegacyLocalTradesFilterService filterService = PluginAccess.plugin().getLegacyLocalTradesFilterService();
                LegacyLocalTradesStore store =
                    PluginAccess.plugin().getProfileSelectionServices().getLegacyLocalTradesStore();
                if (filterService == null || store == null) {
                    return null;
                }
                return filterService.filter(store.getEntries());
            }
        };
    }

    List<LocalTradeDelta> buildAccountwideFromDisk() {
        if (hooks == null) {
            return null;
        }
        List<LocalTradeDelta> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        mergeAccountwideFromDir(merged, seen, hooks.getProfilesDir());
        mergeAccountwideFromDir(merged, seen, hooks.getLegacyProfilesDir());
        mergeAccountwideFromLegacyConfig(merged, seen);
        if (merged.isEmpty()) {
            return null;
        }
        merged.sort(Comparator.comparingLong(delta -> delta != null ? delta.tsClientMs : 0L));
        if (merged.size() > maxLocalTrades) {
            int trim = merged.size() - maxLocalTrades;
            merged.subList(0, trim).clear();
        }
        return merged;
    }

    private void mergeAccountwideFromLegacyConfig(List<LocalTradeDelta> merged, Set<String> seen) {
        if (gson == null) {
            return;
        }
        Map<String, String> entries = hooks.getLegacyLocalTradesCache();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<LocalTradeDelta>>() {}.getType();
        for (String raw : entries.values()) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            try {
                List<LocalTradeDelta> deltas = gson.fromJson(raw, type);
                if (deltas == null || deltas.isEmpty()) {
                    continue;
                }
                for (LocalTradeDelta delta : deltas) {
                    if (delta == null) {
                        continue;
                    }
                    if (seen.add(LocalTradeDeltaUtils.buildLocalTradeSignature(delta))) {
                        merged.add(delta);
                    }
                }
            } catch (JsonParseException ignored) {
            }
        }
    }

    private void mergeAccountwideFromDir(List<LocalTradeDelta> merged, Set<String> seen, Path dir) {
        ProfileHashFileWalker.walk(dir, (profileHash, path) -> {
            ProfileData data = hooks.readProfileData(path);
            if (data == null || data.deltas == null || data.deltas.isEmpty()) {
                return;
            }
            for (LocalTradeDelta delta : data.deltas) {
                if (delta == null) {
                    continue;
                }
                if (seen.add(LocalTradeDeltaUtils.buildLocalTradeSignature(delta))) {
                    merged.add(delta);
                }
            }
        });
    }
}
