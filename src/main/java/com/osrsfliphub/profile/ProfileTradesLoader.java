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
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class ProfileTradesLoader {
    interface Hooks {
        Path getProfileFile(long accountHash);
        long getProfileFileModifiedMs(Path file);
        ProfileData readProfileData(long accountHash);
        List<LocalTradeDelta> buildAccountwideFromDisk();
        List<LocalTradeDelta> readLegacyLocalTrades(long accountHash);
        boolean isPlaceholderDisplayName(String displayName);
        String displayNameFromLegacyKey(String legacyKey);
        String resolveLegacyDisplayNameForHash(long accountHash);
    }

    static final class Result {
        final List<LocalTradeDelta> deltas;
        final String resolvedDisplayName;
        final long profileFileModifiedMs;

        Result(List<LocalTradeDelta> deltas, String resolvedDisplayName, long profileFileModifiedMs) {
            this.deltas = deltas != null ? deltas : new ArrayList<>();
            this.resolvedDisplayName = resolvedDisplayName;
            this.profileFileModifiedMs = profileFileModifiedMs;
        }
    }

    private final long accountwideKey;
    private final Hooks hooks;

    @Inject
    ProfileTradesLoader() {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY, new Hooks() {
            @Override
            public Path getProfileFile(long accountHash) {
                ProfileStorageFacadeService service = PluginInjectorBridge.get(ProfileStorageFacadeService.class);
                return service != null ? service.getProfileFile(accountHash) : null;
            }

            @Override
            public long getProfileFileModifiedMs(Path file) {
                return PluginAccess.plugin().getProfileFileModifiedMs(file);
            }

            @Override
            public ProfileData readProfileData(long accountHash) {
                ProfileStorageFacadeService service = PluginInjectorBridge.get(ProfileStorageFacadeService.class);
                return service != null ? service.readProfileData(accountHash) : null;
            }

            @Override
            public List<LocalTradeDelta> buildAccountwideFromDisk() {
                AccountwideTradesMergeService service = PluginInjectorBridge.get(AccountwideTradesMergeService.class);
                return service != null ? service.buildAccountwideFromDisk() : null;
            }

            @Override
            public List<LocalTradeDelta> readLegacyLocalTrades(long accountHash) {
                ProfileStorageFacadeService service = PluginInjectorBridge.get(ProfileStorageFacadeService.class);
                return service != null ? service.readLegacyLocalTrades(accountHash) : null;
            }

            @Override
            public boolean isPlaceholderDisplayName(String displayName) {
                GeLifecycleLocalTradesRuntimeService runtime = PluginAccess.plugin().getLocalTradesRuntimeService();
                return runtime != null && runtime.isPlaceholderDisplayName(displayName);
            }

            @Override
            public String displayNameFromLegacyKey(String legacyKey) {
                ProfileSelectionPresentationFacadeService service =
                    PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                return service != null ? service.displayNameFromLegacyKey(legacyKey) : null;
            }

            @Override
            public String resolveLegacyDisplayNameForHash(long accountHash) {
                ProfileSelectionPresentationFacadeService service =
                    PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                return service != null ? service.resolveLegacyDisplayNameForHash(accountHash) : null;
            }
        });
    }

    ProfileTradesLoader(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
    }

    Result load(long accountHash,
                Map<Long, String> legacyNameKeysByHash,
                int maxLocalTrades,
                long localEventBucketMs,
                long duplicateTradeWindowMs) {
        if (accountHash < 0 || hooks == null) {
            return null;
        }
        long fileMs = 0L;
        Path file = hooks.getProfileFile(accountHash);
        if (file != null) {
            fileMs = hooks.getProfileFileModifiedMs(file);
        }

        ProfileData profile = hooks.readProfileData(accountHash);
        List<LocalTradeDelta> merged = profile != null ? profile.deltas : null;
        String profileName = profile != null ? profile.displayName : null;
        boolean placeholderName = hooks.isPlaceholderDisplayName(profileName);
        if (accountHash == accountwideKey) {
            merged = hooks.buildAccountwideFromDisk();
        }
        if (accountHash != accountwideKey) {
            if (merged == null || merged.isEmpty()) {
                merged = hooks.readLegacyLocalTrades(accountHash);
            } else if (placeholderName) {
                List<LocalTradeDelta> legacy = hooks.readLegacyLocalTrades(accountHash);
                if (legacy != null && !legacy.isEmpty()) {
                    merged = legacy;
                }
            }
        }
        merged = LocalTradeDeltaUtils.dedupeLocalTrades(
            merged,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
        if (merged == null) {
            merged = new ArrayList<>();
        }

        String resolvedName = null;
        if (profileName != null && !profileName.trim().isEmpty() && !hooks.isPlaceholderDisplayName(profileName)) {
            resolvedName = profileName.trim();
        }
        if (resolvedName == null) {
            String legacyKey = legacyNameKeysByHash != null ? legacyNameKeysByHash.get(accountHash) : null;
            String legacyDisplay = hooks.displayNameFromLegacyKey(legacyKey);
            if (legacyDisplay == null) {
                legacyDisplay = hooks.resolveLegacyDisplayNameForHash(accountHash);
            }
            if (legacyDisplay != null && !legacyDisplay.trim().isEmpty()) {
                resolvedName = legacyDisplay.trim();
            }
        }

        return new Result(merged, resolvedName, Math.max(0L, fileMs));
    }
}
