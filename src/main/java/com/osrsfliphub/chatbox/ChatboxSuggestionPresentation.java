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
final class ChatboxSuggestionPresentation {
    private static final int MIN_SUGGESTION_WIDTH = 12;
    private static final int SUGGESTION_TEXT_CHAR_PX = 7;
    private static final int SUGGESTION_TEXT_PADDING_PX = 8;

    private final Client client;
    private final ChatboxSuggestionRuntimeState runtimeState;
    private final RemainingLimitSuggestion remainingLimitSuggestion;
    private final AffordableLimitSuggestion affordableLimitSuggestion;
    private Integer lastSuggestedPrice;
    private Boolean lastSuggestedIsBuy;
    private Integer lastSuggestedLimit;
    private Integer lastSuggestedAffordableLimit;

    void updatePriceSuggestion(Widget promptWidget, Boolean isBuy) {
        if (!Access.loggedIn(client)) {
            clearPriceSuggestion();
            return;
        }
        if (!ChatboxSuggestionWidgets.isPromptWidgetValid(promptWidget, true)) {
            clearPriceSuggestion();
            return;
        }
        Widget container = runtimeState.getChatboxContainer();
        if (container == null || container.isHidden()) {
            clearPriceSuggestion();
            return;
        }
        if (isBuy == null) {
            clearPriceSuggestion();
            return;
        }
        Integer previewItemId = Access.plugin().offerPreviewItemId;
        FlipHubItem previewItem = Access.plugin().offerPreviewItem;
        if (previewItem == null || previewItemId == null || previewItem.item_id != previewItemId) {
            clearPriceSuggestion();
            return;
        }

        Integer price = isBuy ? previewItem.instabuy_price : previewItem.instasell_price;
        if (price == null || price <= 0) {
            clearPriceSuggestion();
            return;
        }

        Widget suggestion = runtimeState.ensurePriceSuggestionWidget(container);
        boolean changed = false;
        if (lastSuggestedPrice == null || !lastSuggestedPrice.equals(price)
            || lastSuggestedIsBuy == null || !lastSuggestedIsBuy.equals(isBuy)) {
            String label = isBuy ? "Current Buy Price:" : "Current Sell Price:";
            suggestion.setText(label + " " + runtimeState.formatPrice(price) + " gp");
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
        if (!Access.loggedIn(client)) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        if (!ChatboxSuggestionWidgets.isPromptWidgetValid(promptWidget, false)) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }
        Widget container = runtimeState.getChatboxContainer();
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

        Integer previewItemId = Access.plugin().offerPreviewItemId;
        FlipHubItem previewItem = Access.plugin().offerPreviewItem;
        if (previewItem == null || previewItemId == null || previewItem.item_id != previewItemId) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        Integer remaining = previewItem.ge_limit_remaining;
        if (remaining == null || remaining <= 0) {
            remaining = remainingLimitSuggestion.getThrottledSuggestion(previewItemId);
        } else {
            remainingLimitSuggestion.cacheSuggestion(previewItemId, remaining);
        }
        Integer affordable = affordableLimitSuggestion.computeAffordableLimit();
        boolean hasRemainingSuggestion = remaining != null && remaining > 0;
        boolean hasAffordableSuggestion = affordable != null && affordable > 0;

        if (!hasRemainingSuggestion && !hasAffordableSuggestion) {
            clearLimitSuggestion();
            clearAffordableLimitSuggestion();
            return;
        }

        if (hasRemainingSuggestion) {
            Widget suggestion = runtimeState.ensureLimitSuggestionWidget(container);
            String text = "Remaining GE limit: " + runtimeState.formatPrice(remaining);
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
            Widget suggestion = runtimeState.ensureAffordableLimitSuggestionWidget(container);
            String text = "Cash limit: " + runtimeState.formatPrice(affordable);
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
        Widget suggestion = runtimeState.getPriceSuggestionWidget();
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = runtimeState.getChatboxContainer();
            if (!runtimeState.isSuggestionWidgetValid(container)) {
                runtimeState.setPriceSuggestionWidget(null);
            }
        }
        lastSuggestedPrice = null;
        lastSuggestedIsBuy = null;
    }

    void clearLimitSuggestion() {
        Widget suggestion = runtimeState.getLimitSuggestionWidget();
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = runtimeState.getChatboxContainer();
            if (!runtimeState.isLimitWidgetValid(container)) {
                runtimeState.setLimitSuggestionWidget(null);
            }
        }
        lastSuggestedLimit = null;
        remainingLimitSuggestion.clearCache();
    }

    void clearAffordableLimitSuggestion() {
        Widget suggestion = runtimeState.getAffordableLimitSuggestionWidget();
        if (suggestion != null) {
            if (!suggestion.isHidden()) {
                suggestion.setHidden(true);
                suggestion.revalidate();
            }
            Widget container = runtimeState.getChatboxContainer();
            if (!runtimeState.isAffordableLimitWidgetValid(container)) {
                runtimeState.setAffordableLimitSuggestionWidget(null);
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
