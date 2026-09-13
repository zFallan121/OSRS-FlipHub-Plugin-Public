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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalStatsSnapshotService {
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final PluginState pluginState;

    @Inject
    LocalStatsSnapshotService(PluginState pluginState) {
        this.pluginState = pluginState;
    }

    private Set<Long> collectAccountwideProfileKeys() {
        ProfileKeyCollector collector = Bridge.get(ProfileKeyCollector.class);
        ProfileStorage storage = Bridge.get(ProfileStorage.class);
        if (collector == null || storage == null) {
            return Collections.emptySet();
        }
        return collector.collect(
            storage.getProfilesDir(),
            storage.getLegacyProfilesDir(),
            pluginState.getLocalTradeDeltasByAccount(),
            pluginState.getLocalStatsLock(),
            this::loadProfilesFromDisk);
    }

    private Map<Long, String> loadProfilesFromDisk() {
        ProfileSelectionPresentation service =
            Bridge.get(ProfileSelectionPresentation.class);
        return service != null ? service.loadProfilesFromDisk() : Collections.emptyMap();
    }

    StatsSnapshot buildSnapshot(long accountKey, Long sinceMs, StatsItemSort sort) {
        if (accountKey == accountwideKey) {
            return buildAccountwideSnapshot(sinceMs, sort);
        }
        return buildSnapshotForAccount(accountKey, sinceMs, sort);
    }

    void hydrateItemNames(List<StatsItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        ItemLookup itemLookup = Bridge.get(ItemLookup.class);
        for (StatsItem item : items) {
            if (item == null || item.item_id <= 0) {
                continue;
            }
            String cachedName = itemLookup != null ? itemLookup.getCachedItemName(item.item_id) : null;
            item.item_name = cachedName;
            if ((cachedName == null || cachedName.trim().isEmpty()) && itemLookup != null) {
                itemLookup.cacheItemName(item.item_id);
            }
        }
    }

    Comparator<StatsItem> buildComparator(StatsItemSort sort) {
        StatsItemSort effective = sort != null ? sort : StatsItemSort.COMPLETION;
        if (effective == StatsItemSort.ROI) {
            return Comparator
                .comparingDouble((StatsItem item) -> item != null && item.roi_percent != null ? item.roi_percent : 0.0)
                .reversed()
                .thenComparing(Comparator.comparingLong(
                    (StatsItem item) -> item != null && item.total_profit_gp != null ? item.total_profit_gp : 0L
                ).reversed());
        }
        if (effective == StatsItemSort.PROFIT) {
            return Comparator
                .comparingLong((StatsItem item) -> item != null && item.total_profit_gp != null ? item.total_profit_gp : 0L)
                .reversed()
                .thenComparing(Comparator.comparingLong(
                    (StatsItem item) -> item != null && item.last_sell_ts_ms != null ? item.last_sell_ts_ms : 0L
                ).reversed());
        }
        return Comparator
            .comparingLong((StatsItem item) -> item != null && item.last_sell_ts_ms != null ? item.last_sell_ts_ms : 0L)
            .reversed()
            .thenComparing(Comparator.comparingLong(
                (StatsItem item) -> item != null && item.total_profit_gp != null ? item.total_profit_gp : 0L
            ).reversed());
    }

    /**
     * Every character's figures, added up character by character.
     *
     * <p>The alternative, pooling all their trades into one ledger, matches a buy made on one
     * character against a sell made on another: main buys a thousand sharks, an alt sells five
     * hundred it looted, and the pair is booked as a flip at a blended cost that never existed.
     * The flip list is already built per character, so pooling also left the item rows unable
     * to add up to the total above them. Adding up per character agrees with both.
     */
    private StatsSnapshot buildAccountwideSnapshot(Long sinceMs, StatsItemSort sort) {
        Set<Long> profileKeys = collectAccountwideProfileKeys();
        StatsAggregator aggregator = Bridge.get(StatsAggregator.class);
        StatsSnapshot aggregated =
            aggregator != null ? aggregator.buildFromProfiles(profileKeys, sinceMs, sort) : null;
        // No pooled fallback: the accountwide key is zero and the cache refuses a key that is
        // not positive, so asking it for one only ever returned an empty snapshot.
        return aggregated != null ? aggregated : emptySnapshot();
    }

    private StatsSnapshot buildSnapshotForAccount(long accountKey, Long sinceMs, StatsItemSort sort) {
        Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
        LocalStatsCacheService cacheService = Bridge.get(LocalStatsCacheService.class);
        StatsCache cache = cacheService != null ? cacheService.getOrBuild(accountKey) : null;
        if (cache == null) {
            return emptySnapshot();
        }

        StatsSnapshot snapshot = sinceMs == null
            ? new StatsSnapshot(cache.getSummary(), cache.getItems())
            : cache.buildSnapshotSince(sinceMs);
        if (snapshot == null) {
            return emptySnapshot();
        }
        List<StatsItem> items = snapshot.items != null ? snapshot.items : new ArrayList<>();
        hydrateItemNames(items);
        items.sort(buildComparator(sort));
        StatsSummary summary = snapshot.summary != null ? snapshot.summary : new StatsSummary();
        return new StatsSnapshot(summary, items);
    }

    private static StatsSnapshot emptySnapshot() {
        return new StatsSnapshot(new StatsSummary(), new ArrayList<>());
    }
}
