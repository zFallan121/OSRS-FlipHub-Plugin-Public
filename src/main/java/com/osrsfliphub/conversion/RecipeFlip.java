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
import lombok.AllArgsConstructor;

/**
 * One conversion the player recorded themselves: the trades they bought the parts with, the
 * trades they sold the result with, and what the conversion itself cost.
 *
 * <p>This replaces guessing. The plugin used to watch the trade stream and infer that a set had
 * been assembled, which needed a table of 375 recipes, a matcher, a notion of how sure it was,
 * machinery for breaks whose pieces had not all sold yet, and a way for the player to dismiss a
 * wrong guess. All of that existed because the game never says you combined anything. Being told
 * instead of guessing removes the question.</p>
 *
 * <p>Trades are named by {@link TradeKey}, the same identity the stored trade list uses, with a
 * quantity, because one purchase can feed more than one conversion. A stored record is only
 * applied when every trade it names is still present and still has the quantity it claims.</p>
 */
@AllArgsConstructor
final class RecipeFlip {
    /** A quantity taken out of one stored trade. */
    @AllArgsConstructor
    static final class Part {
        TradeKey trade;
        int quantity;

        Part() {
        }

        boolean isUsable() {
            return trade != null && quantity > 0;
        }
    }

    ConversionKind kind;
    /** What the player is making or taking apart, for the card to name. */
    String name;
    /** The purchases consumed. */
    List<Part> inputs;
    /** The sales the result went out through. */
    List<Part> outputs;
    /** Coins the conversion itself cost, such as a repair fee. Never negative. */
    long feeGp;
    /** When the player recorded it, used only to order equally-timed activities. */
    long recordedMs;

    RecipeFlip() {
    }

    List<Part> inputParts() {
        return inputs != null ? inputs : new ArrayList<>();
    }

    List<Part> outputParts() {
        return outputs != null ? outputs : new ArrayList<>();
    }

    /**
     * Whether this is worth applying at all: a kind, at least one purchase and at least one sale,
     * every part naming a trade and a positive quantity.
     */
    boolean isUsable() {
        if (kind == null || inputParts().isEmpty() || outputParts().isEmpty()) {
            return false;
        }
        for (Part part : inputParts()) {
            if (!part.isUsable()) {
                return false;
            }
        }
        for (Part part : outputParts()) {
            if (!part.isUsable()) {
                return false;
            }
        }
        return feeGp >= 0L;
    }

    /** Every trade this record touches, on either side. */
    List<TradeKey> trades() {
        List<TradeKey> out = new ArrayList<>(inputParts().size() + outputParts().size());
        for (Part part : inputParts()) {
            out.add(part.trade);
        }
        for (Part part : outputParts()) {
            out.add(part.trade);
        }
        return out;
    }

    /** The item the activity is filed against: what was produced, or what was taken apart. */
    int subjectItemId() {
        List<Part> subject = outputParts().size() == 1 ? outputParts() : inputParts();
        return subject.isEmpty() || subject.get(0).trade == null ? 0 : subject.get(0).trade.itemId;
    }
}
