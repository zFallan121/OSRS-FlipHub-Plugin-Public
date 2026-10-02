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

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.*;
import net.runelite.api.GrandExchangeOfferState;

/** A stored trade as an upload event. Sending it is {@link RecordSync}'s. */
@Singleton
final class BackfillUploader {
    @Inject
    BackfillUploader() {
    }

    GeEvent buildBackfillEvent(long profileKey, Delta delta, Integer world) {
        if (delta == null || delta.itemId <= 0 || delta.tsClientMs <= 0) {
            return null;
        }
        boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
        if (delta.deltaQty <= 0 && !isCompletion) {
            return null;
        }
        String signature = profileKey + "|" + delta.tsClientMs + "|" + delta.slot + "|" + delta.itemId + "|"
            + delta.isBuy + "|" + delta.deltaQty + "|" + delta.deltaGp + "|" + delta.price + "|"
            + (delta.eventType != null ? delta.eventType : "");
        GeEvent event = new GeEvent();
        event.event_id = UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8)).toString();
        event.event_type = Str.hasText(delta.eventType)
            ? delta.eventType
            : "OFFER_UPDATED";
        event.ts_client_ms = delta.tsClientMs;
        event.slot = Math.max(0, delta.slot);
        event.item_id = delta.itemId;
        event.is_buy = delta.isBuy;
        int qty = Math.max(0, delta.deltaQty);
        long deltaGp = Math.max(0L, delta.deltaGp);
        // A long: since the Grand Exchange allows prices past max cash, a unit price can outgrow an int.
        long fallbackPrice = qty > 0 ? Math.max(1L, deltaGp / Math.max(1, qty)) : 1L;
        // A stored price at the cap was cut down to fit (TradeDeltaRecorder); the coins say what it was,
        // a sale's after tax.
        if (delta.price == Integer.MAX_VALUE && !delta.isBuy) {
            fallbackPrice += GeTax.perItem(delta.itemId, fallbackPrice);
        }
        event.price = delta.price > 0 && delta.price < Integer.MAX_VALUE ? delta.price : fallbackPrice;
        event.total_qty = qty;
        event.filled_qty = qty;
        event.spent_gp = deltaGp;
        event.delta_qty = qty;
        event.delta_gp = deltaGp;
        if ("OFFER_COMPLETED".equals(event.event_type)) {
            event.state = delta.isBuy ? GrandExchangeOfferState.BOUGHT.name() : GrandExchangeOfferState.SOLD.name();
        } else {
            event.state = delta.isBuy ? GrandExchangeOfferState.BUYING.name() : GrandExchangeOfferState.SELLING.name();
        }
        event.prev_state = null;
        event.world = world;
        event.schema_version = 1;
        event.character_id = GeEvent.characterId(profileKey);
        event.source = delta.slot >= Const.GE_HISTORY_SYNTHETIC_SLOT_START ? "import" : null;
        return event;
    }
}
