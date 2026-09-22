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
    /**
     * Which character the trade belongs to ({@link #characterId}). The website pairs purchases with
     * sales across a whole website account, which is often several characters, and without this it
     * paired one character's sale with another character's purchase: 182 Dragon nails bought at
     * 12,306 and sold at 12,544 were booked against a different character's 20,055 as a 1.37m loss.
     */
    public String character_id;
    /**
     * Set only on the parts of a recorded recipe ({@link RecipeUpload}), which ride the trade upload but
     * are not trades. Null on a trade, and Gson leaves a null out, so a trade is sent exactly as before.
     */
    public String recipe_id;
    public String recipe_kind;
    public Integer recipe_parts;
    public Long recipe_fee_gp;
    public Long recipe_trade_end_ms;
    /**
     * Set only on stock recorded as moved to another of the player's characters: that character's
     * {@link #characterId}. The website keeps each character's purchases to itself, so it has to be
     * told whose book they went to.
     */
    public String recipe_to_character_id;

    /**
     * A fixed code for one character, the same on every computer. It is not the character's name,
     * and the key it is made from cannot be read back out of it. That key is the account hash or,
     * for a character without a usable one, a hash of the name, so a code can be checked against a
     * name someone already knows. Null when the character is not known.
     */
    public static String characterId(long accountKey) {
        return accountKey > 0
            ? UUID.nameUUIDFromBytes(("fliphub-character|" + accountKey).getBytes(StandardCharsets.UTF_8)).toString()
            : null;
    }

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
