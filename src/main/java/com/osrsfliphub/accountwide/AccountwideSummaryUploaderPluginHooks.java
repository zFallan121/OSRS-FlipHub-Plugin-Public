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

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class AccountwideSummaryUploaderPluginHooks implements AccountwideSummaryUploader.Hooks {
    private final BooleanSupplier clientFullyReadySupplier;
    private final Supplier<LocalStatsSnapshot> accountwideSnapshotSupplier;
    private final Predicate<String> refreshAttempt;
    private final Runnable clearSessionAction;

    AccountwideSummaryUploaderPluginHooks(
        BooleanSupplier clientFullyReadySupplier,
        Supplier<LocalStatsSnapshot> accountwideSnapshotSupplier,
        Predicate<String> refreshAttempt,
        Runnable clearSessionAction
    ) {
        this.clientFullyReadySupplier = clientFullyReadySupplier;
        this.accountwideSnapshotSupplier = accountwideSnapshotSupplier;
        this.refreshAttempt = refreshAttempt;
        this.clearSessionAction = clearSessionAction;
    }

    @Override
    public boolean isClientFullyReady() {
        return clientFullyReadySupplier != null && clientFullyReadySupplier.getAsBoolean();
    }

    @Override
    public LocalStatsSnapshot buildAccountwideSnapshot() {
        return accountwideSnapshotSupplier != null ? accountwideSnapshotSupplier.get() : null;
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
}
