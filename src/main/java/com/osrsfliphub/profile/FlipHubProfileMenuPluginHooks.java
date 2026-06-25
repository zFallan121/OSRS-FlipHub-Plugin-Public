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
import java.util.function.Consumer;
import java.util.function.Function;

final class FlipHubProfileMenuPluginHooks implements FlipHubProfileMenuCoordinator.Hooks {
    private final Function<Float, Font> fontResolver;
    private final Function<Float, Font> semiBoldFontResolver;
    private final Consumer<String> profileSelected;
    private final Runnable manageDataSelected;

    FlipHubProfileMenuPluginHooks(
        Function<Float, Font> fontResolver,
        Function<Float, Font> semiBoldFontResolver,
        Consumer<String> profileSelected,
        Runnable manageDataSelected
    ) {
        this.fontResolver = fontResolver;
        this.semiBoldFontResolver = semiBoldFontResolver;
        this.profileSelected = profileSelected;
        this.manageDataSelected = manageDataSelected;
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
    public void onProfileSelected(String key) {
        if (profileSelected != null) {
            profileSelected.accept(key);
        }
    }

    @Override
    public void onManageData() {
        if (manageDataSelected != null) {
            manageDataSelected.run();
        }
    }
}
