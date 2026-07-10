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

    private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;

    @Inject
    ProfileTradesLoader() {
    }

    private ProfileStorageFacadeService storage() {
        return PluginInjectorBridge.get(ProfileStorageFacadeService.class);
    }

    private ProfileSelectionPresentationFacadeService presentation() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    private boolean isPlaceholderDisplayName(String displayName) {
        GeLifecycleLocalTradesRuntimeService runtime = PluginAccess.plugin().getLocalTradesRuntimeService();
        return runtime != null && runtime.isPlaceholderDisplayName(displayName);
    }

    Result load(long accountHash,
                Map<Long, String> legacyNameKeysByHash,
                int maxLocalTrades,
                long localEventBucketMs,
                long duplicateTradeWindowMs) {
        if (accountHash < 0) {
            return null;
        }
        ProfileStorageFacadeService storage = storage();
        long fileMs = 0L;
        Path file = storage != null ? storage.getProfileFile(accountHash) : null;
        if (file != null) {
            fileMs = PluginAccess.plugin().getProfileFileModifiedMs(file);
        }

        ProfileData profile = storage != null ? storage.readProfileData(accountHash) : null;
        List<LocalTradeDelta> merged = profile != null ? profile.deltas : null;
        String profileName = profile != null ? profile.displayName : null;
        boolean placeholderName = isPlaceholderDisplayName(profileName);
        if (accountHash == accountwideKey) {
            AccountwideTradesMergeService mergeService =
                PluginInjectorBridge.get(AccountwideTradesMergeService.class);
            merged = mergeService != null ? mergeService.buildAccountwideFromDisk() : null;
        }
        if (accountHash != accountwideKey) {
            if (merged == null || merged.isEmpty()) {
                merged = storage != null ? storage.readLegacyLocalTrades(accountHash) : null;
            } else if (placeholderName) {
                List<LocalTradeDelta> legacy = storage != null ? storage.readLegacyLocalTrades(accountHash) : null;
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
        if (profileName != null && !profileName.trim().isEmpty() && !placeholderName) {
            resolvedName = profileName.trim();
        }
        if (resolvedName == null) {
            ProfileSelectionPresentationFacadeService presentation = presentation();
            String legacyKey = legacyNameKeysByHash != null ? legacyNameKeysByHash.get(accountHash) : null;
            String legacyDisplay = presentation != null ? presentation.displayNameFromLegacyKey(legacyKey) : null;
            if (legacyDisplay == null && presentation != null) {
                legacyDisplay = presentation.resolveLegacyDisplayNameForHash(accountHash);
            }
            if (legacyDisplay != null && !legacyDisplay.trim().isEmpty()) {
                resolvedName = legacyDisplay.trim();
            }
        }

        return new Result(merged, resolvedName, Math.max(0L, fileMs));
    }
}
