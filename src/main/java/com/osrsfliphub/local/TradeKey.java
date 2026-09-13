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

import java.util.Objects;

/**
 * What identifies one stored trade across restarts and replays.
 *
 * <p>Three stored fields and nothing positional: the moment the first unit
 * changed hands, the slot it was on, and the item. A completed offer is one
 * record, and {@link TradeOfferCollapser} gives it the first fill's
 * timestamp, so a key taken from the first fill of an offer still filling
 * names the same record once the offer has completed. Load-time dedupe and
 * normalisation drop or re-value records but never re-stamp them, and an
 * unrelated trade before or after cannot move any of the three.
 *
 * <p>Two sales of the same item on the same slot in the same millisecond
 * would collide, which a real slot cannot produce; the trades recovered from
 * the in-game history each get a synthetic slot of their own. What would break
 * it is a change that re-stamps stored records - a history re-sync that
 * replaced a stored row rather than matching it - after which a correction
 * keyed on the old stamp would silently stop applying.
 */
final class TradeKey {
    long tsMs;
    int slot;
    int itemId;

    TradeKey() {
    }

    TradeKey(long tsMs, int slot, int itemId) {
        this.tsMs = tsMs;
        this.slot = slot;
        this.itemId = itemId;
    }

    static TradeKey of(Delta delta) {
        return delta != null ? new TradeKey(delta.tsClientMs, delta.slot, delta.itemId) : null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TradeKey)) {
            return false;
        }
        TradeKey that = (TradeKey) other;
        return tsMs == that.tsMs && slot == that.slot && itemId == that.itemId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsMs, slot, itemId);
    }

    @Override
    public String toString() {
        return tsMs + "/" + slot + "/" + itemId;
    }
}
