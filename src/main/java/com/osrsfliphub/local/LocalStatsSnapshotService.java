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
    interface Hooks {
        long accountwideKey();
        void ensureProfileLoaded(long accountKey);
        LocalStatsCache getOrBuildStatsCache(long accountKey);
        String getCachedItemName(int itemId);
        void cacheItemName(int itemId);
        Set<Long> collectAccountwideProfileKeys();
        LocalStatsSnapshot buildAccountwideFromProfiles(Set<Long> profileKeys, Long sinceMs, StatsItemSort sort);
    }

    private final Hooks hooks;

    @Inject
    LocalStatsSnapshotService(PluginState pluginState) {
        this(new Hooks() {
            @Override
            public long accountwideKey() {
                return GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
            }

            @Override
            public void ensureProfileLoaded(long accountKey) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
            }

            @Override
            public LocalStatsCache getOrBuildStatsCache(long accountKey) {
                LocalStatsCacheService service =
                    PluginInjectorBridge.get(LocalStatsCacheService.class);
                return service != null ? service.getOrBuild(accountKey) : null;
            }

            @Override
            public String getCachedItemName(int itemId) {
                ItemLookupService service = PluginInjectorBridge.get(ItemLookupService.class);
                return service != null ? service.getCachedItemName(itemId) : null;
            }

            @Override
            public void cacheItemName(int itemId) {
                ItemLookupService service = PluginInjectorBridge.get(ItemLookupService.class);
                if (service != null) {
                    service.cacheItemName(itemId);
                }
            }

            @Override
            public Set<Long> collectAccountwideProfileKeys() {
                AccountwideProfileKeyCollector collector =
                    PluginInjectorBridge.get(AccountwideProfileKeyCollector.class);
                ProfileStorageFacadeService storage = PluginInjectorBridge.get(ProfileStorageFacadeService.class);
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

            @Override
            public LocalStatsSnapshot buildAccountwideFromProfiles(Set<Long> profileKeys, Long sinceMs, StatsItemSort sort) {
                AccountwideStatsAggregator aggregator = PluginInjectorBridge.get(AccountwideStatsAggregator.class);
                return aggregator != null ? aggregator.buildFromProfiles(profileKeys, sinceMs, sort) : null;
            }

            private Map<Long, String> loadProfilesFromDisk() {
                ProfileSelectionPresentationFacadeService service =
                    PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                return service != null ? service.loadProfilesFromDisk() : Collections.emptyMap();
            }
        });
    }

    LocalStatsSnapshotService(Hooks hooks) {
        this.hooks = hooks;
    }

    LocalStatsSnapshot buildSnapshot(long accountKey, Long sinceMs, StatsItemSort sort) {
        if (hooks == null) {
            return emptySnapshot();
        }
        if (accountKey == hooks.accountwideKey()) {
            return buildAccountwideSnapshot(sinceMs, sort);
        }
        return buildSnapshotForAccount(accountKey, sinceMs, sort);
    }

    void hydrateItemNames(List<StatsItem> items) {
        if (hooks == null || items == null || items.isEmpty()) {
            return;
        }
        for (StatsItem item : items) {
            if (item == null || item.item_id <= 0) {
                continue;
            }
            String cachedName = hooks.getCachedItemName(item.item_id);
            item.item_name = cachedName;
            if (cachedName == null || cachedName.trim().isEmpty()) {
                hooks.cacheItemName(item.item_id);
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

    private LocalStatsSnapshot buildAccountwideSnapshot(Long sinceMs, StatsItemSort sort) {
        long accountwideKey = hooks.accountwideKey();
        LocalStatsSnapshot accountwideSnapshot = buildSnapshotForAccount(accountwideKey, sinceMs, sort);
        if (AccountwideStatsAggregator.hasMeaningfulStats(accountwideSnapshot)) {
            return accountwideSnapshot;
        }
        Set<Long> profileKeys = hooks.collectAccountwideProfileKeys();
        LocalStatsSnapshot aggregated = hooks.buildAccountwideFromProfiles(profileKeys, sinceMs, sort);
        return aggregated != null ? aggregated : emptySnapshot();
    }

    private LocalStatsSnapshot buildSnapshotForAccount(long accountKey, Long sinceMs, StatsItemSort sort) {
        hooks.ensureProfileLoaded(accountKey);
        LocalStatsCache cache = hooks.getOrBuildStatsCache(accountKey);
        if (cache == null) {
            return emptySnapshot();
        }

        LocalStatsSnapshot snapshot = sinceMs == null
            ? new LocalStatsSnapshot(cache.getSummary(), cache.getItems())
            : cache.buildSnapshotSince(sinceMs);
        if (snapshot == null) {
            return emptySnapshot();
        }
        List<StatsItem> items = snapshot.items != null ? snapshot.items : new ArrayList<>();
        hydrateItemNames(items);
        items.sort(buildComparator(sort));
        StatsSummary summary = snapshot.summary != null ? snapshot.summary : new StatsSummary();
        return new LocalStatsSnapshot(summary, items);
    }

    private static LocalStatsSnapshot emptySnapshot() {
        return new LocalStatsSnapshot(new StatsSummary(), new ArrayList<>());
    }
}
