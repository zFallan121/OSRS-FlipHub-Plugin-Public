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

import java.util.*;
import javax.inject.*;

@Singleton
final class FlipHistory {
    private final ProfileSelectionPresentation profileSelectionPresentation;
    private final LocalTradesRuntime localTradesRuntime;
    private final TradeSession tradeSession;
    private final LocalFlipHistoryService localFlipHistoryService;
    private final ProfileStorage profileStorage;
    private final ProfileKeyCollector profileKeyCollector;
    private final PluginState state;

    @Inject
    FlipHistory(
        PluginState state,
        LocalFlipHistoryService localFlipHistoryService,
        ProfileStorage profileStorage,
        ProfileKeyCollector profileKeyCollector,
        ProfileSelectionPresentation profileSelectionPresentation,
        LocalTradesRuntime localTradesRuntime,
        TradeSession tradeSession
    ) {
        this.profileSelectionPresentation = profileSelectionPresentation;
        this.localTradesRuntime = localTradesRuntime;
        this.tradeSession = tradeSession;
        this.localFlipHistoryService = localFlipHistoryService;
        this.profileStorage = profileStorage;
        this.profileKeyCollector = profileKeyCollector;
        this.state = state;
    }

    private Set<Long> collectAccountwideProfileKeys() {
        return profileKeyCollector.collect(
            profileStorage.getProfilesDir(),
            profileStorage.getLegacyProfilesDir(),
            state.getLocalTradeDeltasByAccount(),
            state.getLocalStatsLock(),
            () -> profileSelectionPresentation.loadProfilesFromDisk());
    }

    Map<Integer, List<StatsFlipInstance>> buildAccountwideHistory(Long sinceMs) {
        long accountwideKey = Const.ACCOUNTWIDE_KEY;
        localTradesRuntime.ensureProfileLoaded(accountwideKey);

        Map<Integer, List<StatsFlipInstance>> merged = new HashMap<>();
        Set<Long> profileKeys = collectAccountwideProfileKeys();
        if (profileKeys != null && !profileKeys.isEmpty()) {
            for (Long key : profileKeys) {
                if (key == null || key <= 0 || key == accountwideKey) {
                    continue;
                }
                localTradesRuntime.ensureProfileLoaded(key);
                Map<Integer, List<StatsFlipInstance>> perProfile = localFlipHistoryService.buildHistory(
                    (tradeSession.snapshotLocalTradeDeltas(key)),
                    sinceMs,
                    key
                );
                mergeHistory(merged, perProfile);
            }
            if (!merged.isEmpty()) {
                sortHistory(merged);
                return merged;
            }
        }

        Map<Integer, List<StatsFlipInstance>> accountwide = localFlipHistoryService.buildHistory(
            (tradeSession.snapshotLocalTradeDeltas(accountwideKey)),
            sinceMs,
            accountwideKey
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
