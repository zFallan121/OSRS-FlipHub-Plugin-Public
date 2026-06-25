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

import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

final class ChatboxSuggestionCyclePluginHooks implements ChatboxSuggestionCycleFactoryService.RuntimeHooks {
    private final Supplier<Client> clientSupplier;
    private final Supplier<ChatboxSuggestionRuntimeStateService> runtimeStateServiceSupplier;
    private final Supplier<ChatboxSuggestionPresentationService> presentationServiceSupplier;
    private final Supplier<RemainingLimitSuggestionService> remainingLimitSuggestionServiceSupplier;
    private final Supplier<OfferTypeResolver> offerTypeResolverSupplier;
    private final LongSupplier nowMsSupplier;

    ChatboxSuggestionCyclePluginHooks(
        Supplier<Client> clientSupplier,
        Supplier<ChatboxSuggestionRuntimeStateService> runtimeStateServiceSupplier,
        Supplier<ChatboxSuggestionPresentationService> presentationServiceSupplier,
        Supplier<RemainingLimitSuggestionService> remainingLimitSuggestionServiceSupplier,
        Supplier<OfferTypeResolver> offerTypeResolverSupplier,
        LongSupplier nowMsSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.runtimeStateServiceSupplier = runtimeStateServiceSupplier;
        this.presentationServiceSupplier = presentationServiceSupplier;
        this.remainingLimitSuggestionServiceSupplier = remainingLimitSuggestionServiceSupplier;
        this.offerTypeResolverSupplier = offerTypeResolverSupplier;
        this.nowMsSupplier = nowMsSupplier;
    }

    @Override
    public boolean isClientLoggedIn() {
        Client client = resolveClient();
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    @Override
    public boolean isGeInputPromptActive() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null && service.isGeInputPromptActive();
    }

    @Override
    public boolean isChatboxInputVisible() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null && service.isChatboxInputVisible();
    }

    @Override
    public boolean isSuggestionDirty() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null && service.isSuggestionDirty();
    }

    @Override
    public void setSuggestionDirty(boolean dirty) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        if (service != null) {
            service.setSuggestionDirty(dirty);
        }
    }

    @Override
    public void setLastSuggestionUpdateMs(long timestampMs) {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        if (service != null) {
            service.setLastSuggestionUpdateMs(timestampMs);
        }
    }

    @Override
    public void clearSuggestions() {
        ChatboxSuggestionPresentationService presentation = resolvePresentationService();
        if (presentation != null) {
            presentation.clearPriceSuggestion();
            presentation.clearLimitSuggestion();
            presentation.clearAffordableLimitSuggestion();
        }
        RemainingLimitSuggestionService remaining = resolveRemainingLimitSuggestionService();
        if (remaining != null) {
            remaining.clearCache();
        }
    }

    @Override
    public void clearPromptWidgetCache() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        if (service != null) {
            service.clearPromptWidgetCache();
        }
    }

    @Override
    public Widget getPricePromptWidget() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.getPricePromptWidget() : null;
    }

    @Override
    public Widget getQuantityPromptWidget() {
        ChatboxSuggestionRuntimeStateService service = resolveRuntimeStateService();
        return service != null ? service.getQuantityPromptWidget() : null;
    }

    @Override
    public boolean isGeRootVisible() {
        Client client = resolveClient();
        if (client == null) {
            return false;
        }
        Widget geRoot = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        return geRoot != null && !geRoot.isHidden();
    }

    @Override
    public Boolean resolveOfferType() {
        OfferTypeResolver resolver = offerTypeResolverSupplier != null ? offerTypeResolverSupplier.get() : null;
        return resolver != null ? resolver.resolveOfferType() : null;
    }

    @Override
    public void updatePriceSuggestion(Widget promptWidget, Boolean isBuy) {
        ChatboxSuggestionPresentationService service = resolvePresentationService();
        if (service != null) {
            service.updatePriceSuggestion(promptWidget, isBuy);
        }
    }

    @Override
    public void updateLimitSuggestion(Widget promptWidget, Boolean isBuy) {
        ChatboxSuggestionPresentationService service = resolvePresentationService();
        if (service != null) {
            service.updateLimitSuggestion(promptWidget, isBuy);
        }
    }

    @Override
    public long nowMs() {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }

    private Client resolveClient() {
        return clientSupplier != null ? clientSupplier.get() : null;
    }

    private ChatboxSuggestionRuntimeStateService resolveRuntimeStateService() {
        return runtimeStateServiceSupplier != null ? runtimeStateServiceSupplier.get() : null;
    }

    private ChatboxSuggestionPresentationService resolvePresentationService() {
        return presentationServiceSupplier != null ? presentationServiceSupplier.get() : null;
    }

    private RemainingLimitSuggestionService resolveRemainingLimitSuggestionService() {
        return remainingLimitSuggestionServiceSupplier != null
            ? remainingLimitSuggestionServiceSupplier.get()
            : null;
    }
}
