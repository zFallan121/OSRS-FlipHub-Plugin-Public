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

final class LocalProfileTradesLoadService {
    interface Hooks {
        ProfileTradesLoader.Result loadProfileTrades(long accountHash);
        void putLoadedProfileFileMs(long accountHash, long fileMs);
        void setLocalTradeDeltas(long accountHash, List<LocalTradeDelta> deltas);
        void rebuildStatsCache(long accountHash, List<LocalTradeDelta> deltas);
        void putProfileDisplayName(long accountHash, String displayName);
        void cacheItemName(int itemId);
        void persistLocalTrades(long accountHash);
        void markAccountwideUploadDirty();
        void scheduleRefreshSoon();
        void triggerStatsRefresh();
    }

    private final long accountwideKey;
    private final Hooks hooks;

    LocalProfileTradesLoadService(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
    }

    boolean load(long accountHash, boolean persistAfterLoad) {
        if (accountHash < 0 || hooks == null) {
            return false;
        }
        ProfileTradesLoader.Result loaded = hooks.loadProfileTrades(accountHash);
        if (loaded == null) {
            return false;
        }
        if (loaded.profileFileModifiedMs > 0) {
            hooks.putLoadedProfileFileMs(accountHash, loaded.profileFileModifiedMs);
        }
        List<LocalTradeDelta> merged = loaded.deltas != null ? loaded.deltas : new ArrayList<>();
        hooks.setLocalTradeDeltas(accountHash, new ArrayList<>(merged));
        hooks.rebuildStatsCache(accountHash, merged);
        String resolvedName = loaded.resolvedDisplayName;
        if (resolvedName != null && !resolvedName.trim().isEmpty()) {
            hooks.putProfileDisplayName(accountHash, resolvedName.trim());
        }
        for (LocalTradeDelta delta : merged) {
            if (delta != null && delta.itemId > 0) {
                hooks.cacheItemName(delta.itemId);
            }
        }
        if (persistAfterLoad) {
            hooks.persistLocalTrades(accountHash);
        } else if (accountHash != accountwideKey) {
            hooks.markAccountwideUploadDirty();
        }
        hooks.scheduleRefreshSoon();
        hooks.triggerStatsRefresh();
        return true;
    }
}
