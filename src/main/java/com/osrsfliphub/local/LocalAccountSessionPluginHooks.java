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

import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;

final class LocalAccountSessionPluginHooks implements LocalAccountSessionService.Hooks {
    private final Supplier<Client> clientSupplier;
    private final LongSupplier nowMsSupplier;
    private final BiConsumer<Long, Long> mergeLocalAccountDataAction;

    LocalAccountSessionPluginHooks(
        Supplier<Client> clientSupplier,
        LongSupplier nowMsSupplier,
        BiConsumer<Long, Long> mergeLocalAccountDataAction
    ) {
        this.clientSupplier = clientSupplier;
        this.nowMsSupplier = nowMsSupplier;
        this.mergeLocalAccountDataAction = mergeLocalAccountDataAction;
    }

    @Override
    public boolean isLoggedIn() {
        Client client = resolveClient();
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    @Override
    public long readAccountHash() {
        Client client = resolveClient();
        return client != null ? client.getAccountHash() : -1L;
    }

    @Override
    public String readDisplayName() {
        Client client = resolveClient();
        return client != null && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
    }

    @Override
    public long nowMs() {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }

    @Override
    public void mergeLocalAccountData(long accountHash, long nameKey) {
        if (mergeLocalAccountDataAction != null) {
            mergeLocalAccountDataAction.accept(accountHash, nameKey);
        }
    }

    private Client resolveClient() {
        return clientSupplier != null ? clientSupplier.get() : null;
    }
}
