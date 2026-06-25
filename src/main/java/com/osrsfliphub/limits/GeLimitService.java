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

final class GeLimitService {
    interface Hooks {
        boolean isClientFullyReady();
        void invokeOnClientThread(Runnable task);
        Integer lookupGeLimit(int itemId);
        void onLimitsUpdated();
        void logDebug(String message);
    }

    private final int maxLookupsPerRequest;
    private final Hooks hooks;
    private final Object geLimitLock = new Object();
    private final Map<Integer, Integer> geLimitCache = new HashMap<>();
    private final Set<Integer> geLimitPending = new HashSet<>();

    GeLimitService(int maxLookupsPerRequest, Hooks hooks) {
        this.maxLookupsPerRequest = maxLookupsPerRequest;
        this.hooks = hooks;
    }

    Integer getCachedGeLimit(int itemId) {
        synchronized (geLimitLock) {
            return geLimitCache.get(itemId);
        }
    }

    void requestGeLimits(Set<Integer> itemIds) {
        if (hooks == null || !hooks.isClientFullyReady()) {
            return;
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        List<Integer> missing = new ArrayList<>();
        synchronized (geLimitLock) {
            for (int itemId : itemIds) {
                if (missing.size() >= maxLookupsPerRequest) {
                    break;
                }
                if (itemId <= 0) {
                    continue;
                }
                if (geLimitCache.containsKey(itemId) || geLimitPending.contains(itemId)) {
                    continue;
                }
                geLimitPending.add(itemId);
                missing.add(itemId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        hooks.invokeOnClientThread(() -> {
            boolean updated = false;
            for (int itemId : missing) {
                int limit = 0;
                try {
                    Integer lookedUp = hooks.lookupGeLimit(itemId);
                    if (lookedUp != null) {
                        limit = lookedUp;
                    }
                } catch (RuntimeException ex) {
                    hooks.logDebug("Local GE limit lookup failed for " + itemId + ": " + ex.getMessage());
                }
                synchronized (geLimitLock) {
                    if (limit > 0) {
                        geLimitCache.put(itemId, limit);
                        updated = true;
                    }
                    geLimitPending.remove(itemId);
                }
            }
            if (updated) {
                hooks.onLimitsUpdated();
            }
        });
    }
}
