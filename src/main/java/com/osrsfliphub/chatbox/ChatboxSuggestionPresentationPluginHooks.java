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

import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;

final class ChatboxSuggestionPresentationPluginHooks implements ChatboxSuggestionPresentationService.Hooks {
    private final Supplier<Client> clientSupplier;
    private final Supplier<ChatboxSuggestionRuntimeStateService> runtimeStateServiceSupplier;
    private final Supplier<Integer> offerPreviewItemIdSupplier;
    private final Supplier<FlipHubItem> offerPreviewItemSupplier;
    private final Supplier<RemainingLimitSuggestionService> remainingLimitSuggestionServiceSupplier;
    private final Supplier<AffordableLimitSuggestionService> affordableLimitSuggestionServiceSupplier;

    ChatboxSuggestionPresentationPluginHooks(
        Supplier<Client> clientSupplier,
        Supplier<ChatboxSuggestionRuntimeStateService> runtimeStateServiceSupplier,
        Supplier<Integer> offerPreviewItemIdSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Supplier<RemainingLimitSuggestionService> remainingLimitSuggestionServiceSupplier,
        Supplier<AffordableLimitSuggestionService> affordableLimitSuggestionServiceSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.runtimeStateServiceSupplier = runtimeStateServiceSupplier;
        this.offerPreviewItemIdSupplier = offerPreviewItemIdSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.remainingLimitSuggestionServiceSupplier = remainingLimitSuggestionServiceSupplier;
        this.affordableLimitSuggestionServiceSupplier = affordableLimitSuggestionServiceSupplier;
    }

    @Override
    public boolean isClientLoggedIn() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    @Override
    public Widget getChatboxContainer() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.getChatboxContainer() : null;
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
    public Widget ensurePriceSuggestionWidget(Widget container) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.ensurePriceSuggestionWidget(container) : null;
    }

    @Override
    public Widget ensureLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.ensureLimitSuggestionWidget(container) : null;
    }

    @Override
    public Widget ensureAffordableLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.ensureAffordableLimitSuggestionWidget(container) : null;
    }

    @Override
    public Widget getPriceSuggestionWidget() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.getPriceSuggestionWidget() : null;
    }

    @Override
    public void setPriceSuggestionWidget(Widget widget) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        if (service != null) {
            service.setPriceSuggestionWidget(widget);
        }
    }

    @Override
    public Widget getLimitSuggestionWidget() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.getLimitSuggestionWidget() : null;
    }

    @Override
    public void setLimitSuggestionWidget(Widget widget) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        if (service != null) {
            service.setLimitSuggestionWidget(widget);
        }
    }

    @Override
    public Widget getAffordableLimitSuggestionWidget() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.getAffordableLimitSuggestionWidget() : null;
    }

    @Override
    public void setAffordableLimitSuggestionWidget(Widget widget) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        if (service != null) {
            service.setAffordableLimitSuggestionWidget(widget);
        }
    }

    @Override
    public boolean isSuggestionWidgetValid(Widget container) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null && service.isSuggestionWidgetValid(container);
    }

    @Override
    public boolean isLimitWidgetValid(Widget container) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null && service.isLimitWidgetValid(container);
    }

    @Override
    public boolean isAffordableLimitWidgetValid(Widget container) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null && service.isAffordableLimitWidgetValid(container);
    }

    @Override
    public String formatPrice(int price) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.formatPrice(price) : String.valueOf(price);
    }

    @Override
    public Integer getThrottledRemainingLimitSuggestion(int itemId) {
        RemainingLimitSuggestionService service = resolveRemainingLimitSuggestionService();
        return service != null ? service.getThrottledSuggestion(itemId) : null;
    }

    @Override
    public void cacheRemainingLimitSuggestion(int itemId, Integer remaining) {
        RemainingLimitSuggestionService service = resolveRemainingLimitSuggestionService();
        if (service != null) {
            service.cacheSuggestion(itemId, remaining);
        }
    }

    @Override
    public Integer computeAffordableLimitSuggestion(Integer remainingLimit) {
        AffordableLimitSuggestionService service = resolveAffordableLimitSuggestionService();
        return service != null ? service.computeAffordableLimit(remainingLimit) : null;
    }

    @Override
    public void clearRemainingLimitSuggestionCache() {
        RemainingLimitSuggestionService service = resolveRemainingLimitSuggestionService();
        if (service != null) {
            service.clearCache();
        }
    }

    private ChatboxSuggestionRuntimeStateService resolveRuntimeStateService() {
        return runtimeStateServiceSupplier != null ? runtimeStateServiceSupplier.get() : null;
    }

    private RemainingLimitSuggestionService resolveRemainingLimitSuggestionService() {
        return remainingLimitSuggestionServiceSupplier != null
            ? remainingLimitSuggestionServiceSupplier.get()
            : null;
    }

    private AffordableLimitSuggestionService resolveAffordableLimitSuggestionService() {
        return affordableLimitSuggestionServiceSupplier != null
            ? affordableLimitSuggestionServiceSupplier.get()
            : null;
    }
}
