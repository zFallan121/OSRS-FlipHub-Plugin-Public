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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelRefreshCoordinatorFactoryServiceTest {
    @Test
    public void createBuildsCoordinatorThatDelegatesToRuntimeHooks() {
        PanelRefreshCoordinatorFactoryService factory = new PanelRefreshCoordinatorFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.clientFullyReady = true;
        hooks.panelVisible = true;
        hooks.hasPanel = true;
        hooks.statsTabSelected = true;

        PanelRefreshCoordinator coordinator = factory.create(hooks);
        coordinator.triggerPanelRefresh(null);
        coordinator.triggerStatsRefresh(null);

        assertEquals(1, hooks.ensureSelectedProfileLoadedCalls);
        assertEquals(1, hooks.updateProfileHeaderCalls);
        assertEquals(1, hooks.updateLocalItemsPanelCalls);
        assertEquals(1, hooks.renderLocalStatsCalls);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopCoordinator() {
        PanelRefreshCoordinatorFactoryService factory = new PanelRefreshCoordinatorFactoryService();
        PanelRefreshCoordinator coordinator = factory.create(null);

        coordinator.triggerPanelRefresh(null);
        coordinator.triggerStatsRefresh(null);
        coordinator.refreshPanelData(null);
        coordinator.refreshStatsData(null);
    }

    private static final class TestRuntimeHooks implements PanelRefreshCoordinatorFactoryService.RuntimeHooks {
        private boolean eventDispatchThread;
        private boolean clientFullyReady;
        private boolean panelVisible;
        private boolean hasPanel;
        private boolean statsTabSelected;
        private int ensureSelectedProfileLoadedCalls;
        private int updateProfileHeaderCalls;
        private int updateLocalItemsPanelCalls;
        private int renderLocalStatsCalls;

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
