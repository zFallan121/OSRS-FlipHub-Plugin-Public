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

import java.awt.Component;
import java.awt.Font;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import javax.swing.JComponent;
import javax.swing.JLabel;

final class FlipHubItemCardBuilderPluginHooks implements FlipHubItemCardBuilder.Hooks {
    @FunctionalInterface
    interface ItemIconSetter {
        void set(JLabel label, int itemId);
    }

    @FunctionalInterface
    interface ItemPageHandlerAttacher {
        void attach(JComponent component, int itemId, String itemName);
    }

    @FunctionalInterface
    interface AgePairRegistrar {
        void register(Long buyTimestampMs, Long sellTimestampMs, LineComponents buyLine, LineComponents sellLine);
    }

    @FunctionalInterface
    interface CountdownLabelRegistrar {
        void register(JLabel label, Long remainingMs, long asOfMs);
    }

    private final Function<Float, Font> fontResolver;
    private final Function<Float, Font> boldFontResolver;
    private final Function<Float, Font> semiBoldFontResolver;
    private final Function<Float, Font> symbolFontResolver;
    private final ItemIconSetter itemIconSetter;
    private final ItemPageHandlerAttacher itemPageHandlerAttacher;
    private final IntPredicate bookmarkedPredicate;
    private final IntConsumer bookmarkToggler;
    private final BooleanSupplier showBookmarkedOnly;
    private final Runnable renderItems;
    private final IntConsumer hiddenItemHandler;
    private final AgePairRegistrar agePairRegistrar;
    private final CountdownLabelRegistrar countdownLabelRegistrar;
    private final Consumer<Component> wheelForwarderInstaller;

    FlipHubItemCardBuilderPluginHooks(
        Function<Float, Font> fontResolver,
        Function<Float, Font> boldFontResolver,
        Function<Float, Font> semiBoldFontResolver,
        Function<Float, Font> symbolFontResolver,
        ItemIconSetter itemIconSetter,
        ItemPageHandlerAttacher itemPageHandlerAttacher,
        IntPredicate bookmarkedPredicate,
        IntConsumer bookmarkToggler,
        BooleanSupplier showBookmarkedOnly,
        Runnable renderItems,
        IntConsumer hiddenItemHandler,
        AgePairRegistrar agePairRegistrar,
        CountdownLabelRegistrar countdownLabelRegistrar,
        Consumer<Component> wheelForwarderInstaller
    ) {
        this.fontResolver = fontResolver;
        this.boldFontResolver = boldFontResolver;
        this.semiBoldFontResolver = semiBoldFontResolver;
        this.symbolFontResolver = symbolFontResolver;
        this.itemIconSetter = itemIconSetter;
        this.itemPageHandlerAttacher = itemPageHandlerAttacher;
        this.bookmarkedPredicate = bookmarkedPredicate;
        this.bookmarkToggler = bookmarkToggler;
        this.showBookmarkedOnly = showBookmarkedOnly;
        this.renderItems = renderItems;
        this.hiddenItemHandler = hiddenItemHandler;
        this.agePairRegistrar = agePairRegistrar;
        this.countdownLabelRegistrar = countdownLabelRegistrar;
        this.wheelForwarderInstaller = wheelForwarderInstaller;
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
    public Font fontSymbol(float size) {
        return symbolFontResolver != null ? symbolFontResolver.apply(size) : null;
    }

    @Override
    public void setItemIcon(JLabel label, int itemId) {
        if (itemIconSetter != null) {
            itemIconSetter.set(label, itemId);
        }
    }

    @Override
    public void attachOpenItemPageHandler(JComponent component, int itemId, String itemName) {
        if (itemPageHandlerAttacher != null) {
            itemPageHandlerAttacher.attach(component, itemId, itemName);
        }
    }

    @Override
    public boolean isBookmarked(int itemId) {
        return bookmarkedPredicate != null && bookmarkedPredicate.test(itemId);
    }

    @Override
    public void toggleBookmark(int itemId) {
        if (bookmarkToggler != null) {
            bookmarkToggler.accept(itemId);
        }
    }

    @Override
    public boolean isShowBookmarkedOnly() {
        return showBookmarkedOnly != null && showBookmarkedOnly.getAsBoolean();
    }

    @Override
    public void renderItems() {
        if (renderItems != null) {
            renderItems.run();
        }
    }

    @Override
    public void hideItem(int itemId) {
        if (hiddenItemHandler != null) {
            hiddenItemHandler.accept(itemId);
        }
    }

    @Override
    public void registerAgePair(Long buyTimestampMs, Long sellTimestampMs, LineComponents buyLine, LineComponents sellLine) {
        if (agePairRegistrar != null) {
            agePairRegistrar.register(buyTimestampMs, sellTimestampMs, buyLine, sellLine);
        }
    }

    @Override
    public void registerCountdownLabel(JLabel label, Long remainingMs, long asOfMs) {
        if (countdownLabelRegistrar != null) {
            countdownLabelRegistrar.register(label, remainingMs, asOfMs);
        }
    }

    @Override
    public void installWheelForwarder(Component component) {
        if (wheelForwarderInstaller != null) {
            wheelForwarderInstaller.accept(component);
        }
    }
}
