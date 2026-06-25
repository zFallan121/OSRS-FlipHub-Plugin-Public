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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

final class AccountwideStatsAggregatorPluginHooks implements AccountwideStatsAggregator.Hooks {
    private final LongConsumer ensureProfileLoadedConsumer;
    private final LongFunction<LocalStatsCache> localStatsCacheLookup;
    private final Consumer<List<StatsItem>> hydrateItemNamesConsumer;
    private final Function<StatsItemSort, Comparator<StatsItem>> comparatorBuilder;

    AccountwideStatsAggregatorPluginHooks(
        LongConsumer ensureProfileLoadedConsumer,
        LongFunction<LocalStatsCache> localStatsCacheLookup,
        Consumer<List<StatsItem>> hydrateItemNamesConsumer,
        Function<StatsItemSort, Comparator<StatsItem>> comparatorBuilder
    ) {
        this.ensureProfileLoadedConsumer = ensureProfileLoadedConsumer;
        this.localStatsCacheLookup = localStatsCacheLookup;
        this.hydrateItemNamesConsumer = hydrateItemNamesConsumer;
        this.comparatorBuilder = comparatorBuilder;
    }

    @Override
    public void ensureProfileLoaded(long accountKey) {
        if (ensureProfileLoadedConsumer != null) {
            ensureProfileLoadedConsumer.accept(accountKey);
        }
    }

    @Override
    public LocalStatsSnapshot buildSnapshotForProfile(long accountKey, Long sinceMs) {
        LocalStatsCache cache = localStatsCacheLookup != null ? localStatsCacheLookup.apply(accountKey) : null;
        if (cache == null) {
            return new LocalStatsSnapshot(new StatsSummary(), new ArrayList<>());
        }
        return cache.buildSnapshotSince(sinceMs);
    }

    @Override
    public void hydrateItemNames(List<StatsItem> items) {
        if (hydrateItemNamesConsumer != null) {
            hydrateItemNamesConsumer.accept(items);
        }
    }

    @Override
    public Comparator<StatsItem> buildComparator(StatsItemSort sort) {
        return comparatorBuilder != null ? comparatorBuilder.apply(sort) : null;
    }
}
