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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class RemainingLimitSuggestionService {
    private static final long LIMIT_SUGGESTION_REFRESH_MS = 1000L;
    private static final long GE_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;

    interface Hooks {
        long resolveLocalAccountKey();
        long resolveSelectedProfileKey();
        void ensureProfileLoaded(long accountKey);
        void requestGeLimits(Set<Integer> itemIds);
        Integer getCachedGeLimit(int itemId);
        Integer lookupGeLimitSafe(int itemId);
        Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs);
        FlipHubItem getOfferPreviewItem();
        long nowMs();
    }

    private final Hooks hooks;
    private Integer cachedRemainingLimitSuggestion;
    private Integer cachedRemainingLimitItemId;
    private long cachedRemainingLimitAccountKey = Long.MIN_VALUE;
    private long cachedRemainingLimitAtMs;

    @Inject
    RemainingLimitSuggestionService() {
        this(productionHooks());
    }

    RemainingLimitSuggestionService(Hooks hooks) {
        this.hooks = hooks;
    }

    private static GeLimitService geLimitService() {
        return PluginAccess.plugin().getBackfillServices().getBackfillMarketServices().getGeLimitService();
    }

    private static Hooks productionHooks() {
        return new Hooks() {
            @Override
            public long resolveLocalAccountKey() {
                LocalAccountSessionService service =
                    PluginAccess.plugin().getStatsTradesServices().getLocalAccountSessionService();
                return service != null ? service.resolveLocalAccountKey() : 0L;
            }

            @Override
            public long resolveSelectedProfileKey() {
                ProfileSelectionPresentationFacadeService service = PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                return service != null ? service.resolveSelectedProfileKey() : 0L;
            }

            @Override
            public void ensureProfileLoaded(long accountKey) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
            }

            @Override
            public void requestGeLimits(Set<Integer> itemIds) {
                GeLimitService service = geLimitService();
                if (service != null) {
                    service.requestGeLimits(itemIds);
                }
            }

            @Override
            public Integer getCachedGeLimit(int itemId) {
                GeLimitService service = geLimitService();
                return service != null ? service.getCachedGeLimit(itemId) : null;
            }

            @Override
            public Integer lookupGeLimitSafe(int itemId) {
                ItemLookupService service =
                    PluginAccess.plugin().getOfferUiRuntimeServices().getItemServices().getItemLookupService();
                return service != null ? service.lookupGeLimitSafe(itemId) : null;
            }

            @Override
            public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
                LocalTradeSessionFacadeService service =
                    PluginAccess.plugin().getStatsTradesServices().getLocalTradeSessionFacadeService();
                return service != null ? service.buildLocalLimitInfo(accountKey, nowMs) : null;
            }

            @Override
            public FlipHubItem getOfferPreviewItem() {
                return PluginAccess.plugin().offerPreviewItem;
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }
        };
    }

    Integer getThrottledSuggestion(int itemId) {
        if (hooks == null || itemId <= 0) {
            return null;
        }
        long accountKey = resolveAccountKey();
        if (accountKey < 0) {
            return null;
        }
        long now = hooks.nowMs();
        boolean canUseCache = cachedRemainingLimitSuggestion != null
            && cachedRemainingLimitItemId != null
            && cachedRemainingLimitItemId == itemId
            && cachedRemainingLimitAccountKey == accountKey
            && (now - cachedRemainingLimitAtMs) < LIMIT_SUGGESTION_REFRESH_MS;
        if (canUseCache) {
            return cachedRemainingLimitSuggestion;
        }
        Integer computed = computeSuggestion(itemId, accountKey);
        cacheSuggestion(itemId, computed, accountKey, now);
        return computed;
    }

    void cacheSuggestion(int itemId, Integer remaining) {
        if (hooks == null) {
            return;
        }
        cacheSuggestion(itemId, remaining, resolveAccountKey(), hooks.nowMs());
    }

    void clearCache() {
        cachedRemainingLimitSuggestion = null;
        cachedRemainingLimitItemId = null;
        cachedRemainingLimitAccountKey = Long.MIN_VALUE;
        cachedRemainingLimitAtMs = 0L;
    }

    Integer computeSuggestion(int itemId) {
        if (hooks == null || itemId <= 0) {
            return null;
        }
        long accountKey = resolveAccountKey();
        if (accountKey < 0) {
            return null;
        }
        return computeSuggestion(itemId, accountKey);
    }

    private Integer computeSuggestion(int itemId, long accountKey) {
        if (itemId <= 0 || accountKey < 0 || hooks == null) {
            return null;
        }
        hooks.ensureProfileLoaded(accountKey);
        hooks.requestGeLimits(Collections.singleton(itemId));
        Integer geLimit = hooks.getCachedGeLimit(itemId);
        if (geLimit == null || geLimit <= 0) {
            geLimit = hooks.lookupGeLimitSafe(itemId);
        }
        if (geLimit == null || geLimit <= 0) {
            return null;
        }
        Map<Integer, LocalLimitInfo> limitInfo = hooks.buildLocalLimitInfo(accountKey, hooks.nowMs());
        LocalLimitInfo info = limitInfo != null ? limitInfo.get(itemId) : null;
        int remaining = geLimit;
        if (info != null && info.buyQty > 0) {
            remaining = (int) Math.max(0L, geLimit - info.buyQty);
        }
        FlipHubItem previewItem = hooks.getOfferPreviewItem();
        if (previewItem != null && previewItem.item_id == itemId) {
            previewItem.ge_limit_total = geLimit;
            previewItem.ge_limit_remaining = remaining;
            if (info != null && info.firstBuyTs != null) {
                long resetAt = info.firstBuyTs + GE_LIMIT_WINDOW_MS;
                previewItem.ge_limit_reset_ms = Math.max(0L, resetAt - hooks.nowMs());
            }
        }
        return remaining;
    }

    private void cacheSuggestion(int itemId, Integer remaining, long accountKey, long nowMs) {
        cachedRemainingLimitItemId = itemId > 0 ? itemId : null;
        cachedRemainingLimitSuggestion = remaining;
        cachedRemainingLimitAccountKey = accountKey;
        cachedRemainingLimitAtMs = nowMs;
    }

    private long resolveAccountKey() {
        long accountKey = hooks.resolveLocalAccountKey();
        if (accountKey <= 0) {
            accountKey = hooks.resolveSelectedProfileKey();
        }
        return accountKey;
    }
}
