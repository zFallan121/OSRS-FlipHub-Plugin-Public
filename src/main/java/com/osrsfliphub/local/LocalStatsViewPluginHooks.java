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
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class LocalStatsViewPluginHooks implements LocalStatsViewService.Hooks {
    private final Runnable ensureSelectedProfileLoaded;
    private final LongSupplier nowMs;
    private final Supplier<StatsRange> currentStatsRange;
    private final Supplier<StatsItemSort> currentStatsSort;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier;

    LocalStatsViewPluginHooks(
        Runnable ensureSelectedProfileLoaded,
        LongSupplier nowMs,
        Supplier<StatsRange> currentStatsRange,
        Supplier<StatsItemSort> currentStatsSort,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier
    ) {
        this.ensureSelectedProfileLoaded = ensureSelectedProfileLoaded;
        this.nowMs = nowMs;
        this.currentStatsRange = currentStatsRange;
        this.currentStatsSort = currentStatsSort;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.localStatsSnapshotServiceSupplier = localStatsSnapshotServiceSupplier;
    }

    @Override
    public void ensureSelectedProfileLoaded() {
        if (ensureSelectedProfileLoaded != null) {
            ensureSelectedProfileLoaded.run();
        }
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : System.currentTimeMillis();
    }

    @Override
    public StatsRange currentStatsRange() {
        return currentStatsRange != null ? currentStatsRange.get() : null;
    }

    @Override
    public StatsItemSort currentStatsSort() {
        return currentStatsSort != null ? currentStatsSort.get() : null;
    }

    @Override
    public long resolveSelectedProfileKey() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null ? service.resolveSelectedProfileKey() : -1L;
    }

    @Override
    public long resolveSessionStartMs(long accountKey, long nowMs) {
        LocalTradeSessionFacadeService service = resolveLocalTradeSessionFacadeService();
        return service != null ? service.resolveStatsSessionStartMs(accountKey, nowMs) : 0L;
    }

    @Override
    public LocalStatsSnapshot buildLocalStatsSnapshot(long accountKey, Long sinceMs, StatsItemSort sort) {
        LocalStatsSnapshotService service = localStatsSnapshotServiceSupplier != null
            ? localStatsSnapshotServiceSupplier.get()
            : null;
        return service != null ? service.buildSnapshot(accountKey, sinceMs, sort) : null;
    }

    @Override
    public Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs) {
        LocalTradeSessionFacadeService service = resolveLocalTradeSessionFacadeService();
        return service != null ? service.buildStatsFlipHistory(accountKey, sinceMs) : null;
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionFacadeService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }

    private LocalTradeSessionFacadeService resolveLocalTradeSessionFacadeService() {
        return localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
    }
}
