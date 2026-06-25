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

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class OfferPreviewSyncPluginHooks implements OfferPreviewSyncFactoryService.RuntimeHooks {
    private final Supplier<Integer> offerPreviewItemIdSupplier;
    private final Supplier<FlipHubItem> offerPreviewItemSupplier;
    private final Consumer<Integer> offerPreviewItemIdSetter;
    private final Consumer<FlipHubItem> offerPreviewItemSetter;
    private final IntFunction<FlipHubItem> localOfferPreviewBuilder;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<ChatboxSuggestionRuntimeStateService> chatboxSuggestionRuntimeStateServiceSupplier;
    private final Runnable scheduleRefreshSoon;
    private final LongSupplier nowMs;

    OfferPreviewSyncPluginHooks(
        Supplier<Integer> offerPreviewItemIdSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Consumer<Integer> offerPreviewItemIdSetter,
        Consumer<FlipHubItem> offerPreviewItemSetter,
        IntFunction<FlipHubItem> localOfferPreviewBuilder,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ChatboxSuggestionRuntimeStateService> chatboxSuggestionRuntimeStateServiceSupplier,
        Runnable scheduleRefreshSoon,
        LongSupplier nowMs
    ) {
        this.offerPreviewItemIdSupplier = offerPreviewItemIdSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.offerPreviewItemIdSetter = offerPreviewItemIdSetter;
        this.offerPreviewItemSetter = offerPreviewItemSetter;
        this.localOfferPreviewBuilder = localOfferPreviewBuilder;
        this.panelSupplier = panelSupplier;
        this.chatboxSuggestionRuntimeStateServiceSupplier = chatboxSuggestionRuntimeStateServiceSupplier;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.nowMs = nowMs;
    }

    @Override
    public Integer getOfferPreviewItemId() {
        return offerPreviewItemIdSupplier != null ? offerPreviewItemIdSupplier.get() : null;
    }

    @Override
    public FlipHubItem getOfferPreviewItem() {
        return offerPreviewItemSupplier != null ? offerPreviewItemSupplier.get() : null;
    }

    @Override
    public void setOfferPreviewItemId(Integer itemId) {
        if (offerPreviewItemIdSetter != null) {
            offerPreviewItemIdSetter.accept(itemId);
        }
    }

    @Override
    public void setOfferPreviewItem(FlipHubItem item) {
        if (offerPreviewItemSetter != null) {
            offerPreviewItemSetter.accept(item);
        }
    }

    @Override
    public FlipHubItem buildLocalOfferPreview(int itemId) {
        return localOfferPreviewBuilder != null ? localOfferPreviewBuilder.apply(itemId) : null;
    }

    @Override
    public void setPanelOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
        FlipHubPanel panel = panelSupplier != null ? panelSupplier.get() : null;
        if (panel != null) {
            panel.setOfferPreview(item, asOfMs, priceCacheMs);
        }
    }

    @Override
    public void markSuggestionDirty() {
        ChatboxSuggestionRuntimeStateService service = chatboxSuggestionRuntimeStateServiceSupplier != null
            ? chatboxSuggestionRuntimeStateServiceSupplier.get()
            : null;
        if (service != null) {
            service.markSuggestionDirty();
        }
    }

    @Override
    public void scheduleRefreshSoon() {
        if (scheduleRefreshSoon != null) {
            scheduleRefreshSoon.run();
        }
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : 0L;
    }
}
