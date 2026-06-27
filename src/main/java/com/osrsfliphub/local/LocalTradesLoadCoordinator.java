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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalTradesLoadCoordinator {
    interface Hooks {
        long nowMs();
        long resolveAccountHash();
        void invokeOnClientThread(Runnable task);
        void scheduleAsync(ScheduledExecutorService scheduler, Runnable task);
        void ensureProfileLoaded(long accountHash);
        void markLocalTradesLoaded();
    }

    static final class State {
        private long lastAttemptMs;

        long getLastAttemptMs() {
            return lastAttemptMs;
        }

        void setLastAttemptMs(long lastAttemptMs) {
            this.lastAttemptMs = Math.max(0L, lastAttemptMs);
        }
    }

    private final long retryMs;
    private final Hooks hooks;

    @Inject
    LocalTradesLoadCoordinator() {
        this(GeLifecyclePluginConstants.LOCAL_TRADES_LOAD_RETRY_MS, new Hooks() {
            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }

            @Override
            public long resolveAccountHash() {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                return service != null ? service.resolveAccountHash() : -1L;
            }

            @Override
            public void invokeOnClientThread(Runnable task) {
                PluginAccess.plugin().invokeOnClientThread(task);
            }

            @Override
            public void scheduleAsync(ScheduledExecutorService scheduler, Runnable task) {
                PluginAccess.plugin().executeOnScheduler(scheduler, task);
            }

            @Override
            public void ensureProfileLoaded(long accountHash) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoadedBoxed(accountHash);
            }

            @Override
            public void markLocalTradesLoaded() {
                PluginAccess.plugin().getLocalTradesRuntimeService().markLocalTradesLoadedForLogin();
            }
        });
    }

    LocalTradesLoadCoordinator(long retryMs, Hooks hooks) {
        this.retryMs = Math.max(0L, retryMs);
        this.hooks = hooks;
    }

    void ensureLocalTradesLoaded(long accountKey) {
        if (accountKey <= 0 || hooks == null) {
            return;
        }
        hooks.ensureProfileLoaded(accountKey);
        hooks.markLocalTradesLoaded();
    }

    void scheduleLocalTradesLoad(State state, ScheduledExecutorService scheduler, boolean hasClientThread) {
        if (state == null || hooks == null) {
            return;
        }
        long nowMs = hooks.nowMs();
        if (nowMs - state.getLastAttemptMs() < retryMs) {
            return;
        }
        state.setLastAttemptMs(nowMs);

        if (!hasClientThread || scheduler == null || scheduler.isShutdown()) {
            attemptLocalTradesLoad();
            return;
        }

        hooks.invokeOnClientThread(() -> {
            long accountHash = hooks.resolveAccountHash();
            if (accountHash <= 0) {
                return;
            }
            hooks.scheduleAsync(scheduler, () -> loadLocalTradesAsync(accountHash));
        });
    }

    void attemptLocalTradesLoad() {
        if (hooks == null) {
            return;
        }
        long accountHash = hooks.resolveAccountHash();
        if (accountHash <= 0) {
            return;
        }
        loadLocalTradesAsync(accountHash);
    }

    void loadLocalTradesAsync(long accountHash) {
        if (hooks == null || accountHash <= 0) {
            return;
        }
        hooks.ensureProfileLoaded(accountHash);
        hooks.markLocalTradesLoaded();
    }
}
