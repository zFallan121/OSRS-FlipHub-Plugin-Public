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

import java.awt.EventQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.callback.ClientThread;
import org.slf4j.Logger;

final class PanelRefreshCoordinatorPluginHooks implements PanelRefreshCoordinatorFactoryService.RuntimeHooks {
    private final BooleanSupplier isClientFullyReady;
    private final BooleanSupplier isPanelVisible;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Runnable ensureSelectedProfileLoaded;
    private final Runnable updateProfileHeader;
    private final Supplier<ClientThread> clientThreadSupplier;
    private final Runnable updateLocalItemsPanel;
    private final Runnable renderLocalStats;
    private final Consumer<Runnable> executeAsync;
    private final Supplier<Logger> loggerSupplier;

    PanelRefreshCoordinatorPluginHooks(
        BooleanSupplier isClientFullyReady,
        BooleanSupplier isPanelVisible,
        Supplier<FlipHubPanel> panelSupplier,
        Runnable ensureSelectedProfileLoaded,
        Runnable updateProfileHeader,
        Supplier<ClientThread> clientThreadSupplier,
        Runnable updateLocalItemsPanel,
        Runnable renderLocalStats,
        Consumer<Runnable> executeAsync,
        Supplier<Logger> loggerSupplier
    ) {
        this.isClientFullyReady = isClientFullyReady;
        this.isPanelVisible = isPanelVisible;
        this.panelSupplier = panelSupplier;
        this.ensureSelectedProfileLoaded = ensureSelectedProfileLoaded;
        this.updateProfileHeader = updateProfileHeader;
        this.clientThreadSupplier = clientThreadSupplier;
        this.updateLocalItemsPanel = updateLocalItemsPanel;
        this.renderLocalStats = renderLocalStats;
        this.executeAsync = executeAsync;
        this.loggerSupplier = loggerSupplier;
    }

    @Override
    public boolean isEventDispatchThread() {
        return EventQueue.isDispatchThread();
    }

    @Override
    public boolean isClientFullyReady() {
        return isClientFullyReady != null && isClientFullyReady.getAsBoolean();
    }

    @Override
    public boolean isPanelVisible() {
        return isPanelVisible != null && isPanelVisible.getAsBoolean();
    }

    @Override
    public boolean hasPanel() {
        return panelSupplier != null && panelSupplier.get() != null;
    }

    @Override
    public boolean isStatsTabSelected() {
        FlipHubPanel panel = panelSupplier != null ? panelSupplier.get() : null;
        return panel != null && panel.isStatsTabSelected();
    }

    @Override
    public void ensureSelectedProfileLoaded() {
        if (ensureSelectedProfileLoaded != null) {
            ensureSelectedProfileLoaded.run();
        }
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
    }

    @Override
    public void invokeOnClientThreadOrRun(Runnable task) {
        if (task == null) {
            return;
        }
        ClientThread clientThread = clientThreadSupplier != null ? clientThreadSupplier.get() : null;
        if (clientThread != null) {
            clientThread.invokeLater(task);
        } else {
            task.run();
        }
    }

    @Override
    public void updateLocalItemsPanel() {
        if (updateLocalItemsPanel != null) {
            updateLocalItemsPanel.run();
        }
    }

    @Override
    public void renderLocalStats() {
        if (renderLocalStats != null) {
            renderLocalStats.run();
        }
    }

    @Override
    public void executeAsync(Runnable task) {
        if (executeAsync != null) {
            executeAsync.accept(task);
        }
    }

    @Override
    public void logWarn(String message, Throwable error) {
        if (message == null) {
            return;
        }
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (logger == null) {
            return;
        }
        if (error != null) {
            logger.warn(message, error);
        } else {
            logger.warn(message);
        }
    }
}
