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
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class AccountwideBackfillExecutionPluginHooks implements AccountwideBackfillExecutionService.Hooks {
    private final BooleanSupplier isClientLoggedIn;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<net.runelite.client.config.ConfigManager> configManagerSupplier;
    private final LongSupplier nowMs;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Supplier<AccountwideBackfillCoordinator> accountwideBackfillCoordinatorSupplier;

    AccountwideBackfillExecutionPluginHooks(
        BooleanSupplier isClientLoggedIn,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<net.runelite.client.config.ConfigManager> configManagerSupplier,
        LongSupplier nowMs,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<AccountwideBackfillCoordinator> accountwideBackfillCoordinatorSupplier
    ) {
        this.isClientLoggedIn = isClientLoggedIn;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.nowMs = nowMs;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.accountwideBackfillCoordinatorSupplier = accountwideBackfillCoordinatorSupplier;
    }

    @Override
    public boolean isClientLoggedIn() {
        return isClientLoggedIn != null && isClientLoggedIn.getAsBoolean();
    }

    @Override
    public boolean isLinked() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null && service.isLinked();
    }

    @Override
    public boolean isBackfillReady() {
        return apiClientSupplier != null && apiClientSupplier.get() != null
            && configManagerSupplier != null && configManagerSupplier.get() != null;
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : 0L;
    }

    @Override
    public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
        UploadBackfillDispatchService service = uploadBackfillDispatchServiceSupplier != null
            ? uploadBackfillDispatchServiceSupplier.get()
            : null;
        ScheduledExecutorService scheduler = schedulerSupplier != null ? schedulerSupplier.get() : null;
        if (service != null) {
            service.requestBackfillAttempt(scheduler, delaySeconds, resetBackoff);
        }
    }

    @Override
    public AccountwideBackfillCoordinator.Result runBackfillCycle() {
        AccountwideBackfillCoordinator coordinator = accountwideBackfillCoordinatorSupplier != null
            ? accountwideBackfillCoordinatorSupplier.get()
            : null;
        return coordinator != null ? coordinator.runCycle() : null;
    }

    @Override
    public void scheduleBackfillRetry() {
        UploadBackfillDispatchService service = uploadBackfillDispatchServiceSupplier != null
            ? uploadBackfillDispatchServiceSupplier.get()
            : null;
        ScheduledExecutorService scheduler = schedulerSupplier != null ? schedulerSupplier.get() : null;
        if (service != null) {
            service.scheduleBackfillRetry(scheduler);
        }
    }
}
