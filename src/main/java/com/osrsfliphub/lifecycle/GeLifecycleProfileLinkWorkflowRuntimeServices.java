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

import static com.osrsfliphub.GeLifecyclePluginConstants.*;

import com.google.gson.Gson;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;

final class GeLifecycleProfileLinkWorkflowRuntimeServices {
    private final GeLifecycleSharedState sharedState;
    private final ProfileSelectionState profileSelection;
    private final Map<Long, String> profileDisplayNames;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Supplier<Gson> gsonSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier;
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<GeLifecycleBackfillServices> backfillServicesSupplier;
    private final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    private final Runnable refreshPanelDataAction;
    private final Consumer<Runnable> executeIoConsumer;
    private final GeLifecycleRuntimeUtilityServices runtimeUtilityServices;
    private final Supplier<Logger> loggerSupplier;


    GeLifecycleProfileLinkWorkflowRuntimeServices(
        GeLifecycleSharedState sharedState,
        ProfileSelectionState profileSelection,
        Map<Long, String> profileDisplayNames,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, Long> loadedProfileFileMs,
        Supplier<Gson> gsonSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Client> clientSupplier,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Runnable refreshPanelDataAction,
        Consumer<Runnable> executeIoConsumer,
        GeLifecycleRuntimeUtilityServices runtimeUtilityServices,
        Supplier<Logger> loggerSupplier
    ) {
        this.sharedState = sharedState;
        this.profileSelection = profileSelection;
        this.profileDisplayNames = profileDisplayNames;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.gsonSupplier = gsonSupplier;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.clientSupplier = clientSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.panelSupplier = panelSupplier;
        this.eventManageHistoryServicesSupplier = eventManageHistoryServicesSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.statsTradesServicesSupplier = statsTradesServicesSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.backfillServicesSupplier = backfillServicesSupplier;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.refreshPanelDataAction = refreshPanelDataAction;
        this.executeIoConsumer = executeIoConsumer;
        this.runtimeUtilityServices = runtimeUtilityServices;
        this.loggerSupplier = loggerSupplier;
    }

    GeLifecycleProfileWorkflowService getProfileWorkflowService() {
        return PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
    }

    LegacyLocalTradesFilterService getLegacyLocalTradesFilterService() {
        return PluginInjectorBridge.get(LegacyLocalTradesFilterService.class);
    }

    private GeLifecycleEventManageHistoryServices getEventManageHistoryServices() {
        return eventManageHistoryServicesSupplier.get();
    }

    private GeLifecycleStatsTradesServices getStatsTradesServices() {
        return statsTradesServicesSupplier.get();
    }

    private GeLifecycleBackfillServices getBackfillServices() {
        return backfillServicesSupplier.get();
    }

    private void executeIo(Runnable task) {
        executeIoConsumer.accept(task);
    }

    private void debugLog(String message) {
        Logger logger = loggerSupplier.get();
        if (logger != null) {
            logger.debug(message);
        }
    }

    private void warnLinkFailure(Throwable ex) {
        Logger logger = loggerSupplier.get();
        if (logger != null) {
            logger.warn("FlipHub link failed", ex);
        }
    }
}
