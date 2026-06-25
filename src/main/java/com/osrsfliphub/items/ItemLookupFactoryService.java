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

final class ItemLookupFactoryService {
    interface RuntimeHooks {
        Integer findItemIdByExactName(String name);
        String lookupItemName(int itemId);
        Integer lookupGeLimit(int itemId);
        Integer lookupGuidePrice(int itemId);
        boolean canCacheItemNamesAsync();
        void invokeOnClientThread(Runnable task);
        void onItemNameCacheUpdated();
    }

    private final Map<String, Integer> itemNameLookupCache;
    private final Map<Integer, String> itemNameCache;

    ItemLookupFactoryService(Map<String, Integer> itemNameLookupCache,
                             Map<Integer, String> itemNameCache) {
        this.itemNameLookupCache = itemNameLookupCache;
        this.itemNameCache = itemNameCache;
    }

    ItemLookupService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new ItemLookupService(itemNameLookupCache, itemNameCache, null);
        }
        return new ItemLookupService(
            itemNameLookupCache,
            itemNameCache,
            new ItemLookupService.Hooks() {
                @Override
                public Integer findItemIdByExactName(String name) {
                    return runtimeHooks.findItemIdByExactName(name);
                }

                @Override
                public String lookupItemName(int itemId) {
                    return runtimeHooks.lookupItemName(itemId);
                }

                @Override
                public Integer lookupGeLimit(int itemId) {
                    return runtimeHooks.lookupGeLimit(itemId);
                }

                @Override
                public Integer lookupGuidePrice(int itemId) {
                    return runtimeHooks.lookupGuidePrice(itemId);
                }

                @Override
                public boolean canCacheItemNamesAsync() {
                    return runtimeHooks.canCacheItemNamesAsync();
                }

                @Override
                public void invokeOnClientThread(Runnable task) {
                    runtimeHooks.invokeOnClientThread(task);
                }

                @Override
                public void onItemNameCacheUpdated() {
                    runtimeHooks.onItemNameCacheUpdated();
                }
            }
        );
    }
}
