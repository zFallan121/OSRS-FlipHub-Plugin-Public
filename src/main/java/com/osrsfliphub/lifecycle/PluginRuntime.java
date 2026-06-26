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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.NavigationButton;

/**
 * Singleton holder for the late-bound runtime objects that are created during
 * {@code startUp} (panel, executors, overlay) and the small pieces of volatile
 * UI state shared across services. Replaces the {@code Supplier<>}/setter lambdas
 * the old hand-rolled DI threaded through every factory. Threading helpers keep
 * the previous plugin-owned scheduler semantics (with a ForkJoinPool fallback)
 * rather than switching to RuneLite's shared scheduler.
 */
@Singleton
final class PluginRuntime {
    private final ClientThread clientThread;

    private volatile FlipHubPanel panel;
    private volatile NavigationButton navButton;
    private volatile GeOfferTimerOverlay offerTimerOverlay;
    private volatile ScheduledExecutorService scheduler;
    private volatile ExecutorService ioExecutor;

    private volatile Integer offerPreviewItemId;
    private volatile FlipHubItem offerPreviewItem;
    private volatile String currentQuery = "";
    private volatile int currentPage = 1;
    private volatile boolean bookmarkFilterEnabled = false;
    private volatile boolean panelVisible = false;
    private volatile StatsRange currentStatsRange = StatsRange.SESSION;
    private volatile StatsItemSort currentStatsSort = StatsItemSort.COMPLETION;
    private volatile boolean localTradesLoadedThisLogin = false;

    @Inject
    PluginRuntime(ClientThread clientThread) {
        this.clientThread = clientThread;
    }

    FlipHubPanel getPanel() {
        return panel;
    }

    void setPanel(FlipHubPanel panel) {
        this.panel = panel;
    }

    NavigationButton getNavButton() {
        return navButton;
    }

    void setNavButton(NavigationButton navButton) {
        this.navButton = navButton;
    }

    GeOfferTimerOverlay getOfferTimerOverlay() {
        return offerTimerOverlay;
    }

    void setOfferTimerOverlay(GeOfferTimerOverlay offerTimerOverlay) {
        this.offerTimerOverlay = offerTimerOverlay;
    }

    ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    void setIoExecutor(ExecutorService ioExecutor) {
        this.ioExecutor = ioExecutor;
    }

    Integer getOfferPreviewItemId() {
        return offerPreviewItemId;
    }

    void setOfferPreviewItemId(Integer offerPreviewItemId) {
        this.offerPreviewItemId = offerPreviewItemId;
    }

    FlipHubItem getOfferPreviewItem() {
        return offerPreviewItem;
    }

    void setOfferPreviewItem(FlipHubItem offerPreviewItem) {
        this.offerPreviewItem = offerPreviewItem;
    }

    String getCurrentQuery() {
        return currentQuery;
    }

    void setCurrentQuery(String currentQuery) {
        this.currentQuery = currentQuery;
    }

    int getCurrentPage() {
        return currentPage;
    }

    void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    boolean isBookmarkFilterEnabled() {
        return bookmarkFilterEnabled;
    }

    void setBookmarkFilterEnabled(boolean bookmarkFilterEnabled) {
        this.bookmarkFilterEnabled = bookmarkFilterEnabled;
    }

    boolean isPanelVisible() {
        return panelVisible;
    }

    void setPanelVisible(boolean panelVisible) {
        this.panelVisible = panelVisible;
    }

    StatsRange getCurrentStatsRange() {
        return currentStatsRange;
    }

    void setCurrentStatsRange(StatsRange currentStatsRange) {
        this.currentStatsRange = currentStatsRange;
    }

    StatsItemSort getCurrentStatsSort() {
        return currentStatsSort;
    }

    void setCurrentStatsSort(StatsItemSort currentStatsSort) {
        this.currentStatsSort = currentStatsSort;
    }

    boolean isLocalTradesLoadedThisLogin() {
        return localTradesLoadedThisLogin;
    }

    void setLocalTradesLoadedThisLogin(boolean localTradesLoadedThisLogin) {
        this.localTradesLoadedThisLogin = localTradesLoadedThisLogin;
    }

    void invokeOnClientThread(Runnable task) {
        if (clientThread != null && task != null) {
            clientThread.invokeLater(task);
        }
    }

    void executeOnScheduler(Runnable task) {
        ScheduledExecutorService activeScheduler = scheduler;
        if (activeScheduler != null && task != null) {
            activeScheduler.execute(task);
        }
    }

    void executeAsync(Runnable task) {
        if (task == null) {
            return;
        }
        ScheduledExecutorService activeScheduler = scheduler;
        if (activeScheduler != null && !activeScheduler.isShutdown()) {
            activeScheduler.execute(task);
            return;
        }
        ForkJoinPool.commonPool().execute(task);
    }

    void executeIo(Runnable task) {
        if (task == null) {
            return;
        }
        ExecutorService activeIoExecutor = ioExecutor;
        if (activeIoExecutor != null && !activeIoExecutor.isShutdown()) {
            activeIoExecutor.execute(task);
            return;
        }
        executeAsync(task);
    }
}
