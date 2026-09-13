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
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalStatsCacheService {
    private final Map<Long, StatsCache> statsCacheByAccount;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Object localStatsLock;

    @Inject
    LocalStatsCacheService(PluginState pluginState) {
        this(pluginState.getStatsCacheByAccount(),
            pluginState.getLocalTradeDeltasByAccount(),
            pluginState.getLocalStatsLock());
    }

    LocalStatsCacheService(Map<Long, StatsCache> statsCacheByAccount,
                           Map<Long, List<Delta>> localTradeDeltasByAccount,
                           Object localStatsLock) {
        this.statsCacheByAccount = statsCacheByAccount;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
    }

    StatsCache getOrBuild(long accountKey) {
        if (accountKey <= 0) {
            return null;
        }
        StatsCache cache = statsCacheByAccount.get(accountKey);
        if (cache != null) {
            return cache;
        }
        List<Delta> snapshot = snapshotDeltas(accountKey);
        StatsCache created = new StatsCache(accountKey);
        created.rebuild(snapshot);
        statsCacheByAccount.put(accountKey, created);
        return created;
    }

    void rebuild(long accountKey, List<Delta> deltas) {
        if (accountKey <= 0) {
            return;
        }
        StatsCache cache = new StatsCache(accountKey);
        cache.rebuild(deltas != null ? deltas : new ArrayList<>());
        statsCacheByAccount.put(accountKey, cache);
    }

    /**
     * Rebuild from what is stored. Used when the stored list changed shape rather than
     * grew - an offer's fills folded into one record on completion - so the running
     * aggregate shows exactly what a fresh start would replay.
     */
    void rebuildFromStored(long accountKey) {
        if (accountKey <= 0) {
            return;
        }
        rebuild(accountKey, snapshotDeltas(accountKey));
    }

    /**
     * Drop every cached aggregate so the next read rebuilds it from the stored
     * deltas. Used when something the rebuild depends on changed after the fact
     * - the conversion table resolving is the only such thing today.
     */
    void invalidateAll() {
        statsCacheByAccount.clear();
    }

    void applyDelta(long accountKey, Delta delta) {
        if (accountKey <= 0 || delta == null) {
            return;
        }
        StatsCache cache = statsCacheByAccount.get(accountKey);
        if (cache == null) {
            // No aggregate yet, because it was invalidated, wiped, or never built. Callers
            // append the delta to the stored list before applying it here, so a rebuild sees
            // this delta and everything before it. Seeding an empty cache with this one delta
            // would instead report a single fill as the account's entire history.
            rebuild(accountKey, snapshotDeltas(accountKey));
            return;
        }
        if (!cache.applyDeltaInOrder(delta)) {
            rebuild(accountKey, snapshotDeltas(accountKey));
        }
    }

    private List<Delta> snapshotDeltas(long accountKey) {
        synchronized (localStatsLock) {
            List<Delta> deltas = localTradeDeltasByAccount.get(accountKey);
            return deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
        }
    }
}
