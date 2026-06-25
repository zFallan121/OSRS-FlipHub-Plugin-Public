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

final class LocalItemsAssemblerFactoryService {
    interface RuntimeHooks {
        String getCachedItemName(int itemId);
        void cacheItemName(int itemId);
        void applyGuidePrices(FlipHubItem item, int itemId);
        void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info);
        void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info);
        void applyMarginInfo(FlipHubItem item);
    }

    LocalItemsAssembler create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new LocalItemsAssembler(null);
        }
        return new LocalItemsAssembler(new LocalItemsAssembler.Hooks() {
            @Override
            public String getCachedItemName(int itemId) {
                return runtimeHooks.getCachedItemName(itemId);
            }

            @Override
            public void cacheItemName(int itemId) {
                runtimeHooks.cacheItemName(itemId);
            }

            @Override
            public void applyGuidePrices(FlipHubItem item, int itemId) {
                runtimeHooks.applyGuidePrices(item, itemId);
            }

            @Override
            public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
                runtimeHooks.applyLocalTradeInfo(item, info);
            }

            @Override
            public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
                runtimeHooks.applyLocalLimitInfo(item, itemId, info);
            }

            @Override
            public void applyMarginInfo(FlipHubItem item) {
                runtimeHooks.applyMarginInfo(item);
            }
        });
    }
}
