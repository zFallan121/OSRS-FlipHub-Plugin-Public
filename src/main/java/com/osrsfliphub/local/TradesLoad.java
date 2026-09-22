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
import javax.inject.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class TradesLoad {
    private static final long LOCAL_TRADES_LOAD_RETRY_MS = 1000L;

    private final TradeSession tradeSession;
    private final LocalTradesRuntime localTradesRuntime;

    static final class State {
        @Getter
        private long lastAttemptMs;

        void setLastAttemptMs(long lastAttemptMs) {
            this.lastAttemptMs = Math.max(0L, lastAttemptMs);
        }
    }

    private final long retryMs = Math.max(0L, LOCAL_TRADES_LOAD_RETRY_MS);

    void ensureLocalTradesLoaded(long accountKey) {
        if (accountKey <= 0) {
            return;
        }
        localTradesRuntime.ensureProfileLoaded(accountKey);
        localTradesRuntime.markLocalTradesLoadedForLogin();
    }

    void scheduleLocalTradesLoad(State state, ScheduledExecutorService scheduler, boolean hasClientThread) {
        if (state == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs - state.getLastAttemptMs() < retryMs) {
            return;
        }
        state.setLastAttemptMs(nowMs);

        if (!hasClientThread || scheduler == null || scheduler.isShutdown()) {
            attemptLocalTradesLoad();
            return;
        }

        Access.plugin().invokeOnClientThread(() -> {
            long accountHash = tradeSession.resolveAccountHash();
            if (accountHash <= 0) {
                return;
            }
            Access.plugin().executeOnScheduler(scheduler, () -> loadLocalTradesAsync(accountHash));
        });
    }

    void attemptLocalTradesLoad() {
        long accountHash = tradeSession.resolveAccountHash();
        if (accountHash <= 0) {
            return;
        }
        loadLocalTradesAsync(accountHash);
    }

    void loadLocalTradesAsync(long accountHash) {
        if (accountHash <= 0) {
            return;
        }
        ensureLocalTradesLoaded(accountHash);
    }
}
