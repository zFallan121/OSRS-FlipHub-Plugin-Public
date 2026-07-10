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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalProfileTradesLoadService {
    private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
    private final PluginState pluginState;
    private final Gson gson;

    @Inject
    LocalProfileTradesLoadService(PluginState pluginState, Gson gson) {
        this.pluginState = pluginState;
        this.gson = gson;
    }

    private ProfileTradesLoader.Result loadProfileTrades(long accountHash) {
        ProfileTradesLoader loader = PluginInjectorBridge.get(ProfileTradesLoader.class);
        if (gson == null || loader == null) {
            return null;
        }
        return loader.load(
            accountHash,
            pluginState.getLegacyNameKeysByHash(),
            GeLifecyclePluginConstants.MAX_LOCAL_TRADES,
            GeLifecyclePluginConstants.LOCAL_EVENT_BUCKET_MS,
            GeLifecyclePluginConstants.DUPLICATE_TRADE_WINDOW_MS);
    }

    boolean load(long accountHash, boolean persistAfterLoad) {
        if (accountHash < 0) {
            return false;
        }
        ProfileTradesLoader.Result loaded = loadProfileTrades(accountHash);
        if (loaded == null) {
            return false;
        }
        if (loaded.profileFileModifiedMs > 0) {
            pluginState.getLoadedProfileFileMs().put(accountHash, loaded.profileFileModifiedMs);
        }
        List<LocalTradeDelta> merged = loaded.deltas != null ? loaded.deltas : new ArrayList<>();
        synchronized (pluginState.getLocalStatsLock()) {
            pluginState.getLocalTradeDeltasByAccount().put(accountHash, new ArrayList<>(merged));
        }
        LocalStatsCacheService statsCache = PluginInjectorBridge.get(LocalStatsCacheService.class);
        if (statsCache != null) {
            statsCache.rebuild(accountHash, merged);
        }
        String resolvedName = loaded.resolvedDisplayName;
        if (resolvedName != null && !resolvedName.trim().isEmpty()) {
            pluginState.getProfileDisplayNames().put(accountHash, resolvedName.trim());
        }
        ItemLookupService itemLookup = PluginInjectorBridge.get(ItemLookupService.class);
        for (LocalTradeDelta delta : merged) {
            if (delta != null && delta.itemId > 0 && itemLookup != null) {
                itemLookup.cacheItemName(delta.itemId);
            }
        }
        if (persistAfterLoad) {
            PluginAccess.plugin().getLocalTradesRuntimeService().persistLocalTrades(accountHash);
        } else if (accountHash != accountwideKey) {
            PluginAccess.plugin().markAccountwideUploadDirty();
        }
        PanelRefreshCoordinator coordinator = PluginAccess.plugin().getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.scheduleRefreshSoon(PluginAccess.plugin().scheduler);
            coordinator.triggerStatsRefresh(PluginAccess.plugin().scheduler);
        }
        return true;
    }
}
