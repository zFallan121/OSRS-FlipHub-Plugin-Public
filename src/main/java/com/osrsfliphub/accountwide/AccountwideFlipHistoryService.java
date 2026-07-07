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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class AccountwideFlipHistoryService {
    interface Hooks {
        long accountwideKey();
        void ensureProfileLoaded(long accountKey);
        List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey);
        Map<Integer, List<StatsFlipInstance>> buildLocalHistory(List<LocalTradeDelta> deltas, Long sinceMs);
        Set<Long> collectAccountwideProfileKeys();
    }

    private final Hooks hooks;

    @Inject
    AccountwideFlipHistoryService(PluginState state) {
        this(productionHooks(state));
    }

    AccountwideFlipHistoryService(Hooks hooks) {
        this.hooks = hooks;
    }

    private static ProfileSelectionPresentationFacadeService profileSelectionFacade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    private static ProfileStorageFacadeService profileStorage() {
        return PluginInjectorBridge.get(ProfileStorageFacadeService.class);
    }

    private static Hooks productionHooks(PluginState state) {
        return new Hooks() {
            @Override
            public long accountwideKey() {
                return GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
            }

            @Override
            public void ensureProfileLoaded(long accountKey) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
            }

            @Override
            public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
                return PluginInjectorBridge.get(LocalTradeSessionFacadeService.class)
                    .snapshotLocalTradeDeltas(accountKey);
            }

            @Override
            public Map<Integer, List<StatsFlipInstance>> buildLocalHistory(List<LocalTradeDelta> deltas, Long sinceMs) {
                return PluginInjectorBridge.get(LocalFlipHistoryService.class).buildHistory(deltas, sinceMs);
            }

            @Override
            public Set<Long> collectAccountwideProfileKeys() {
                ProfileStorageFacadeService storage = profileStorage();
                return PluginInjectorBridge.get(AccountwideProfileKeyCollector.class).collect(
                    storage != null ? storage.getProfilesDir() : null,
                    storage != null ? storage.getLegacyProfilesDir() : null,
                    state.getLocalTradeDeltasByAccount(),
                    state.getLocalStatsLock(),
                    () -> profileSelectionFacade().loadProfilesFromDisk());
            }
        };
    }

    Map<Integer, List<StatsFlipInstance>> buildAccountwideHistory(Long sinceMs) {
        if (hooks == null) {
            return new HashMap<>();
        }
        long accountwideKey = hooks.accountwideKey();
        hooks.ensureProfileLoaded(accountwideKey);

        Map<Integer, List<StatsFlipInstance>> merged = new HashMap<>();
        Set<Long> profileKeys = hooks.collectAccountwideProfileKeys();
        if (profileKeys != null && !profileKeys.isEmpty()) {
            for (Long key : profileKeys) {
                if (key == null || key <= 0 || key == accountwideKey) {
                    continue;
                }
                hooks.ensureProfileLoaded(key);
                Map<Integer, List<StatsFlipInstance>> perProfile = hooks.buildLocalHistory(
                    hooks.snapshotLocalTradeDeltas(key),
                    sinceMs
                );
                mergeHistory(merged, perProfile);
            }
            if (!merged.isEmpty()) {
                sortHistory(merged);
                return merged;
            }
        }

        Map<Integer, List<StatsFlipInstance>> accountwide = hooks.buildLocalHistory(
            hooks.snapshotLocalTradeDeltas(accountwideKey),
            sinceMs
        );
        if (accountwide == null || accountwide.isEmpty()) {
            return new HashMap<>();
        }
        sortHistory(accountwide);
        return accountwide;
    }

    private void sortHistory(Map<Integer, List<StatsFlipInstance>> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        for (List<StatsFlipInstance> entries : history.values()) {
            entries.sort(Comparator.comparingLong((StatsFlipInstance instance) -> instance.completionTsMs).reversed());
        }
    }

    private void mergeHistory(Map<Integer, List<StatsFlipInstance>> target, Map<Integer, List<StatsFlipInstance>> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<StatsFlipInstance>> entry : source.entrySet()) {
            Integer itemId = entry.getKey();
            List<StatsFlipInstance> values = entry.getValue();
            if (itemId == null || itemId <= 0 || values == null || values.isEmpty()) {
                continue;
            }
            target.computeIfAbsent(itemId, ignored -> new ArrayList<>()).addAll(values);
        }
    }
}
