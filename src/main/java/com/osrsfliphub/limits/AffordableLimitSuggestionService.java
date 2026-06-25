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

final class AffordableLimitSuggestionService {
    interface Hooks {
        Integer getEnteredOfferPrice();
        Integer getSelectedOfferPrice();
        long getInventoryCoins();
    }

    private final Hooks hooks;

    AffordableLimitSuggestionService(Hooks hooks) {
        this.hooks = hooks;
    }

    Integer computeAffordableLimit(Integer remainingLimit) {
        if (hooks == null) {
            return null;
        }
        Integer offerPrice = resolveOfferPrice();
        if (offerPrice == null || offerPrice <= 0) {
            return null;
        }
        long coins = hooks.getInventoryCoins();
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

    private Integer resolveOfferPrice() {
        Integer entered = hooks.getEnteredOfferPrice();
        if (entered != null && entered > 0) {
            return entered;
        }
        Integer selected = hooks.getSelectedOfferPrice();
        if (selected != null && selected > 0) {
            return selected;
        }
        return null;
    }
}
