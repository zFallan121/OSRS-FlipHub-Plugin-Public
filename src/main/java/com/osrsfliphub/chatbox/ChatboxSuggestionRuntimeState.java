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
final class ChatboxSuggestionRuntimeState {
    private final Client client;
    private Widget priceSuggestionWidget;
    private Widget limitSuggestionWidget;
    private Widget affordableLimitSuggestionWidget;
    private Widget cachedPricePromptWidget;
    private Widget cachedQuantityPromptWidget;
    private volatile boolean suggestionDirty;
    private long lastSuggestionUpdateMs;
    // Bumped whenever the chatbox is rebuilt, so a fruitless deep scan for a prompt widget can be
    // remembered as a miss and skipped until the chatbox actually changes again.
    private long chatboxGeneration;
    private long pricePromptMissGeneration = -1L;
    private long quantityPromptMissGeneration = -1L;

    @Inject
    ChatboxSuggestionRuntimeState(Client client) {
        this.client = client;
    }

    void markSuggestionDirty() {
        suggestionDirty = true;
        chatboxGeneration++;
    }

    /**
     * The suggestion pass walks the chatbox widget tree, which is large while the GE item search
     * list is open. Run it when something has actually changed, and otherwise no more often than
     * {@link Const#SUGGESTION_UPDATE_INTERVAL_MS}.
     */
    boolean shouldUpdate(long nowMs) {
        return suggestionDirty
            || nowMs - lastSuggestionUpdateMs >= Const.SUGGESTION_UPDATE_INTERVAL_MS;
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
        pricePromptMissGeneration = -1L;
        quantityPromptMissGeneration = -1L;
    }

    Widget getPricePromptWidget() {
        if (ChatboxSuggestionWidgets.isPromptWidgetValid(cachedPricePromptWidget, true)) {
            return cachedPricePromptWidget;
        }
        if (pricePromptMissGeneration == chatboxGeneration) {
            cachedPricePromptWidget = null;
            return null;
        }
        ChatboxPromptWidgetResolver resolver = Bridge.get(ChatboxPromptWidgetResolver.class);
        cachedPricePromptWidget = resolver != null
            ? resolver.resolvePromptWidget(cachedPricePromptWidget, true)
            : null;
        if (cachedPricePromptWidget == null) {
            pricePromptMissGeneration = chatboxGeneration;
        }
        return cachedPricePromptWidget;
    }

    Widget getQuantityPromptWidget() {
        if (ChatboxSuggestionWidgets.isPromptWidgetValid(cachedQuantityPromptWidget, false)) {
            return cachedQuantityPromptWidget;
        }
        if (quantityPromptMissGeneration == chatboxGeneration) {
            cachedQuantityPromptWidget = null;
            return null;
        }
        ChatboxPromptWidgetResolver resolver = Bridge.get(ChatboxPromptWidgetResolver.class);
        cachedQuantityPromptWidget = resolver != null
            ? resolver.resolvePromptWidget(cachedQuantityPromptWidget, false)
            : null;
        if (cachedQuantityPromptWidget == null) {
            quantityPromptMissGeneration = chatboxGeneration;
        }
        return cachedQuantityPromptWidget;
    }

    // The chatbox input type the client uses for the price and quantity prompts.
    private static final int INPUT_TYPE_GE_PROMPT = 7;
    // The chatbox input type the client uses for the GE item search.
    private static final int INPUT_TYPE_GE_ITEM_SEARCH = 14;

    boolean isGeInputPromptActive() {
        Client client = this.client;
        if (client == null) {
            return false;
        }
        int inputType = client.getVarcIntValue(VarClientInt.INPUT_TYPE);
        if (inputType == INPUT_TYPE_GE_PROMPT) {
            return true;
        }
        if (inputType <= 0) {
            return false;
        }
        // The GE item search is never a price or quantity prompt, and its result list makes the
        // chatbox tree huge. Rule it out up front rather than deep-scanning that list.
        if (inputType == INPUT_TYPE_GE_ITEM_SEARCH || isGeItemSearchOpen()) {
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

    private boolean isGeItemSearchOpen() {
        Widget searchResults = client != null
            ? client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS)
            : null;
        return searchResults != null && !searchResults.isHidden();
    }

    boolean isChatboxInputVisible() {
        Client client = this.client;
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
        Client client = this.client;
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
        ChatboxSuggestionWidgetFactory factory = Bridge.get(ChatboxSuggestionWidgetFactory.class);
        priceSuggestionWidget = factory != null
            ? factory.ensurePriceSuggestionWidget(container, priceSuggestionWidget)
            : priceSuggestionWidget;
        return priceSuggestionWidget;
    }

    Widget ensureLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionWidgetFactory factory = Bridge.get(ChatboxSuggestionWidgetFactory.class);
        limitSuggestionWidget = factory != null
            ? factory.ensureLimitSuggestionWidget(container, limitSuggestionWidget)
            : limitSuggestionWidget;
        return limitSuggestionWidget;
    }

    Widget ensureAffordableLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionWidgetFactory factory = Bridge.get(ChatboxSuggestionWidgetFactory.class);
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
