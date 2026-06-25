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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PanelRefreshCoordinatorTest {
    @Test
    public void triggerPanelRefreshRunsLocalItemsUpdateWhenEligible() {
        TestHooks hooks = new TestHooks();
        hooks.clientFullyReady = true;
        hooks.panelVisible = true;
        hooks.hasPanel = true;
        hooks.statsTabSelected = true;
        PanelRefreshCoordinator coordinator = new PanelRefreshCoordinator(hooks);

        coordinator.triggerPanelRefresh(null);

        assertEquals(1, hooks.ensureSelectedProfileLoadedCalls);
        assertEquals(1, hooks.updateProfileHeaderCalls);
        assertEquals(1, hooks.updateLocalItemsPanelCalls);
    }

    @Test
    public void triggerStatsRefreshSkipsWhenStatsTabNotSelected() {
        TestHooks hooks = new TestHooks();
        hooks.clientFullyReady = true;
        hooks.panelVisible = true;
        hooks.hasPanel = true;
        hooks.statsTabSelected = false;
        PanelRefreshCoordinator coordinator = new PanelRefreshCoordinator(hooks);

        coordinator.triggerStatsRefresh(null);

        assertEquals(0, hooks.renderLocalStatsCalls);
    }

    @Test
    public void refreshStatsDataRunsWhenEligible() {
        TestHooks hooks = new TestHooks();
        hooks.clientFullyReady = true;
        hooks.panelVisible = true;
        hooks.hasPanel = true;
        hooks.statsTabSelected = true;
        PanelRefreshCoordinator coordinator = new PanelRefreshCoordinator(hooks);

        coordinator.refreshStatsData(null);

        assertEquals(1, hooks.renderLocalStatsCalls);
    }

    @Test
    public void scheduleRefreshSoonCoalescesPendingRefresh() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.clientFullyReady = true;
        hooks.panelVisible = true;
        hooks.hasPanel = true;
        hooks.statsTabSelected = true;
        PanelRefreshCoordinator coordinator = new PanelRefreshCoordinator(hooks);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        try {
            coordinator.scheduleRefreshSoon(scheduler);
            coordinator.scheduleRefreshSoon(scheduler);

            Thread.sleep(300L);

            assertEquals(1, hooks.updateLocalItemsPanelCalls);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scheduleRefreshSoonRunsTrailingRefreshWhenUpdateArrivesInFlight() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.clientFullyReady = true;
        hooks.panelVisible = true;
        hooks.hasPanel = true;
        hooks.statsTabSelected = true;
        hooks.blockFirstUpdate = true;
        hooks.firstUpdateEntered = new CountDownLatch(1);
        hooks.allowFirstUpdateToFinish = new CountDownLatch(1);
        PanelRefreshCoordinator coordinator = new PanelRefreshCoordinator(hooks);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        try {
            coordinator.scheduleRefreshSoon(scheduler);
            assertTrue(hooks.firstUpdateEntered.await(1, TimeUnit.SECONDS));

            coordinator.scheduleRefreshSoon(scheduler);
            hooks.allowFirstUpdateToFinish.countDown();

            long deadline = System.currentTimeMillis() + 1500L;
            while (System.currentTimeMillis() < deadline && hooks.updateLocalItemsPanelCalls < 2) {
                Thread.sleep(20L);
            }
            assertEquals(2, hooks.updateLocalItemsPanelCalls);
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static final class TestHooks implements PanelRefreshCoordinator.Hooks {
        private boolean eventDispatchThread;
        private boolean clientFullyReady;
        private boolean panelVisible;
        private boolean hasPanel;
        private boolean statsTabSelected;
        private int ensureSelectedProfileLoadedCalls;
        private int updateProfileHeaderCalls;
        private int updateLocalItemsPanelCalls;
        private int renderLocalStatsCalls;
        private boolean blockFirstUpdate;
        private CountDownLatch firstUpdateEntered;
        private CountDownLatch allowFirstUpdateToFinish;

        @Override
        public boolean isEventDispatchThread() {
            return eventDispatchThread;
        }

        @Override
        public boolean isClientFullyReady() {
            return clientFullyReady;
        }

        @Override
        public boolean isPanelVisible() {
            return panelVisible;
        }

        @Override
        public boolean hasPanel() {
            return hasPanel;
        }

        @Override
        public boolean isStatsTabSelected() {
            return statsTabSelected;
        }

        @Override
        public void ensureSelectedProfileLoaded() {
            ensureSelectedProfileLoadedCalls++;
        }

        @Override
        public void updateProfileHeader() {
            updateProfileHeaderCalls++;
        }

        @Override
        public void invokeOnClientThreadOrRun(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void updateLocalItemsPanel() {
            updateLocalItemsPanelCalls++;
            if (blockFirstUpdate && updateLocalItemsPanelCalls == 1) {
                if (firstUpdateEntered != null) {
                    firstUpdateEntered.countDown();
                }
                if (allowFirstUpdateToFinish != null) {
                    try {
                        allowFirstUpdateToFinish.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        @Override
        public void renderLocalStats() {
            renderLocalStatsCalls++;
        }

        @Override
        public void executeAsync(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void logWarn(String message, Throwable error) {
        }
    }
}
