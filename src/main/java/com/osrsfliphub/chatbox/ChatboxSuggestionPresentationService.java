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
import net.runelite.api.widgets.WidgetSizeMode;

final class ChatboxSuggestionPresentationService {
    private static final int MIN_SUGGESTION_WIDTH = 12;
    private static final int SUGGESTION_TEXT_CHAR_PX = 7;
    private static final int SUGGESTION_TEXT_PADDING_PX = 8;

    interface Hooks {
        boolean isClientLoggedIn();
        Widget getChatboxContainer();
        Integer getOfferPreviewItemId();
        FlipHubItem getOfferPreviewItem();
        Widget ensurePriceSuggestionWidget(Widget container);
        Widget ensureLimitSuggestionWidget(Widget container);
        Widget ensureAffordableLimitSuggestionWidget(Widget container);
        Widget getPriceSuggestionWidget();
        void setPriceSuggestionWidget(Widget widget);
        Widget getLimitSuggestionWidget();
        void setLimitSuggestionWidget(Widget widget);
        Widget getAffordableLimitSuggestionWidget();
        void setAffordableLimitSuggestionWidget(Widget widget);
        boolean isSuggestionWidgetValid(Widget container);
        boolean isLimitWidgetValid(Widget container);
        boolean isAffordableLimitWidgetValid(Widget container);
        String formatPrice(int price);
        Integer getThrottledRemainingLimitSuggestion(int itemId);
        void cacheRemainingLimitSuggestion(int itemId, Integer remaining);
        Integer computeAffordableLimitSuggestion(Integer remainingLimit);
        void clearRemainingLimitSuggestionCache();
    }

    private final Hooks hooks;
    private Integer lastSuggestedPrice;
    private Boolean lastSuggestedIsBuy;
    private Integer lastSuggestedLimit;
    private Integer lastSuggestedAffordableLimit;

    ChatboxSuggestionPresentationService(Hooks hooks) {
        this.hooks = hooks;
    }

    void updatePriceSuggestion(Widget promptWidget, Boolean isBuy) {
        if (hooks == null || !hooks.isClientLoggedIn()) {
            clearPriceSuggestion();
            return;
        }
        if (!ChatboxSuggestionWidgets.isPromptWidgetValid(promptWidget, true)) {
            clearPriceSuggestion();
            return;
        }
        Widget container = hooks.getChatboxContainer();
        if (container == null || container.isHidden()) {
            clearPriceSuggestion();
            return;
        }
        if (isBuy == null) {
            clearPriceSuggestion();
            return;
        }
        Integer previewItemId = hooks.getOfferPreviewItemId();
        FlipHubItem previewItem = hooks.getOfferPreviewItem();
        if (previewItem == null || previewItemId == null || previewItem.item_id != previewItemId) {
            clearPriceSuggestion();
            return;
        }

        Integer price = isBuy ? previewItem.instabuy_price : previewItem.instasell_price;
        if (price == null || price <= 0) {
            clearPriceSuggestion();
            return;
        }

        Widget suggestion = hooks.ensurePriceSuggestionWidget(container);
        boolean changed = false;
        if (lastSuggestedPrice == null || !lastSuggestedPrice.equals(price)
            || lastSuggestedIsBuy == null || !lastSuggestedIsBuy.equals(isBuy)) {
            String label = isBuy ? "Current Buy Price:" : "Current Sell Price:";
            suggestion.setText(label + " " + hooks.formatPrice(price) + " gp");
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
        if (hooks == null || !hooks.isClientLoggedIn()) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        if (!ChatboxSuggestionWidgets.isPromptWidgetValid(promptWidget, false)) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        Widget container = hooks.getChatboxContainer();
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

        Integer previewItemId = hooks.getOfferPreviewItemId();
        FlipHubItem previewItem = hooks.getOfferPreviewItem();
        if (previewItem == null || previewItemId == null || previewItem.item_id != previewItemId) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        Integer remaining = previewItem.ge_limit_remaining;
        if (remaining == null || remaining <= 0) {
            remaining = hooks.getThrottledRemainingLimitSuggestion(previewItemId);
        } else {
            hooks.cacheRemainingLimitSuggestion(previewItemId, remaining);
        }
        Integer affordable = hooks.computeAffordableLimitSuggestion(remaining);
        boolean hasRemainingSuggestion = remaining != null && remaining > 0;
        boolean hasAffordableSuggestion = affordable != null && affordable > 0;

        if (!hasRemainingSuggestion && !hasAffordableSuggestion) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        if (hasRemainingSuggestion) {
            Widget suggestion = hooks.ensureLimitSuggestionWidget(container);
            String text = "Remaining GE limit: " + hooks.formatPrice(remaining);
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
            Widget suggestion = hooks.ensureAffordableLimitSuggestionWidget(container);
            String text = "Cash limit: " + hooks.formatPrice(affordable);
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
        Widget suggestion = hooks != null ? hooks.getPriceSuggestionWidget() : null;
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = hooks.getChatboxContainer();
            if (!hooks.isSuggestionWidgetValid(container)) {
                hooks.setPriceSuggestionWidget(null);
            }
        }
        lastSuggestedPrice = null;
        lastSuggestedIsBuy = null;
    }

    void clearLimitSuggestion() {
        Widget suggestion = hooks != null ? hooks.getLimitSuggestionWidget() : null;
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = hooks.getChatboxContainer();
            if (!hooks.isLimitWidgetValid(container)) {
                hooks.setLimitSuggestionWidget(null);
            }
        }
        lastSuggestedLimit = null;
        if (hooks != null) {
            hooks.clearRemainingLimitSuggestionCache();
        }
    }

    void clearAffordableLimitSuggestion() {
        Widget suggestion = hooks != null ? hooks.getAffordableLimitSuggestionWidget() : null;
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = hooks.getChatboxContainer();
            if (!hooks.isAffordableLimitWidgetValid(container)) {
                hooks.setAffordableLimitSuggestionWidget(null);
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
