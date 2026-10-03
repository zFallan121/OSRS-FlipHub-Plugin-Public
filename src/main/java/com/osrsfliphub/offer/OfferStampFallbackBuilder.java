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

import java.util.*;
import javax.inject.*;

@Singleton
final class OfferStampFallbackBuilder {
    @Inject
    OfferStampFallbackBuilder() {
    }

    /** @param offers the offers in the slots now, by slot, as last read from the game */
    List<FlipHubItem> buildItems(Map<Integer, Stamp> stamps, Map<Integer, OfferSnapshot> offers) {
        List<FlipHubItem> items = new ArrayList<>();
        if (stamps == null) {
            return items;
        }
        ItemLookup lookup = Bridge.get(ItemLookup.class);
        for (Map.Entry<Integer, Stamp> slot : stamps.entrySet()) {
            Stamp stamp = slot.getValue();
            if (stamp == null || stamp.itemId <= 0) {
                continue;
            }
            FlipHubItem item = new FlipHubItem();
            item.item_id = stamp.itemId;
            if (lookup != null) {
                String itemName = lookup.lookupItemNameSafe(stamp.itemId);
                if (Str.hasText(itemName)) {
                    item.item_name = itemName;
                }
            }
            // A saved slot position holds its price up to max cash and no further. Past that
            // the offer still in the slot says it; before the game has been asked, nothing does,
            // and the card says N/A.
            Long price = null;
            OfferSnapshot offer = offers.get(slot.getKey());
            if (stamp.price < Integer.MAX_VALUE) {
                price = (long) stamp.price;
            } else if (offer != null && offer.itemId == stamp.itemId) {
                price = offer.price;
            }
            if (stamp.isBuy) {
                item.last_buy_price = price;
            } else {
                item.last_sell_price = price;
            }
            // The same prices and the same margin every other card gets. These used to be the
            // game's single guide price written into both the buy and the sell slot, so a card
            // on a fresh install showed one number twice and a margin of nothing, which reads
            // as a broken price feed rather than as a card with no trades behind it yet.
            ItemEnrichment enrichment = Bridge.get(ItemEnrichment.class);
            if (enrichment != null) {
                enrichment.applyGuidePrices(item, stamp.itemId, false);
                enrichment.applyLocalLimitInfo(item, stamp.itemId, null);
                enrichment.applyMarginInfo(item);
            }
            items.add(item);
        }
        return items;
    }
}
