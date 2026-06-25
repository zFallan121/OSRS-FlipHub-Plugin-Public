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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseWheelListener;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.border.Border;

final class FlipHubStatsPanelBuilderPluginHooks implements FlipHubStatsPanelBuilder.Hooks {
    @FunctionalInterface
    interface RoundedBorderFactory {
        Border create(int arc, Color color, Insets padding);
    }

    private final Function<Float, Font> fontResolver;
    private final Function<Float, Font> boldFontResolver;
    private final Function<Float, Font> semiBoldFontResolver;
    private final RoundedBorderFactory roundedBorderFactory;
    private final Consumer<JComboBox<?>> comboStyler;
    private final Consumer<JTextField> textFieldStyler;
    private final Consumer<Component> wheelForwarderInstaller;
    private final Supplier<MouseWheelListener> wheelForwarderSupplier;
    private final BooleanSupplier statsSortAscending;
    private final Consumer<StatsRange> statsRangeChanged;
    private final Consumer<StatsItemSort> statsSortChanged;
    private final Runnable statsSortDirectionToggled;
    private final Consumer<String> statsSearchChanged;
    private final Runnable renderStatsItems;
    private final Runnable updateStatsSummary;

    FlipHubStatsPanelBuilderPluginHooks(
        Function<Float, Font> fontResolver,
        Function<Float, Font> boldFontResolver,
        Function<Float, Font> semiBoldFontResolver,
        RoundedBorderFactory roundedBorderFactory,
        Consumer<JComboBox<?>> comboStyler,
        Consumer<JTextField> textFieldStyler,
        Consumer<Component> wheelForwarderInstaller,
        Supplier<MouseWheelListener> wheelForwarderSupplier,
        BooleanSupplier statsSortAscending,
        Consumer<StatsRange> statsRangeChanged,
        Consumer<StatsItemSort> statsSortChanged,
        Runnable statsSortDirectionToggled,
        Consumer<String> statsSearchChanged,
        Runnable renderStatsItems,
        Runnable updateStatsSummary
    ) {
        this.fontResolver = fontResolver;
        this.boldFontResolver = boldFontResolver;
        this.semiBoldFontResolver = semiBoldFontResolver;
        this.roundedBorderFactory = roundedBorderFactory;
        this.comboStyler = comboStyler;
        this.textFieldStyler = textFieldStyler;
        this.wheelForwarderInstaller = wheelForwarderInstaller;
        this.wheelForwarderSupplier = wheelForwarderSupplier;
        this.statsSortAscending = statsSortAscending;
        this.statsRangeChanged = statsRangeChanged;
        this.statsSortChanged = statsSortChanged;
        this.statsSortDirectionToggled = statsSortDirectionToggled;
        this.statsSearchChanged = statsSearchChanged;
        this.renderStatsItems = renderStatsItems;
        this.updateStatsSummary = updateStatsSummary;
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
    public Border roundedBorder(int arc, Color color, Insets padding) {
        return roundedBorderFactory != null ? roundedBorderFactory.create(arc, color, padding) : null;
    }

    @Override
    public void styleComboBox(JComboBox<?> combo) {
        if (comboStyler != null) {
            comboStyler.accept(combo);
        }
    }

    @Override
    public void styleTextField(JTextField field) {
        if (textFieldStyler != null) {
            textFieldStyler.accept(field);
        }
    }

    @Override
    public void installWheelForwarder(Component component) {
        if (wheelForwarderInstaller != null) {
            wheelForwarderInstaller.accept(component);
        }
    }

    @Override
    public MouseWheelListener wheelForwarder() {
        return wheelForwarderSupplier != null ? wheelForwarderSupplier.get() : null;
    }

    @Override
    public boolean isStatsSortAscending() {
        return statsSortAscending != null && statsSortAscending.getAsBoolean();
    }

    @Override
    public void onStatsRangeChanged(StatsRange range) {
        if (statsRangeChanged != null) {
            statsRangeChanged.accept(range);
        }
    }

    @Override
    public void onStatsSortChanged(StatsItemSort sort) {
        if (statsSortChanged != null) {
            statsSortChanged.accept(sort);
        }
    }

    @Override
    public void onStatsSortDirectionToggled() {
        if (statsSortDirectionToggled != null) {
            statsSortDirectionToggled.run();
        }
    }

    @Override
    public void onStatsSearchChanged(String query) {
        if (statsSearchChanged != null) {
            statsSearchChanged.accept(query);
        }
    }

    @Override
    public void renderStatsItems() {
        if (renderStatsItems != null) {
            renderStatsItems.run();
        }
    }

    @Override
    public void updateStatsSummary() {
        if (updateStatsSummary != null) {
            updateStatsSummary.run();
        }
    }
}
