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

final class PanelRefreshCoordinatorFactoryService {
    interface RuntimeHooks {
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

    PanelRefreshCoordinator create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new PanelRefreshCoordinator(null);
        }
        return new PanelRefreshCoordinator(new PanelRefreshCoordinator.Hooks() {
            @Override
            public boolean isEventDispatchThread() {
                return runtimeHooks.isEventDispatchThread();
            }

            @Override
            public boolean isClientFullyReady() {
                return runtimeHooks.isClientFullyReady();
            }

            @Override
            public boolean isPanelVisible() {
                return runtimeHooks.isPanelVisible();
            }

            @Override
            public boolean hasPanel() {
                return runtimeHooks.hasPanel();
            }

            @Override
            public boolean isStatsTabSelected() {
                return runtimeHooks.isStatsTabSelected();
            }

            @Override
            public void ensureSelectedProfileLoaded() {
                runtimeHooks.ensureSelectedProfileLoaded();
            }

            @Override
            public void updateProfileHeader() {
                runtimeHooks.updateProfileHeader();
            }

            @Override
            public void invokeOnClientThreadOrRun(Runnable task) {
                runtimeHooks.invokeOnClientThreadOrRun(task);
            }

            @Override
            public void updateLocalItemsPanel() {
                runtimeHooks.updateLocalItemsPanel();
            }

            @Override
            public void renderLocalStats() {
                runtimeHooks.renderLocalStats();
            }

            @Override
            public void executeAsync(Runnable task) {
                runtimeHooks.executeAsync(task);
            }

            @Override
            public void logWarn(String message, Throwable error) {
                runtimeHooks.logWarn(message, error);
            }
        });
    }
}
