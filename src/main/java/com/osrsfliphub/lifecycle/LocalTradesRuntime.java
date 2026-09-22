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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.*;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class LocalTradesRuntime {
    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(LocalTradesRuntime.class);

    /** Accounts whose in-memory trades differ from what is on disk. */
    private final Map<Long, AtomicBoolean> unsavedProfiles = new ConcurrentHashMap<>();
    /** Accounts a write is already running for, so two writers cannot race the same file. */
    private final Map<Long, AtomicBoolean> savingProfiles = new ConcurrentHashMap<>();

    private final PluginState state;
    // Providers rather than the services: most of them reach back here, and a unit test leaves
    // out whichever it does not exercise.
    private final Provider<TradesLoad> tradesLoad;
    private final Provider<ProfileTradesLoad> profileTradesLoad;
    private final Provider<ProfileStorage> profileStorage;
    private final Provider<SummaryUploader> summaryUploader;
    private final Provider<ProfileSelectionPresentation> profileSelection;
    private final Provider<UploadBackfillDispatch> uploads;
    private final Provider<ProfileUi> profileUi;

    void ensureLocalTradesLoaded(long accountKey) {
        TradesLoad loads = tradesLoad.get();
        if (loads != null) {
            loads.ensureLocalTradesLoaded(accountKey);
        }
    }

    void scheduleLocalTradesLoad() {
        TradesLoad loads = tradesLoad.get();
        if (loads != null) {
            GeLifecyclePlugin plugin = Access.plugin();
            loads.scheduleLocalTradesLoad(state.getLocalTradesLoadState(), plugin.scheduler, plugin.clientThread != null);
        }
    }

    void loadLocalTradesAsync(long accountHash) {
        TradesLoad loads = tradesLoad.get();
        if (loads != null) {
            loads.loadLocalTradesAsync(accountHash);
        }
    }

    boolean loadLocalTradesForAccount(long accountHash, boolean persistAfterLoad) {
        ProfileTradesLoad service = profileTradesLoad.get();
        return service != null && service.load(accountHash, persistAfterLoad);
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
        markLocalTradesLoadedForLogin();
        if (accountKey != Const.ACCOUNTWIDE_KEY) {
            SummaryUploader uploader = summaryUploader.get();
            if (uploader != null) {
                uploader.markDirty();
            }
            ProfileSelectionPresentation profiles = profileSelection.get();
            if (profiles != null && profiles.isLinked()) {
                UploadBackfillDispatch dispatch = uploads.get();
                if (dispatch != null) {
                    dispatch.requestAccountwideSync();
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
        GeLifecyclePlugin plugin = Access.pluginOrNull();
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
        List<Delta> snapshot;
        synchronized (state.getLocalStatsLock()) {
            List<Delta> deltas = state.getLocalTradeDeltasByAccount().get(accountKey);
            snapshot = deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
        }
        ProfileStorage storageFacade = profileStorage.get();
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
     * of its offer into one record (see {@link TradeOfferCollapser}). The caller
     * must hold the local stats lock.
     *
     * @return what became of the record, so the caller can keep the live aggregate in
     *         step: a fill is applied to it, a collapse rebuilds it from the stored list,
     *         and a dropped record never reaches it
     */
    TradeOfferCollapser.Outcome appendTradeDelta(long accountKey, Delta delta) {
        if (accountKey < 0 || delta == null) {
            return TradeOfferCollapser.Outcome.DROPPED;
        }
        List<Delta> deltas = state.getLocalTradeDeltasByAccount().computeIfAbsent(accountKey, key -> new ArrayList<>());
        if (TradeDeltaUtils.isLikelyDuplicateTradeDelta(
            deltas, delta, Const.LOCAL_EVENT_BUCKET_MS, Const.DUPLICATE_TRADE_WINDOW_MS, 12)) {
            return TradeOfferCollapser.Outcome.DROPPED;
        }
        return TradeOfferCollapser.append(deltas, delta);
    }

    void ensureProfileLoaded(long accountKey) {
        Set<Long> loaded = state.getLoadedProfiles();
        if (accountKey < 0 || loaded.contains(accountKey)) {
            return;
        }
        loadLocalTradesForAccount(accountKey, true);
        loaded.add(accountKey);
        ProfileUi ui = profileUi.get();
        ui.updateProfileOptionsUi();
        ui.updateProfileHeader();
    }

    void markLocalTradesLoadedForLogin() {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin != null) {
            plugin.localTradesLoadedThisLogin = true;
        }
    }

    /**
     * Stores the record for the account and for the accountwide pool.
     *
     * @return the account's outcome; the pool holds the same records and is rebuilt from
     *         the per-account files on load, so it follows the account's lead
     */
    TradeOfferCollapser.Outcome appendTradeDeltaPair(long accountKey, long accountwideKey, Delta delta) {
        synchronized (state.getLocalStatsLock()) {
            TradeOfferCollapser.Outcome outcome = appendTradeDelta(accountKey, delta);
            appendTradeDelta(accountwideKey, delta);
            return outcome;
        }
    }
}
