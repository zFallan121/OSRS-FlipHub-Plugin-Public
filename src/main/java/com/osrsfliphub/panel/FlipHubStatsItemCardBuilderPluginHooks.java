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

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import javax.swing.JLabel;

final class FlipHubStatsItemCardBuilderPluginHooks implements FlipHubStatsItemCardBuilder.Hooks {
    @FunctionalInterface
    interface ItemIconSetter {
        void set(JLabel label, int itemId);
    }

    private final Function<Float, Font> fontResolver;
    private final Function<Float, Font> boldFontResolver;
    private final Function<Float, Font> semiBoldFontResolver;
    private final ItemIconSetter itemIconSetter;
    private final IntPredicate statsItemExpandedPredicate;
    private final IntPredicate statsHistoryExpandedPredicate;
    private final IntFunction<List<StatsFlipInstance>> statsFlipHistoryProvider;
    private final IntConsumer statsItemExpandToggler;
    private final IntConsumer statsHistoryExpandToggler;

    FlipHubStatsItemCardBuilderPluginHooks(
        Function<Float, Font> fontResolver,
        Function<Float, Font> boldFontResolver,
        Function<Float, Font> semiBoldFontResolver,
        ItemIconSetter itemIconSetter,
        IntPredicate statsItemExpandedPredicate,
        IntPredicate statsHistoryExpandedPredicate,
        IntFunction<List<StatsFlipInstance>> statsFlipHistoryProvider,
        IntConsumer statsItemExpandToggler,
        IntConsumer statsHistoryExpandToggler
    ) {
        this.fontResolver = fontResolver;
        this.boldFontResolver = boldFontResolver;
        this.semiBoldFontResolver = semiBoldFontResolver;
        this.itemIconSetter = itemIconSetter;
        this.statsItemExpandedPredicate = statsItemExpandedPredicate;
        this.statsHistoryExpandedPredicate = statsHistoryExpandedPredicate;
        this.statsFlipHistoryProvider = statsFlipHistoryProvider;
        this.statsItemExpandToggler = statsItemExpandToggler;
        this.statsHistoryExpandToggler = statsHistoryExpandToggler;
    }

    @Override
    public Font font(float size) {
        return fontResolver != null ? fontResolver.apply(size) : null;
    }

    @Override
    public Font fontBold(float size) {
        return boldFontResolver != null ? boldFontResolver.apply(size) : null;
    }

    @Override
    public Font fontSemiBold(float size) {
        return semiBoldFontResolver != null ? semiBoldFontResolver.apply(size) : null;
    }

    @Override
    public void setItemIcon(JLabel label, int itemId) {
        if (itemIconSetter != null) {
            itemIconSetter.set(label, itemId);
        }
    }

    @Override
    public boolean isStatsItemExpanded(int itemId) {
        return statsItemExpandedPredicate != null && statsItemExpandedPredicate.test(itemId);
    }

    @Override
    public boolean isStatsHistoryExpanded(int itemId) {
        return statsHistoryExpandedPredicate != null && statsHistoryExpandedPredicate.test(itemId);
    }

    @Override
    public List<StatsFlipInstance> getStatsFlipHistory(int itemId) {
        List<StatsFlipInstance> history = statsFlipHistoryProvider != null ? statsFlipHistoryProvider.apply(itemId) : null;
        return history != null ? history : new ArrayList<>();
    }

    @Override
    public void toggleStatsItemExpanded(int itemId) {
        if (statsItemExpandToggler != null) {
            statsItemExpandToggler.accept(itemId);
        }
    }

    @Override
    public void toggleStatsHistoryExpanded(int itemId) {
        if (statsHistoryExpandToggler != null) {
            statsHistoryExpandToggler.accept(itemId);
        }
    }
}
