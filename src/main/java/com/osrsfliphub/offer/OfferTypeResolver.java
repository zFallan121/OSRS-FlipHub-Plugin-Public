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

import java.util.List;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;

final class OfferTypeResolver {
    interface Hooks {
        Widget getOfferContainer();
        GrandExchangeOffer getSelectedOffer();
        int getNewOfferTypeVarbit();
        Widget getVisibleGeRoot();
        List<String> collectWidgetText(Widget widget);
        String normalizeOfferText(String text);
    }

    private final Hooks hooks;
    private Integer newOfferTypeBuyValue;
    private Integer newOfferTypeSellValue;
    private Boolean lastResolvedOfferType;

    OfferTypeResolver(Hooks hooks) {
        this.hooks = hooks;
    }

    Boolean resolveOfferType() {
        if (hooks == null) {
            return null;
        }
        Boolean fromSetupText = findOfferTypeFromSetupWidgets();
        if (fromSetupText != null) {
            cacheOfferTypeMapping(fromSetupText);
            return remember(fromSetupText);
        }
        Boolean fromSelectedSlot = findOfferTypeFromSelectedSlot();
        if (fromSelectedSlot != null) {
            return remember(fromSelectedSlot);
        }
        Boolean fromVarbit = mapNewOfferType(hooks.getNewOfferTypeVarbit());
        if (fromVarbit != null) {
            return remember(fromVarbit);
        }
        Boolean fromGeRoot = findOfferTypeFromGeRoot();
        if (fromGeRoot != null) {
            return remember(fromGeRoot);
        }
        return lastResolvedOfferType;
    }

    private Boolean findOfferTypeFromSetupWidgets() {
        Widget offerContainer = hooks.getOfferContainer();
        return findOfferTypeInWidget(offerContainer);
    }

    private Boolean findOfferTypeInWidget(Widget widget) {
        if (widget == null || widget.isHidden()) {
            return null;
        }
        String normalized = hooks.normalizeOfferText(widget.getText());
        if (normalized != null) {
            String lower = normalized.toLowerCase();
            if (lower.contains("buy offer")) {
                return true;
            }
            if (lower.contains("sell offer")) {
                return false;
            }
        }
        Boolean match = findOfferTypeInChildren(widget.getChildren());
        if (match != null) {
            return match;
        }
        match = findOfferTypeInChildren(widget.getDynamicChildren());
        if (match != null) {
            return match;
        }
        return findOfferTypeInChildren(widget.getNestedChildren());
    }

    private Boolean findOfferTypeInChildren(Widget[] children) {
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            Boolean match = findOfferTypeInWidget(child);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private Boolean findOfferTypeFromSelectedSlot() {
        GrandExchangeOffer offer = hooks.getSelectedOffer();
        if (offer == null) {
            return null;
        }
        GrandExchangeOfferState state = offer.getState();
        if (state == null) {
            return null;
        }
        if (state == GrandExchangeOfferState.BUYING
            || state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.CANCELLED_BUY) {
            return true;
        }
        if (state == GrandExchangeOfferState.SELLING
            || state == GrandExchangeOfferState.SOLD
            || state == GrandExchangeOfferState.CANCELLED_SELL) {
            return false;
        }
        return null;
    }

    private void cacheOfferTypeMapping(boolean isBuy) {
        int offerType = hooks.getNewOfferTypeVarbit();
        if (offerType <= 0) {
            return;
        }
        if (isBuy) {
            newOfferTypeBuyValue = offerType;
        } else {
            newOfferTypeSellValue = offerType;
        }
    }

    private Boolean mapNewOfferType(int offerType) {
        if (offerType <= 0) {
            return null;
        }
        if (newOfferTypeBuyValue != null && offerType == newOfferTypeBuyValue) {
            return true;
        }
        if (newOfferTypeSellValue != null && offerType == newOfferTypeSellValue) {
            return false;
        }
        if (offerType == 1) {
            return true;
        }
        if (offerType == 2) {
            return false;
        }
        return null;
    }

    private Boolean findOfferTypeFromGeRoot() {
        Widget geRoot = hooks.getVisibleGeRoot();
        if (geRoot == null || geRoot.isHidden()) {
            return null;
        }
        List<String> texts = hooks.collectWidgetText(geRoot);
        boolean seenBuy = false;
        boolean seenSell = false;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase();
            if (lower.contains("buy offer")) {
                seenBuy = true;
            }
            if (lower.contains("sell offer")) {
                seenSell = true;
            }
        }
        if (seenSell && !seenBuy) {
            return false;
        }
        if (seenBuy && !seenSell) {
            return true;
        }
        return null;
    }

    private Boolean remember(Boolean offerType) {
        if (offerType != null) {
            lastResolvedOfferType = offerType;
        }
        return offerType;
    }
}
