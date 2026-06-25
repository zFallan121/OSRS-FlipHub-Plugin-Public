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

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.widgets.Widget;

final class ChatboxSuggestionCycleRuntimeHooks implements ChatboxSuggestionCycleFactoryService.RuntimeHooks {
    interface WidgetSuggestionUpdater {
        void update(Widget widget, Boolean isBuy);
    }

    private final BooleanSupplier clientLoggedIn;
    private final BooleanSupplier geInputPromptActive;
    private final BooleanSupplier chatboxInputVisible;
    private final BooleanSupplier suggestionDirty;
    private final Consumer<Boolean> setSuggestionDirty;
    private final LongConsumer setLastSuggestionUpdateMs;
    private final Runnable clearSuggestions;
    private final Runnable clearPromptWidgetCache;
    private final Supplier<Widget> pricePromptWidgetSupplier;
    private final Supplier<Widget> quantityPromptWidgetSupplier;
    private final BooleanSupplier geRootVisible;
    private final Supplier<Boolean> offerTypeSupplier;
    private final WidgetSuggestionUpdater updatePriceSuggestion;
    private final WidgetSuggestionUpdater updateLimitSuggestion;
    private final LongSupplier nowMs;

    ChatboxSuggestionCycleRuntimeHooks(BooleanSupplier clientLoggedIn,
                                       BooleanSupplier geInputPromptActive,
                                       BooleanSupplier chatboxInputVisible,
                                       BooleanSupplier suggestionDirty,
                                       Consumer<Boolean> setSuggestionDirty,
                                       LongConsumer setLastSuggestionUpdateMs,
                                       Runnable clearSuggestions,
                                       Runnable clearPromptWidgetCache,
                                       Supplier<Widget> pricePromptWidgetSupplier,
                                       Supplier<Widget> quantityPromptWidgetSupplier,
                                       BooleanSupplier geRootVisible,
                                       Supplier<Boolean> offerTypeSupplier,
                                       WidgetSuggestionUpdater updatePriceSuggestion,
                                       WidgetSuggestionUpdater updateLimitSuggestion,
                                       LongSupplier nowMs) {
        this.clientLoggedIn = clientLoggedIn;
        this.geInputPromptActive = geInputPromptActive;
        this.chatboxInputVisible = chatboxInputVisible;
        this.suggestionDirty = suggestionDirty;
        this.setSuggestionDirty = setSuggestionDirty;
        this.setLastSuggestionUpdateMs = setLastSuggestionUpdateMs;
        this.clearSuggestions = clearSuggestions;
        this.clearPromptWidgetCache = clearPromptWidgetCache;
        this.pricePromptWidgetSupplier = pricePromptWidgetSupplier;
        this.quantityPromptWidgetSupplier = quantityPromptWidgetSupplier;
        this.geRootVisible = geRootVisible;
        this.offerTypeSupplier = offerTypeSupplier;
        this.updatePriceSuggestion = updatePriceSuggestion;
        this.updateLimitSuggestion = updateLimitSuggestion;
        this.nowMs = nowMs;
    }

    @Override
    public boolean isClientLoggedIn() {
        return clientLoggedIn != null && clientLoggedIn.getAsBoolean();
    }

    @Override
    public boolean isGeInputPromptActive() {
        return geInputPromptActive != null && geInputPromptActive.getAsBoolean();
    }

    @Override
    public boolean isChatboxInputVisible() {
        return chatboxInputVisible != null && chatboxInputVisible.getAsBoolean();
    }

    @Override
    public boolean isSuggestionDirty() {
        return suggestionDirty != null && suggestionDirty.getAsBoolean();
    }

    @Override
    public void setSuggestionDirty(boolean dirty) {
        if (setSuggestionDirty != null) {
            setSuggestionDirty.accept(dirty);
        }
    }

    @Override
    public void setLastSuggestionUpdateMs(long timestampMs) {
        if (setLastSuggestionUpdateMs != null) {
            setLastSuggestionUpdateMs.accept(timestampMs);
        }
    }

    @Override
    public void clearSuggestions() {
        if (clearSuggestions != null) {
            clearSuggestions.run();
        }
    }

    @Override
    public void clearPromptWidgetCache() {
        if (clearPromptWidgetCache != null) {
            clearPromptWidgetCache.run();
        }
    }

    @Override
    public Widget getPricePromptWidget() {
        return pricePromptWidgetSupplier != null ? pricePromptWidgetSupplier.get() : null;
    }

    @Override
    public Widget getQuantityPromptWidget() {
        return quantityPromptWidgetSupplier != null ? quantityPromptWidgetSupplier.get() : null;
    }

    @Override
    public boolean isGeRootVisible() {
        return geRootVisible != null && geRootVisible.getAsBoolean();
    }

    @Override
    public Boolean resolveOfferType() {
        return offerTypeSupplier != null ? offerTypeSupplier.get() : null;
    }

    @Override
    public void updatePriceSuggestion(Widget promptWidget, Boolean isBuy) {
        if (updatePriceSuggestion != null) {
            updatePriceSuggestion.update(promptWidget, isBuy);
        }
    }

    @Override
    public void updateLimitSuggestion(Widget promptWidget, Boolean isBuy) {
        if (updateLimitSuggestion != null) {
            updateLimitSuggestion.update(promptWidget, isBuy);
        }
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : System.currentTimeMillis();
    }
}
