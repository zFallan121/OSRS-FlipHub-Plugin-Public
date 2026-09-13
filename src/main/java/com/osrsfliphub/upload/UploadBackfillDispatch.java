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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class UploadBackfillDispatch {
    private final BackfillRetryScheduler backfillRetryScheduler;
    private final AtomicBoolean flushInFlight = new AtomicBoolean(false);
    private final AtomicBoolean accountwideSyncInFlight = new AtomicBoolean(false);

    @Inject
    UploadBackfillDispatch(BackfillRetryScheduler backfillRetryScheduler) {
        this.backfillRetryScheduler = backfillRetryScheduler;
    }

    private boolean executeIo(Runnable task) {
        return Access.plugin().executeIo(task);
    }

    private void flushEvents() {
        Bridge.get(UploadEventDispatch.class).flushEvents(
            Access.plugin().apiClient,
            Access.plugin().config,
            GeLifecyclePlugin.log);
    }

    private void syncAccountwideSummaryIfNeeded() {
        SummaryUploader uploader = Bridge.get(SummaryUploader.class);
        if (uploader != null) {
            uploader.syncIfNeeded(Access.plugin().apiClient, Access.plugin().config);
        }
    }

    private void attemptAccountwideBackfillIfNeeded() {
        Bridge.get(BackfillExecution.class).attemptIfNeeded();
    }

    void requestEventFlush() {
        if (!flushInFlight.compareAndSet(false, true)) {
            return;
        }
        // The finally that lowers the flag lives inside the task, so a refused task leaves it
        // raised for good and event upload stops for the rest of the client session. The
        // window is real: shutDown stops the pools before the plugin's references are nulled,
        // so the two-second flush can arrive just after they close.
        if (!executeIo(() -> {
            try {
                flushEvents();
            } finally {
                flushInFlight.set(false);
            }
        })) {
            flushInFlight.set(false);
        }
    }

    void requestAccountwideSync() {
        if (!accountwideSyncInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!executeIo(() -> {
            try {
                syncAccountwideSummaryIfNeeded();
            } finally {
                accountwideSyncInFlight.set(false);
            }
        })) {
            accountwideSyncInFlight.set(false);
        }
    }

    void requestBackfillAttempt(ScheduledExecutorService scheduler, long delaySeconds, boolean resetBackoff) {
        if (backfillRetryScheduler == null) {
            return;
        }
        backfillRetryScheduler.requestAttempt(
            scheduler,
            delaySeconds,
            resetBackoff,
            () -> executeIo(this::attemptAccountwideBackfillIfNeeded)
        );
    }

    void scheduleBackfillRetry(ScheduledExecutorService scheduler) {
        if (backfillRetryScheduler == null) {
            return;
        }
        backfillRetryScheduler.scheduleRetry(
            scheduler,
            () -> executeIo(this::attemptAccountwideBackfillIfNeeded)
        );
    }

    void resetBackfillRetryState() {
        if (backfillRetryScheduler == null) {
            return;
        }
        backfillRetryScheduler.reset();
    }
}
