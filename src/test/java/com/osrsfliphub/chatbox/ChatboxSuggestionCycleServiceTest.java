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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatboxSuggestionCycleServiceTest {
    @Test
    public void updateClearsSuggestionsWhenLoggedOut() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = false;
        hooks.suggestionDirty = true;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(1, hooks.clearSuggestionsCalls);
        assertEquals(0, hooks.clearPromptCacheCalls);
        assertFalse(hooks.suggestionDirty);
    }

    @Test
    public void updateClearsAndResetsPromptCacheWhenPromptInactive() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.geInputPromptActive = false;
        hooks.suggestionDirty = true;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(1, hooks.clearSuggestionsCalls);
        assertEquals(1, hooks.clearPromptCacheCalls);
        assertFalse(hooks.suggestionDirty);
    }

    @Test
    public void updateRetriesWhenNotDirtyButPromptIsOpen() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = false;
        hooks.preparePromptResult = true;
        hooks.geRootVisible = true;
        hooks.offerType = Boolean.TRUE;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(0, hooks.clearSuggestionsCalls);
        assertEquals(1, hooks.preparePromptCalls);
        assertEquals(1, hooks.updatePreparedSuggestionsCalls);
        assertEquals(hooks.nowMs, hooks.lastSuggestionUpdateMs);
        assertFalse(hooks.suggestionDirty);
    }

    @Test
    public void updateClearsWhenPromptsMissing() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = true;
        hooks.preparePromptResult = false;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(1, hooks.preparePromptCalls);
        assertEquals(1, hooks.clearSuggestionsCalls);
        assertEquals(0, hooks.updatePreparedSuggestionsCalls);
        assertEquals(hooks.nowMs, hooks.lastSuggestionUpdateMs);
    }

    @Test
    public void updateClearsWhenGeRootHidden() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = true;
        hooks.preparePromptResult = true;
        hooks.geRootVisible = false;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(1, hooks.preparePromptCalls);
        assertEquals(1, hooks.clearSuggestionsCalls);
        assertEquals(0, hooks.updatePreparedSuggestionsCalls);
    }

    @Test
    public void updateResolvesOfferTypeAndUpdatesPreparedSuggestions() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = true;
        hooks.preparePromptResult = true;
        hooks.geRootVisible = true;
        hooks.offerType = Boolean.TRUE;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(1, hooks.preparePromptCalls);
        assertEquals(1, hooks.resolveOfferTypeCalls);
        assertEquals(1, hooks.updatePreparedSuggestionsCalls);
        assertTrue(Boolean.TRUE.equals(hooks.lastUpdatedIsBuy));
        assertEquals(0, hooks.clearSuggestionsCalls);
        assertFalse(hooks.suggestionDirty);
    }

    @Test
    public void updateStillRendersWhenGeRootHiddenButOfferTypeResolved() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = true;
        hooks.preparePromptResult = true;
        hooks.geRootVisible = false;
        hooks.offerType = Boolean.FALSE;

        ChatboxSuggestionCycleService service = new ChatboxSuggestionCycleService(hooks);
        service.update();

        assertEquals(1, hooks.preparePromptCalls);
        assertEquals(1, hooks.resolveOfferTypeCalls);
        assertEquals(1, hooks.updatePreparedSuggestionsCalls);
        assertTrue(Boolean.FALSE.equals(hooks.lastUpdatedIsBuy));
        assertEquals(0, hooks.clearSuggestionsCalls);
        assertFalse(hooks.suggestionDirty);
    }

    private static final class TestHooks implements ChatboxSuggestionCycleService.Hooks {
        private boolean loggedIn;
        private boolean geInputPromptActive;
        private boolean chatboxInputVisible;
        private boolean suggestionDirty;
        private long nowMs = 12345L;
        private int clearSuggestionsCalls;
        private int clearPromptCacheCalls;
        private int preparePromptCalls;
        private boolean preparePromptResult;
        private boolean geRootVisible;
        private Boolean offerType;
        private int resolveOfferTypeCalls;
        private int updatePreparedSuggestionsCalls;
        private Boolean lastUpdatedIsBuy;
        private long lastSuggestionUpdateMs;

        @Override
        public boolean isClientLoggedIn() {
            return loggedIn;
        }

        @Override
        public boolean isGeInputPromptActive() {
            return geInputPromptActive;
        }

        @Override
        public boolean isChatboxInputVisible() {
            return chatboxInputVisible;
        }

        @Override
        public boolean isSuggestionDirty() {
            return suggestionDirty;
        }

        @Override
        public void setSuggestionDirty(boolean dirty) {
            suggestionDirty = dirty;
        }

        @Override
        public void setLastSuggestionUpdateMs(long timestampMs) {
            lastSuggestionUpdateMs = timestampMs;
        }

        @Override
        public void clearSuggestions() {
            clearSuggestionsCalls++;
        }

        @Override
        public void clearPromptWidgetCache() {
            clearPromptCacheCalls++;
        }

        @Override
        public boolean preparePromptWidgets() {
            preparePromptCalls++;
            return preparePromptResult;
        }

        @Override
        public boolean isGeRootVisible() {
            return geRootVisible;
        }

        @Override
        public Boolean resolveOfferType() {
            resolveOfferTypeCalls++;
            return offerType;
        }

        @Override
        public void updatePreparedSuggestions(Boolean isBuy) {
            updatePreparedSuggestionsCalls++;
            lastUpdatedIsBuy = isBuy;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }
}
