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

import net.runelite.api.widgets.Widget;

final class ChatboxSuggestionCycleFactoryService {
    interface RuntimeHooks {
        boolean isClientLoggedIn();
        boolean isGeInputPromptActive();
        boolean isChatboxInputVisible();
        boolean isSuggestionDirty();
        void setSuggestionDirty(boolean dirty);
        void setLastSuggestionUpdateMs(long timestampMs);
        void clearSuggestions();
        void clearPromptWidgetCache();
        Widget getPricePromptWidget();
        Widget getQuantityPromptWidget();
        boolean isGeRootVisible();
        Boolean resolveOfferType();
        void updatePriceSuggestion(Widget promptWidget, Boolean isBuy);
        void updateLimitSuggestion(Widget promptWidget, Boolean isBuy);
        long nowMs();
    }

    ChatboxSuggestionCycleService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new ChatboxSuggestionCycleService(null);
        }
        return new ChatboxSuggestionCycleService(new ChatboxSuggestionCycleService.Hooks() {
            private Widget preparedPricePrompt;
            private Widget preparedQuantityPrompt;

            @Override
            public boolean isClientLoggedIn() {
                return runtimeHooks.isClientLoggedIn();
            }

            @Override
            public boolean isGeInputPromptActive() {
                return runtimeHooks.isGeInputPromptActive();
            }

            @Override
            public boolean isChatboxInputVisible() {
                return runtimeHooks.isChatboxInputVisible();
            }

            @Override
            public boolean isSuggestionDirty() {
                return runtimeHooks.isSuggestionDirty();
            }

            @Override
            public void setSuggestionDirty(boolean dirty) {
                runtimeHooks.setSuggestionDirty(dirty);
            }

            @Override
            public void setLastSuggestionUpdateMs(long timestampMs) {
                runtimeHooks.setLastSuggestionUpdateMs(timestampMs);
            }

            @Override
            public void clearSuggestions() {
                runtimeHooks.clearSuggestions();
            }

            @Override
            public void clearPromptWidgetCache() {
                runtimeHooks.clearPromptWidgetCache();
            }

            @Override
            public boolean preparePromptWidgets() {
                preparedPricePrompt = runtimeHooks.getPricePromptWidget();
                preparedQuantityPrompt = runtimeHooks.getQuantityPromptWidget();
                return preparedPricePrompt != null || preparedQuantityPrompt != null;
            }

            @Override
            public boolean isGeRootVisible() {
                return runtimeHooks.isGeRootVisible();
            }

            @Override
            public Boolean resolveOfferType() {
                return runtimeHooks.resolveOfferType();
            }

            @Override
            public void updatePreparedSuggestions(Boolean isBuy) {
                runtimeHooks.updatePriceSuggestion(preparedPricePrompt, isBuy);
                runtimeHooks.updateLimitSuggestion(preparedQuantityPrompt, isBuy);
            }

            @Override
            public long nowMs() {
                return runtimeHooks.nowMs();
            }
        });
    }
}
