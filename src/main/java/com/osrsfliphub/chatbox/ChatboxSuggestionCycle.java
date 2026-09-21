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

import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.widgets.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ChatboxSuggestionCycle {
    private final Client client;
    private final ChatboxSuggestionRuntimeState runtimeState;
    private final ChatboxSuggestionPresentation presentation;
    private final RemainingLimitSuggestion remainingLimitSuggestion;
    private final OfferTypeResolver offerTypeResolver;
    private Widget preparedPricePrompt;
    private Widget preparedQuantityPrompt;

    private void clearSuggestions() {
        presentation.clearPriceSuggestion();
        presentation.clearLimitSuggestion();
        presentation.clearAffordableLimitSuggestion();
        remainingLimitSuggestion.clearCache();
    }

    private boolean preparePromptWidgets() {
        preparedPricePrompt = runtimeState.getPricePromptWidget();
        preparedQuantityPrompt = runtimeState.getQuantityPromptWidget();
        return preparedPricePrompt != null || preparedQuantityPrompt != null;
    }

    private boolean isGeRootVisible() {
        Widget geRoot = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        return geRoot != null && !geRoot.isHidden();
    }

    private void updatePreparedSuggestions(Boolean isBuy) {
        presentation.updatePriceSuggestion(preparedPricePrompt, isBuy);
        presentation.updateLimitSuggestion(preparedQuantityPrompt, isBuy);
    }

    void update() {
        long nowMs = System.currentTimeMillis();
        if (!runtimeState.shouldUpdate(nowMs)) {
            return;
        }
        runtimeState.setLastSuggestionUpdateMs(nowMs);

        if (!Access.loggedIn(client)) {
            clearSuggestions();
            runtimeState.setSuggestionDirty(false);
            return;
        }

        if (!runtimeState.isGeInputPromptActive()) {
            clearSuggestions();
            runtimeState.clearPromptWidgetCache();
            runtimeState.setSuggestionDirty(false);
            return;
        }

        if (!runtimeState.isChatboxInputVisible()) {
            clearSuggestions();
            runtimeState.clearPromptWidgetCache();
            runtimeState.setSuggestionDirty(false);
            return;
        }

        runtimeState.setSuggestionDirty(false);

        boolean promptsPrepared = preparePromptWidgets();

        Boolean offerType = offerTypeResolver.resolveOfferType();
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
