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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class GeLifecycleLocalTradesRuntimeService {
    private final long accountwideKey;
    private final int maxLocalTrades;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final Object localStatsLock;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Set<Long> loadedProfiles;
    private final LocalTradesLoadCoordinator.State localTradesLoadState;
    private final Supplier<LocalTradesLoadCoordinator> localTradesLoadCoordinatorSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final BooleanSupplier clientThreadAvailableSupplier;
    private final Supplier<LocalProfileTradesLoadService> localProfileTradesLoadServiceSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Runnable markLocalTradesLoadedForLogin;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Runnable onProfileOptionsChanged;
    private final Runnable onProfileHeaderChanged;

    @Inject
    GeLifecycleLocalTradesRuntimeService(PluginState pluginState) {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY,
            GeLifecyclePluginConstants.MAX_LOCAL_TRADES,
            GeLifecyclePluginConstants.LOCAL_EVENT_BUCKET_MS,
            GeLifecyclePluginConstants.DUPLICATE_TRADE_WINDOW_MS,
            pluginState.getLocalStatsLock(),
            pluginState.getLocalTradeDeltasByAccount(),
            pluginState.getLoadedProfiles(),
            pluginState.getLocalTradesLoadState(),
            () -> PluginInjectorBridge.get(LocalTradesLoadCoordinator.class),
            () -> PluginAccess.plugin().scheduler,
            () -> PluginAccess.plugin().clientThread != null,
            () -> PluginInjectorBridge.get(LocalProfileTradesLoadService.class),
            () -> PluginInjectorBridge.get(ProfileStorageFacadeService.class),
            () -> PluginAccess.plugin().localTradesLoadedThisLogin = true,
            () -> PluginInjectorBridge.get(AccountwideSummaryUploader.class),
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> PluginInjectorBridge.get(UploadBackfillDispatchService.class),
            () -> PluginAccess.plugin().getProfileWorkflowService().updateProfileOptionsUI(),
            () -> PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader());
    }

    GeLifecycleLocalTradesRuntimeService(
        long accountwideKey,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        Object localStatsLock,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Set<Long> loadedProfiles,
        LocalTradesLoadCoordinator.State localTradesLoadState,
        Supplier<LocalTradesLoadCoordinator> localTradesLoadCoordinatorSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        BooleanSupplier clientThreadAvailableSupplier,
        Supplier<LocalProfileTradesLoadService> localProfileTradesLoadServiceSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Runnable markLocalTradesLoadedForLogin,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Runnable onProfileOptionsChanged,
        Runnable onProfileHeaderChanged
    ) {
        this.accountwideKey = accountwideKey;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.localStatsLock = localStatsLock;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.loadedProfiles = loadedProfiles;
        this.localTradesLoadState = localTradesLoadState;
        this.localTradesLoadCoordinatorSupplier = localTradesLoadCoordinatorSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.clientThreadAvailableSupplier = clientThreadAvailableSupplier;
        this.localProfileTradesLoadServiceSupplier = localProfileTradesLoadServiceSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.markLocalTradesLoadedForLogin = markLocalTradesLoadedForLogin;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.profileSelectionPresentationFacadeSupplier = profileSelectionPresentationFacadeSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.onProfileOptionsChanged = onProfileOptionsChanged;
        this.onProfileHeaderChanged = onProfileHeaderChanged;
    }

    void ensureLocalTradesLoaded(long accountKey) {
        LocalTradesLoadCoordinator coordinator = localTradesLoadCoordinatorSupplier.get();
        if (coordinator != null) {
            coordinator.ensureLocalTradesLoaded(accountKey);
        }
    }

    void scheduleLocalTradesLoad() {
        LocalTradesLoadCoordinator coordinator = localTradesLoadCoordinatorSupplier.get();
        if (coordinator != null) {
            coordinator.scheduleLocalTradesLoad(
                localTradesLoadState,
                schedulerSupplier.get(),
                clientThreadAvailableSupplier.getAsBoolean()
            );
        }
    }

    void attemptLocalTradesLoad() {
        LocalTradesLoadCoordinator coordinator = localTradesLoadCoordinatorSupplier.get();
        if (coordinator != null) {
            coordinator.attemptLocalTradesLoad();
        }
    }

    void loadLocalTradesAsync(long accountHash) {
        LocalTradesLoadCoordinator coordinator = localTradesLoadCoordinatorSupplier.get();
        if (coordinator != null) {
            coordinator.loadLocalTradesAsync(accountHash);
        }
    }

    boolean loadLocalTradesForAccount(long accountHash) {
        return loadLocalTradesForAccount(accountHash, true);
    }

    boolean loadLocalTradesForAccount(long accountHash, boolean persistAfterLoad) {
        LocalProfileTradesLoadService service = localProfileTradesLoadServiceSupplier.get();
        if (service == null) {
            return false;
        }
        return service.load(accountHash, persistAfterLoad);
    }

    boolean isPlaceholderDisplayName(String displayName) {
        if (displayName == null) {
            return true;
        }
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return trimmed.startsWith("Profile ");
    }

    void persistLocalTrades(long accountKey) {
        if (accountKey < 0) {
            return;
        }
        List<LocalTradeDelta> snapshot;
        synchronized (localStatsLock) {
            List<LocalTradeDelta> deltas = localTradeDeltasByAccount.get(accountKey);
            snapshot = deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
        }
        ProfileStorageFacadeService storageFacade = profileStorageFacadeServiceSupplier.get();
        if (storageFacade == null) {
            return;
        }
        storageFacade.writeProfileData(accountKey, snapshot);
        markLocalTradesLoadedForLogin.run();
        if (accountKey != accountwideKey) {
            AccountwideSummaryUploader uploader = accountwideSummaryUploaderSupplier.get();
            if (uploader != null) {
                uploader.markDirty();
            }
            ProfileSelectionPresentationFacadeService profileFacade = profileSelectionPresentationFacadeSupplier.get();
            if (profileFacade != null && profileFacade.isLinked()) {
                UploadBackfillDispatchService uploadService = uploadBackfillDispatchServiceSupplier.get();
                if (uploadService != null) {
                    uploadService.requestAccountwideSync();
                }
            }
        }
    }

    void appendTradeDelta(long accountKey, LocalTradeDelta delta) {
        if (accountKey < 0 || delta == null) {
            return;
        }
        List<LocalTradeDelta> deltas = localTradeDeltasByAccount.computeIfAbsent(accountKey, key -> new ArrayList<>());
        if (LocalTradeDeltaUtils.isLikelyDuplicateTradeDelta(
            deltas,
            delta,
            localEventBucketMs,
            duplicateTradeWindowMs,
            12
        )) {
            return;
        }
        deltas.add(delta);
        if (deltas.size() > maxLocalTrades) {
            int trim = deltas.size() - maxLocalTrades;
            deltas.subList(0, trim).clear();
        }
    }

    void ensureProfileLoaded(long accountKey) {
        if (accountKey < 0) {
            return;
        }
        if (accountKey == accountwideKey) {
            if (!loadedProfiles.contains(accountKey)) {
                loadLocalTradesForAccount(accountKey);
                loadedProfiles.add(accountKey);
                onProfileOptionsChanged.run();
                onProfileHeaderChanged.run();
            }
            return;
        }
        if (loadedProfiles.contains(accountKey)) {
            return;
        }
        loadLocalTradesForAccount(accountKey);
        loadedProfiles.add(accountKey);
        onProfileOptionsChanged.run();
        onProfileHeaderChanged.run();
    }

    void ensureProfileLoadedBoxed(Long accountHash) {
        if (accountHash == null) {
            return;
        }
        ensureProfileLoaded(accountHash);
    }

    void markLocalTradesLoadedForLogin() {
        markLocalTradesLoadedForLogin.run();
    }

    void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
        synchronized (localStatsLock) {
            appendTradeDelta(accountKey, delta);
            appendTradeDelta(accountwideKey, delta);
        }
    }
}
