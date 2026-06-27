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

import java.text.NumberFormat;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

@Singleton
final class ChatboxSuggestionRuntimeStateService {
    interface Hooks {
        Client getClient();
        ChatboxSuggestionWidgetFactoryService getWidgetFactoryService();
        ChatboxPromptWidgetResolverService getPromptWidgetResolverService();
    }

    private final Hooks hooks;
    private Widget priceSuggestionWidget;
    private Widget limitSuggestionWidget;
    private Widget affordableLimitSuggestionWidget;
    private Widget cachedPricePromptWidget;
    private Widget cachedQuantityPromptWidget;
    private volatile boolean suggestionDirty;
    private long lastSuggestionUpdateMs;

    @Inject
    ChatboxSuggestionRuntimeStateService(Client client) {
        this(new Hooks() {
            @Override
            public Client getClient() {
                return client;
            }

            @Override
            public ChatboxSuggestionWidgetFactoryService getWidgetFactoryService() {
                return PluginInjectorBridge.get(ChatboxSuggestionWidgetFactoryService.class);
            }

            @Override
            public ChatboxPromptWidgetResolverService getPromptWidgetResolverService() {
                return PluginInjectorBridge.get(ChatboxPromptWidgetResolverService.class);
            }
        });
    }

    ChatboxSuggestionRuntimeStateService(Hooks hooks) {
        this.hooks = hooks;
    }

    void markSuggestionDirty() {
        suggestionDirty = true;
    }

    boolean isSuggestionDirty() {
        return suggestionDirty;
    }

    void setSuggestionDirty(boolean dirty) {
        suggestionDirty = dirty;
    }

    void setLastSuggestionUpdateMs(long timestampMs) {
        lastSuggestionUpdateMs = timestampMs;
    }

    void clearPromptWidgetCache() {
        cachedPricePromptWidget = null;
        cachedQuantityPromptWidget = null;
    }

    Widget getPricePromptWidget() {
        ChatboxPromptWidgetResolverService resolver = hooks != null ? hooks.getPromptWidgetResolverService() : null;
        cachedPricePromptWidget = resolver != null
            ? resolver.resolvePromptWidget(cachedPricePromptWidget, true)
            : null;
        return cachedPricePromptWidget;
    }

    Widget getQuantityPromptWidget() {
        ChatboxPromptWidgetResolverService resolver = hooks != null ? hooks.getPromptWidgetResolverService() : null;
        cachedQuantityPromptWidget = resolver != null
            ? resolver.resolvePromptWidget(cachedQuantityPromptWidget, false)
            : null;
        return cachedQuantityPromptWidget;
    }

    boolean isGeInputPromptActive() {
        Client client = hooks != null ? hooks.getClient() : null;
        if (client == null) {
            return false;
        }
        int inputType = client.getVarcIntValue(VarClientInt.INPUT_TYPE);
        if (inputType == 7) {
            return true;
        }
        if (inputType <= 0) {
            return false;
        }

        // Fallback for client variants where the GE prompt still exists but INPUT_TYPE differs.
        Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
        if (ChatboxSuggestionWidgets.isPromptWidgetValid(title, true)
            || ChatboxSuggestionWidgets.isPromptWidgetValid(title, false)) {
            return true;
        }
        Widget firstLine = client.getWidget(ComponentID.CHATBOX_FIRST_MESSAGE);
        if (ChatboxSuggestionWidgets.isPromptWidgetValid(firstLine, true)
            || ChatboxSuggestionWidgets.isPromptWidgetValid(firstLine, false)) {
            return true;
        }

        return getPricePromptWidget() != null || getQuantityPromptWidget() != null;
    }

