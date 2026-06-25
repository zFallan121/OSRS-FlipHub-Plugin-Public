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

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class LinkAttemptRuntimeHooks implements LinkAttemptService.Hooks {
    interface LinkDeviceInvoker {
        ApiClient.LinkResponse invoke(String licenseKey, String deviceId) throws IOException;
    }

    interface RetryScheduler {
        void schedule(Runnable task, long delaySeconds);
    }

    private final BooleanSupplier clientLoggedIn;
    private final Supplier<String> currentDeviceId;
    private final LinkDeviceInvoker linkDeviceInvoker;
    private final BiConsumer<String, String> persistLinkedSession;
    private final Runnable resetAccountwideUploadSnapshot;
    private final Runnable resetUploadDiagnosticsState;
    private final Runnable updateUploadDiagnosticsUi;
    private final BiConsumer<Long, Boolean> requestBackfillAttempt;
    private final LongConsumer scheduleAccountwideSync;
    private final Runnable refreshPanelData;
    private final Runnable updateProfileHeader;
    private final Predicate<Throwable> timeoutException;
    private final Runnable logTimeout;
    private final Consumer<Throwable> logFailure;
    private final Consumer<Runnable> executeIo;
    private final RetryScheduler retryScheduler;

    LinkAttemptRuntimeHooks(BooleanSupplier clientLoggedIn,
                            Supplier<String> currentDeviceId,
                            LinkDeviceInvoker linkDeviceInvoker,
                            BiConsumer<String, String> persistLinkedSession,
                            Runnable resetAccountwideUploadSnapshot,
                            Runnable resetUploadDiagnosticsState,
                            Runnable updateUploadDiagnosticsUi,
                            BiConsumer<Long, Boolean> requestBackfillAttempt,
                            LongConsumer scheduleAccountwideSync,
                            Runnable refreshPanelData,
                            Runnable updateProfileHeader,
                            Predicate<Throwable> timeoutException,
                            Runnable logTimeout,
                            Consumer<Throwable> logFailure,
                            Consumer<Runnable> executeIo,
                            RetryScheduler retryScheduler) {
        this.clientLoggedIn = clientLoggedIn;
        this.currentDeviceId = currentDeviceId;
        this.linkDeviceInvoker = linkDeviceInvoker;
        this.persistLinkedSession = persistLinkedSession;
        this.resetAccountwideUploadSnapshot = resetAccountwideUploadSnapshot;
        this.resetUploadDiagnosticsState = resetUploadDiagnosticsState;
        this.updateUploadDiagnosticsUi = updateUploadDiagnosticsUi;
        this.requestBackfillAttempt = requestBackfillAttempt;
        this.scheduleAccountwideSync = scheduleAccountwideSync;
        this.refreshPanelData = refreshPanelData;
        this.updateProfileHeader = updateProfileHeader;
        this.timeoutException = timeoutException;
        this.logTimeout = logTimeout;
        this.logFailure = logFailure;
        this.executeIo = executeIo;
        this.retryScheduler = retryScheduler;
    }

    @Override
    public boolean isClientLoggedIn() {
        return clientLoggedIn != null && clientLoggedIn.getAsBoolean();
    }

    @Override
    public String currentDeviceId() {
        return currentDeviceId != null ? currentDeviceId.get() : null;
    }

    @Override
    public ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId) throws IOException {
        if (linkDeviceInvoker == null) {
            return null;
        }
        return linkDeviceInvoker.invoke(licenseKey, deviceId);
    }

    @Override
    public void persistLinkedSession(String sessionToken, String signingSecret) {
        if (persistLinkedSession != null) {
            persistLinkedSession.accept(sessionToken, signingSecret);
        }
    }

    @Override
    public void resetAccountwideUploadSnapshot() {
        if (resetAccountwideUploadSnapshot != null) {
            resetAccountwideUploadSnapshot.run();
        }
    }

    @Override
    public void resetUploadDiagnosticsState() {
        if (resetUploadDiagnosticsState != null) {
            resetUploadDiagnosticsState.run();
        }
    }

    @Override
    public void updateUploadDiagnosticsUi() {
        if (updateUploadDiagnosticsUi != null) {
            updateUploadDiagnosticsUi.run();
        }
    }

    @Override
    public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
        if (requestBackfillAttempt != null) {
            requestBackfillAttempt.accept(delaySeconds, resetBackoff);
        }
    }

    @Override
    public void scheduleAccountwideSync(long delaySeconds) {
        if (scheduleAccountwideSync != null) {
            scheduleAccountwideSync.accept(delaySeconds);
        }
    }

    @Override
    public void refreshPanelData() {
        if (refreshPanelData != null) {
            refreshPanelData.run();
        }
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
    }

    @Override
    public boolean isTimeoutException(Throwable ex) {
        return timeoutException != null && timeoutException.test(ex);
    }

    @Override
    public void logTimeout() {
        if (logTimeout != null) {
            logTimeout.run();
        }
    }

    @Override
    public void logFailure(Throwable ex) {
        if (logFailure != null) {
            logFailure.accept(ex);
        }
    }

    @Override
    public void executeIo(Runnable task) {
        if (executeIo != null) {
            executeIo.accept(task);
        }
    }

    @Override
    public void scheduleRetry(Runnable task, long delaySeconds) {
        if (retryScheduler != null) {
            retryScheduler.schedule(task, delaySeconds);
        }
    }
}
