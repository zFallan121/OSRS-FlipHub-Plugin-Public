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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

final class AccountwideFlipHistoryPluginHooks implements AccountwideFlipHistoryService.Hooks {
    private final long accountwideKey;
    private final LongConsumer ensureProfileLoadedConsumer;
    private final LongFunction<List<LocalTradeDelta>> snapshotLocalTradeDeltasFn;
    private final BiFunction<List<LocalTradeDelta>, Long, Map<Integer, List<StatsFlipInstance>>> buildLocalHistoryFn;
    private final Supplier<Set<Long>> collectAccountwideProfileKeysSupplier;

    AccountwideFlipHistoryPluginHooks(
        long accountwideKey,
        LongConsumer ensureProfileLoadedConsumer,
        LongFunction<List<LocalTradeDelta>> snapshotLocalTradeDeltasFn,
        BiFunction<List<LocalTradeDelta>, Long, Map<Integer, List<StatsFlipInstance>>> buildLocalHistoryFn,
        Supplier<Set<Long>> collectAccountwideProfileKeysSupplier
    ) {
        this.accountwideKey = accountwideKey;
        this.ensureProfileLoadedConsumer = ensureProfileLoadedConsumer;
        this.snapshotLocalTradeDeltasFn = snapshotLocalTradeDeltasFn;
        this.buildLocalHistoryFn = buildLocalHistoryFn;
        this.collectAccountwideProfileKeysSupplier = collectAccountwideProfileKeysSupplier;
    }

    @Override
    public long accountwideKey() {
        return accountwideKey;
    }

    @Override
    public void ensureProfileLoaded(long accountKey) {
        ensureProfileLoadedConsumer.accept(accountKey);
    }

    @Override
    public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
        return snapshotLocalTradeDeltasFn.apply(accountKey);
    }

    @Override
    public Map<Integer, List<StatsFlipInstance>> buildLocalHistory(List<LocalTradeDelta> deltas, Long sinceMs) {
        return buildLocalHistoryFn.apply(deltas, sinceMs);
    }

    @Override
    public Set<Long> collectAccountwideProfileKeys() {
        return collectAccountwideProfileKeysSupplier.get();
    }
}
