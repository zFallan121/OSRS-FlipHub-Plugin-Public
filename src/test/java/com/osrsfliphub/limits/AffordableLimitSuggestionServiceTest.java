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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AffordableLimitSuggestionServiceTest {
    @Test
    public void computeUsesEnteredPriceBeforeSelectedOfferPrice() {
        TestHooks hooks = new TestHooks();
        hooks.enteredOfferPrice = 100;
        hooks.selectedOfferPrice = 200;
        hooks.inventoryCoins = 10_000L;
        AffordableLimitSuggestionService service = new AffordableLimitSuggestionService(hooks);

        assertEquals(Integer.valueOf(100), service.computeAffordableLimit(null));
    }

    @Test
    public void computeFallsBackToSelectedOfferPrice() {
        TestHooks hooks = new TestHooks();
        hooks.enteredOfferPrice = 0;
        hooks.selectedOfferPrice = 250;
        hooks.inventoryCoins = 2_750L;
        AffordableLimitSuggestionService service = new AffordableLimitSuggestionService(hooks);

        assertEquals(Integer.valueOf(11), service.computeAffordableLimit(null));
    }

    @Test
    public void computeClampsToRemainingLimitWhenPresent() {
        TestHooks hooks = new TestHooks();
        hooks.enteredOfferPrice = 100;
        hooks.inventoryCoins = 10_000L;
        AffordableLimitSuggestionService service = new AffordableLimitSuggestionService(hooks);

        assertEquals(Integer.valueOf(25), service.computeAffordableLimit(25));
    }

    @Test
    public void computeReturnsNullWithoutUsablePriceOrCoins() {
        TestHooks hooks = new TestHooks();
        AffordableLimitSuggestionService service = new AffordableLimitSuggestionService(hooks);

        assertNull(service.computeAffordableLimit(10));

        hooks.enteredOfferPrice = 100;
        hooks.inventoryCoins = 0L;
        assertNull(service.computeAffordableLimit(10));
    }

    @Test
    public void computeCapsAtIntegerMaxValue() {
        TestHooks hooks = new TestHooks();
        hooks.enteredOfferPrice = 1;
        hooks.inventoryCoins = (long) Integer.MAX_VALUE + 1000L;
        AffordableLimitSuggestionService service = new AffordableLimitSuggestionService(hooks);

        assertEquals(Integer.valueOf(Integer.MAX_VALUE), service.computeAffordableLimit(null));
    }

    private static final class TestHooks implements AffordableLimitSuggestionService.Hooks {
        private Integer enteredOfferPrice;
        private Integer selectedOfferPrice;
        private long inventoryCoins;

        @Override
        public Integer getEnteredOfferPrice() {
            return enteredOfferPrice;
        }

        @Override
        public Integer getSelectedOfferPrice() {
            return selectedOfferPrice;
        }

        @Override
        public long getInventoryCoins() {
            return inventoryCoins;
        }
    }
}
