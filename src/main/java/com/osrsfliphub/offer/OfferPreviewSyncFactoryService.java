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

final class OfferPreviewSyncFactoryService {
    interface RuntimeHooks {
        Integer getOfferPreviewItemId();
        FlipHubItem getOfferPreviewItem();
        void setOfferPreviewItemId(Integer itemId);
        void setOfferPreviewItem(FlipHubItem item);
        FlipHubItem buildLocalOfferPreview(int itemId);
        void setPanelOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs);
        void markSuggestionDirty();
        void scheduleRefreshSoon();
        long nowMs();
    }

    OfferPreviewSyncService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new OfferPreviewSyncService(null);
        }
        return new OfferPreviewSyncService(new OfferPreviewSyncService.Hooks() {
            @Override
            public Integer getOfferPreviewItemId() {
                return runtimeHooks.getOfferPreviewItemId();
            }

            @Override
            public FlipHubItem getOfferPreviewItem() {
                return runtimeHooks.getOfferPreviewItem();
            }

            @Override
            public void setOfferPreviewItemId(Integer itemId) {
                runtimeHooks.setOfferPreviewItemId(itemId);
            }

            @Override
            public void setOfferPreviewItem(FlipHubItem item) {
                runtimeHooks.setOfferPreviewItem(item);
            }

            @Override
            public FlipHubItem buildLocalOfferPreview(int itemId) {
                return runtimeHooks.buildLocalOfferPreview(itemId);
            }

            @Override
            public void setPanelOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
                runtimeHooks.setPanelOfferPreview(item, asOfMs, priceCacheMs);
            }

            @Override
            public void markSuggestionDirty() {
                runtimeHooks.markSuggestionDirty();
            }

            @Override
            public void scheduleRefreshSoon() {
                runtimeHooks.scheduleRefreshSoon();
            }

            @Override
            public long nowMs() {
                return runtimeHooks.nowMs();
            }
        });
    }
}
