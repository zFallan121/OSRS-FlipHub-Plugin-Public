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
    interface Hooks {
        boolean isClientLoggedIn();
        boolean isGeInputPromptActive();
        boolean isChatboxInputVisible();
        boolean isSuggestionDirty();
        void setSuggestionDirty(boolean dirty);
        void setLastSuggestionUpdateMs(long timestampMs);
        void clearSuggestions();
        void clearPromptWidgetCache();
        boolean preparePromptWidgets();
        boolean isGeRootVisible();
        Boolean resolveOfferType();
        void updatePreparedSuggestions(Boolean isBuy);
        long nowMs();
    }

    private final Hooks hooks;

    @Inject
    ChatboxSuggestionCycleService(Client client) {
        this(productionHooks(client));
    }

    private static ChatboxSuggestionRuntimeStateService runtimeState() {
        return PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class);
    }

    private static ChatboxSuggestionPresentationService presentation() {
        return PluginInjectorBridge.get(ChatboxSuggestionPresentationService.class);
    }

    private static Hooks productionHooks(Client client) {
        return new Hooks() {
            private Widget preparedPricePrompt;
            private Widget preparedQuantityPrompt;

            @Override
            public boolean isClientLoggedIn() {
                return client != null && client.getGameState() == GameState.LOGGED_IN;
            }

            @Override
            public boolean isGeInputPromptActive() {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                return service != null && service.isGeInputPromptActive();
            }

            @Override
            public boolean isChatboxInputVisible() {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                return service != null && service.isChatboxInputVisible();
            }

            @Override
            public boolean isSuggestionDirty() {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                return service != null && service.isSuggestionDirty();
            }

            @Override
            public void setSuggestionDirty(boolean dirty) {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                if (service != null) {
                    service.setSuggestionDirty(dirty);
                }
            }

            @Override
            public void setLastSuggestionUpdateMs(long timestampMs) {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                if (service != null) {
                    service.setLastSuggestionUpdateMs(timestampMs);
                }
            }

            @Override
            public void clearSuggestions() {
                ChatboxSuggestionPresentationService presentation = presentation();
                if (presentation != null) {
                    presentation.clearPriceSuggestion();
                    presentation.clearLimitSuggestion();
                    presentation.clearAffordableLimitSuggestion();
                }
                RemainingLimitSuggestionService remaining =
                    PluginInjectorBridge.get(RemainingLimitSuggestionService.class);
                if (remaining != null) {
                    remaining.clearCache();
                }
            }

            @Override
            public void clearPromptWidgetCache() {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                if (service != null) {
                    service.clearPromptWidgetCache();
                }
            }

            @Override
            public boolean preparePromptWidgets() {
                ChatboxSuggestionRuntimeStateService service = runtimeState();
                preparedPricePrompt = service != null ? service.getPricePromptWidget() : null;
                preparedQuantityPrompt = service != null ? service.getQuantityPromptWidget() : null;
                return preparedPricePrompt != null || preparedQuantityPrompt != null;
            }

            @Override
            public boolean isGeRootVisible() {
                if (client == null) {
                    return false;
                }
                Widget geRoot = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
                return geRoot != null && !geRoot.isHidden();
            }

            @Override
            public Boolean resolveOfferType() {
                OfferTypeResolver resolver = PluginInjectorBridge.get(OfferTypeResolver.class);
                return resolver != null ? resolver.resolveOfferType() : null;
            }

            @Override
            public void updatePreparedSuggestions(Boolean isBuy) {
                ChatboxSuggestionPresentationService service = presentation();
                if (service != null) {
                    service.updatePriceSuggestion(preparedPricePrompt, isBuy);
                    service.updateLimitSuggestion(preparedQuantityPrompt, isBuy);
                }
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }
        };
    }

    ChatboxSuggestionCycleService(Hooks hooks) {
        this.hooks = hooks;
    }

    void update() {
        if (hooks == null) {
            return;
        }

        if (!hooks.isClientLoggedIn()) {
            hooks.clearSuggestions();
            hooks.setSuggestionDirty(false);
            return;
        }

        if (!hooks.isGeInputPromptActive()) {
            hooks.clearSuggestions();
            hooks.clearPromptWidgetCache();
            hooks.setSuggestionDirty(false);
            return;
        }

        if (!hooks.isChatboxInputVisible()) {
            hooks.clearSuggestions();
            hooks.clearPromptWidgetCache();
            hooks.setSuggestionDirty(false);
            return;
        }

        hooks.setLastSuggestionUpdateMs(hooks.nowMs());
        hooks.setSuggestionDirty(false);

        boolean promptsPrepared = hooks.preparePromptWidgets();

        Boolean offerType = hooks.resolveOfferType();
        if (!promptsPrepared && !hooks.isGeRootVisible() && offerType == null) {
            hooks.clearSuggestions();
            return;
        }
        if (!hooks.isGeRootVisible() && offerType == null) {
            hooks.clearSuggestions();
            return;
        }

        hooks.updatePreparedSuggestions(offerType);
    }
}
