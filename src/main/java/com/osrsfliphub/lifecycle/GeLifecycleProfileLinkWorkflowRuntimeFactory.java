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
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;

final class GeLifecycleProfileLinkWorkflowRuntimeFactory {
    GeLifecycleProfileLinkWorkflowRuntimeServices create(
        GeLifecycleSharedState sharedState,
        ProfileSelectionState profileSelection,
        Map<Long, String> profileDisplayNames,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, Long> loadedProfileFileMs,
        Gson gson,
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
        return new GeLifecycleProfileLinkWorkflowRuntimeServices(
            sharedState,
            profileSelection,
            profileDisplayNames,
            legacyNameKeysByHash,
            loadedProfileFileMs,
            () -> gson,
            configSupplier,
            configManagerSupplier,
            clientSupplier,
            apiClientSupplier,
            schedulerSupplier,
            panelSupplier,
            eventManageHistoryServicesSupplier,
            localTradesRuntimeServiceSupplier,
            statsTradesServicesSupplier,
            uploadEventDispatchFacadeServiceSupplier,
            uploadBackfillDispatchServiceSupplier,
            backfillServicesSupplier,
            bookmarkStateServiceSupplier,
            refreshPanelDataAction,
            executeIoConsumer,
            runtimeUtilityServices,
            loggerSupplier
        );
    }
}
