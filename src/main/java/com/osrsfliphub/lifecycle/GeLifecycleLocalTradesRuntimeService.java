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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class GeLifecycleLocalTradesRuntimeService {
    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(GeLifecycleLocalTradesRuntimeService.class);

    /** Accounts whose in-memory trades differ from what is on disk. */
    private final Map<Long, AtomicBoolean> unsavedProfiles = new ConcurrentHashMap<>();
    /** Accounts a write is already running for, so two writers cannot race the same file. */
    private final Map<Long, AtomicBoolean> savingProfiles = new ConcurrentHashMap<>();

    private final long accountwideKey;
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

    /**
     * Records that an account's trades need saving, and gets the file written.
     *
     * <p>Callers are usually on the thread drawing the game: this runs once per Grand Exchange
     * fill, and serialising thousands of deltas and writing them there costs frames. The write
     * moves to the IO pool, coalesced per account so a burst of fills produces one write of the
     * latest state rather than a queue of stale ones, and serialised so two writers never race
     * the same file. If there is no pool to hand the work to it is written here instead, because
     * losing it would be worse than the delay.
     */
    void persistLocalTrades(long accountKey) {
        if (accountKey < 0) {
            return;
        }
        unsavedProfiles.computeIfAbsent(accountKey, key -> new AtomicBoolean()).set(true);
        beginSaving(accountKey);
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

    private void beginSaving(long accountKey) {
        AtomicBoolean saving = savingProfiles.computeIfAbsent(accountKey, key -> new AtomicBoolean());
        if (!saving.compareAndSet(false, true)) {
            return;
        }
        Runnable write = () -> drainSaves(accountKey, saving);
        GeLifecyclePlugin plugin = PluginAccess.pluginOrNull();
        ExecutorService io = plugin != null ? plugin.ioExecutor : null;
        if (io != null && !io.isShutdown()) {
            try {
                io.execute(write);
                return;
            } catch (RejectedExecutionException ignored) {
                // Stopped between the check and the submit; fall through and write here.
            }
        }
        write.run();
    }

    private void drainSaves(long accountKey, AtomicBoolean saving) {
        try {
            AtomicBoolean unsaved = unsavedProfiles.get(accountKey);
            while (unsaved != null && unsaved.getAndSet(false)) {
                if (!writeProfileSnapshot(accountKey)) {
                    // The disk refused it. Put the mark back so the next fill or the flush at
                    // shutdown tries again, and stop: retrying in a tight loop only fails faster.
                    unsaved.set(true);
                    return;
                }
            }
        } finally {
            saving.set(false);
        }
        AtomicBoolean unsaved = unsavedProfiles.get(accountKey);
        if (unsaved != null && unsaved.get()) {
            // Marked again while this writer was finishing.
            beginSaving(accountKey);
        }
    }

    /** @return true when the account's trades reached disk. */
    private boolean writeProfileSnapshot(long accountKey) {
        List<LocalTradeDelta> snapshot;
        synchronized (localStatsLock) {
            List<LocalTradeDelta> deltas = localTradeDeltasByAccount.get(accountKey);
            snapshot = deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
        }
        ProfileStorageFacadeService storageFacade = profileStorageFacadeServiceSupplier.get();
        return storageFacade != null && storageFacade.writeProfileData(accountKey, snapshot);
    }

    /** Writes anything still unsaved here and now, for shutdown paths that cannot wait. */
    void flushUnsavedProfiles() {
        for (Map.Entry<Long, AtomicBoolean> entry : unsavedProfiles.entrySet()) {
            if (!entry.getValue().getAndSet(false)) {
                continue;
            }
            // Each account gets its own attempt. A throw on the first used to abandon the loop,
            // so every later account lost its trades too - and this runs at shutdown, where
            // there is no later chance.
            boolean written;
            try {
                written = writeProfileSnapshot(entry.getKey());
            } catch (RuntimeException ex) {
                log.warn("FlipHub: failed to write trades for account {}", entry.getKey(), ex);
                written = false;
            }
            if (!written) {
                // Still unsaved. Another shutdown path may yet get it to disk.
                entry.getValue().set(true);
            }
        }
    }

    /**
     * Store one record for an account. A fill is appended; a completion folds the fills
     * of its offer into one record (see {@link LocalTradeOfferCollapser}). The caller
     * must hold the local stats lock.
     *
     * @return what became of the record, so the caller can keep the live aggregate in
     *         step: a fill is applied to it, a collapse rebuilds it from the stored list,
     *         and a dropped record never reaches it
     */
    LocalTradeOfferCollapser.Outcome appendTradeDelta(long accountKey, LocalTradeDelta delta) {
        if (accountKey < 0 || delta == null) {
            return LocalTradeOfferCollapser.Outcome.DROPPED;
        }
        List<LocalTradeDelta> deltas = localTradeDeltasByAccount.computeIfAbsent(accountKey, key -> new ArrayList<>());
        if (LocalTradeDeltaUtils.isLikelyDuplicateTradeDelta(
            deltas,
            delta,
            localEventBucketMs,
            duplicateTradeWindowMs,
            12
        )) {
            return LocalTradeOfferCollapser.Outcome.DROPPED;
        }
        return LocalTradeOfferCollapser.append(deltas, delta);
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

    /**
     * Stores the record for the account and for the accountwide pool.
     *
     * @return the account's outcome; the pool holds the same records and is rebuilt from
     *         the per-account files on load, so it follows the account's lead
     */
    LocalTradeOfferCollapser.Outcome appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
        synchronized (localStatsLock) {
            LocalTradeOfferCollapser.Outcome outcome = appendTradeDelta(accountKey, delta);
            appendTradeDelta(accountwideKey, delta);
            return outcome;
        }
    }
}
