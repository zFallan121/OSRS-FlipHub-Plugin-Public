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
import java.util.function.Predicate;

final class UploadEventDispatchPluginHooks implements UploadEventDispatchFacadeService.Hooks {
    private final BooleanSupplier clientLoggedInSupplier;
    private final Consumer<List<GeEvent>> requeueConsumer;
    private final Predicate<String> refreshAttempt;
    private final Runnable clearSessionAction;
    private final BooleanSupplier panelVisibleSupplier;
    private final Runnable updateProfileHeaderAction;
    private final Runnable clearUploadDiagnosticsTooltipAction;

    UploadEventDispatchPluginHooks(
        BooleanSupplier clientLoggedInSupplier,
        Consumer<List<GeEvent>> requeueConsumer,
        Predicate<String> refreshAttempt,
        Runnable clearSessionAction,
        BooleanSupplier panelVisibleSupplier,
        Runnable updateProfileHeaderAction,
        Runnable clearUploadDiagnosticsTooltipAction
    ) {
        this.clientLoggedInSupplier = clientLoggedInSupplier;
        this.requeueConsumer = requeueConsumer;
        this.refreshAttempt = refreshAttempt;
        this.clearSessionAction = clearSessionAction;
        this.panelVisibleSupplier = panelVisibleSupplier;
        this.updateProfileHeaderAction = updateProfileHeaderAction;
        this.clearUploadDiagnosticsTooltipAction = clearUploadDiagnosticsTooltipAction;
    }

    @Override
    public boolean isClientLoggedIn() {
        return clientLoggedInSupplier != null && clientLoggedInSupplier.getAsBoolean();
    }

    @Override
    public void requeue(List<GeEvent> batch) {
        if (requeueConsumer != null) {
            requeueConsumer.accept(batch);
        }
    }

    @Override
    public boolean attemptRefresh(String currentToken) {
        return refreshAttempt != null && refreshAttempt.test(currentToken);
    }

    @Override
    public void clearSession() {
        if (clearSessionAction != null) {
            clearSessionAction.run();
        }
    }

    @Override
    public boolean isPanelVisible() {
        return panelVisibleSupplier != null && panelVisibleSupplier.getAsBoolean();
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeaderAction != null) {
            updateProfileHeaderAction.run();
        }
    }

    @Override
    public void clearUploadDiagnosticsTooltip() {
        if (clearUploadDiagnosticsTooltipAction != null) {
            clearUploadDiagnosticsTooltipAction.run();
        }
    }
}
