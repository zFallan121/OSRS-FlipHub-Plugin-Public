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
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;

final class GeLifecycleUploadRuntimeServices {
    private final UploadDiagnosticsState uploadState;
    private final GeLifecycleRuntimeUtilityServices runtimeUtilityServices;
    private final int maxPendingUploadEvents;
    private final int maxBatchSize;
    private final long backfillRetryIntervalSeconds;
    private final long backfillRetryMaxIntervalSeconds;
    private final long backfillMinIntervalMs;
    private final Supplier<Client> clientSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<Logger> loggerSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Supplier<GeLifecycleBackfillServices> backfillServicesSupplier;
    private final Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Consumer<Runnable> ioExecutor;

    private UploadEventDispatchFacadeService uploadEventDispatchFacadeService;
    private BackfillRetryScheduler backfillRetryScheduler;
    private UploadBackfillDispatchService uploadBackfillDispatchService;
    private AccountwideBackfillExecutionService accountwideBackfillExecutionService;

    GeLifecycleUploadRuntimeServices(
        UploadDiagnosticsState uploadState,
        GeLifecycleRuntimeUtilityServices runtimeUtilityServices,
        int maxPendingUploadEvents,
        int maxBatchSize,
        long backfillRetryIntervalSeconds,
        long backfillRetryMaxIntervalSeconds,
        long backfillMinIntervalMs,
        Supplier<Client> clientSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Logger> loggerSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Consumer<Runnable> ioExecutor
    ) {
        this.uploadState = uploadState;
        this.runtimeUtilityServices = runtimeUtilityServices;
        this.maxPendingUploadEvents = maxPendingUploadEvents;
        this.maxBatchSize = maxBatchSize;
        this.backfillRetryIntervalSeconds = backfillRetryIntervalSeconds;
        this.backfillRetryMaxIntervalSeconds = backfillRetryMaxIntervalSeconds;
        this.backfillMinIntervalMs = backfillMinIntervalMs;
        this.clientSupplier = clientSupplier;
        this.panelSupplier = panelSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.loggerSupplier = loggerSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.backfillServicesSupplier = backfillServicesSupplier;
        this.profileWorkflowServiceSupplier = profileWorkflowServiceSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.ioExecutor = ioExecutor;
    }

    UploadEventDispatchFacadeService getUploadEventDispatchFacadeService() {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeService;
        if (service != null) {
            return service;
        }
        service = new UploadEventDispatchFacadeService(
            uploadState,
            maxPendingUploadEvents,
            maxBatchSize,
            new UploadEventDispatchPluginHooks(
                () -> runtimeUtilityServices.isClientLoggedIn(clientSupplier.get()),
                this::requeue,
                this::attemptRefresh,
                this::clearSession,
                () -> runtimeUtilityServices.isPanelVisible(panelSupplier.get()),
                () -> profileWorkflowServiceSupplier.get().updateProfileHeader(),
                () -> {
                    FlipHubPanel panel = panelSupplier.get();
                    if (panel != null) {
                        panel.setUploadDiagnosticsTooltip(null);
                    }
                }
            )
        );
        uploadEventDispatchFacadeService = service;
        return service;
    }

    UploadBackfillDispatchService getUploadBackfillDispatchService() {
        UploadBackfillDispatchService service = uploadBackfillDispatchService;
        if (service != null) {
            return service;
        }
        service = new UploadBackfillDispatchService(
            getBackfillRetryScheduler(),
            new UploadBackfillDispatchPluginHooks(
                this::executeIo,
                () -> getUploadEventDispatchFacadeService().flushEvents(
                    apiClientSupplier.get(),
                    configSupplier.get(),
                    loggerSupplier.get()
                ),
                () -> backfillServicesSupplier.get().getAccountwideSummaryUploader(),
                apiClientSupplier,
                configSupplier,
                new AccountwideSummaryUploaderPluginHooks(
                    () -> runtimeUtilityServices.isClientFullyReady(clientSupplier.get()),
                    () -> profileWorkflowServiceSupplier.get().buildReconciledAccountwideSnapshot(),
                    this::attemptRefresh,
                    this::clearSession
                ),
                () -> getAccountwideBackfillExecutionService().attemptIfNeeded()
            )
        );
        uploadBackfillDispatchService = service;
        return service;
    }

    AccountwideBackfillExecutionService getAccountwideBackfillExecutionService() {
        AccountwideBackfillExecutionService service = accountwideBackfillExecutionService;
        if (service != null) {
            return service;
        }
        service = new AccountwideBackfillExecutionService(
            backfillMinIntervalMs,
            new AccountwideBackfillExecutionPluginHooks(
                () -> runtimeUtilityServices.isClientLoggedIn(clientSupplier.get()),
                profileSelectionPresentationFacadeServiceSupplier,
                apiClientSupplier,
                configManagerSupplier,
                System::currentTimeMillis,
                this::getUploadBackfillDispatchService,
                schedulerSupplier,
                () -> backfillServicesSupplier.get().getBackfillMarketServices().getAccountwideBackfillCoordinator()
            )
        );
        accountwideBackfillExecutionService = service;
        return service;
    }

    boolean attemptRefresh(String currentToken) {
        SessionRefreshService service = getSessionRefreshService();
        return service != null && service.attemptRefresh(currentToken);
    }

    void clearSession() {
        SessionRefreshService service = getSessionRefreshService();
        if (service != null) {
            service.clearSession();
        }
    }

    ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String sessionToken, Long sinceMs, boolean allowRefresh) {
        return runtimeUtilityServices.fetchRemoteStatsSummary(
            apiClientSupplier.get(),
            configSupplier.get(),
            getSessionRefreshService(),
            sessionToken,
            sinceMs,
            allowRefresh
        );
    }

    void requeue(List<GeEvent> batch) {
        runtimeUtilityServices.requeue(getUploadEventDispatchFacadeService(), batch);
    }

    void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
        getUploadBackfillDispatchService().requestBackfillAttempt(
            schedulerSupplier.get(),
            delaySeconds,
            resetBackoff
        );
    }

    void scheduleBackfillRetry() {
        getUploadBackfillDispatchService().scheduleBackfillRetry(schedulerSupplier.get());
    }

    void resetBackfillRetryState() {
        getUploadBackfillDispatchService().resetBackfillRetryState();
    }

    BackfillRetryScheduler getBackfillRetryScheduler() {
        BackfillRetryScheduler service = backfillRetryScheduler;
        if (service != null) {
            return service;
        }
        service = new BackfillRetryScheduler(
            backfillRetryIntervalSeconds,
            backfillRetryMaxIntervalSeconds
        );
        backfillRetryScheduler = service;
        return service;
    }

    private SessionRefreshService getSessionRefreshService() {
        GeLifecycleBackfillServices services = backfillServicesSupplier.get();
        if (services == null) {
            return null;
        }
        GeLifecycleBackfillMarketServices marketServices = services.getBackfillMarketServices();
        if (marketServices == null) {
            return null;
        }
        return marketServices.getSessionRefreshService();
    }

    private void executeIo(Runnable task) {
        if (task == null) {
            return;
        }
        ioExecutor.accept(task);
    }
}
