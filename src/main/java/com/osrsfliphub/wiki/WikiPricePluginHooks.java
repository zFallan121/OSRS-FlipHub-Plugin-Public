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

import com.google.gson.Gson;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;

final class WikiPricePluginHooks implements WikiPriceFactoryService.RuntimeHooks {
    private final BooleanSupplier panelVisible;
    private final BooleanSupplier debugEnabled;
    private final Supplier<Logger> loggerSupplier;
    private final OkHttpClient httpClient;
    private final Gson gson;

    WikiPricePluginHooks(
        BooleanSupplier panelVisible,
        BooleanSupplier debugEnabled,
        Supplier<Logger> loggerSupplier,
        OkHttpClient httpClient,
        Gson gson
    ) {
        this.panelVisible = panelVisible;
        this.debugEnabled = debugEnabled;
        this.loggerSupplier = loggerSupplier;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public boolean isPanelVisible() {
        return panelVisible != null && panelVisible.getAsBoolean();
    }

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled != null && debugEnabled.getAsBoolean();
    }

    @Override
    public void logDebug(String message) {
        if (message == null || debugEnabled == null || !debugEnabled.getAsBoolean()) {
            return;
        }
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (logger != null) {
            logger.debug(message);
        }
    }

    @Override
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public Gson getGson() {
        return gson;
    }
}
