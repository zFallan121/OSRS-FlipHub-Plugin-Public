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

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

final class AccountwideBackfillCoordinatorRuntimeHooks
    implements AccountwideBackfillCoordinatorFactoryService.RuntimeHooks {

    interface LikelySyncedProfilesInferer {
        Set<Long> infer(Set<Long> profileKeys, Map<Long, StatsSummary> localSummaries, StatsSummary remoteSummary);
    }

    private final Supplier<Set<Long>> collectAccountwideProfileKeys;
    private final Supplier<Set<Long>> loadBackfilledProfileKeys;
    private final LongConsumer ensureProfileLoaded;
    private final LongFunction<LocalStatsSnapshot> buildLocalStatsSnapshot;
    private final Supplier<String> sessionToken;
    private final Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummary;
    private final LikelySyncedProfilesInferer inferLikelySyncedProfiles;
    private final LongPredicate backfillProfileTrades;
    private final Consumer<Set<Long>> persistBackfilledProfileKeys;
    private final Runnable triggerStatsRefresh;
    private final Runnable triggerPanelRefresh;
    private final Runnable resetBackfillRetryState;
    private final Consumer<String> logWarn;
    private final BiConsumer<String, Throwable> logWarnWithError;

    AccountwideBackfillCoordinatorRuntimeHooks(Supplier<Set<Long>> collectAccountwideProfileKeys,
                                               Supplier<Set<Long>> loadBackfilledProfileKeys,
                                               LongConsumer ensureProfileLoaded,
                                               LongFunction<LocalStatsSnapshot> buildLocalStatsSnapshot,
                                               Supplier<String> sessionToken,
                                               Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummary,
                                               LikelySyncedProfilesInferer inferLikelySyncedProfiles,
                                               LongPredicate backfillProfileTrades,
                                               Consumer<Set<Long>> persistBackfilledProfileKeys,
                                               Runnable triggerStatsRefresh,
                                               Runnable triggerPanelRefresh,
                                               Runnable resetBackfillRetryState,
                                               Consumer<String> logWarn,
                                               BiConsumer<String, Throwable> logWarnWithError) {
        this.collectAccountwideProfileKeys = collectAccountwideProfileKeys;
        this.loadBackfilledProfileKeys = loadBackfilledProfileKeys;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.buildLocalStatsSnapshot = buildLocalStatsSnapshot;
        this.sessionToken = sessionToken;
        this.fetchRemoteStatsSummary = fetchRemoteStatsSummary;
        this.inferLikelySyncedProfiles = inferLikelySyncedProfiles;
        this.backfillProfileTrades = backfillProfileTrades;
        this.persistBackfilledProfileKeys = persistBackfilledProfileKeys;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.triggerPanelRefresh = triggerPanelRefresh;
        this.resetBackfillRetryState = resetBackfillRetryState;
        this.logWarn = logWarn;
        this.logWarnWithError = logWarnWithError;
    }

    @Override
    public Set<Long> collectAccountwideProfileKeys() {
        return collectAccountwideProfileKeys != null ? collectAccountwideProfileKeys.get() : null;
    }

    @Override
    public Set<Long> loadBackfilledProfileKeys() {
        return loadBackfilledProfileKeys != null ? loadBackfilledProfileKeys.get() : null;
    }

    @Override
    public void ensureProfileLoaded(long key) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(key);
        }
    }

    @Override
    public LocalStatsSnapshot buildLocalStatsSnapshot(long key) {
        return buildLocalStatsSnapshot != null ? buildLocalStatsSnapshot.apply(key) : null;
    }

    @Override
    public String getSessionToken() {
        return sessionToken != null ? sessionToken.get() : null;
    }

    @Override
    public ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
        return fetchRemoteStatsSummary != null ? fetchRemoteStatsSummary.apply(token) : null;
    }

    @Override
    public Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                               Map<Long, StatsSummary> localSummaries,
                                               StatsSummary remoteSummary) {
        return inferLikelySyncedProfiles != null
            ? inferLikelySyncedProfiles.infer(profileKeys, localSummaries, remoteSummary)
            : null;
    }

    @Override
    public boolean backfillProfileTrades(long profileKey) {
        return backfillProfileTrades != null && backfillProfileTrades.test(profileKey);
    }

    @Override
    public void persistBackfilledProfileKeys(Set<Long> keys) {
        if (persistBackfilledProfileKeys != null) {
            persistBackfilledProfileKeys.accept(keys);
        }
    }

    @Override
    public void triggerStatsRefresh() {
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }

    @Override
    public void triggerPanelRefresh() {
        if (triggerPanelRefresh != null) {
            triggerPanelRefresh.run();
        }
    }

    @Override
    public void resetBackfillRetryState() {
        if (resetBackfillRetryState != null) {
            resetBackfillRetryState.run();
        }
    }

    @Override
    public void logWarn(String message) {
        if (logWarn != null) {
            logWarn.accept(message);
        }
    }

    @Override
    public void logWarn(String message, Throwable error) {
        if (logWarnWithError != null) {
            logWarnWithError.accept(message, error);
        }
    }
}
