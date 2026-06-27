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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;

@Singleton
final class AccountwideBackfillExecutionService {
    interface Hooks {
        boolean isClientLoggedIn();
        boolean isLinked();
        boolean isBackfillReady();
        long nowMs();
        void requestBackfillAttempt(long delaySeconds, boolean resetBackoff);
        AccountwideBackfillCoordinator.Result runBackfillCycle();
        void scheduleBackfillRetry();
    }

    private final long backfillMinIntervalMs;
    private final Hooks hooks;
    private final AtomicBoolean backfillInFlight = new AtomicBoolean(false);
    private volatile long lastBackfillAttemptMs;

    @Inject
    AccountwideBackfillExecutionService(Client client, ApiClient apiClient, ConfigManager configManager) {
        this(GeLifecyclePluginConstants.BACKFILL_MIN_INTERVAL_MS, productionHooks(client, apiClient, configManager));
    }

    AccountwideBackfillExecutionService(long backfillMinIntervalMs, Hooks hooks) {
        this.backfillMinIntervalMs = Math.max(0L, backfillMinIntervalMs);
        this.hooks = hooks;
    }

    private static UploadBackfillDispatchService uploadBackfillDispatch() {
        return PluginAccess.plugin().getUploadRuntimeServices().getUploadBackfillDispatchService();
    }

    private static Hooks productionHooks(Client client, ApiClient apiClient, ConfigManager configManager) {
        return new Hooks() {
            @Override
            public boolean isClientLoggedIn() {
                return client != null && client.getGameState() == GameState.LOGGED_IN;
            }

            @Override
            public boolean isLinked() {
                ProfileSelectionPresentationFacadeService service = PluginAccess.plugin()
                    .getProfileSelectionServices().getProfileSelectionPresentationFacadeService();
                return service != null && service.isLinked();
            }

            @Override
            public boolean isBackfillReady() {
                return apiClient != null && configManager != null;
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }

            @Override
            public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
                UploadBackfillDispatchService service = uploadBackfillDispatch();
                if (service != null) {
                    service.requestBackfillAttempt(PluginAccess.plugin().scheduler, delaySeconds, resetBackoff);
                }
            }

            @Override
            public AccountwideBackfillCoordinator.Result runBackfillCycle() {
                AccountwideBackfillCoordinator coordinator = PluginAccess.plugin()
                    .getBackfillServices().getBackfillMarketServices().getAccountwideBackfillCoordinator();
                return coordinator != null ? coordinator.runCycle() : null;
            }

            @Override
            public void scheduleBackfillRetry() {
                UploadBackfillDispatchService service = uploadBackfillDispatch();
                if (service != null) {
                    service.scheduleBackfillRetry(PluginAccess.plugin().scheduler);
                }
            }
        };
    }

    void attemptIfNeeded() {
        boolean shouldRetry = false;
        if (hooks == null) {
            return;
        }
        if (!hooks.isClientLoggedIn() || !hooks.isLinked() || !hooks.isBackfillReady()) {
            return;
        }
        long nowMs = hooks.nowMs();
        long elapsedMs = nowMs - lastBackfillAttemptMs;
        if (elapsedMs >= 0 && elapsedMs < backfillMinIntervalMs) {
            long remainingMs = backfillMinIntervalMs - elapsedMs;
            long delaySeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(remainingMs));
            hooks.requestBackfillAttempt(delaySeconds, false);
            return;
        }
        if (!backfillInFlight.compareAndSet(false, true)) {
            return;
        }
        lastBackfillAttemptMs = nowMs;
        try {
            AccountwideBackfillCoordinator.Result result = hooks.runBackfillCycle();
            shouldRetry = result != null && result.shouldRetry;
        } finally {
            backfillInFlight.set(false);
            if (shouldRetry && hooks.isLinked()) {
                hooks.scheduleBackfillRetry();
            }
        }
    }
}
