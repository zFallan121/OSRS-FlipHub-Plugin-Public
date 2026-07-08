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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

@Singleton
final class ChatboxSuggestionCycleService {
    private final Client client;
    private Widget preparedPricePrompt;
    private Widget preparedQuantityPrompt;

    @Inject
    ChatboxSuggestionCycleService(Client client) {
        this.client = client;
    }

    private static ChatboxSuggestionRuntimeStateService runtimeState() {
        return PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class);
    }

    private static ChatboxSuggestionPresentationService presentation() {
        return PluginInjectorBridge.get(ChatboxSuggestionPresentationService.class);
    }

    private boolean isClientLoggedIn() {
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    private boolean isGeInputPromptActive() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null && service.isGeInputPromptActive();
    }

    private boolean isChatboxInputVisible() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null && service.isChatboxInputVisible();
    }

    private void setSuggestionDirty(boolean dirty) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        if (service != null) {
            service.setSuggestionDirty(dirty);
        }
    }

    private void setLastSuggestionUpdateMs(long timestampMs) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        if (service != null) {
            service.setLastSuggestionUpdateMs(timestampMs);
        }
    }

    private void clearSuggestions() {
        ChatboxSuggestionPresentationService presentation = presentation();
        if (presentation != null) {
            presentation.clearPriceSuggestion();
            presentation.clearLimitSuggestion();
            presentation.clearAffordableLimitSuggestion();
        }
        RemainingLimitSuggestionService remaining = PluginInjectorBridge.get(RemainingLimitSuggestionService.class);
        if (remaining != null) {
            remaining.clearCache();
        }
    }

    private void clearPromptWidgetCache() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        if (service != null) {
            service.clearPromptWidgetCache();
        }
    }

    private boolean preparePromptWidgets() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        preparedPricePrompt = service != null ? service.getPricePromptWidget() : null;
        preparedQuantityPrompt = service != null ? service.getQuantityPromptWidget() : null;
        return preparedPricePrompt != null || preparedQuantityPrompt != null;
    }

    private boolean isGeRootVisible() {
        if (client == null) {
            return false;
        }
        Widget geRoot = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        return geRoot != null && !geRoot.isHidden();
    }

    private Boolean resolveOfferType() {
        OfferTypeResolver resolver = PluginInjectorBridge.get(OfferTypeResolver.class);
        return resolver != null ? resolver.resolveOfferType() : null;
    }

    private void updatePreparedSuggestions(Boolean isBuy) {
        ChatboxSuggestionPresentationService service = presentation();
        if (service != null) {
            service.updatePriceSuggestion(preparedPricePrompt, isBuy);
            service.updateLimitSuggestion(preparedQuantityPrompt, isBuy);
        }
    }

    void update() {
        if (!isClientLoggedIn()) {
            clearSuggestions();
            setSuggestionDirty(false);
            return;
        }

        if (!isGeInputPromptActive()) {
            clearSuggestions();
            clearPromptWidgetCache();
            setSuggestionDirty(false);
            return;
        }

        if (!isChatboxInputVisible()) {
            clearSuggestions();
            clearPromptWidgetCache();
            setSuggestionDirty(false);
            return;
        }

        setLastSuggestionUpdateMs(System.currentTimeMillis());
        setSuggestionDirty(false);

        boolean promptsPrepared = preparePromptWidgets();

        Boolean offerType = resolveOfferType();
        if (!promptsPrepared && !isGeRootVisible() && offerType == null) {
            clearSuggestions();
            return;
        }
        if (!isGeRootVisible() && offerType == null) {
            clearSuggestions();
            return;
        }

        updatePreparedSuggestions(offerType);
    }
}
