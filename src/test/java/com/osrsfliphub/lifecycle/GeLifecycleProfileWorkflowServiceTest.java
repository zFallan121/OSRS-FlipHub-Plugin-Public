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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeLifecycleProfileWorkflowServiceTest {
    @Test
    public void reloadProfileFromDiskAllowsAccountwideKey() {
        TestFixture fixture = new TestFixture();

        fixture.service.reloadProfileFromDisk(fixture.accountwideKey);

        assertEquals(1, fixture.loadedAccounts.size());
        assertEquals(Long.valueOf(fixture.accountwideKey), fixture.loadedAccounts.get(0));
        assertTrue(fixture.loadedProfiles.contains(fixture.accountwideKey));
    }

    @Test
    public void reloadProfileFromDiskReloadsAccountwideAfterProfileReload() {
        TestFixture fixture = new TestFixture();
        long profileKey = 123L;

        fixture.service.reloadProfileFromDisk(profileKey);

        assertEquals(2, fixture.loadedAccounts.size());
        assertEquals(Long.valueOf(profileKey), fixture.loadedAccounts.get(0));
        assertEquals(Long.valueOf(fixture.accountwideKey), fixture.loadedAccounts.get(1));
        assertTrue(fixture.loadedProfiles.contains(profileKey));
        assertTrue(fixture.loadedProfiles.contains(fixture.accountwideKey));
    }

    private static final class TestFixture {
        private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
        private final Object localStatsLock = new Object();
        private final Set<Long> loadedProfiles = new HashSet<>();
        private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount = new HashMap<>();
        private final Map<Long, Long> localSessionStartByAccount = new HashMap<>();
        private final Map<Long, LocalStatsCache> statsCacheByAccount = new HashMap<>();
        private final Set<Integer> bookmarkedItems = new HashSet<>();
        private final Map<Integer, OfferSnapshot> snapshots = new HashMap<>();
        private final List<Long> loadedAccounts = new ArrayList<>();

        private final GeLifecycleProfileWorkflowService service;

        private TestFixture() {
            LocalProfileTradesLoadService localProfileTradesLoadService = new LocalProfileTradesLoadService(
                accountwideKey,
                new LocalProfileTradesLoadService.Hooks() {
                    @Override
                    public ProfileTradesLoader.Result loadProfileTrades(long accountHash) {
                        loadedAccounts.add(accountHash);
                        return new ProfileTradesLoader.Result(new ArrayList<>(), null, 0L);
                    }

                    @Override
                    public void putLoadedProfileFileMs(long accountHash, long fileMs) {
                    }

                    @Override
                    public void setLocalTradeDeltas(long accountHash, List<LocalTradeDelta> deltas) {
                    }

                    @Override
                    public void rebuildStatsCache(long accountHash, List<LocalTradeDelta> deltas) {
                    }

                    @Override
                    public void putProfileDisplayName(long accountHash, String displayName) {
                    }

                    @Override
                    public void cacheItemName(int itemId) {
                    }

                    @Override
                    public void persistLocalTrades(long accountHash) {
                    }

                    @Override
                    public void markAccountwideUploadDirty() {
                    }

                    @Override
                    public void scheduleRefreshSoon() {
                    }

                    @Override
                    public void triggerStatsRefresh() {
                    }
                }
            );

            AccountwideSummaryUploader uploader = new AccountwideSummaryUploader();
            ProfileSelectionState profileSelection = new ProfileSelectionState(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY_STRING);

            ProfileSelectionPresentationFacadeService profileFacade = new ProfileSelectionPresentationFacadeService(
                profileSelection,
                new HashMap<>(),
                new HashMap<>(),
                new ProfileSelectionPresentationFacadeService.Hooks() {
                    @Override
                    public ProfileSelectionResolverService getProfileSelectionResolverService() {
                        return null;
                    }

                    @Override
                    public ProfilePresentationService getProfilePresentationService() {
                        return null;
                    }

                    @Override
                    public ProfileCatalogService getProfileCatalogService() {
                        return null;
                    }

                    @Override
                    public LegacyLocalTradesStore getLegacyLocalTradesStore() {
                        return null;
                    }

                    @Override
                    public LocalAccountSessionService getLocalAccountSessionService() {
                        return null;
                    }

                    @Override
                    public LinkSessionGuardService getLinkSessionGuardService() {
                        return new LinkSessionGuardService(null);
                    }

                    @Override
                    public long resolveAccountHash() {
                        return accountwideKey;
                    }
                }
            );

            GeLifecycleLocalTradesRuntimeService localTradesRuntime = new GeLifecycleLocalTradesRuntimeService(
                accountwideKey,
                10_000,
                600L,
                5_000L,
                localStatsLock,
                localTradeDeltasByAccount,
                loadedProfiles,
                new LocalTradesLoadCoordinator.State(),
                () -> null,
                () -> null,
                () -> false,
                () -> localProfileTradesLoadService,
                () -> null,
                () -> {},
                () -> uploader,
                () -> profileFacade,
                () -> null,
                () -> {},
                () -> {}
            );

            ProfileUiCoordinator profileUiCoordinator = new ProfileUiCoordinator();

            service = new GeLifecycleProfileWorkflowService(
                accountwideKey,
                10_000,
                localStatsLock,
                loadedProfiles,
                localTradeDeltasByAccount,
                localSessionStartByAccount,
                statsCacheByAccount,
                bookmarkedItems,
                snapshots,
                () -> localTradesRuntime,
                () -> uploader,
                () -> profileFacade,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                profileSelection,
                () -> null,
                () -> null,
                () -> profileUiCoordinator,
                () -> null,
                () -> null,
                () -> null,
                () -> null
            );
        }
    }
}