    boolean isChatboxInputVisible() {
        Client client = hooks != null ? hooks.getClient() : null;
        if (client == null) {
            return false;
        }
        Widget fullInput = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        if (ChatboxSuggestionWidgets.isWidgetVisible(fullInput)) {
            return true;
        }
        Widget input = client.getWidget(ComponentID.CHATBOX_INPUT);
        if (ChatboxSuggestionWidgets.isWidgetVisible(input)) {
            return true;
        }
        Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
        if (ChatboxSuggestionWidgets.isWidgetVisible(title)
            && (ChatboxSuggestionWidgets.isPricePromptWidget(title)
                || ChatboxSuggestionWidgets.isQuantityPromptWidget(title))) {
            return true;
        }
        Widget firstLine = client.getWidget(ComponentID.CHATBOX_FIRST_MESSAGE);
        return ChatboxSuggestionWidgets.isWidgetVisible(firstLine)
            && (ChatboxSuggestionWidgets.isPricePromptWidget(firstLine)
                || ChatboxSuggestionWidgets.isQuantityPromptWidget(firstLine));
    }

    Widget getChatboxContainer() {
        Client client = hooks != null ? hooks.getClient() : null;
        if (client == null) {
            return null;
        }
        Widget container = client.getWidget(ComponentID.CHATBOX_CONTAINER);
        if (container != null) {
            return container;
        }
        return client.getWidget(ComponentID.CHATBOX_PARENT);
    }

    Widget ensurePriceSuggestionWidget(Widget container) {
        ChatboxSuggestionWidgetFactoryService factory = hooks != null ? hooks.getWidgetFactoryService() : null;
        priceSuggestionWidget = factory != null
            ? factory.ensurePriceSuggestionWidget(container, priceSuggestionWidget)
            : priceSuggestionWidget;
        return priceSuggestionWidget;
    }

    Widget ensureLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionWidgetFactoryService factory = hooks != null ? hooks.getWidgetFactoryService() : null;
        limitSuggestionWidget = factory != null
            ? factory.ensureLimitSuggestionWidget(container, limitSuggestionWidget)
            : limitSuggestionWidget;
        return limitSuggestionWidget;
    }

    Widget ensureAffordableLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionWidgetFactoryService factory = hooks != null ? hooks.getWidgetFactoryService() : null;
        affordableLimitSuggestionWidget = factory != null
            ? factory.ensureAffordableLimitSuggestionWidget(container, affordableLimitSuggestionWidget)
            : affordableLimitSuggestionWidget;
        return affordableLimitSuggestionWidget;
    }

    Widget getPriceSuggestionWidget() {
        return priceSuggestionWidget;
    }

    void setPriceSuggestionWidget(Widget widget) {
        priceSuggestionWidget = widget;
    }

    Widget getLimitSuggestionWidget() {
        return limitSuggestionWidget;
    }

    void setLimitSuggestionWidget(Widget widget) {
        limitSuggestionWidget = widget;
    }

    Widget getAffordableLimitSuggestionWidget() {
        return affordableLimitSuggestionWidget;
    }

    void setAffordableLimitSuggestionWidget(Widget widget) {
        affordableLimitSuggestionWidget = widget;
    }

    boolean isSuggestionWidgetValid(Widget container) {
        return isSuggestionWidgetAttached(container, priceSuggestionWidget);
    }

    boolean isLimitWidgetValid(Widget container) {
        return isSuggestionWidgetAttached(container, limitSuggestionWidget);
    }

    boolean isAffordableLimitWidgetValid(Widget container) {
        return isSuggestionWidgetAttached(container, affordableLimitSuggestionWidget);
    }

    String formatPrice(int price) {
        return NumberFormat.getIntegerInstance(Locale.US).format(price);
    }

    private boolean isSuggestionWidgetAttached(Widget container, Widget suggestionWidget) {
        if (suggestionWidget == null || container == null) {
            return false;
        }
        if (suggestionWidget.getParent() != container) {
            return false;
        }
        if (suggestionWidget.getParentId() != container.getId()) {
            return false;
        }
        return ChatboxSuggestionWidgets.isWidgetInParent(container, suggestionWidget);
    }
}
