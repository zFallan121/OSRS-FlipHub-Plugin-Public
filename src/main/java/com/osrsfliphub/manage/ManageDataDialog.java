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

import javax.inject.*;
import javax.swing.*;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ManageDataDialog {
    private final ProfileSelectionPresentation profileSelectionPresentation;
    private final ManageDataCommand command;
    private final ProfileWipe profileWipe;
    private final WebsiteStatsWipe websiteStatsWipe;
    private final ProfileWorkflow profileWorkflow;

    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;

    private String showInputDialog(String body, String title) {
        return JOptionPane.showInputDialog(Access.plugin().panel, body, title, JOptionPane.WARNING_MESSAGE);
    }

    void showManageDataDialog() {
        if (Access.plugin().panel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            long selectedKey = profileSelectionPresentation.resolveSelectedProfileKey();
            String selectedLabel = profileSelectionPresentation.resolveSelectedProfileLabel();
            boolean linked = profileSelectionPresentation.isLinked();
            if (command == null) {
                profileWorkflow.showManageDataError("Manage Data is unavailable right now.");
                return;
            }

            ManageDataCommand.DialogModel dialogModel = command.buildDialog(selectedLabel, linked);
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

            ManageDataCommand.Action action = command.resolveAction(options[choice]);
            if (action == ManageDataCommand.Action.WIPE_SELECTED_PROFILE) {
                handleWipeSelectedProfile(command, selectedKey, selectedLabel);
            } else if (action == ManageDataCommand.Action.WIPE_ALL_LOCAL_PROFILES) {
                handleWipeAllProfiles(command);
            } else if (action == ManageDataCommand.Action.WIPE_WEBSITE) {
                handleWipeWebsiteStats(command);
            }
        });
    }

    private void handleWipeSelectedProfile(ManageDataCommand commandService, long selectedKey, String selectedLabel) {
        String validationError = commandService.validateSelectedProfileSelection(selectedKey, accountwideKey);
        if (validationError != null) {
            profileWorkflow.showManageDataError(validationError);
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
            profileWorkflow.showManageDataError("Confirmation did not match. No data was wiped.");
            return;
        }
        Access.plugin().invokeOnClientThread(() -> {
            profileWipe.wipeSingleLocalProfile(selectedKey, label);
        });
    }

    private void handleWipeAllProfiles(ManageDataCommand commandService) {
        ManageDataCommand.ConfirmationRequest confirmation = commandService.confirmationForAllProfiles();
        String input = showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            profileWorkflow.showManageDataError("Confirmation did not match. No data was wiped.");
            return;
        }
        Access.plugin().invokeOnClientThread(() -> {
            profileWipe.wipeAllLocalProfiles();
        });
    }

    private void handleWipeWebsiteStats(ManageDataCommand commandService) {
        ManageDataCommand.ConfirmationRequest confirmation = commandService.confirmationForWebsite();
        String input = showInputDialog(confirmation.promptBody, confirmation.title);
        if (input == null) {
            return;
        }
        if (!commandService.confirmationMatches(input, confirmation.expectedPhrase)) {
            profileWorkflow.showManageDataError("Confirmation did not match. No data was wiped.");
            return;
        }
        websiteStatsWipe.wipeWebsiteStatsAsync();
    }
}
