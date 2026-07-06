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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;

@Singleton
final class PanelRefreshCoordinator {
    private static final long REFRESH_DEBOUNCE_MILLIS = 75L;

    interface Hooks {
        boolean isEventDispatchThread();
        boolean isClientFullyReady();
        boolean isPanelVisible();
        boolean hasPanel();
        boolean isStatsTabSelected();
        void ensureSelectedProfileLoaded();
        void updateProfileHeader();
        void invokeOnClientThreadOrRun(Runnable task);
        void updateLocalItemsPanel();
        void renderLocalStats();
        void executeAsync(Runnable task);
        void logWarn(String message, Throwable error);
    }

    private final Hooks hooks;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final AtomicBoolean statsRefreshInFlight = new AtomicBoolean(false);

    @Inject
    PanelRefreshCoordinator() {
        this(new Hooks() {
            @Override
            public boolean isEventDispatchThread() {
                return EventQueue.isDispatchThread();
            }

            @Override
            public boolean isClientFullyReady() {
                return PluginAccess.plugin().runtimeUtilityServices
                    .isClientFullyReady(PluginAccess.plugin().client);
            }

            @Override
            public boolean isPanelVisible() {
                return PluginAccess.plugin().runtimeUtilityServices
                    .isPanelVisible(PluginAccess.plugin().panel);
            }

            @Override
            public boolean hasPanel() {
                return PluginAccess.plugin().panel != null;
            }

            @Override
            public boolean isStatsTabSelected() {
                FlipHubPanel panel = PluginAccess.plugin().panel;
                return panel != null && panel.isStatsTabSelected();
            }

            @Override
            public void ensureSelectedProfileLoaded() {
                PluginAccess.plugin().getProfileWorkflowService().ensureSelectedProfileLoaded();
            }

            @Override
            public void updateProfileHeader() {
                PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader();
            }

            @Override
            public void invokeOnClientThreadOrRun(Runnable task) {
                if (task == null) {
                    return;
                }
                ClientThread clientThread = PluginAccess.plugin().clientThread;
                if (clientThread != null) {
                    clientThread.invokeLater(task);
                } else {
                    task.run();
                }
            }

            @Override
            public void updateLocalItemsPanel() {
                PluginInjectorBridge.get(GeLifecyclePanelDataRuntimeService.class).updateLocalItemsPanel();
            }

            @Override
            public void renderLocalStats() {
                PluginInjectorBridge.get(GeLifecyclePanelDataRuntimeService.class).renderLocalStats();
            }

            @Override
            public void executeAsync(Runnable task) {
                PluginAccess.plugin().executeAsync(task);
            }

            @Override
            public void logWarn(String message, Throwable error) {
                if (message == null) {
                    return;
                }
                if (error != null) {
                    GeLifecyclePlugin.log.warn(message, error);
                } else {
                    GeLifecyclePlugin.log.warn(message);
                }
            }
        });
    }

    PanelRefreshCoordinator(Hooks hooks) {
        this.hooks = hooks;
    }

    void scheduleRefreshSoon(ScheduledExecutorService scheduler) {
        if (scheduler == null || hooks == null) {
            return;
        }
        refreshPending.set(true);
        if (!refreshQueued.compareAndSet(false, true)) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                do {
                    refreshPending.set(false);
                    refreshPanelData(scheduler);
                } while (refreshPending.get());
            } finally {
                refreshQueued.set(false);
                if (refreshPending.get()) {
                    scheduleRefreshSoon(scheduler);
                }
            }
        }, REFRESH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    void triggerPanelRefresh(ScheduledExecutorService scheduler) {
        if (hooks == null) {
            return;
        }
        submit(scheduler, () -> refreshPanelData(scheduler));
    }

    void triggerStatsRefresh(ScheduledExecutorService scheduler) {
        if (hooks == null) {
            return;
        }
        if (hooks.hasPanel() && !hooks.isStatsTabSelected()) {
            return;
        }
        submit(scheduler, () -> refreshStatsData(scheduler));
    }

    void refreshPanelData(ScheduledExecutorService scheduler) {
        if (hooks == null) {
            return;
        }
        if (hooks.isEventDispatchThread()) {
            submit(scheduler, () -> refreshPanelData(scheduler));
            return;
        }
        if (!hooks.isClientFullyReady()) {
            return;
        }
        if (!hooks.isPanelVisible()) {
            return;
        }
        if (refreshInFlight.getAndSet(true)) {
            return;
        }

        try {
            if (!hooks.hasPanel()) {
                return;
            }
            hooks.ensureSelectedProfileLoaded();
            hooks.updateProfileHeader();
            hooks.invokeOnClientThreadOrRun(hooks::updateLocalItemsPanel);
        } catch (RuntimeException ex) {
            hooks.logWarn("FlipHub local item refresh failed", ex);
        } finally {
            refreshInFlight.set(false);
        }
    }

    void refreshStatsData(ScheduledExecutorService scheduler) {
        if (hooks == null) {
            return;
        }
        if (hooks.isEventDispatchThread()) {
            submit(scheduler, () -> refreshStatsData(scheduler));
            return;
        }
        if (!hooks.isClientFullyReady()) {
            return;
        }
        if (!hooks.isPanelVisible() || !hooks.hasPanel() || !hooks.isStatsTabSelected()) {
            return;
        }
        if (statsRefreshInFlight.getAndSet(true)) {
            return;
        }

        try {
            hooks.renderLocalStats();
        } finally {
            statsRefreshInFlight.set(false);
        }
    }

    private void submit(ScheduledExecutorService scheduler, Runnable task) {
        if (task == null || hooks == null) {
            return;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(task);
            return;
        }
        hooks.executeAsync(task);
    }
}
