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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LegacyLocalTradesFilterService {
    @FunctionalInterface
    interface WipeBarrierState {
        boolean isWipeBarrierArmed(long accountKey);
    }

    private final WipeBarrierState wipeBarrierState;

    @Inject
    LegacyLocalTradesFilterService() {
        this(accountKey -> {
            GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
            return store != null && store.isWipeBarrierArmed(accountKey);
        });
    }

    LegacyLocalTradesFilterService(WipeBarrierState wipeBarrierState) {
        this.wipeBarrierState = wipeBarrierState;
    }

    Map<String, String> filter(Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }
        Map<String, String> filtered = new HashMap<>();
        Set<String> excludedValues = new HashSet<>();

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            String value = entry.getValue();
            Long hash = parseLegacyLocalTradesHashKey(key);
            if (hash != null && hash > 0 && isWipeBarrierArmed(hash)) {
                if (value != null && !value.trim().isEmpty()) {
                    excludedValues.add(value);
                }
            }
        }

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && excludedValues.contains(value)) {
                continue;
            }
            Long hash = parseLegacyLocalTradesHashKey(key);
            if (hash != null && hash > 0 && isWipeBarrierArmed(hash)) {
                continue;
            }
            if (key != null && value != null) {
                filtered.put(key, value);
            }
        }
        return filtered;
    }

    private boolean isWipeBarrierArmed(long accountKey) {
        return wipeBarrierState != null && wipeBarrierState.isWipeBarrierArmed(accountKey);
    }

    private static Long parseLegacyLocalTradesHashKey(String rawKey) {
        if (rawKey == null || rawKey.trim().isEmpty()) {
            return null;
        }
        String key = rawKey.trim();
        String numericPart = key.startsWith("hash_") ? key.substring(5) : key;
        if (numericPart.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(numericPart);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
