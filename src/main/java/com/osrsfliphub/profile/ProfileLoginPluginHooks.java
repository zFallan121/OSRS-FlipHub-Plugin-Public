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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

final class ProfileLoginPluginHooks implements ProfileLoginService.Hooks {
    private final Map<Long, String> profileDisplayNames;
    private final Consumer<Runnable> executeAsync;
    private final LongConsumer loadLocalTradesAsync;
    private final Runnable persistProfileSelectionState;
    private final Runnable updateProfileOptionsUi;
    private final Runnable updateProfileHeader;

    ProfileLoginPluginHooks(
        Map<Long, String> profileDisplayNames,
        Consumer<Runnable> executeAsync,
        LongConsumer loadLocalTradesAsync,
        Runnable persistProfileSelectionState,
        Runnable updateProfileOptionsUi,
        Runnable updateProfileHeader
    ) {
        this.profileDisplayNames = profileDisplayNames;
        this.executeAsync = executeAsync;
        this.loadLocalTradesAsync = loadLocalTradesAsync;
        this.persistProfileSelectionState = persistProfileSelectionState;
        this.updateProfileOptionsUi = updateProfileOptionsUi;
        this.updateProfileHeader = updateProfileHeader;
    }

    @Override
    public void putProfileDisplayName(long accountHash, String displayName) {
        if (profileDisplayNames == null || accountHash <= 0 || displayName == null || displayName.trim().isEmpty()) {
            return;
        }
        profileDisplayNames.put(accountHash, displayName.trim());
    }

    @Override
    public void executeAsync(Runnable task) {
        if (executeAsync != null) {
            executeAsync.accept(task);
        }
    }

    @Override
    public void loadLocalTradesAsync(long accountHash) {
        if (loadLocalTradesAsync != null) {
            loadLocalTradesAsync.accept(accountHash);
        }
    }

    @Override
    public void persistProfileSelectionState() {
        if (persistProfileSelectionState != null) {
            persistProfileSelectionState.run();
        }
    }

    @Override
    public void updateProfileOptionsUi() {
        if (updateProfileOptionsUi != null) {
            updateProfileOptionsUi.run();
        }
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
    }
}
