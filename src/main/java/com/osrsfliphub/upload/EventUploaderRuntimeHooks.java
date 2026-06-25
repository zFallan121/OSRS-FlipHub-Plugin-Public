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

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class EventUploaderRuntimeHooks implements EventUploader.Hooks {
    @FunctionalInterface
    interface UploadSuccessRecorder {
        void accept(int uploadedCount, int statusCode);
    }

    @FunctionalInterface
    interface UploadFailureRecorder {
        void accept(Integer statusCode, String errorMessage, boolean dropped, int droppedCount);
    }

    private final BooleanSupplier isClientLoggedIn;
    private final IntSupplier getPendingUploadEvents;
    private final Supplier<GeEvent> dequeueEvent;
    private final Consumer<List<GeEvent>> requeue;
    private final Predicate<String> attemptRefresh;
    private final Runnable clearSession;
    private final BooleanSupplier isPanelVisible;
    private final Runnable updateProfileHeader;
    private final Consumer<String> setUploadBlocked;
    private final Runnable recordUploadAttempt;
    private final UploadSuccessRecorder recordUploadSuccess;
    private final UploadFailureRecorder recordUploadFailure;
    private final Runnable updateUploadDiagnosticsUi;

    EventUploaderRuntimeHooks(BooleanSupplier isClientLoggedIn,
                              IntSupplier getPendingUploadEvents,
                              Supplier<GeEvent> dequeueEvent,
                              Consumer<List<GeEvent>> requeue,
                              Predicate<String> attemptRefresh,
                              Runnable clearSession,
                              BooleanSupplier isPanelVisible,
                              Runnable updateProfileHeader,
                              Consumer<String> setUploadBlocked,
                              Runnable recordUploadAttempt,
                              UploadSuccessRecorder recordUploadSuccess,
                              UploadFailureRecorder recordUploadFailure,
                              Runnable updateUploadDiagnosticsUi) {
        this.isClientLoggedIn = isClientLoggedIn;
        this.getPendingUploadEvents = getPendingUploadEvents;
        this.dequeueEvent = dequeueEvent;
        this.requeue = requeue;
        this.attemptRefresh = attemptRefresh;
        this.clearSession = clearSession;
        this.isPanelVisible = isPanelVisible;
        this.updateProfileHeader = updateProfileHeader;
        this.setUploadBlocked = setUploadBlocked;
        this.recordUploadAttempt = recordUploadAttempt;
        this.recordUploadSuccess = recordUploadSuccess;
        this.recordUploadFailure = recordUploadFailure;
        this.updateUploadDiagnosticsUi = updateUploadDiagnosticsUi;
    }

    @Override
    public boolean isClientLoggedIn() {
        return isClientLoggedIn != null && isClientLoggedIn.getAsBoolean();
    }

    @Override
    public int getPendingUploadEvents() {
        return getPendingUploadEvents != null ? getPendingUploadEvents.getAsInt() : 0;
    }

    @Override
    public GeEvent dequeueEvent() {
        return dequeueEvent != null ? dequeueEvent.get() : null;
    }

    @Override
    public void requeue(List<GeEvent> batch) {
        if (requeue != null) {
            requeue.accept(batch);
        }
    }

    @Override
    public boolean attemptRefresh(String currentToken) {
        return attemptRefresh != null && attemptRefresh.test(currentToken);
    }

    @Override
    public void clearSession() {
        if (clearSession != null) {
            clearSession.run();
        }
    }

    @Override
    public boolean isPanelVisible() {
        return isPanelVisible != null && isPanelVisible.getAsBoolean();
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
    }

    @Override
    public void setUploadBlocked(String reason) {
        if (setUploadBlocked != null) {
            setUploadBlocked.accept(reason);
        }
    }

    @Override
    public void recordUploadAttempt() {
        if (recordUploadAttempt != null) {
            recordUploadAttempt.run();
        }
    }

    @Override
    public void recordUploadSuccess(int uploadedCount, int statusCode) {
        if (recordUploadSuccess != null) {
            recordUploadSuccess.accept(uploadedCount, statusCode);
        }
    }

    @Override
    public void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        if (recordUploadFailure != null) {
            recordUploadFailure.accept(statusCode, errorMessage, dropped, droppedCount);
        }
    }

    @Override
    public void updateUploadDiagnosticsUi() {
        if (updateUploadDiagnosticsUi != null) {
            updateUploadDiagnosticsUi.run();
        }
    }
}
