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

import java.util.Map;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

final class OfferUpdateStampLegacyMatcher {
    boolean matchesCurrentOffers(Map<Integer, OfferUpdateStamp> stamps, GrandExchangeOffer[] offers) {
        if (stamps == null || stamps.isEmpty() || offers == null || offers.length == 0) {
            return false;
        }

        int compared = 0;
        int matches = 0;
        for (Map.Entry<Integer, OfferUpdateStamp> entry : stamps.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int slot = entry.getKey();
            if (slot < 0 || slot >= offers.length) {
                continue;
            }
            OfferUpdateStamp stamp = entry.getValue();
            if (stamp.itemId <= 0) {
                continue;
            }
            GrandExchangeOffer offer = offers[slot];
            if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0) {
                continue;
            }
            compared++;
            boolean isBuy = isBuyOffer(offer);
            if (stamp.itemId == offer.getItemId() && stamp.isBuy == isBuy) {
                matches++;
            }
        }

        if (compared <= 0) {
            return false;
        }
        int required = compared >= 2 ? 2 : 1;
        return matches >= required;
    }

    private boolean isBuyOffer(GrandExchangeOffer offer) {
        if (offer == null) {
            return false;
        }
        GrandExchangeOfferState state = offer.getState();
        return state == GrandExchangeOfferState.BUYING
            || state == GrandExchangeOfferState.BOUGHT
            || state == GrandExchangeOfferState.CANCELLED_BUY;
    }
}
