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
import java.util.*;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Singleton
final class ProfileKeyCollector {
    Set<Long> collect(Path profilesDir,
                      Path legacyProfilesDir,
                      Map<Long, List<Delta>> localTradeDeltasByAccount,
                      Object localStatsLock,
                      Supplier<Map<Long, String>> fallbackProfilesSupplier) {
        Set<Long> keys = new HashSet<>();
        ProfileHashFileWalker.walk(profilesDir, (hash, path) -> keys.add(hash));
        ProfileHashFileWalker.walk(legacyProfilesDir, (hash, path) -> keys.add(hash));

        synchronized (localStatsLock) {
            localTradeDeltasByAccount.forEach((key, deltas) -> {
                if (key != null && key > 0 && deltas != null && !deltas.isEmpty()) {
                    keys.add(key);
                }
            });
        }

        if (keys.isEmpty()) {
            Map<Long, String> fallback = fallbackProfilesSupplier.get();
            if (fallback != null) {
                for (Long key : fallback.keySet()) {
                    if (key != null && key > 0) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }
}
