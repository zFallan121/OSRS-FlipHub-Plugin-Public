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

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import org.slf4j.Logger;

final class GeHistoryAutoSyncCoordinatorPluginHooks implements GeHistoryAutoSyncCoordinatorFactoryService.RuntimeHooks {
    private final Supplier<Client> clientSupplier;
    private final int historyGroupId;
    private final int historyContainerChildId;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Consumer<String> pushGameMessage;
    private final Supplier<Logger> loggerSupplier;

    GeHistoryAutoSyncCoordinatorPluginHooks(
        Supplier<Client> clientSupplier,
        int historyGroupId,
        int historyContainerChildId,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Consumer<String> pushGameMessage,
        Supplier<Logger> loggerSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.historyGroupId = historyGroupId;
        this.historyContainerChildId = historyContainerChildId;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.pushGameMessage = pushGameMessage;
        this.loggerSupplier = loggerSupplier;
    }

    @Override
    public boolean isClientLoggedIn() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    @Override
    public long resolveLocalAccountKey() {
        LocalAccountSessionService service = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return service != null ? service.resolveLocalAccountKey() : -1L;
    }

    @Override
    public GeHistoryAutoSyncCoordinatorService.HistorySnapshot readHistorySnapshot() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        Widget historyContainer = client != null
            ? client.getWidget(historyGroupId, historyContainerChildId)
            : null;
        if (historyContainer == null || historyContainer.isHidden()) {
            return new GeHistoryAutoSyncCoordinatorService.HistorySnapshot(false, null);
        }
        return new GeHistoryAutoSyncCoordinatorService.HistorySnapshot(
            true,
            historyContainer.getDynamicChildren()
        );
    }

    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }

    @Override
    public void pushGameMessage(String message) {
        if (pushGameMessage != null) {
            pushGameMessage.accept(message);
        }
    }

    @Override
    public void logAddedTrades(int addedTrades, int parsedTrades, long accountKey) {
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (logger != null) {
            logger.info(
                "GE history auto-sync added {} missing trades ({} parsed) for account {}",
                addedTrades,
                parsedTrades,
                accountKey
            );
        }
    }
}
