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

public class ChatboxSuggestionApplyServiceTest {
    @Test
    public void applySuggestedPriceToChatUsesBuyOrSellPriceByOfferType() {
        TestHooks hooks = new TestHooks();
        hooks.canApply = true;
        hooks.offerPreviewItem = previewItem(4151, 100, 200, 50);
        ChatboxSuggestionApplyService service = new ChatboxSuggestionApplyService(hooks);

        hooks.offerType = Boolean.TRUE;
        service.applySuggestedPriceToChat();
        assertEquals("100", hooks.lastChatInput);
        assertEquals(1, hooks.rebuildCalls);

        hooks.offerType = Boolean.FALSE;
        service.applySuggestedPriceToChat();
        assertEquals("200", hooks.lastChatInput);
        assertEquals(2, hooks.rebuildCalls);
    }

    @Test
    public void applySuggestedPriceToChatNoopsWhenUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.canApply = false;
        hooks.offerType = Boolean.TRUE;
        hooks.offerPreviewItem = previewItem(4151, 100, 200, 50);
        ChatboxSuggestionApplyService service = new ChatboxSuggestionApplyService(hooks);

        service.applySuggestedPriceToChat();

        assertNull(hooks.lastChatInput);
        assertEquals(0, hooks.rebuildCalls);
    }

    @Test
    public void applySuggestedLimitToChatUsesRemainingOnlyForBuyOffers() {
        TestHooks hooks = new TestHooks();
        hooks.canApply = true;
        hooks.offerPreviewItem = previewItem(4151, 100, 200, 37);
        ChatboxSuggestionApplyService service = new ChatboxSuggestionApplyService(hooks);

        hooks.offerType = Boolean.FALSE;
        service.applySuggestedLimitToChat();
        assertNull(hooks.lastChatInput);

        hooks.offerType = Boolean.TRUE;
        service.applySuggestedLimitToChat();
        assertEquals("37", hooks.lastChatInput);
        assertEquals(1, hooks.rebuildCalls);
    }

    @Test
    public void applySuggestedAffordableLimitToChatFallsBackToRemainingSuggestion() {
        TestHooks hooks = new TestHooks();
        hooks.canApply = true;
        hooks.offerType = Boolean.TRUE;
        hooks.offerPreviewItem = previewItem(4151, 100, 200, null);
        hooks.computedRemainingSuggestion = 88;
        hooks.computedAffordableSuggestion = 30;
        ChatboxSuggestionApplyService service = new ChatboxSuggestionApplyService(hooks);

        service.applySuggestedAffordableLimitToChat();

        assertEquals(Integer.valueOf(4151), hooks.lastComputedRemainingItemId);
        assertEquals(Integer.valueOf(88), hooks.lastAffordableInput);
        assertEquals("30", hooks.lastChatInput);
        assertEquals(1, hooks.rebuildCalls);
    }

    @Test
    public void applySuggestedAffordableLimitToChatNoopsWhenAffordableMissing() {
        TestHooks hooks = new TestHooks();
        hooks.canApply = true;
        hooks.offerType = Boolean.TRUE;
        hooks.offerPreviewItem = previewItem(4151, 100, 200, 40);
        hooks.computedAffordableSuggestion = null;
        ChatboxSuggestionApplyService service = new ChatboxSuggestionApplyService(hooks);

        service.applySuggestedAffordableLimitToChat();

        assertEquals(Integer.valueOf(40), hooks.lastAffordableInput);
        assertNull(hooks.lastChatInput);
        assertEquals(0, hooks.rebuildCalls);
    }

    private static FlipHubItem previewItem(int itemId, Integer buyPrice, Integer sellPrice, Integer remaining) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.instabuy_price = buyPrice;
        item.instasell_price = sellPrice;
        item.ge_limit_remaining = remaining;
        return item;
    }

    private static final class TestHooks implements ChatboxSuggestionApplyService.Hooks {
        private boolean canApply;
        private Boolean offerType;
        private FlipHubItem offerPreviewItem;
        private Integer computedRemainingSuggestion;
        private Integer computedAffordableSuggestion;
        private Integer lastComputedRemainingItemId;
        private Integer lastAffordableInput;
        private String lastChatInput;
        private int rebuildCalls;

        @Override
        public boolean canApplyChatInput() {
            return canApply;
        }

        @Override
        public Boolean resolveOfferType() {
            return offerType;
        }

        @Override
        public FlipHubItem getOfferPreviewItem() {
            return offerPreviewItem;
        }

        @Override
        public Integer computeRemainingLimitSuggestion(int itemId) {
            lastComputedRemainingItemId = itemId;
            return computedRemainingSuggestion;
        }

        @Override
        public Integer computeAffordableLimitSuggestion(Integer remainingLimit) {
            lastAffordableInput = remainingLimit;
            return computedAffordableSuggestion;
        }

        @Override
        public void setChatInput(String value) {
            lastChatInput = value;
        }

        @Override
        public void rebuildChatInput() {
            rebuildCalls++;
        }
    }
}
