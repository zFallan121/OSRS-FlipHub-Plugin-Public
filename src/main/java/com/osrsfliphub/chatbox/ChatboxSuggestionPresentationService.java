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
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;

@Singleton
final class ChatboxSuggestionPresentationService {
    private static final int MIN_SUGGESTION_WIDTH = 12;
    private static final int SUGGESTION_TEXT_CHAR_PX = 7;
    private static final int SUGGESTION_TEXT_PADDING_PX = 8;

    private final Client client;
    private Integer lastSuggestedPrice;
    private Boolean lastSuggestedIsBuy;
    private Integer lastSuggestedLimit;
    private Integer lastSuggestedAffordableLimit;

    @Inject
    ChatboxSuggestionPresentationService(Client client) {
        this.client = client;
    }

    private static ChatboxSuggestionRuntimeStateService runtimeState() {
        return PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class);
    }

    private static RemainingLimitSuggestionService remaining() {
        return PluginInjectorBridge.get(RemainingLimitSuggestionService.class);
    }

    private boolean isClientLoggedIn() {
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    private Widget getChatboxContainer() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.getChatboxContainer() : null;
    }

    private Integer getOfferPreviewItemId() {
        return PluginAccess.plugin().offerPreviewItemId;
    }

    private FlipHubItem getOfferPreviewItem() {
        return PluginAccess.plugin().offerPreviewItem;
    }

    private Widget ensurePriceSuggestionWidget(Widget container) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.ensurePriceSuggestionWidget(container) : null;
    }

    private Widget ensureLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.ensureLimitSuggestionWidget(container) : null;
    }

    private Widget ensureAffordableLimitSuggestionWidget(Widget container) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.ensureAffordableLimitSuggestionWidget(container) : null;
    }

    private Widget getPriceSuggestionWidget() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.getPriceSuggestionWidget() : null;
    }

    private void setPriceSuggestionWidget(Widget widget) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        if (service != null) {
            service.setPriceSuggestionWidget(widget);
        }
    }

    private Widget getLimitSuggestionWidget() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.getLimitSuggestionWidget() : null;
    }

    private void setLimitSuggestionWidget(Widget widget) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        if (service != null) {
            service.setLimitSuggestionWidget(widget);
        }
    }

    private Widget getAffordableLimitSuggestionWidget() {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.getAffordableLimitSuggestionWidget() : null;
    }

    private void setAffordableLimitSuggestionWidget(Widget widget) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        if (service != null) {
            service.setAffordableLimitSuggestionWidget(widget);
        }
    }

    private boolean isSuggestionWidgetValid(Widget container) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null && service.isSuggestionWidgetValid(container);
    }

    private boolean isLimitWidgetValid(Widget container) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null && service.isLimitWidgetValid(container);
    }

    private boolean isAffordableLimitWidgetValid(Widget container) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null && service.isAffordableLimitWidgetValid(container);
    }

    private String formatPrice(int price) {
        ChatboxSuggestionRuntimeStateService service = runtimeState();
        return service != null ? service.formatPrice(price) : String.valueOf(price);
    }

    private Integer getThrottledRemainingLimitSuggestion(int itemId) {
        RemainingLimitSuggestionService service = remaining();
        return service != null ? service.getThrottledSuggestion(itemId) : null;
    }

    private void cacheRemainingLimitSuggestion(int itemId, Integer remainingLimit) {
        RemainingLimitSuggestionService service = remaining();
        if (service != null) {
            service.cacheSuggestion(itemId, remainingLimit);
        }
    }

    private Integer computeAffordableLimitSuggestion() {
        AffordableLimitSuggestionService service = PluginInjectorBridge.get(AffordableLimitSuggestionService.class);
        return service != null ? service.computeAffordableLimit() : null;
    }

    private void clearRemainingLimitSuggestionCache() {
        RemainingLimitSuggestionService service = remaining();
        if (service != null) {
            service.clearCache();
        }
    }

    void updatePriceSuggestion(Widget promptWidget, Boolean isBuy) {
        if (!isClientLoggedIn()) {
            clearPriceSuggestion();
            return;
        }
        if (!ChatboxSuggestionWidgets.isPromptWidgetValid(promptWidget, true)) {
            clearPriceSuggestion();
            return;
        }
        Widget container = getChatboxContainer();
        if (container == null || container.isHidden()) {
            clearPriceSuggestion();
            return;
        }
        if (isBuy == null) {
            clearPriceSuggestion();
            return;
        }
        Integer previewItemId = getOfferPreviewItemId();
        FlipHubItem previewItem = getOfferPreviewItem();
        if (previewItem == null || previewItemId == null || previewItem.item_id != previewItemId) {
            clearPriceSuggestion();
            return;
        }

        Integer price = isBuy ? previewItem.instabuy_price : previewItem.instasell_price;
        if (price == null || price <= 0) {
            clearPriceSuggestion();
            return;
        }

        Widget suggestion = ensurePriceSuggestionWidget(container);
        boolean changed = false;
        if (lastSuggestedPrice == null || !lastSuggestedPrice.equals(price)
            || lastSuggestedIsBuy == null || !lastSuggestedIsBuy.equals(isBuy)) {
            String label = isBuy ? "Current Buy Price:" : "Current Sell Price:";
            suggestion.setText(label + " " + formatPrice(price) + " gp");
            lastSuggestedPrice = price;
            lastSuggestedIsBuy = isBuy;
            changed = true;
        }
        if (suggestion.isHidden()) {
            suggestion.setHidden(false);
            changed = true;
        }
        if (changed) {
            suggestion.revalidate();
        }
    }

    void updateLimitSuggestion(Widget promptWidget, Boolean isBuy) {
        if (!isClientLoggedIn()) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        if (!ChatboxSuggestionWidgets.isPromptWidgetValid(promptWidget, false)) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        Widget container = getChatboxContainer();
        if (container == null || container.isHidden()) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        if (isBuy == null || !isBuy) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        Integer previewItemId = getOfferPreviewItemId();
        FlipHubItem previewItem = getOfferPreviewItem();
        if (previewItem == null || previewItemId == null || previewItem.item_id != previewItemId) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        Integer remaining = previewItem.ge_limit_remaining;
        if (remaining == null || remaining <= 0) {
            remaining = getThrottledRemainingLimitSuggestion(previewItemId);
        } else {
            cacheRemainingLimitSuggestion(previewItemId, remaining);
        }
        Integer affordable = computeAffordableLimitSuggestion();
        boolean hasRemainingSuggestion = remaining != null && remaining > 0;
        boolean hasAffordableSuggestion = affordable != null && affordable > 0;

        if (!hasRemainingSuggestion && !hasAffordableSuggestion) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        if (hasRemainingSuggestion) {
            Widget suggestion = ensureLimitSuggestionWidget(container);
            String text = "Remaining GE limit: " + formatPrice(remaining);
            boolean changed = applySuggestionTextAndWidth(suggestion, text);
            lastSuggestedLimit = remaining;
            if (suggestion.isHidden()) {
                suggestion.setHidden(false);
                changed = true;
            }
            if (changed) {
                suggestion.revalidate();
            }
        } else {
            clearLimitSuggestion();
        }

        if (hasAffordableSuggestion) {
            Widget suggestion = ensureAffordableLimitSuggestionWidget(container);
            String text = "Cash limit: " + formatPrice(affordable);
            boolean changed = applySuggestionTextAndWidth(suggestion, text);
            lastSuggestedAffordableLimit = affordable;
            if (suggestion.isHidden()) {
                suggestion.setHidden(false);
                changed = true;
            }
            if (changed) {
                suggestion.revalidate();
            }
        } else {
            clearAffordableLimitSuggestion();
        }
    }

    void clearPriceSuggestion() {
        Widget suggestion = getPriceSuggestionWidget();
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = getChatboxContainer();
            if (!isSuggestionWidgetValid(container)) {
                setPriceSuggestionWidget(null);
            }
        }
        lastSuggestedPrice = null;
        lastSuggestedIsBuy = null;
    }

    void clearLimitSuggestion() {
        Widget suggestion = getLimitSuggestionWidget();
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = getChatboxContainer();
            if (!isLimitWidgetValid(container)) {
                setLimitSuggestionWidget(null);
            }
        }
        lastSuggestedLimit = null;
        clearRemainingLimitSuggestionCache();
    }

    void clearAffordableLimitSuggestion() {
        Widget suggestion = getAffordableLimitSuggestionWidget();
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = getChatboxContainer();
            if (!isAffordableLimitWidgetValid(container)) {
                setAffordableLimitSuggestionWidget(null);
            }
        }
        lastSuggestedAffordableLimit = null;
    }

    private boolean applySuggestionTextAndWidth(Widget suggestion, String text) {
        if (suggestion == null) {
            return false;
        }
        boolean changed = false;
        String normalized = text == null ? "" : text;
        if (!normalized.equals(suggestion.getText())) {
            suggestion.setText(normalized);
            changed = true;
        }
        int width = Math.max(MIN_SUGGESTION_WIDTH, (normalized.length() * SUGGESTION_TEXT_CHAR_PX) + SUGGESTION_TEXT_PADDING_PX);
        if (suggestion.getOriginalWidth() != width) {
            suggestion.setOriginalWidth(width);
            changed = true;
        }
        suggestion.setWidthMode(WidgetSizeMode.ABSOLUTE);
        return changed;
    }
}
