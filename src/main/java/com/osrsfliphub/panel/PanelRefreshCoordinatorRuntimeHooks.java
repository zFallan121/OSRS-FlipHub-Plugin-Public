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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PanelRefreshCoordinatorRuntimeHooks implements PanelRefreshCoordinatorFactoryService.RuntimeHooks {
    private final BooleanSupplier eventDispatchThread;
    private final BooleanSupplier clientFullyReady;
    private final BooleanSupplier panelVisible;
    private final BooleanSupplier hasPanel;
    private final BooleanSupplier statsTabSelected;
    private final Runnable ensureSelectedProfileLoaded;
    private final Runnable updateProfileHeader;
    private final Consumer<Runnable> invokeOnClientThreadOrRun;
    private final Runnable updateLocalItemsPanel;
    private final Runnable renderLocalStats;
    private final Consumer<Runnable> executeAsync;
    private final BiConsumer<String, Throwable> logWarn;

    PanelRefreshCoordinatorRuntimeHooks(BooleanSupplier eventDispatchThread,
                                        BooleanSupplier clientFullyReady,
                                        BooleanSupplier panelVisible,
                                        BooleanSupplier hasPanel,
                                        BooleanSupplier statsTabSelected,
                                        Runnable ensureSelectedProfileLoaded,
                                        Runnable updateProfileHeader,
                                        Consumer<Runnable> invokeOnClientThreadOrRun,
                                        Runnable updateLocalItemsPanel,
                                        Runnable renderLocalStats,
                                        Consumer<Runnable> executeAsync,
                                        BiConsumer<String, Throwable> logWarn) {
        this.eventDispatchThread = eventDispatchThread;
        this.clientFullyReady = clientFullyReady;
        this.panelVisible = panelVisible;
        this.hasPanel = hasPanel;
        this.statsTabSelected = statsTabSelected;
        this.ensureSelectedProfileLoaded = ensureSelectedProfileLoaded;
        this.updateProfileHeader = updateProfileHeader;
        this.invokeOnClientThreadOrRun = invokeOnClientThreadOrRun;
        this.updateLocalItemsPanel = updateLocalItemsPanel;
        this.renderLocalStats = renderLocalStats;
        this.executeAsync = executeAsync;
        this.logWarn = logWarn;
    }

    @Override
    public boolean isEventDispatchThread() {
        return eventDispatchThread != null && eventDispatchThread.getAsBoolean();
    }

    @Override
    public boolean isClientFullyReady() {
        return clientFullyReady != null && clientFullyReady.getAsBoolean();
    }

    @Override
    public boolean isPanelVisible() {
        return panelVisible != null && panelVisible.getAsBoolean();
    }

    @Override
    public boolean hasPanel() {
        return hasPanel != null && hasPanel.getAsBoolean();
    }

    @Override
    public boolean isStatsTabSelected() {
        return statsTabSelected != null && statsTabSelected.getAsBoolean();
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
        if (invokeOnClientThreadOrRun != null) {
            invokeOnClientThreadOrRun.accept(task);
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
        if (logWarn != null) {
            logWarn.accept(message, error);
        }
    }
}
