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

import com.google.gson.Gson;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;

final class GeLifecycleRuntimeSchedulerServices {
    static final class RuntimeState {
        private final ApiClient apiClient;
        private final ScheduledExecutorService scheduler;
        private final ExecutorService ioExecutor;

        RuntimeState(ApiClient apiClient, ScheduledExecutorService scheduler, ExecutorService ioExecutor) {
            this.apiClient = apiClient;
            this.scheduler = scheduler;
            this.ioExecutor = ioExecutor;
        }

        ApiClient getApiClient() {
            return apiClient;
        }

        ScheduledExecutorService getScheduler() {
            return scheduler;
        }

        ExecutorService getIoExecutor() {
            return ioExecutor;
        }
    }

    RuntimeState start(
        OkHttpClient httpClient,
        Gson gson,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Runnable refreshPanelData,
        Runnable refreshStatsData,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<OfferPreviewItemResolver> offerPreviewItemResolverSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        long accountwideUploadIntervalSeconds,
        long offerPollIntervalMs,
        Supplier<WikiPriceService> wikiPriceServiceSupplier,
        Runnable startProfileWatcher,
        Supplier<LinkAttemptService> linkAttemptServiceSupplier,
        Supplier<PluginConfig> configSupplier
    ) {
        ApiClient apiClient = new ApiClient(httpClient, gson);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService ioExecutor = Executors.newFixedThreadPool(2);

        scheduler.scheduleAtFixedRate(
            () -> {
                UploadBackfillDispatchService service = resolve(uploadBackfillDispatchServiceSupplier);
                if (service != null) {
                    service.requestEventFlush();
                }
            },
            2,
            2,
            TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(
            () -> {
                UploadBackfillDispatchService service = resolve(uploadBackfillDispatchServiceSupplier);
                if (service != null) {
                    service.requestAccountwideSync();
                }
            },
            60,
            accountwideUploadIntervalSeconds,
            TimeUnit.SECONDS
        );
        if (refreshPanelData != null) {
            scheduler.scheduleAtFixedRate(refreshPanelData, 5, 60, TimeUnit.SECONDS);
            scheduler.execute(refreshPanelData);
        }
        if (refreshStatsData != null) {
            scheduler.scheduleAtFixedRate(refreshStatsData, 5, 60, TimeUnit.SECONDS);
            scheduler.execute(refreshStatsData);
        }
        scheduler.scheduleAtFixedRate(
            () -> {
                OfferPreviewRuntimeFacadeService runtime = resolve(offerPreviewRuntimeFacadeServiceSupplier);
                if (runtime == null) {
                    return;
                }
                runtime.pollAndUpdate(
                    resolve(clientThreadSupplier),
                    resolve(offerPreviewItemResolverSupplier),
                    itemId -> {
                        OfferPreviewSyncService sync = PluginInjectorBridge.get(OfferPreviewSyncService.class);
                        if (sync == null) {
                            return false;
                        }
                        sync.setPreviewItem(itemId);
                        return true;
                    },
                    () -> {
                        OfferPreviewSyncService sync = PluginInjectorBridge.get(OfferPreviewSyncService.class);
                        if (sync != null) {
                            sync.clearPreview();
                        }
                    }
                );
            },
            1,
            offerPollIntervalMs,
            TimeUnit.MILLISECONDS
        );

        ProfileSelectionPresentationFacadeService profileSelection = resolve(profileSelectionPresentationFacadeServiceSupplier);
        if (profileSelection != null && profileSelection.isLinked()) {
            UploadBackfillDispatchService service = resolve(uploadBackfillDispatchServiceSupplier);
            if (service != null) {
                service.requestBackfillAttempt(scheduler, 12, true);
                scheduler.schedule(service::requestAccountwideSync, 10, TimeUnit.SECONDS);
            }
        }

        WikiPriceService wikiPriceService = resolve(wikiPriceServiceSupplier);
        if (wikiPriceService != null) {
            wikiPriceService.start(scheduler);
        }
        if (startProfileWatcher != null) {
            startProfileWatcher.run();
        }

        LinkAttemptService linkAttemptService = resolve(linkAttemptServiceSupplier);
        PluginConfig config = resolve(configSupplier);
        if (linkAttemptService != null && config != null) {
            String linkInput = linkAttemptService.resolveLinkInput(config.licenseKey(), config.linkCode());
            if (linkInput != null && !linkInput.trim().isEmpty()) {
                linkAttemptService.attemptLink(linkInput.trim());
            }
        }

        return new RuntimeState(apiClient, scheduler, ioExecutor);
    }

    void shutDown(
        ApiClient apiClient,
        ScheduledExecutorService scheduler,
        ExecutorService ioExecutor,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<WikiPriceService> wikiPriceServiceSupplier,
        Runnable stopProfileWatcher,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<Logger> loggerSupplier,
        Map<Integer, OfferSnapshot> snapshots,
        Runnable persistOfferUpdateTimes,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        Supplier<RecentTradeDeduper> recentTradeDeduperSupplier,
        UploadDiagnosticsState uploadState
    ) {
        ClientThread clientThread = resolve(clientThreadSupplier);
        if (clientThread != null) {
            clientThread.invokeLater(this::clearSuggestions);
        }

        WikiPriceService wikiPriceService = resolve(wikiPriceServiceSupplier);
        if (wikiPriceService != null) {
            wikiPriceService.stop();
        }
        if (stopProfileWatcher != null) {
            stopProfileWatcher.run();
        }

        UploadBackfillDispatchService backfillDispatch = resolve(uploadBackfillDispatchServiceSupplier);
        if (backfillDispatch != null) {
            backfillDispatch.resetBackfillRetryState();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }

        UploadEventDispatchFacadeService uploadDispatch = resolve(uploadEventDispatchFacadeServiceSupplier);
        if (uploadDispatch != null) {
            uploadDispatch.flushEvents(apiClient, resolve(configSupplier), resolve(loggerSupplier));
        }

        if (snapshots != null) {
            snapshots.clear();
        }
        if (persistOfferUpdateTimes != null) {
            persistOfferUpdateTimes.run();
        }
        if (offerUpdateStamps != null) {
            offerUpdateStamps.clear();
        }

        RecentTradeDeduper deduper = resolve(recentTradeDeduperSupplier);
        if (deduper != null) {
            deduper.clearAll();
        }
        if (uploadState != null) {
            uploadState.reset();
        }
        if (uploadDispatch != null) {
            uploadDispatch.updateUploadDiagnosticsUi();
        }
    }

    private void clearSuggestions() {
        ChatboxSuggestionPresentationService presentation =
            PluginInjectorBridge.get(ChatboxSuggestionPresentationService.class);
        presentation.clearPriceSuggestion();
        presentation.clearLimitSuggestion();
        presentation.clearAffordableLimitSuggestion();
        PluginInjectorBridge.get(RemainingLimitSuggestionService.class).clearCache();
        ChatboxSuggestionRuntimeStateService runtimeState =
            PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class);
        runtimeState.clearPromptWidgetCache();
        runtimeState.setSuggestionDirty(false);
    }

    private <T> T resolve(Supplier<T> supplier) {
        return supplier != null ? supplier.get() : null;
    }
}
