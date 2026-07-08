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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.http.api.item.ItemPrice;

@Singleton
final class ItemLookupService {
    private final Map<String, Integer> itemNameLookupCache;
    private final Map<Integer, String> itemNameCache;
    private final ItemManager itemManager;
    private final ClientThread clientThread;

    @Inject
    ItemLookupService(ItemManager itemManager, ClientThread clientThread, PluginState state) {
        this.itemNameLookupCache = state.getItemNameLookupCache();
        this.itemNameCache = state.getItemNameCache();
        this.itemManager = itemManager;
        this.clientThread = clientThread;
    }

    private Integer findItemIdByExactName(String name) {
        if (name == null || itemManager == null) {
            return null;
        }
        for (ItemPrice price : itemManager.search(name)) {
            if (price.getName() != null && price.getName().equalsIgnoreCase(name)) {
                return price.getId();
            }
        }
        return null;
    }

    private String lookupItemName(int itemId) {
        if (itemManager == null || itemId <= 0) {
            return null;
        }
        ItemComposition composition = itemManager.getItemComposition(itemId);
        return composition != null ? composition.getName() : null;
    }

    private Integer lookupGeLimit(int itemId) {
        if (itemManager == null || itemId <= 0) {
            return null;
        }
        ItemStats stats = itemManager.getItemStats(itemId);
        if (stats != null && stats.getGeLimit() > 0) {
            return stats.getGeLimit();
        }
        return null;
    }

    private Integer lookupGuidePrice(int itemId) {
        if (itemManager == null || itemId <= 0) {
            return null;
        }
        int guidePrice = itemManager.getItemPrice(itemId);
        return guidePrice > 0 ? guidePrice : null;
    }

    private boolean canCacheItemNamesAsync() {
        return itemManager != null && clientThread != null;
    }

    private void invokeOnClientThread(Runnable task) {
        if (task != null && clientThread != null) {
            clientThread.invokeLater(task);
        }
    }

    private void onItemNameCacheUpdated() {
        GeLifecyclePlugin plugin = PluginAccess.plugin();
        PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.scheduleRefreshSoon(plugin.scheduler);
            coordinator.triggerStatsRefresh(plugin.scheduler);
        }
    }

    int resolveItemIdFromName(String name) {
        if (name == null) {
            return -1;
        }
        String key = name.trim().toLowerCase();
        if (key.isEmpty()) {
            return -1;
        }
        Integer cached = itemNameLookupCache != null ? itemNameLookupCache.get(key) : null;
        if (cached != null) {
            return cached;
        }
        try {
            Integer resolved = findItemIdByExactName(name);
            if (resolved != null && resolved > 0) {
                if (itemNameLookupCache != null) {
                    itemNameLookupCache.put(key, resolved);
                }
                return resolved;
            }
        } catch (RuntimeException ignored) {
        }
        return -1;
    }

    String lookupItemNameSafe(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        try {
            return lookupItemName(itemId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    Integer lookupGeLimitSafe(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        try {
            Integer geLimit = lookupGeLimit(itemId);
            return geLimit != null && geLimit > 0 ? geLimit : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    Integer lookupGuidePriceSafe(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        try {
            Integer guidePrice = lookupGuidePrice(itemId);
            return guidePrice != null && guidePrice > 0 ? guidePrice : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    void cacheItemName(int itemId) {
        if (itemId <= 0 || !canCacheItemNamesAsync()) {
            return;
        }
        if (itemNameCache != null && itemNameCache.containsKey(itemId)) {
            return;
        }
        invokeOnClientThread(() -> {
            try {
                String name = lookupItemNameSafe(itemId);
                if (name == null || name.trim().isEmpty()) {
                    return;
                }
                if (itemNameCache == null) {
                    return;
                }
                if (itemNameCache.putIfAbsent(itemId, name) == null) {
                    onItemNameCacheUpdated();
                }
            } catch (AssertionError ignored) {
            } catch (RuntimeException ignored) {
            }
        });
    }

    String getCachedItemName(int itemId) {
        if (itemId <= 0 || itemNameCache == null) {
            return null;
        }
        return itemNameCache.get(itemId);
    }
}
