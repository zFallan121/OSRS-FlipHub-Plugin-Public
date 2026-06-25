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

import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

final class RuneLiteAffordableLimitSuggestionPluginHooks implements AffordableLimitSuggestionFactoryService.RuntimeHooks {
    private final Supplier<Client> clientSupplier;
    private final int geOfferPriceVarbit;
    private final int coinsItemId;
    private final int selectedSlotVarbit;
    private final Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier;

    RuneLiteAffordableLimitSuggestionPluginHooks(
        Supplier<Client> clientSupplier,
        int geOfferPriceVarbit,
        int coinsItemId,
        int selectedSlotVarbit,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.geOfferPriceVarbit = geOfferPriceVarbit;
        this.coinsItemId = coinsItemId;
        this.selectedSlotVarbit = selectedSlotVarbit;
        this.offerPreviewRuntimeFacadeServiceSupplier = offerPreviewRuntimeFacadeServiceSupplier;
    }

    @Override
    public Integer getEnteredOfferPrice() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        if (client == null) {
            return null;
        }
        int enteredPrice = client.getVarbitValue(geOfferPriceVarbit);
        return enteredPrice > 0 ? enteredPrice : null;
    }

    @Override
    public Integer getSelectedOfferPrice() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        OfferPreviewRuntimeFacadeService service = offerPreviewRuntimeFacadeServiceSupplier != null
            ? offerPreviewRuntimeFacadeServiceSupplier.get()
            : null;
        if (client == null || service == null) {
            return null;
        }
        GrandExchangeOffer offer = service.getSelectedOffer(client, selectedSlotVarbit);
        if (offer == null || offer.getPrice() <= 0) {
            return null;
        }
        return offer.getPrice();
    }

    @Override
    public long getInventoryCoins() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
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
            if (item == null || item.getId() != coinsItemId) {
                continue;
            }
            totalCoins += Math.max(0, item.getQuantity());
        }
        return totalCoins;
    }
}
