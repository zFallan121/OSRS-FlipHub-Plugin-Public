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

public class AffordableLimitSuggestionFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        AffordableLimitSuggestionFactoryService factory = new AffordableLimitSuggestionFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.enteredOfferPrice = 100;
        hooks.selectedOfferPrice = 200;
        hooks.inventoryCoins = 10_000L;

        AffordableLimitSuggestionService service = factory.create(hooks);

        assertEquals(Integer.valueOf(25), service.computeAffordableLimit(25));
        assertEquals(1, hooks.getEnteredOfferPriceCalls);
        assertEquals(0, hooks.getSelectedOfferPriceCalls);
        assertEquals(1, hooks.getInventoryCoinsCalls);

        hooks.enteredOfferPrice = 0;
        hooks.selectedOfferPrice = 250;
        assertEquals(Integer.valueOf(40), service.computeAffordableLimit(null));
        assertEquals(2, hooks.getEnteredOfferPriceCalls);
        assertEquals(1, hooks.getSelectedOfferPriceCalls);
        assertEquals(2, hooks.getInventoryCoinsCalls);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        AffordableLimitSuggestionFactoryService factory = new AffordableLimitSuggestionFactoryService();
        AffordableLimitSuggestionService service = factory.create(null);

        assertNull(service.computeAffordableLimit(10));
    }

    private static final class TestRuntimeHooks implements AffordableLimitSuggestionFactoryService.RuntimeHooks {
        private Integer enteredOfferPrice;
        private Integer selectedOfferPrice;
        private long inventoryCoins;
        private int getEnteredOfferPriceCalls;
        private int getSelectedOfferPriceCalls;
        private int getInventoryCoinsCalls;

        @Override
        public Integer getEnteredOfferPrice() {
            getEnteredOfferPriceCalls++;
            return enteredOfferPrice;
        }

        @Override
        public Integer getSelectedOfferPrice() {
            getSelectedOfferPriceCalls++;
            return selectedOfferPrice;
        }

        @Override
        public long getInventoryCoins() {
            getInventoryCoinsCalls++;
            return inventoryCoins;
        }
    }
}
