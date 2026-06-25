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
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class GeHistoryAutoSyncCoordinatorRuntimeHooks implements GeHistoryAutoSyncCoordinatorFactoryService.RuntimeHooks {
    @FunctionalInterface
    interface AddedTradesLogger {
        void log(int addedTrades, int parsedTrades, long accountKey);
    }

    private final BooleanSupplier isClientLoggedIn;
    private final LongSupplier resolveLocalAccountKey;
    private final Supplier<GeHistoryAutoSyncCoordinatorService.HistorySnapshot> readHistorySnapshot;
    private final LongSupplier nowMs;
    private final Consumer<String> pushGameMessage;
    private final AddedTradesLogger logAddedTrades;

    GeHistoryAutoSyncCoordinatorRuntimeHooks(BooleanSupplier isClientLoggedIn,
                                             LongSupplier resolveLocalAccountKey,
                                             Supplier<GeHistoryAutoSyncCoordinatorService.HistorySnapshot> readHistorySnapshot,
                                             LongSupplier nowMs,
                                             Consumer<String> pushGameMessage,
                                             AddedTradesLogger logAddedTrades) {
        this.isClientLoggedIn = isClientLoggedIn;
        this.resolveLocalAccountKey = resolveLocalAccountKey;
        this.readHistorySnapshot = readHistorySnapshot;
        this.nowMs = nowMs;
        this.pushGameMessage = pushGameMessage;
        this.logAddedTrades = logAddedTrades;
    }

    @Override
    public boolean isClientLoggedIn() {
        return isClientLoggedIn != null && isClientLoggedIn.getAsBoolean();
    }

    @Override
    public long resolveLocalAccountKey() {
        return resolveLocalAccountKey != null ? resolveLocalAccountKey.getAsLong() : -1L;
    }

    @Override
    public GeHistoryAutoSyncCoordinatorService.HistorySnapshot readHistorySnapshot() {
        return readHistorySnapshot != null ? readHistorySnapshot.get() : null;
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : 0L;
    }

    @Override
    public void pushGameMessage(String message) {
        if (pushGameMessage != null) {
            pushGameMessage.accept(message);
        }
    }

    @Override
    public void logAddedTrades(int addedTrades, int parsedTrades, long accountKey) {
        if (logAddedTrades != null) {
            logAddedTrades.log(addedTrades, parsedTrades, accountKey);
        }
    }
}
