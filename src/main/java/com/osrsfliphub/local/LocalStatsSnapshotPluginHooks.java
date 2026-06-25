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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class LocalStatsSnapshotPluginHooks implements LocalStatsSnapshotService.Hooks {
    private final long accountwideKey;
    private final LongConsumer ensureProfileLoaded;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Map<Long, java.util.List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Object localStatsLock;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier;

    LocalStatsSnapshotPluginHooks(
        long accountwideKey,
        LongConsumer ensureProfileLoaded,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Map<Long, java.util.List<LocalTradeDelta>> localTradeDeltasByAccount,
        Object localStatsLock,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier
    ) {
        this.accountwideKey = accountwideKey;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.accountwideProfileKeyCollectorSupplier = accountwideProfileKeyCollectorSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.accountwideStatsAggregatorSupplier = accountwideStatsAggregatorSupplier;
    }

    @Override
    public long accountwideKey() {
        return accountwideKey;
    }

    @Override
    public void ensureProfileLoaded(long accountKey) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(accountKey);
        }
    }

    @Override
    public LocalStatsCache getOrBuildStatsCache(long accountKey) {
        LocalStatsCacheService service = localStatsCacheServiceSupplier != null
            ? localStatsCacheServiceSupplier.get()
            : null;
        return service != null ? service.getOrBuild(accountKey) : null;
    }

    @Override
    public String getCachedItemName(int itemId) {
        ItemLookupService service = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        return service != null ? service.getCachedItemName(itemId) : null;
    }

    @Override
    public void cacheItemName(int itemId) {
        ItemLookupService service = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        if (service != null) {
            service.cacheItemName(itemId);
        }
    }

    @Override
    public Set<Long> collectAccountwideProfileKeys() {
        AccountwideProfileKeyCollector collector = accountwideProfileKeyCollectorSupplier != null
            ? accountwideProfileKeyCollectorSupplier.get()
            : null;
        ProfileStorageFacadeService profileStorageFacadeService = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        if (collector == null || profileStorageFacadeService == null) {
            return Collections.emptySet();
        }
        return collector.collect(
            profileStorageFacadeService.getProfilesDir(),
            profileStorageFacadeService.getLegacyProfilesDir(),
            localTradeDeltasByAccount,
            localStatsLock,
            this::loadProfilesFromDisk
        );
    }

    @Override
    public LocalStatsSnapshot buildAccountwideFromProfiles(Set<Long> profileKeys, Long sinceMs, StatsItemSort sort) {
        AccountwideStatsAggregator aggregator = accountwideStatsAggregatorSupplier != null
            ? accountwideStatsAggregatorSupplier.get()
            : null;
        return aggregator != null ? aggregator.buildFromProfiles(profileKeys, sinceMs, sort) : null;
    }

    private Map<Long, String> loadProfilesFromDisk() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.loadProfilesFromDisk() : Collections.emptyMap();
    }
}
