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

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

final class RuneLiteItemLookupPluginHooks implements ItemLookupFactoryService.RuntimeHooks {
    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final Runnable scheduleRefreshSoon;
    private final Runnable triggerStatsRefresh;

    RuneLiteItemLookupPluginHooks(
        ItemManager itemManager,
        ClientThread clientThread,
        Runnable scheduleRefreshSoon,
        Runnable triggerStatsRefresh
    ) {
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.triggerStatsRefresh = triggerStatsRefresh;
    }

    @Override
    public Integer findItemIdByExactName(String name) {
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

    @Override
    public String lookupItemName(int itemId) {
        if (itemManager == null || itemId <= 0) {
            return null;
        }
        net.runelite.api.ItemComposition composition = itemManager.getItemComposition(itemId);
        return composition != null ? composition.getName() : null;
    }

    @Override
    public Integer lookupGeLimit(int itemId) {
        if (itemManager == null || itemId <= 0) {
            return null;
        }
        net.runelite.client.game.ItemStats stats = itemManager.getItemStats(itemId);
        if (stats != null && stats.getGeLimit() > 0) {
            return stats.getGeLimit();
        }
        return null;
    }

    @Override
    public Integer lookupGuidePrice(int itemId) {
        if (itemManager == null || itemId <= 0) {
            return null;
        }
        int guidePrice = itemManager.getItemPrice(itemId);
        return guidePrice > 0 ? guidePrice : null;
    }

    @Override
    public boolean canCacheItemNamesAsync() {
        return itemManager != null && clientThread != null;
    }

    @Override
    public void invokeOnClientThread(Runnable task) {
        if (task == null || clientThread == null) {
            return;
        }
        clientThread.invokeLater(task);
    }

    @Override
    public void onItemNameCacheUpdated() {
        if (scheduleRefreshSoon != null) {
            scheduleRefreshSoon.run();
        }
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }
}
