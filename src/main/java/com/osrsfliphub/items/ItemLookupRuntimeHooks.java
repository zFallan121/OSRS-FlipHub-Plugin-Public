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

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

final class ItemLookupRuntimeHooks implements ItemLookupFactoryService.RuntimeHooks {
    private final Function<String, Integer> findItemIdByExactName;
    private final IntFunction<String> lookupItemName;
    private final IntFunction<Integer> lookupGeLimit;
    private final IntFunction<Integer> lookupGuidePrice;
    private final BooleanSupplier canCacheItemNamesAsync;
    private final Consumer<Runnable> invokeOnClientThread;
    private final Runnable onItemNameCacheUpdated;

    ItemLookupRuntimeHooks(Function<String, Integer> findItemIdByExactName,
                           IntFunction<String> lookupItemName,
                           IntFunction<Integer> lookupGeLimit,
                           IntFunction<Integer> lookupGuidePrice,
                           BooleanSupplier canCacheItemNamesAsync,
                           Consumer<Runnable> invokeOnClientThread,
                           Runnable onItemNameCacheUpdated) {
        this.findItemIdByExactName = findItemIdByExactName;
        this.lookupItemName = lookupItemName;
        this.lookupGeLimit = lookupGeLimit;
        this.lookupGuidePrice = lookupGuidePrice;
        this.canCacheItemNamesAsync = canCacheItemNamesAsync;
        this.invokeOnClientThread = invokeOnClientThread;
        this.onItemNameCacheUpdated = onItemNameCacheUpdated;
    }

    @Override
    public Integer findItemIdByExactName(String name) {
        return findItemIdByExactName != null ? findItemIdByExactName.apply(name) : null;
    }

    @Override
    public String lookupItemName(int itemId) {
        return lookupItemName != null ? lookupItemName.apply(itemId) : null;
    }

    @Override
    public Integer lookupGeLimit(int itemId) {
        return lookupGeLimit != null ? lookupGeLimit.apply(itemId) : null;
    }

    @Override
    public Integer lookupGuidePrice(int itemId) {
        return lookupGuidePrice != null ? lookupGuidePrice.apply(itemId) : null;
    }

    @Override
    public boolean canCacheItemNamesAsync() {
        return canCacheItemNamesAsync != null && canCacheItemNamesAsync.getAsBoolean();
    }

    @Override
    public void invokeOnClientThread(Runnable task) {
        if (invokeOnClientThread != null) {
            invokeOnClientThread.accept(task);
        }
    }

    @Override
    public void onItemNameCacheUpdated() {
        if (onItemNameCacheUpdated != null) {
            onItemNameCacheUpdated.run();
        }
    }
}
