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
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.runelite.client.callback.ClientThread;
import org.slf4j.Logger;

final class GeLimitPluginHooks implements GeLimitFactoryService.RuntimeHooks {
    private final BooleanSupplier isClientFullyReady;
    private final Supplier<ClientThread> clientThreadSupplier;
    private final IntFunction<Integer> lookupGeLimit;
    private final Runnable onLimitsUpdated;
    private final Supplier<Logger> loggerSupplier;
    private final BooleanSupplier debugEnabled;

    GeLimitPluginHooks(
        BooleanSupplier isClientFullyReady,
        Supplier<ClientThread> clientThreadSupplier,
        IntFunction<Integer> lookupGeLimit,
        Runnable onLimitsUpdated,
        Supplier<Logger> loggerSupplier,
        BooleanSupplier debugEnabled
    ) {
        this.isClientFullyReady = isClientFullyReady;
        this.clientThreadSupplier = clientThreadSupplier;
        this.lookupGeLimit = lookupGeLimit;
        this.onLimitsUpdated = onLimitsUpdated;
        this.loggerSupplier = loggerSupplier;
        this.debugEnabled = debugEnabled;
    }

    @Override
    public boolean isClientFullyReady() {
        return isClientFullyReady != null && isClientFullyReady.getAsBoolean();
    }

    @Override
    public void invokeOnClientThread(Runnable task) {
        if (task == null) {
            return;
        }
        ClientThread clientThread = clientThreadSupplier != null ? clientThreadSupplier.get() : null;
        if (clientThread != null) {
            clientThread.invokeLater(task);
        }
    }

    @Override
    public Integer lookupGeLimit(int itemId) {
        Integer geLimit = lookupGeLimit != null ? lookupGeLimit.apply(itemId) : null;
        return geLimit != null ? geLimit : 0;
    }

    @Override
    public void onLimitsUpdated() {
        if (onLimitsUpdated != null) {
            onLimitsUpdated.run();
        }
    }

    @Override
    public void logDebug(String message) {
        if (message == null || debugEnabled == null || !debugEnabled.getAsBoolean()) {
            return;
        }
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (logger != null) {
            logger.debug(message);
        }
    }
}
