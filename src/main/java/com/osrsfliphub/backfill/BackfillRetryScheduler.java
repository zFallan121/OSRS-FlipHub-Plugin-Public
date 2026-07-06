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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class BackfillRetryScheduler {
    private final long baseRetryIntervalSeconds;
    private final long maxRetryIntervalSeconds;
    private final Object scheduleLock = new Object();
    private final AtomicLong retryDelaySeconds;
    private volatile ScheduledFuture<?> retryFuture;

    @Inject
    BackfillRetryScheduler() {
        this(GeLifecyclePluginConstants.BACKFILL_RETRY_INTERVAL_SECONDS,
            GeLifecyclePluginConstants.BACKFILL_RETRY_MAX_INTERVAL_SECONDS);
    }

    BackfillRetryScheduler(long baseRetryIntervalSeconds, long maxRetryIntervalSeconds) {
        this.baseRetryIntervalSeconds = Math.max(1L, baseRetryIntervalSeconds);
        this.maxRetryIntervalSeconds = Math.max(this.baseRetryIntervalSeconds, maxRetryIntervalSeconds);
        this.retryDelaySeconds = new AtomicLong(this.baseRetryIntervalSeconds);
    }

    void requestAttempt(ScheduledExecutorService scheduler, long delaySeconds, boolean resetBackoff, Runnable task) {
        if (scheduler == null || scheduler.isShutdown() || task == null) {
            return;
        }
        if (resetBackoff) {
            retryDelaySeconds.set(baseRetryIntervalSeconds);
        }
        long normalizedDelaySeconds = Math.max(0L, delaySeconds);
        synchronized (scheduleLock) {
            if (retryFuture != null && !retryFuture.isDone()) {
                long existingDelaySeconds = Math.max(0L, retryFuture.getDelay(TimeUnit.SECONDS));
                if (existingDelaySeconds <= normalizedDelaySeconds) {
                    return;
                }
                retryFuture.cancel(false);
            }
            retryFuture = scheduler.schedule(() -> {
                synchronized (scheduleLock) {
                    retryFuture = null;
                }
                task.run();
            }, normalizedDelaySeconds, TimeUnit.SECONDS);
        }
    }

    void scheduleRetry(ScheduledExecutorService scheduler, Runnable task) {
        long delaySeconds = retryDelaySeconds.getAndUpdate(previous -> {
            long base = previous > 0 ? previous : baseRetryIntervalSeconds;
            long doubled = base * 2L;
            return Math.max(baseRetryIntervalSeconds, Math.min(maxRetryIntervalSeconds, doubled));
        });
        if (delaySeconds <= 0) {
            delaySeconds = baseRetryIntervalSeconds;
        }
        requestAttempt(scheduler, delaySeconds, false, task);
    }

    void reset() {
        retryDelaySeconds.set(baseRetryIntervalSeconds);
        synchronized (scheduleLock) {
            if (retryFuture != null && !retryFuture.isDone()) {
                retryFuture.cancel(false);
            }
            retryFuture = null;
        }
    }
}
