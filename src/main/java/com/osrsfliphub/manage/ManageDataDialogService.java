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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

@Singleton
final class ManageDataDialogService {
    interface Hooks {
        boolean hasPanel();
        void invokeOnUiThread(Runnable task);
        long resolveSelectedProfileKey();
        String resolveSelectedProfileLabel();
        boolean isLinked();
        ManageDataCommandService getManageDataCommandService();
        int showOptionDialog(String body, Object[] options, Object defaultOption);
        String showInputDialog(String body, String title);
        void showError(String message);
        void invokeOnClientThread(Runnable task);
        void wipeSingleLocalProfile(long accountKey, String label);
        void wipeAllLocalProfiles();
        void wipeWebsiteStatsAsync();
    }

    private final long accountwideKey;
    private final Hooks hooks;

    @Inject
    ManageDataDialogService() {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY, productionHooks());
    }

    ManageDataDialogService(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
    }

    private static ProfileSelectionPresentationFacadeService profileSelectionFacade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    private static GeLifecycleManageDataServices manageDataServices() {
        return PluginAccess.plugin().getEventManageHistoryServices().getManageDataServices();
    }

    private static Hooks productionHooks() {
        return new Hooks() {
            @Override
            public boolean hasPanel() {
                return PluginAccess.plugin().panel != null;
            }

            @Override
            public void invokeOnUiThread(Runnable task) {
                if (task != null) {
                    SwingUtilities.invokeLater(task);
                }
            }

            @Override
            public long resolveSelectedProfileKey() {
                ProfileSelectionPresentationFacadeService service = profileSelectionFacade();
                return service != null ? service.resolveSelectedProfileKey() : -1L;
            }

            @Override
            public String resolveSelectedProfileLabel() {
                ProfileSelectionPresentationFacadeService service = profileSelectionFacade();
                return service != null ? service.resolveSelectedProfileLabel() : "";
            }

            @Override
            public boolean isLinked() {
                ProfileSelectionPresentationFacadeService service = profileSelectionFacade();
                return service != null && service.isLinked();
            }

            @Override
            public ManageDataCommandService getManageDataCommandService() {
                return PluginInjectorBridge.get(ManageDataCommandService.class);
            }

            @Override
            public int showOptionDialog(String body, Object[] options, Object defaultOption) {
                return JOptionPane.showOptionDialog(
                    PluginAccess.plugin().panel,
                    body,
                    "FlipHub Manage Data",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    defaultOption);
            }

            @Override
            public String showInputDialog(String body, String title) {
                return JOptionPane.showInputDialog(
                    PluginAccess.plugin().panel, body, title, JOptionPane.WARNING_MESSAGE);
            }

            @Override
            public void showError(String message) {
                PluginAccess.plugin().getProfileWorkflowService().showManageDataError(message);
            }

            @Override
            public void invokeOnClientThread(Runnable task) {
                PluginAccess.plugin().invokeOnClientThread(task);
            }

            @Override
            public void wipeSingleLocalProfile(long accountKey, String label) {
                LocalProfileWipeService service = manageDataServices().getLocalProfileWipeService();
                if (service != null) {
                    service.wipeSingleLocalProfile(accountKey, label);
                }
            }

            @Override
            public void wipeAllLocalProfiles() {
                LocalProfileWipeService service = manageDataServices().getLocalProfileWipeService();
                if (service != null) {
                    service.wipeAllLocalProfiles();
                }
            }

            @Override
            public void wipeWebsiteStatsAsync() {
                WebsiteStatsWipeService service = manageDataServices().getWebsiteStatsWipeService();
                if (service != null) {
                    service.wipeWebsiteStatsAsync();
                }
            }
        };
    }

    void showManageDataDialog() {
        if (hooks == null || !hooks.hasPanel()) {
            return;
        }
        hooks.invokeOnUiThread(() -> {
            long selectedKey = hooks.resolveSelectedProfileKey();
            String selectedLabel = hooks.resolveSelectedProfileLabel();
            boolean linked = hooks.isLinked();
            ManageDataCommandService commandService = hooks.getManageDataCommandService();
            if (commandService == null) {
                hooks.showError("Manage Data is unavailable right now.");
                return;
            }

            ManageDataCommandService.DialogModel dialogModel = commandService.buildDialog(selectedLabel, linked);
            Object[] options = dialogModel.options.toArray();
            int choice = hooks.showOptionDialog(dialogModel.body, options, dialogModel.defaultOption);
            if (choice < 0 || choice >= options.length) {
                return;
            }

            ManageDataCommandService.Action action = commandService.resolveAction(options[choice]);
            if (action == ManageDataCommandService.Action.WIPE_SELECTED_PROFILE) {
                handleWipeSelectedProfile(commandService, selectedKey, selectedLabel);
            } else if (action == ManageDataCommandService.Action.WIPE_ALL_LOCAL_PROFILES) {
                handleWipeAllProfiles(commandService);
            } else if (action == ManageDataCommandService.Action.WIPE_WEBSITE) {
                handleWipeWebsiteStats(commandService);
            }
        });
    }

    private void handleWipeSelectedProfile(ManageDataCommandService commandService, long selectedKey, String selectedLabel) {
        String validationError = commandService.validateSelectedProfileSelection(selectedKey, accountwideKey);
        if (validationError != null) {
            hooks.showError(validationError);
            return;
        }
        String label = commandService.resolveProfileLabel(selectedKey, selectedLabel);
        ManageDataCommandService.ConfirmationRequest confirmation =
            commandService.confirmationForSelectedProfile(label);
        String input = hooks.showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            hooks.showError("Confirmation did not match. No data was wiped.");
            return;
        }
        hooks.invokeOnClientThread(() -> hooks.wipeSingleLocalProfile(selectedKey, label));
    }

    private void handleWipeAllProfiles(ManageDataCommandService commandService) {
        ManageDataCommandService.ConfirmationRequest confirmation = commandService.confirmationForAllProfiles();
        String input = hooks.showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            hooks.showError("Confirmation did not match. No data was wiped.");
            return;
        }
        hooks.invokeOnClientThread(hooks::wipeAllLocalProfiles);
    }

    private void handleWipeWebsiteStats(ManageDataCommandService commandService) {
        ManageDataCommandService.ConfirmationRequest confirmation = commandService.confirmationForWebsite();
        String input = hooks.showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            hooks.showError("Confirmation did not match. No data was wiped.");
            return;
        }
        hooks.wipeWebsiteStatsAsync();
    }
}
