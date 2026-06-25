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

import java.util.ArrayList;
import java.util.List;

final class OfferStampFallbackBuilder {
    interface Hooks {
        String getItemName(int itemId);
        Integer getGuidePrice(int itemId);
    }

    private final Hooks hooks;

    OfferStampFallbackBuilder(Hooks hooks) {
        this.hooks = hooks;
    }

    List<FlipHubItem> buildItems(Iterable<OfferUpdateStamp> stamps) {
        List<FlipHubItem> items = new ArrayList<>();
        if (stamps == null) {
            return items;
        }
        for (OfferUpdateStamp stamp : stamps) {
            if (stamp == null || stamp.itemId <= 0) {
                continue;
            }
            FlipHubItem item = new FlipHubItem();
            item.item_id = stamp.itemId;
            if (hooks != null) {
                String itemName = hooks.getItemName(stamp.itemId);
                if (itemName != null && !itemName.trim().isEmpty()) {
                    item.item_name = itemName;
                }
                Integer guidePrice = hooks.getGuidePrice(stamp.itemId);
                if (guidePrice != null && guidePrice > 0) {
                    item.instabuy_price = guidePrice;
                    item.instasell_price = guidePrice;
                }
            }
            if (stamp.isBuy) {
                item.last_buy_price = stamp.price;
            } else {
                item.last_sell_price = stamp.price;
            }
            if (item.last_buy_price != null && item.last_sell_price != null
                && item.last_buy_price > 0 && item.last_sell_price > 0) {
                int margin = item.last_sell_price - item.last_buy_price;
                item.margin = margin;
                item.roi_percent = item.last_buy_price > 0 ? (margin * 100.0) / item.last_buy_price : null;
            }
            items.add(item);
        }
        return items;
    }
}
