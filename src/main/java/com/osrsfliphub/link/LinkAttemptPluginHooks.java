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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class LinkAttemptPluginHooks implements LinkAttemptService.Hooks {
    private final BooleanSupplier isClientLoggedIn;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<ApiClient> apiClientSupplier;
    private final String username;
    private final String pluginVersion;
    private final Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Runnable refreshPanelData;
    private final Runnable updateProfileHeader;
    private final Predicate<Throwable> timeoutExceptionPredicate;
    private final Runnable logTimeout;
    private final Consumer<Throwable> logFailure;
    private final Consumer<Runnable> executeIoConsumer;

    LinkAttemptPluginHooks(
        BooleanSupplier isClientLoggedIn,
        Supplier<PluginConfig> configSupplier,
        Supplier<ApiClient> apiClientSupplier,
        String username,
        String pluginVersion,
        Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Runnable refreshPanelData,
        Runnable updateProfileHeader,
        Predicate<Throwable> timeoutExceptionPredicate,
        Runnable logTimeout,
        Consumer<Throwable> logFailure,
        Consumer<Runnable> executeIoConsumer
    ) {
        this.isClientLoggedIn = isClientLoggedIn;
        this.configSupplier = configSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.username = username;
        this.pluginVersion = pluginVersion;
        this.linkSessionConfigStoreSupplier = linkSessionConfigStoreSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.refreshPanelData = refreshPanelData;
        this.updateProfileHeader = updateProfileHeader;
        this.timeoutExceptionPredicate = timeoutExceptionPredicate;
        this.logTimeout = logTimeout;
        this.logFailure = logFailure;
        this.executeIoConsumer = executeIoConsumer;
    }

    @Override
    public boolean isClientLoggedIn() {
        return isClientLoggedIn != null && isClientLoggedIn.getAsBoolean();
    }

    @Override
    public String currentDeviceId() {
        PluginConfig pluginConfig = configSupplier != null ? configSupplier.get() : null;
        return pluginConfig != null ? pluginConfig.deviceId() : null;
    }

    @Override
    public ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId) throws IOException {
        ApiClient apiClient = apiClientSupplier != null ? apiClientSupplier.get() : null;
        if (apiClient == null) {
            return null;
        }
        return apiClient.linkDevice(licenseKey, deviceId, username, pluginVersion);
    }

    @Override
    public void persistLinkedSession(String sessionToken, String signingSecret) {
        LinkSessionConfigStore linkStore = linkSessionConfigStoreSupplier != null
            ? linkSessionConfigStoreSupplier.get()
            : null;
        if (linkStore != null) {
            linkStore.persistLinkedSession(sessionToken, signingSecret);
        }
    }

    @Override
    public void resetAccountwideUploadSnapshot() {
        AccountwideSummaryUploader uploader = accountwideSummaryUploaderSupplier != null
            ? accountwideSummaryUploaderSupplier.get()
            : null;
        if (uploader != null) {
            uploader.resetUploadSnapshot();
        }
    }

    @Override
    public void resetUploadDiagnosticsState() {
        UploadEventDispatchFacadeService uploadService = resolveUploadEventDispatchFacadeService();
        if (uploadService != null) {
            uploadService.resetStatus();
        }
    }

    @Override
    public void updateUploadDiagnosticsUi() {
        UploadEventDispatchFacadeService uploadService = resolveUploadEventDispatchFacadeService();
        if (uploadService != null) {
            uploadService.updateUploadDiagnosticsUi();
        }
    }

    @Override
    public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
        ScheduledExecutorService scheduler = resolveScheduler();
        UploadBackfillDispatchService backfillDispatchService = resolveUploadBackfillDispatchService();
        if (scheduler == null || backfillDispatchService == null) {
            return;
        }
        backfillDispatchService.requestBackfillAttempt(scheduler, delaySeconds, resetBackoff);
    }

    @Override
    public void scheduleAccountwideSync(long delaySeconds) {
        ScheduledExecutorService scheduler = resolveScheduler();
        UploadBackfillDispatchService backfillDispatchService = resolveUploadBackfillDispatchService();
        if (scheduler == null || backfillDispatchService == null) {
            return;
        }
        scheduler.schedule(backfillDispatchService::requestAccountwideSync, delaySeconds, TimeUnit.SECONDS);
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
        return timeoutExceptionPredicate != null && timeoutExceptionPredicate.test(ex);
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
        if (executeIoConsumer != null) {
            executeIoConsumer.accept(task);
        }
    }

    @Override
    public void scheduleRetry(Runnable task, long delaySeconds) {
        ScheduledExecutorService scheduler = resolveScheduler();
        if (scheduler == null || task == null) {
            return;
        }
        scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }

    private UploadEventDispatchFacadeService resolveUploadEventDispatchFacadeService() {
        return uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
    }

    private UploadBackfillDispatchService resolveUploadBackfillDispatchService() {
        return uploadBackfillDispatchServiceSupplier != null
            ? uploadBackfillDispatchServiceSupplier.get()
            : null;
    }

    private ScheduledExecutorService resolveScheduler() {
        return schedulerSupplier != null ? schedulerSupplier.get() : null;
    }
}
