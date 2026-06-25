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

final class LocalStatsCacheService {
    private final Map<Long, LocalStatsCache> statsCacheByAccount;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Object localStatsLock;

    LocalStatsCacheService(Map<Long, LocalStatsCache> statsCacheByAccount,
                           Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
                           Object localStatsLock) {
        this.statsCacheByAccount = statsCacheByAccount;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
    }

    LocalStatsCache getOrBuild(long accountKey) {
        if (accountKey <= 0) {
            return null;
        }
        LocalStatsCache cache = statsCacheByAccount.get(accountKey);
        if (cache != null) {
            return cache;
        }
        List<LocalTradeDelta> snapshot = snapshotDeltas(accountKey);
        LocalStatsCache created = new LocalStatsCache();
        created.rebuild(snapshot);
        statsCacheByAccount.put(accountKey, created);
        return created;
    }

    void rebuild(long accountKey, List<LocalTradeDelta> deltas) {
        if (accountKey <= 0) {
            return;
        }
        LocalStatsCache cache = new LocalStatsCache();
        cache.rebuild(deltas != null ? deltas : new ArrayList<>());
        statsCacheByAccount.put(accountKey, cache);
    }

    void applyDelta(long accountKey, LocalTradeDelta delta) {
        if (accountKey <= 0 || delta == null) {
            return;
        }
        LocalStatsCache cache = statsCacheByAccount.get(accountKey);
        if (cache == null) {
            cache = new LocalStatsCache();
            statsCacheByAccount.put(accountKey, cache);
        }
        if (!cache.applyDeltaInOrder(delta)) {
            rebuild(accountKey, snapshotDeltas(accountKey));
        }
    }

    private List<LocalTradeDelta> snapshotDeltas(long accountKey) {
        synchronized (localStatsLock) {
            List<LocalTradeDelta> deltas = localTradeDeltasByAccount.get(accountKey);
            return deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
        }
    }
}
