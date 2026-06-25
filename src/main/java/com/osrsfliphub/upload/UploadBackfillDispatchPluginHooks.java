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

import java.util.function.Consumer;
import java.util.function.Supplier;

final class UploadBackfillDispatchPluginHooks implements UploadBackfillDispatchService.Hooks {
    private final Consumer<Runnable> ioExecutor;
    private final Runnable flushEventsAction;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<PluginConfig> pluginConfigSupplier;
    private final AccountwideSummaryUploader.Hooks accountwideSummaryHooks;
    private final Runnable attemptAccountwideBackfillAction;

    UploadBackfillDispatchPluginHooks(
        Consumer<Runnable> ioExecutor,
        Runnable flushEventsAction,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<PluginConfig> pluginConfigSupplier,
        AccountwideSummaryUploader.Hooks accountwideSummaryHooks,
        Runnable attemptAccountwideBackfillAction
    ) {
        this.ioExecutor = ioExecutor;
        this.flushEventsAction = flushEventsAction;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.pluginConfigSupplier = pluginConfigSupplier;
        this.accountwideSummaryHooks = accountwideSummaryHooks;
        this.attemptAccountwideBackfillAction = attemptAccountwideBackfillAction;
    }

    @Override
    public void executeIo(Runnable task) {
        if (ioExecutor != null) {
            ioExecutor.accept(task);
        }
    }

    @Override
    public void flushEvents() {
        if (flushEventsAction != null) {
            flushEventsAction.run();
        }
    }

    @Override
    public void syncAccountwideSummaryIfNeeded() {
        AccountwideSummaryUploader uploader = accountwideSummaryUploaderSupplier != null
            ? accountwideSummaryUploaderSupplier.get()
            : null;
        ApiClient apiClient = apiClientSupplier != null ? apiClientSupplier.get() : null;
        PluginConfig config = pluginConfigSupplier != null ? pluginConfigSupplier.get() : null;
        if (uploader == null || apiClient == null || config == null) {
            return;
        }
        uploader.syncIfNeeded(apiClient, config, accountwideSummaryHooks);
    }

    @Override
    public void attemptAccountwideBackfillIfNeeded() {
        if (attemptAccountwideBackfillAction != null) {
            attemptAccountwideBackfillAction.run();
        }
    }
}
