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

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Singleton
final class AccountwideProfileKeyCollector {
    Set<Long> collect(Path profilesDir,
                      Path legacyProfilesDir,
                      Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
                      Object localStatsLock,
                      Supplier<Map<Long, String>> fallbackProfilesSupplier) {
        Set<Long> keys = new HashSet<>();
        collectFromDir(keys, profilesDir);
        collectFromDir(keys, legacyProfilesDir);

        if (localTradeDeltasByAccount != null) {
            if (localStatsLock != null) {
                synchronized (localStatsLock) {
                    addKeysFromLocalTrades(keys, localTradeDeltasByAccount);
                }
            } else {
                addKeysFromLocalTrades(keys, localTradeDeltasByAccount);
            }
        }

        if (keys.isEmpty() && fallbackProfilesSupplier != null) {
            Map<Long, String> fallback = fallbackProfilesSupplier.get();
            if (fallback != null && !fallback.isEmpty()) {
                for (Long key : fallback.keySet()) {
                    if (key != null && key > 0) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    private void collectFromDir(Set<Long> keys, Path dir) {
        if (keys == null) {
            return;
        }
        ProfileHashFileWalker.walk(dir, (hash, path) -> keys.add(hash));
    }

    private void addKeysFromLocalTrades(Set<Long> keys, Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount) {
        for (Map.Entry<Long, List<LocalTradeDelta>> entry : localTradeDeltasByAccount.entrySet()) {
            if (entry == null) {
                continue;
            }
            Long key = entry.getKey();
            List<LocalTradeDelta> deltas = entry.getValue();
            if (key != null && key > 0 && deltas != null && !deltas.isEmpty()) {
                keys.add(key);
            }
        }
    }
}
