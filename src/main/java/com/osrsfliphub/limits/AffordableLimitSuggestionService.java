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
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.VarbitID;

@Singleton
final class AffordableLimitSuggestionService {
    private final Client client;
    private final OfferPreviewRuntimeFacadeService facade;

    @Inject
    AffordableLimitSuggestionService(Client client, OfferPreviewRuntimeFacadeService facade) {
        this.client = client;
        this.facade = facade;
    }

    Integer computeAffordableLimit(Integer remainingLimit) {
        return computeAffordableLimit(remainingLimit, enteredOfferPrice(), selectedOfferPrice(), inventoryCoins());
    }

    // Pure computation, split out so it stays unit-testable without a client.
    Integer computeAffordableLimit(Integer remainingLimit, Integer enteredPrice, Integer selectedPrice, long coins) {
        Integer offerPrice = enteredPrice != null && enteredPrice > 0
            ? enteredPrice
            : (selectedPrice != null && selectedPrice > 0 ? selectedPrice : null);
        if (offerPrice == null || offerPrice <= 0) {
            return null;
        }
        if (coins <= 0) {
            return null;
        }
        long affordable = coins / offerPrice;
        if (remainingLimit != null && remainingLimit > 0) {
            affordable = Math.min(affordable, remainingLimit.longValue());
        }
        if (affordable <= 0) {
            return null;
        }
        return affordable > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) affordable;
    }

    private Integer enteredOfferPrice() {
        if (client == null) {
            return null;
        }
        int enteredPrice = client.getVarbitValue(GeLifecyclePluginConstants.GE_OFFER_PRICE_VARBIT);
        return enteredPrice > 0 ? enteredPrice : null;
    }

    private Integer selectedOfferPrice() {
        if (client == null || facade == null) {
            return null;
        }
        GrandExchangeOffer offer = facade.getSelectedOffer(client, VarbitID.GE_SELECTEDSLOT);
        if (offer == null || offer.getPrice() <= 0) {
            return null;
        }
        return offer.getPrice();
    }

    private long inventoryCoins() {
        if (client == null) {
            return 0L;
        }
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0L;
        }
        Item[] items = inventory.getItems();
        if (items == null || items.length == 0) {
            return 0L;
        }
        long totalCoins = 0L;
        for (Item item : items) {
            if (item == null || item.getId() != GeLifecyclePluginConstants.COINS_ITEM_ID) {
                continue;
            }
            totalCoins += Math.max(0, item.getQuantity());
        }
        return totalCoins;
    }
}
