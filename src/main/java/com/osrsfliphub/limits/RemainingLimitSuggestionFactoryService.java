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

import java.util.Map;
import java.util.Set;

final class RemainingLimitSuggestionFactoryService {
    interface RuntimeHooks {
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

    RemainingLimitSuggestionService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new RemainingLimitSuggestionService(null);
        }
        return new RemainingLimitSuggestionService(new RemainingLimitSuggestionService.Hooks() {
            @Override
            public long resolveLocalAccountKey() {
                return runtimeHooks.resolveLocalAccountKey();
            }

            @Override
            public long resolveSelectedProfileKey() {
                return runtimeHooks.resolveSelectedProfileKey();
            }

            @Override
            public void ensureProfileLoaded(long accountKey) {
                runtimeHooks.ensureProfileLoaded(accountKey);
            }

            @Override
            public void requestGeLimits(Set<Integer> itemIds) {
                runtimeHooks.requestGeLimits(itemIds);
            }

            @Override
            public Integer getCachedGeLimit(int itemId) {
                return runtimeHooks.getCachedGeLimit(itemId);
            }

            @Override
            public Integer lookupGeLimitSafe(int itemId) {
                return runtimeHooks.lookupGeLimitSafe(itemId);
            }

            @Override
            public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
                return runtimeHooks.buildLocalLimitInfo(accountKey, nowMs);
            }

            @Override
            public FlipHubItem getOfferPreviewItem() {
                return runtimeHooks.getOfferPreviewItem();
            }

            @Override
            public long nowMs() {
                return runtimeHooks.nowMs();
            }
        });
    }
}
