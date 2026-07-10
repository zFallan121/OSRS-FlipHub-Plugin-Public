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
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;

@Singleton
final class ChatboxSuggestionApplyService {
    private final Client client;

    @Inject
    ChatboxSuggestionApplyService(Client client) {
        this.client = client;
    }

    private Boolean resolveOfferType() {
        OfferTypeResolver resolver = PluginInjectorBridge.get(OfferTypeResolver.class);
        return resolver != null ? resolver.resolveOfferType() : null;
    }

    private FlipHubItem getOfferPreviewItem() {
        return PluginAccess.plugin().offerPreviewItem;
    }

    void applySuggestedPriceToChat() {
        if (client == null) {
            return;
        }
        Boolean isBuy = resolveOfferType();
        FlipHubItem previewItem = getOfferPreviewItem();
        if (isBuy == null || previewItem == null) {
            return;
        }
        Integer price = isBuy ? previewItem.instabuy_price : previewItem.instasell_price;
        if (price == null || price <= 0) {
            return;
        }
        applySuggestedQuantityToChat(price);
    }

    void applySuggestedLimitToChat() {
        if (client == null) {
            return;
        }
        Boolean isBuy = resolveOfferType();
        FlipHubItem previewItem = getOfferPreviewItem();
        if (isBuy == null || !isBuy || previewItem == null) {
            return;
        }
        Integer remaining = previewItem.ge_limit_remaining;
        if (remaining == null || remaining <= 0) {
            return;
        }
        applySuggestedQuantityToChat(remaining);
    }

    void applySuggestedAffordableLimitToChat() {
        if (client == null) {
            return;
        }
        Boolean isBuy = resolveOfferType();
        FlipHubItem previewItem = getOfferPreviewItem();
        if (isBuy == null || !isBuy || previewItem == null) {
            return;
        }
        Integer remaining = previewItem.ge_limit_remaining;
        if (remaining == null || remaining <= 0) {
            RemainingLimitSuggestionService remainingService =
                PluginInjectorBridge.get(RemainingLimitSuggestionService.class);
            remaining = remainingService != null ? remainingService.computeSuggestion(previewItem.item_id) : null;
        }
        AffordableLimitSuggestionService affordableService =
            PluginInjectorBridge.get(AffordableLimitSuggestionService.class);
        Integer affordable = affordableService != null ? affordableService.computeAffordableLimit(remaining) : null;
        if (affordable == null || affordable <= 0) {
            return;
        }
        applySuggestedQuantityToChat(affordable);
    }

    private void applySuggestedQuantityToChat(int quantity) {
        if (quantity <= 0 || client == null) {
            return;
        }
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(quantity));
        client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
    }
}
