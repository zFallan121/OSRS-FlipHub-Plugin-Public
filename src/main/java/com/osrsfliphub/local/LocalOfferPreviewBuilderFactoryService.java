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

final class LocalOfferPreviewBuilderFactoryService {
    interface RuntimeHooks {
        void ensureSelectedProfileLoaded();
        void requestGeLimit(int itemId);
        String getItemName(int itemId);
        void applyGuidePrices(FlipHubItem item, int itemId);
        long resolveSelectedProfileKey();
        long resolveLimitAccountKey(long fallbackAccountKey);
        Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey);
        void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info);
        void ensureProfileLoaded(long accountKey);
        Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs);
        void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info);
        Integer lookupGeLimit(int itemId);
        void applyMarginInfo(FlipHubItem item);
        long nowMs();
    }

    LocalOfferPreviewBuilder create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new LocalOfferPreviewBuilder(null);
        }
        return new LocalOfferPreviewBuilder(new LocalOfferPreviewBuilder.Hooks() {
            @Override
            public void ensureSelectedProfileLoaded() {
                runtimeHooks.ensureSelectedProfileLoaded();
            }

            @Override
            public void requestGeLimit(int itemId) {
                runtimeHooks.requestGeLimit(itemId);
            }

            @Override
            public String getItemName(int itemId) {
                return runtimeHooks.getItemName(itemId);
            }

            @Override
            public void applyGuidePrices(FlipHubItem item, int itemId) {
                runtimeHooks.applyGuidePrices(item, itemId);
            }

            @Override
            public long resolveSelectedProfileKey() {
                return runtimeHooks.resolveSelectedProfileKey();
            }

            @Override
            public long resolveLimitAccountKey(long fallbackAccountKey) {
                return runtimeHooks.resolveLimitAccountKey(fallbackAccountKey);
            }

            @Override
            public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
                return runtimeHooks.buildLocalTradeInfo(accountKey);
            }

            @Override
            public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
                runtimeHooks.applyLocalTradeInfo(item, info);
            }

            @Override
            public void ensureProfileLoaded(long accountKey) {
                runtimeHooks.ensureProfileLoaded(accountKey);
            }

            @Override
            public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
                return runtimeHooks.buildLocalLimitInfo(accountKey, nowMs);
            }

            @Override
            public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
                runtimeHooks.applyLocalLimitInfo(item, itemId, info);
            }

            @Override
            public Integer lookupGeLimit(int itemId) {
                return runtimeHooks.lookupGeLimit(itemId);
            }

            @Override
            public void applyMarginInfo(FlipHubItem item) {
                runtimeHooks.applyMarginInfo(item);
            }

            @Override
            public long nowMs() {
                return runtimeHooks.nowMs();
            }
        });
    }
}
