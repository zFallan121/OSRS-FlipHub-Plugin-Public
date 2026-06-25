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

import java.util.function.Predicate;
import java.util.function.Supplier;

final class BackfillUploaderPluginHooks implements BackfillUploaderFactoryService.RuntimeHooks {
    private final Predicate<String> attemptRefresh;
    private final Runnable clearSession;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;

    BackfillUploaderPluginHooks(
        Predicate<String> attemptRefresh,
        Runnable clearSession,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier
    ) {
        this.attemptRefresh = attemptRefresh;
        this.clearSession = clearSession;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
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
    public void setUploadBlocked(String reason) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.markBlocked(reason);
        }
    }

    @Override
    public void recordUploadAttempt() {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.markAttempt();
        }
    }

    @Override
    public void recordUploadSuccess(int uploadedCount, int statusCode) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.markSuccess(uploadedCount, statusCode);
        }
    }

    @Override
    public void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.markFailure(statusCode, errorMessage, dropped, droppedCount);
        }
    }
}
