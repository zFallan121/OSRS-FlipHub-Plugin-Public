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

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class WebsiteStatsWipePluginHooks implements WebsiteStatsWipeService.Hooks {
    @FunctionalInterface
    interface WipeStatsInvoker {
        ApiClient.WipeStatsResponse invoke(String sessionToken, String signingSecret) throws Exception;
    }

    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<ExecutorService> ioExecutorSupplier;
    private final Consumer<Runnable> runOnClientThreadConsumer;
    private final WipeStatsInvoker wipeStatsInvoker;
    private final Consumer<String> showErrorConsumer;
    private final Consumer<String> pushGameMessageConsumer;
    private final Runnable triggerStatsRefresh;

    WebsiteStatsWipePluginHooks(
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<ExecutorService> ioExecutorSupplier,
        Consumer<Runnable> runOnClientThreadConsumer,
        WipeStatsInvoker wipeStatsInvoker,
        Consumer<String> showErrorConsumer,
        Consumer<String> pushGameMessageConsumer,
        Runnable triggerStatsRefresh
    ) {
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.runOnClientThreadConsumer = runOnClientThreadConsumer;
        this.wipeStatsInvoker = wipeStatsInvoker;
        this.showErrorConsumer = showErrorConsumer;
        this.pushGameMessageConsumer = pushGameMessageConsumer;
        this.triggerStatsRefresh = triggerStatsRefresh;
    }

    @Override
    public boolean isLinked() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null && service.isLinked();
    }

    @Override
    public LinkSessionGuardService.Credentials resolveLinkedCredentials() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null ? service.resolveLinkedCredentials() : null;
    }

    @Override
    public boolean hasIoExecutor() {
        return ioExecutorSupplier != null && ioExecutorSupplier.get() != null;
    }

    @Override
    public void executeIo(Runnable task) {
        ExecutorService executor = ioExecutorSupplier != null ? ioExecutorSupplier.get() : null;
        if (executor != null && task != null) {
            executor.execute(task);
        }
    }

    @Override
    public void runOnClientThread(Runnable task) {
        if (task != null && runOnClientThreadConsumer != null) {
            runOnClientThreadConsumer.accept(task);
        }
    }

    @Override
    public ApiClient.WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws Exception {
        return wipeStatsInvoker != null ? wipeStatsInvoker.invoke(sessionToken, signingSecret) : null;
    }

    @Override
    public void showError(String message) {
        if (showErrorConsumer != null) {
            showErrorConsumer.accept(message);
        }
    }

    @Override
    public void pushGameMessage(String message) {
        if (pushGameMessageConsumer != null) {
            pushGameMessageConsumer.accept(message);
        }
    }

    @Override
    public void triggerStatsRefresh() {
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionFacadeService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }
}
