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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class ProfileLoginService {
    interface Hooks {
        void putProfileDisplayName(long accountHash, String displayName);
        void executeAsync(Runnable task);
        void loadLocalTradesAsync(long accountHash);
        void persistProfileSelectionState();
        void updateProfileOptionsUi();
        void updateProfileHeader();
    }

    private final Hooks hooks;

    @Inject
    ProfileLoginService(PluginState pluginState) {
        this(new Hooks() {
            @Override
            public void putProfileDisplayName(long accountHash, String displayName) {
                if (accountHash <= 0 || displayName == null || displayName.trim().isEmpty()) {
                    return;
                }
                pluginState.getProfileDisplayNames().put(accountHash, displayName.trim());
            }

            @Override
            public void executeAsync(Runnable task) {
                PluginAccess.plugin().executeAsync(task);
            }

            @Override
            public void loadLocalTradesAsync(long accountHash) {
                PluginAccess.plugin().getLocalTradesRuntimeService().loadLocalTradesAsync(accountHash);
            }

            @Override
            public void persistProfileSelectionState() {
                PluginAccess.plugin().getProfileWorkflowService().persistProfileSelectionState();
            }

            @Override
            public void updateProfileOptionsUi() {
                PluginAccess.plugin().getProfileWorkflowService().updateProfileOptionsUI();
            }

            @Override
            public void updateProfileHeader() {
                PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader();
            }
        });
    }

    ProfileLoginService(Hooks hooks) {
        this.hooks = hooks;
    }

    void handleLogin(ProfileSelectionState profileSelection, long accountHash, String displayName) {
        if (hooks == null || profileSelection == null || accountHash <= 0) {
            return;
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            hooks.putProfileDisplayName(accountHash, displayName.trim());
        }
        hooks.executeAsync(() -> hooks.loadLocalTradesAsync(accountHash));
        if (profileSelection.updateForLogin(accountHash)) {
            hooks.persistProfileSelectionState();
        }
        hooks.updateProfileOptionsUi();
        hooks.updateProfileHeader();
    }
}
