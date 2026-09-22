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
import javax.inject.*;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

@Singleton
final class BackfillExecution {
    private static final long BACKFILL_MIN_INTERVAL_MS = 60_000L;

    private final UploadBackfillDispatch uploadBackfillDispatch;
    private final AccountwideBackfillCoordinator accountwideBackfillCoordinator;
    private final ProfileSelectionPresentation profileSelectionPresentation;
    private final long backfillMinIntervalMs;
    private final Client client;
    private final ApiClient apiClient;
    private final ConfigManager configManager;
    private final AtomicBoolean backfillInFlight = new AtomicBoolean(false);
    private volatile long lastBackfillAttemptMs;

    @Inject
    BackfillExecution(
        Client client,
        ApiClient apiClient,
        ConfigManager configManager,
        ProfileSelectionPresentation profileSelectionPresentation,
        UploadBackfillDispatch uploadBackfillDispatch,
        AccountwideBackfillCoordinator accountwideBackfillCoordinator
    ) {
        this.uploadBackfillDispatch = uploadBackfillDispatch;
        this.accountwideBackfillCoordinator = accountwideBackfillCoordinator;
        this.profileSelectionPresentation = profileSelectionPresentation;
        this.backfillMinIntervalMs = Math.max(0L, BACKFILL_MIN_INTERVAL_MS);
        this.client = client;
        this.apiClient = apiClient;
        this.configManager = configManager;
    }

    void attemptIfNeeded() {
        boolean shouldRetry = false;
        if (!Access.loggedIn(client) || !profileSelectionPresentation.isLinked() || !hasApiAccess()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long elapsedMs = nowMs - lastBackfillAttemptMs;
        if (elapsedMs >= 0 && elapsedMs < backfillMinIntervalMs) {
            long remainingMs = backfillMinIntervalMs - elapsedMs;
            long delaySeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(remainingMs));
            uploadBackfillDispatch.requestBackfillAttempt(Access.plugin().scheduler, delaySeconds, false);
            return;
        }
        if (!backfillInFlight.compareAndSet(false, true)) {
            return;
        }
        lastBackfillAttemptMs = nowMs;
        try {
            AccountwideBackfillCoordinator.Result result = accountwideBackfillCoordinator.runCycle();
            shouldRetry = result != null && result.shouldRetry;
        } finally {
            backfillInFlight.set(false);
            if (shouldRetry && profileSelectionPresentation.isLinked()) {
                uploadBackfillDispatch.scheduleBackfillRetry(Access.plugin().scheduler);
            }
        }
    }

    /** Whether there is anything to upload with: a client for the calls, and config to sign them. */
    private boolean hasApiAccess() {
        return configManager != null;
    }

}
