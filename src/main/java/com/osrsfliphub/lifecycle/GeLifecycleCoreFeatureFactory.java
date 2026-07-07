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

import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;

final class GeLifecycleCoreFeatureFactory {
    private GeLifecycleCoreFeatureFactory() {
    }

    static GeLifecycleBackfillServices createBackfillServices(
        GeLifecycleCoreFeatureRuntimeContext context,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummaryByToken
    ) {
        return new GeLifecycleBackfillServices(
            context.apiClientSupplier,
            context.configSupplier,
            context.configManagerSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.uploadEventDispatchFacadeServiceSupplier,
            () -> PluginInjectorBridge.get(AccountwideProfileKeyCollector.class),
            () -> PluginInjectorBridge.get(ProfileStorageFacadeService.class),
            context.sharedState,
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> PluginInjectorBridge.get(BackfilledProfilesStore.class),
            accountKey -> localTradesRuntimeServiceSupplier.get().ensureProfileLoaded(accountKey),
            () -> PluginInjectorBridge.get(LocalStatsSnapshotService.class),
            fetchRemoteStatsSummaryByToken::apply,
            () -> context.runtimeUtilityServices.triggerStatsRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> context.runtimeUtilityServices.triggerPanelRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> PluginInjectorBridge.get(LocalTradeSessionFacadeService.class),
            context.clientSupplier,
            context.clientThreadSupplier,
            () -> PluginInjectorBridge.get(ItemLookupService.class),
            () -> context.runtimeUtilityServices.scheduleRefreshSoon(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            context.panelVisibleSupplier,
            context.loggerSupplier,
            () -> {
                Logger logger = context.loggerSupplier.get();
                return logger != null && logger.isDebugEnabled();
            },
            context.httpClient,
            context.gson,
            () -> context.runtimeUtilityServices.isClientFullyReady(context.clientSupplier.get()),
            context.hasItemManager
        );
    }
}
