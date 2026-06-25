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

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class LocalTradesLoadCoordinatorPluginHooks implements LocalTradesLoadCoordinator.Hooks {
    private final LongSupplier nowMs;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Consumer<Runnable> invokeOnClientThread;
    private final LongConsumerWithScheduler scheduleAsync;
    private final Consumer<Long> ensureProfileLoaded;
    private final Runnable markLocalTradesLoaded;

    @FunctionalInterface
    interface LongConsumerWithScheduler {
        void schedule(ScheduledExecutorService scheduler, Runnable task);
    }

    LocalTradesLoadCoordinatorPluginHooks(
        LongSupplier nowMs,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Consumer<Runnable> invokeOnClientThread,
        LongConsumerWithScheduler scheduleAsync,
        Consumer<Long> ensureProfileLoaded,
        Runnable markLocalTradesLoaded
    ) {
        this.nowMs = nowMs;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.invokeOnClientThread = invokeOnClientThread;
        this.scheduleAsync = scheduleAsync;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.markLocalTradesLoaded = markLocalTradesLoaded;
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : 0L;
    }

    @Override
    public long resolveAccountHash() {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.resolveAccountHash() : -1L;
    }

    @Override
    public void invokeOnClientThread(Runnable task) {
        if (invokeOnClientThread != null) {
            invokeOnClientThread.accept(task);
        }
    }

    @Override
    public void scheduleAsync(ScheduledExecutorService scheduler, Runnable task) {
        if (scheduleAsync != null) {
            scheduleAsync.schedule(scheduler, task);
        }
    }

    @Override
    public void ensureProfileLoaded(long accountHash) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(accountHash);
        }
    }

    @Override
    public void markLocalTradesLoaded() {
        if (markLocalTradesLoaded != null) {
            markLocalTradesLoaded.run();
        }
    }
}
