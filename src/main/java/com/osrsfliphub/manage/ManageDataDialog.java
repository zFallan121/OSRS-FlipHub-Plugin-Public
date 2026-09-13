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
final class ManageDataDialog {
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;

    @Inject
    ManageDataDialog() {
    }

    private String showInputDialog(String body, String title) {
        return JOptionPane.showInputDialog(Access.plugin().panel, body, title, JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String message) {
        Access.plugin().getProfileWorkflowService().showManageDataError(message);
    }

    void showManageDataDialog() {
        if (Access.plugin().panel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ProfileSelectionPresentation facade = Bridge.get(ProfileSelectionPresentation.class);
            long selectedKey = facade != null ? facade.resolveSelectedProfileKey() : -1L;
            String selectedLabel = facade != null ? facade.resolveSelectedProfileLabel() : "";
            boolean linked = facade != null && facade.isLinked();
            ManageDataCommand commandService = Bridge.get(ManageDataCommand.class);
            if (commandService == null) {
                showError("Manage Data is unavailable right now.");
                return;
            }

            ManageDataCommand.DialogModel dialogModel = commandService.buildDialog(selectedLabel, linked);
            Object[] options = dialogModel.options.toArray();
            int choice = JOptionPane.showOptionDialog(
            Access.plugin().panel,
            dialogModel.body,
            "FlipHub Manage Data",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            dialogModel.defaultOption);
            if (choice < 0 || choice >= options.length) {
                return;
            }

            ManageDataCommand.Action action = commandService.resolveAction(options[choice]);
            if (action == ManageDataCommand.Action.WIPE_SELECTED_PROFILE) {
                handleWipeSelectedProfile(commandService, selectedKey, selectedLabel);
            } else if (action == ManageDataCommand.Action.WIPE_ALL_LOCAL_PROFILES) {
                handleWipeAllProfiles(commandService);
            } else if (action == ManageDataCommand.Action.WIPE_WEBSITE) {
                handleWipeWebsiteStats(commandService);
            }
        });
    }

    private void handleWipeSelectedProfile(ManageDataCommand commandService, long selectedKey, String selectedLabel) {
        String validationError = commandService.validateSelectedProfileSelection(selectedKey, accountwideKey);
        if (validationError != null) {
            showError(validationError);
            return;
        }
        String label = commandService.resolveProfileLabel(selectedKey, selectedLabel);
        ManageDataCommand.ConfirmationRequest confirmation =
            commandService.confirmationForSelectedProfile(label);
        String input = showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            showError("Confirmation did not match. No data was wiped.");
            return;
        }
        Access.plugin().invokeOnClientThread(() -> {
            ProfileWipe service = Bridge.get(ProfileWipe.class);
            if (service != null) {
                service.wipeSingleLocalProfile(selectedKey, label);
            }
        });
    }

    private void handleWipeAllProfiles(ManageDataCommand commandService) {
        ManageDataCommand.ConfirmationRequest confirmation = commandService.confirmationForAllProfiles();
        String input = showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            showError("Confirmation did not match. No data was wiped.");
            return;
        }
        Access.plugin().invokeOnClientThread(() -> {
            ProfileWipe service = Bridge.get(ProfileWipe.class);
            if (service != null) {
                service.wipeAllLocalProfiles();
            }
        });
    }

    private void handleWipeWebsiteStats(ManageDataCommand commandService) {
        ManageDataCommand.ConfirmationRequest confirmation = commandService.confirmationForWebsite();
        String input = showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            showError("Confirmation did not match. No data was wiped.");
            return;
        }
        WebsiteStatsWipe service = Bridge.get(WebsiteStatsWipe.class);
        if (service != null) {
            service.wipeWebsiteStatsAsync();
        }
    }
}
