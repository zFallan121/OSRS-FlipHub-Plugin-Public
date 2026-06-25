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
import java.util.ArrayList;
import java.util.List;

final class ProfileStorageFacadeService {
    interface Hooks {
        ProfileStore getProfileStore();
        LegacyLocalTradesStore getLegacyLocalTradesStore();
        boolean isLegacyReadEnabled();
        boolean isWipeBarrierArmed(long accountHash);
        String getLegacyNameKey(long accountHash);
        String getProfileDisplayName(long accountHash);
        void putLoadedProfileFileMs(long accountHash, long fileMs);
    }

    private final long accountwideKey;
    private final int maxLocalTrades;
    private final Hooks hooks;

    ProfileStorageFacadeService(long accountwideKey, int maxLocalTrades, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.maxLocalTrades = maxLocalTrades;
        this.hooks = hooks;
    }

    Path getProfilesDir() {
        ProfileStore store = hooks != null ? hooks.getProfileStore() : null;
        return store != null ? store.getProfilesDir() : null;
    }

    Path getLegacyProfilesDir() {
        ProfileStore store = hooks != null ? hooks.getProfileStore() : null;
        return store != null ? store.getLegacyProfilesDir() : null;
    }

    Path getProfileFile(long accountHash) {
        ProfileStore store = hooks != null ? hooks.getProfileStore() : null;
        return store != null ? store.getProfileFile(accountHash, accountwideKey) : null;
    }

    ProfileData readProfileData(long accountHash) {
        ProfileStore store = hooks != null ? hooks.getProfileStore() : null;
        return store != null ? store.readProfileData(accountHash, accountwideKey) : null;
    }

    ProfileData readProfileData(Path file) {
        ProfileStore store = hooks != null ? hooks.getProfileStore() : null;
        return store != null ? store.readProfileData(file) : null;
    }

    void writeProfileData(long accountHash, List<LocalTradeDelta> deltas) {
        if (hooks == null) {
            return;
        }
        ProfileStore store = hooks.getProfileStore();
        if (store == null) {
            return;
        }
        String displayName = accountHash == accountwideKey ? "Accountwide" : hooks.getProfileDisplayName(accountHash);
        List<LocalTradeDelta> snapshot = deltas != null ? deltas : new ArrayList<>();
        long fileMs = store.writeProfileData(accountHash, accountwideKey, displayName, snapshot);
        if (fileMs > 0) {
            hooks.putLoadedProfileFileMs(accountHash, fileMs);
        }
    }

    List<LocalTradeDelta> readLegacyLocalTrades(long accountHash) {
        if (hooks == null || !hooks.isLegacyReadEnabled() || accountHash <= 0) {
            return null;
        }
        if (hooks.isWipeBarrierArmed(accountHash)) {
            return null;
        }
        List<LocalTradeDelta> byName = null;
        String legacyNameKey = hooks.getLegacyNameKey(accountHash);
        if (legacyNameKey != null) {
            byName = readLegacyLocalTrades(legacyNameKey);
        }
        String primaryKey = "hash_" + accountHash;
        List<String> keys = buildLegacyLocalTradesKeys(primaryKey, accountHash);
        if (byName != null && !byName.isEmpty()) {
            return LocalTradeDeltaUtils.mergeLocalTrades(
                byName,
                readLegacyLocalTrades(keys, 0),
                readLegacyLocalTrades(keys, 1),
                readLegacyLocalTrades(keys, 2),
                maxLocalTrades
            );
        }
        return LocalTradeDeltaUtils.mergeLocalTrades(
            readLegacyLocalTrades(keys, 0),
            readLegacyLocalTrades(keys, 1),
            readLegacyLocalTrades(keys, 2),
            readLegacyLocalTrades(keys, 3),
            maxLocalTrades
        );
    }

    private List<LocalTradeDelta> readLegacyLocalTrades(List<String> keys, int index) {
        if (keys == null || index < 0 || index >= keys.size()) {
            return null;
        }
        return readLegacyLocalTrades(keys.get(index));
    }

    private List<LocalTradeDelta> readLegacyLocalTrades(String key) {
        LegacyLocalTradesStore store = hooks != null ? hooks.getLegacyLocalTradesStore() : null;
        return store != null ? store.readLocalTrades(key) : null;
    }

    private List<String> buildLegacyLocalTradesKeys(String primaryKey, long accountHash) {
        List<String> keys = new ArrayList<>();
        addLocalTradesKey(keys, primaryKey);
        if (accountHash > 0) {
            addLocalTradesKey(keys, "hash_" + accountHash);
            addLocalTradesKey(keys, String.valueOf(accountHash));
        }
        return keys;
    }

    private void addLocalTradesKey(List<String> keys, String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        String trimmed = key.trim();
        if (!keys.contains(trimmed)) {
            keys.add(trimmed);
        }
    }
}
