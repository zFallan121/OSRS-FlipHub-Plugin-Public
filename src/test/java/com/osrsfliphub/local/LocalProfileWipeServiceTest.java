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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalProfileWipeServiceTest {
    private static final long ACCOUNTWIDE_KEY = 0L;

    @Test
    public void wipeSingleAbortsWhenGeHistoryUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.historyTrades = null;
        LocalProfileWipeService service = new LocalProfileWipeService(ACCOUNTWIDE_KEY, hooks);

        service.wipeSingleLocalProfile(123L, "Profile 123");

        assertEquals(1, hooks.errorMessages.size());
        assertTrue(hooks.errorMessages.get(0).contains("Open the GE History tab"));
        assertEquals(1, hooks.gameMessages.size());
        assertTrue(hooks.gameMessages.get(0).contains("wipe failed"));
        assertTrue(hooks.clearedProfiles.isEmpty());
    }

    @Test
    public void wipeSingleClearsProfileAndRefreshesAccountwide() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 123L;
        hooks.historyTrades = sampleTrades();
        hooks.cursorSignatures = Arrays.asList("sig1", "sig2");
        LocalProfileWipeService service = new LocalProfileWipeService(ACCOUNTWIDE_KEY, hooks);

        service.wipeSingleLocalProfile(123L, "My Profile");

        assertEquals("My Profile", hooks.profileDisplayNames.get(123L));
        assertEquals(Boolean.TRUE, hooks.wipeBarrierArmed.get(123L));
        assertEquals(Arrays.asList("sig1", "sig2"), hooks.persistedCursors.get(123L));
        assertEquals(1, hooks.clearedProfiles.size());
        assertEquals("123|My Profile|true", hooks.clearedProfiles.get(0));
        assertEquals(Arrays.asList("123|false", "0|true"), hooks.loadLocalTradesCalls);
        assertEquals(1, hooks.refreshUiCalls);
        assertEquals(1, hooks.markDirtyCalls);
        assertEquals(1, hooks.gameMessages.size());
        assertTrue(hooks.gameMessages.get(0).contains("cleared history for My Profile"));
    }

    @Test
    public void wipeAllClearsAllProfileKeysAndAccountwide() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 300L;
        hooks.historyTrades = sampleTrades();
        hooks.cursorSignatures = Arrays.asList("baseline");
        hooks.diskProfiles.put(100L, "A");
        hooks.diskProfiles.put(200L, "B");
        hooks.profileDisplayNames.put(100L, "A");
        hooks.profileDisplayNames.put(200L, "B");
        hooks.profileDisplayNames.put(300L, "C");
        LocalProfileWipeService service = new LocalProfileWipeService(ACCOUNTWIDE_KEY, hooks);

        service.wipeAllLocalProfiles();

        assertEquals(Boolean.TRUE, hooks.wipeBarrierArmed.get(100L));
        assertEquals(Boolean.TRUE, hooks.wipeBarrierArmed.get(200L));
        assertEquals(Boolean.TRUE, hooks.wipeBarrierArmed.get(300L));
        assertEquals(Arrays.asList("baseline"), hooks.persistedCursors.get(300L));
        assertEquals(3, hooks.clearedProfiles.size());
        assertTrue(hooks.clearedProfiles.contains("100|A|false"));
        assertTrue(hooks.clearedProfiles.contains("200|B|false"));
        assertTrue(hooks.clearedProfiles.contains("300|C|false"));
        assertEquals(1, hooks.clearAccountwideCalls);
        assertEquals(1, hooks.clearAllLegacyCalls);
        assertEquals(Arrays.asList("0|true"), hooks.loadLocalTradesCalls);
        assertEquals(1, hooks.refreshUiCalls);
        assertEquals(1, hooks.markDirtyCalls);
        assertEquals(1, hooks.gameMessages.size());
        assertTrue(hooks.gameMessages.get(0).contains("all profiles"));
    }

    private static List<GeHistoryTrade> sampleTrades() {
        List<GeHistoryTrade> trades = new ArrayList<>();
        trades.add(new GeHistoryTrade(4151, false, 1, 2_500_000, 2_500_000L));
        return trades;
    }

    private static final class TestHooks implements LocalProfileWipeService.Hooks {
        private long localAccountKey;
        private List<GeHistoryTrade> historyTrades;
        private List<String> cursorSignatures = new ArrayList<>();
        private final Map<Long, String> diskProfiles = new HashMap<>();
        private final Map<Long, String> profileDisplayNames = new HashMap<>();
        private final Map<Long, Boolean> wipeBarrierArmed = new HashMap<>();
        private final Map<Long, List<String>> persistedCursors = new HashMap<>();
        private final List<String> clearedProfiles = new ArrayList<>();
        private int clearAccountwideCalls;
        private int clearAllLegacyCalls;
        private final List<String> loadLocalTradesCalls = new ArrayList<>();
        private int refreshUiCalls;
        private int markDirtyCalls;
        private final List<String> gameMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        @Override
        public long resolveLocalAccountKey() {
            return localAccountKey;
        }

        @Override
        public List<GeHistoryTrade> tryParseCurrentGeHistoryTrades() {
            return historyTrades;
        }

        @Override
        public List<String> buildGeHistoryCursorSignatures(List<GeHistoryTrade> trades) {
            return new ArrayList<>(cursorSignatures);
        }

        @Override
        public Map<Long, String> loadProfilesFromDisk() {
            return diskProfiles;
        }

        @Override
        public String resolveProfileDisplayName(long accountKey) {
            return profileDisplayNames.get(accountKey);
        }

        @Override
        public void setProfileDisplayName(long accountKey, String displayName) {
            profileDisplayNames.put(accountKey, displayName);
        }

        @Override
        public void setWipeBarrierArmed(long accountKey, boolean armed) {
            wipeBarrierArmed.put(accountKey, armed);
        }

        @Override
        public void persistGeHistoryCursor(long accountKey, List<String> cursor) {
            persistedCursors.put(accountKey, cursor == null ? null : new ArrayList<>(cursor));
        }

        @Override
        public void clearProfileData(long accountKey, String displayName, boolean clearLegacyTradeCache) {
            String label = displayName != null ? displayName : "";
            clearedProfiles.add(accountKey + "|" + label + "|" + clearLegacyTradeCache);
        }

        @Override
        public void clearAccountwideData() {
            clearAccountwideCalls++;
        }

        @Override
        public void clearAllLegacyLocalTrades() {
            clearAllLegacyCalls++;
        }

        @Override
        public void loadLocalTradesForAccount(long accountKey, boolean forceReload) {
            loadLocalTradesCalls.add(accountKey + "|" + forceReload);
        }

        @Override
        public void refreshUiAfterWipe() {
            refreshUiCalls++;
        }

        @Override
        public void markAccountwideUploadDirty() {
            markDirtyCalls++;
        }

        @Override
        public void pushGameMessage(String message) {
            gameMessages.add(message);
        }

        @Override
        public void showError(String message) {
            errorMessages.add(message);
        }
    }
}
