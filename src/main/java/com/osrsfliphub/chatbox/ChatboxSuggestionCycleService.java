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

final class ChatboxSuggestionCycleService {
    interface Hooks {
        boolean isClientLoggedIn();
        boolean isGeInputPromptActive();
        boolean isChatboxInputVisible();
        boolean isSuggestionDirty();
        void setSuggestionDirty(boolean dirty);
        void setLastSuggestionUpdateMs(long timestampMs);
        void clearSuggestions();
        void clearPromptWidgetCache();
        boolean preparePromptWidgets();
        boolean isGeRootVisible();
        Boolean resolveOfferType();
        void updatePreparedSuggestions(Boolean isBuy);
        long nowMs();
    }

    private final Hooks hooks;

    ChatboxSuggestionCycleService(Hooks hooks) {
        this.hooks = hooks;
    }

    void update() {
        if (hooks == null) {
            return;
        }

        if (!hooks.isClientLoggedIn()) {
            hooks.clearSuggestions();
            hooks.setSuggestionDirty(false);
            return;
        }

        if (!hooks.isGeInputPromptActive()) {
            hooks.clearSuggestions();
            hooks.clearPromptWidgetCache();
            hooks.setSuggestionDirty(false);
            return;
        }

        if (!hooks.isChatboxInputVisible()) {
            hooks.clearSuggestions();
            hooks.clearPromptWidgetCache();
            hooks.setSuggestionDirty(false);
            return;
        }

        hooks.setLastSuggestionUpdateMs(hooks.nowMs());
        hooks.setSuggestionDirty(false);

        boolean promptsPrepared = hooks.preparePromptWidgets();

        Boolean offerType = hooks.resolveOfferType();
        if (!promptsPrepared && !hooks.isGeRootVisible() && offerType == null) {
            hooks.clearSuggestions();
            return;
        }
        if (!hooks.isGeRootVisible() && offerType == null) {
            hooks.clearSuggestions();
            return;
        }

        hooks.updatePreparedSuggestions(offerType);
    }
}
