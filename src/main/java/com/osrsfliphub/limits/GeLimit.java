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
import javax.inject.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

@Singleton
@Slf4j
final class GeLimit {

    private final int maxLookupsPerRequest;
    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final Client client;
    private final Object geLimitLock = new Object();
    private final Map<Integer, Integer> geLimitCache = new HashMap<>();
    private final Set<Integer> geLimitPending = new HashSet<>();

    @Inject
    GeLimit(ItemManager itemManager, ClientThread clientThread, Client client) {
        this.maxLookupsPerRequest = Const.MAX_GE_LIMIT_LOOKUPS_PER_REQUEST;
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.client = client;
    }

    private boolean isClientFullyReady() {
        return client != null
            && client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null
            && clientThread != null
            && itemManager != null;
    }

    private void invokeOnClientThread(Runnable task) {
        if (task != null && clientThread != null) {
            clientThread.invokeLater(task);
        }
    }

    private Integer lookupGeLimit(int itemId) {
        ItemLookup service = Bridge.get(ItemLookup.class);
        Integer geLimit = service != null ? service.lookupGeLimitSafe(itemId) : null;
        return geLimit != null ? geLimit : 0;
    }

    private void onLimitsUpdated() {
        GeLifecyclePlugin plugin = Access.plugin();
        PanelRefresh coordinator = plugin.getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.scheduleRefreshSoon(plugin.scheduler);
        }
    }

    private void logDebug(String message) {
        if (message != null && log.isDebugEnabled()) {
            log.debug(message);
        }
    }

    Integer getCachedGeLimit(int itemId) {
        synchronized (geLimitLock) {
            return geLimitCache.get(itemId);
        }
    }

    void requestGeLimits(Set<Integer> itemIds) {
        if (!isClientFullyReady()) {
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
        invokeOnClientThread(() -> {
            boolean updated = false;
            for (int itemId : missing) {
                int limit = 0;
                boolean answered = false;
                try {
                    Integer lookedUp = lookupGeLimit(itemId);
                    if (lookedUp != null) {
                        limit = lookedUp;
                    }
                    answered = true;
                } catch (RuntimeException ex) {
                    logDebug("Local GE limit lookup failed for " + itemId + ": " + ex.getMessage());
                }
                synchronized (geLimitLock) {
                    if (answered) {
                        // "This item has no limit" is an answer and has to be remembered.
                        // Recording only real limits left every limitless item looking unasked,
                        // so each refresh asked about them again and they filled the
                        // per-refresh budget ahead of items whose limit nobody knew yet. Those
                        // items never got a turn and their remaining-limit line stayed blank.
                        geLimitCache.put(itemId, limit);
                        updated |= limit > 0;
                    }
                    geLimitPending.remove(itemId);
                }
            }
            if (updated) {
                onLimitsUpdated();
            }
        });
    }
}
