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
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseWheelListener;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JTextField;
import javax.swing.border.Border;

final class FlipHubFlippingPanelBuilderPluginHooks implements FlipHubFlippingPanelBuilder.Hooks {
    @FunctionalInterface
    interface RoundedBorderFactory {
        Border create(int arc, Color color, Insets padding);
    }

    private final Function<Float, Font> fontResolver;
    private final Function<Float, Font> semiBoldFontResolver;
    private final Function<Float, Font> symbolFontResolver;
    private final RoundedBorderFactory roundedBorderFactory;
    private final Consumer<JTextField> textFieldStyler;
    private final Consumer<Boolean> bookmarkFilterChanged;
    private final Runnable prevPageRequested;
    private final Runnable nextPageRequested;
    private final Runnable searchListenerHookInstaller;
    private final Supplier<MouseWheelListener> wheelForwarderSupplier;

    FlipHubFlippingPanelBuilderPluginHooks(
        Function<Float, Font> fontResolver,
        Function<Float, Font> semiBoldFontResolver,
        Function<Float, Font> symbolFontResolver,
        RoundedBorderFactory roundedBorderFactory,
        Consumer<JTextField> textFieldStyler,
        Consumer<Boolean> bookmarkFilterChanged,
        Runnable prevPageRequested,
        Runnable nextPageRequested,
        Runnable searchListenerHookInstaller,
        Supplier<MouseWheelListener> wheelForwarderSupplier
    ) {
        this.fontResolver = fontResolver;
        this.semiBoldFontResolver = semiBoldFontResolver;
        this.symbolFontResolver = symbolFontResolver;
        this.roundedBorderFactory = roundedBorderFactory;
        this.textFieldStyler = textFieldStyler;
        this.bookmarkFilterChanged = bookmarkFilterChanged;
        this.prevPageRequested = prevPageRequested;
        this.nextPageRequested = nextPageRequested;
        this.searchListenerHookInstaller = searchListenerHookInstaller;
        this.wheelForwarderSupplier = wheelForwarderSupplier;
    }

    @Override
    public Font font(float size) {
        return fontResolver != null ? fontResolver.apply(size) : null;
    }

    @Override
    public Font fontSemiBold(float size) {
        return semiBoldFontResolver != null ? semiBoldFontResolver.apply(size) : null;
    }

    @Override
    public Font fontSymbol(float size) {
        return symbolFontResolver != null ? symbolFontResolver.apply(size) : null;
    }

    @Override
    public Border roundedBorder(int arc, Color color, Insets padding) {
        return roundedBorderFactory != null ? roundedBorderFactory.create(arc, color, padding) : null;
    }

    @Override
    public void styleTextField(JTextField field) {
        if (textFieldStyler != null) {
            textFieldStyler.accept(field);
        }
    }

    @Override
    public void onBookmarkFilterChanged(boolean enabled) {
        if (bookmarkFilterChanged != null) {
            bookmarkFilterChanged.accept(enabled);
        }
    }

    @Override
    public void onPrevPageRequested() {
        if (prevPageRequested != null) {
            prevPageRequested.run();
        }
    }

    @Override
    public void onNextPageRequested() {
        if (nextPageRequested != null) {
            nextPageRequested.run();
        }
    }

    @Override
    public void hookSearchListener() {
        if (searchListenerHookInstaller != null) {
            searchListenerHookInstaller.run();
        }
    }

    @Override
    public MouseWheelListener wheelForwarder() {
        return wheelForwarderSupplier != null ? wheelForwarderSupplier.get() : null;
    }
}
