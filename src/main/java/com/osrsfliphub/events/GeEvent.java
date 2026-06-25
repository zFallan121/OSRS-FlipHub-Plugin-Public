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

import java.util.UUID;

public class GeEvent {
    public String event_id;
    public String event_type;
    public long ts_client_ms;
    public int slot;
    public int item_id;
    public boolean is_buy;
    public int price;
    public int total_qty;
    public int filled_qty;
    public long spent_gp;
    public String state;
    public String prev_state;
    public int delta_qty;
    public long delta_gp;
    public Integer world;
    public int schema_version = 1;

    public static GeEvent createBase(OfferSnapshot snap, OfferSnapshot prev, String eventType) {
        GeEvent e = new GeEvent();
        e.event_id = UUID.randomUUID().toString();
        e.event_type = eventType;
        e.ts_client_ms = System.currentTimeMillis();
        e.slot = snap.slot;
        e.item_id = snap.itemId;
        e.is_buy = snap.isBuy;
        e.price = snap.price;
        e.total_qty = snap.totalQty;
        e.filled_qty = snap.filledQty;
        e.spent_gp = snap.spentGp;
        e.state = snap.state;
        e.prev_state = prev != null ? prev.state : null;
        return e;
    }
}
