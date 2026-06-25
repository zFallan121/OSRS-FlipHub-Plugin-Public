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

import java.util.Locale;
import java.util.Map;

final class LocalAccountSessionService {
    interface Hooks {
        boolean isLoggedIn();
        long readAccountHash();
        String readDisplayName();
        long nowMs();
        void mergeLocalAccountData(long accountHash, long nameKey);
    }

    private final Hooks hooks;
    private long lastMergedAccountHash = -1L;
    private long lastMergedNameKey = -1L;

    LocalAccountSessionService(Hooks hooks) {
        this.hooks = hooks;
    }

    long resolveLocalAccountKey() {
        if (hooks == null || !hooks.isLoggedIn()) {
            return -1L;
        }
        long accountHash = resolveAccountHash();
        long nameKey = resolveNameAccountKey();
        maybeMergeLocalAccounts(accountHash, nameKey);
        if (accountHash > 0) {
            return accountHash;
        }
        if (nameKey > 0) {
            return nameKey;
        }
        return -1L;
    }

    long resolveLimitAccountKey(long fallbackAccountKey) {
        if (fallbackAccountKey > 0) {
            return fallbackAccountKey;
        }
        long localAccountKey = resolveLocalAccountKey();
        if (localAccountKey > 0) {
            return localAccountKey;
        }
        return fallbackAccountKey;
    }

    long resolveAccountHash() {
        if (hooks == null || !hooks.isLoggedIn()) {
            return -1L;
        }
        try {
            long hash = hooks.readAccountHash();
            return hash > 0 ? hash : -1L;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    String resolveDisplayName() {
        if (hooks == null) {
            return null;
        }
        try {
            String name = hooks.readDisplayName();
            return name != null ? name.trim() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    void updateLocalAccountSessionStart(Map<Long, Long> localSessionStartByAccount,
                                        Object localStatsLock,
                                        long accountwideKey) {
        if (localSessionStartByAccount == null || localStatsLock == null) {
            return;
        }
        long accountKey = resolveAccountHash();
        if (accountKey <= 0) {
            return;
        }
        long nowMs = hooks != null ? hooks.nowMs() : System.currentTimeMillis();
        synchronized (localStatsLock) {
            localSessionStartByAccount.put(accountKey, nowMs);
            if (accountwideKey >= 0) {
                localSessionStartByAccount.put(accountwideKey, nowMs);
            }
        }
    }

    void ensureLocalSessionStart(Map<Long, Long> localSessionStartByAccount,
                                 Object localStatsLock,
                                 long accountKey,
                                 long nowMs) {
        if (localSessionStartByAccount == null || localStatsLock == null) {
            return;
        }
        synchronized (localStatsLock) {
            localSessionStartByAccount.putIfAbsent(accountKey, nowMs);
        }
    }

    long resolveStatsSessionStartMs(Map<Long, Long> localSessionStartByAccount,
                                    Object localStatsLock,
                                    long accountKey,
                                    long nowMs) {
        if (accountKey < 0) {
            return nowMs;
        }
        if (localSessionStartByAccount == null || localStatsLock == null) {
            return nowMs;
        }
        long sessionStartMs = nowMs;
        synchronized (localStatsLock) {
            Long storedStart = localSessionStartByAccount.get(accountKey);
            if (storedStart != null && storedStart > 0) {
                sessionStartMs = storedStart;
            } else {
                localSessionStartByAccount.put(accountKey, nowMs);
            }
        }
        return sessionStartMs;
    }

    private long resolveNameAccountKey() {
        String name = resolveDisplayName();
        if (name == null) {
            return -1L;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return -1L;
        }
        long fallback = trimmed.toLowerCase(Locale.US).hashCode();
        return fallback != 0 ? Math.abs(fallback) : 1L;
    }

    private void maybeMergeLocalAccounts(long accountHash, long nameKey) {
        if (hooks == null || accountHash <= 0 || nameKey <= 0 || accountHash == nameKey) {
            return;
        }
        if (accountHash == lastMergedAccountHash && nameKey == lastMergedNameKey) {
            return;
        }
        hooks.mergeLocalAccountData(accountHash, nameKey);
        lastMergedAccountHash = accountHash;
        lastMergedNameKey = nameKey;
    }
}
