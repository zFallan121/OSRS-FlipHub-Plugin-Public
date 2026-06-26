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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Singleton;

@Singleton
final class ManageDataCommandService {
    static final String OPTION_WIPE_PROFILE = "Wipe this profile";
    static final String OPTION_WIPE_ALL_LOCAL = "Wipe ALL local profiles";
    static final String OPTION_WIPE_WEBSITE = "Wipe WEBSITE (My Statistics)";
    static final String OPTION_CANCEL = "Cancel";

    enum Action {
        WIPE_SELECTED_PROFILE,
        WIPE_ALL_LOCAL_PROFILES,
        WIPE_WEBSITE,
        CANCEL
    }

    static final class DialogModel {
        final String body;
        final List<String> options;
        final String defaultOption;

        private DialogModel(String body, List<String> options, String defaultOption) {
            this.body = body;
            this.options = options;
            this.defaultOption = defaultOption;
        }
    }

    static final class ConfirmationRequest {
        final String expectedPhrase;
        final String promptBody;
        final String title;

        private ConfirmationRequest(String expectedPhrase, String promptBody, String title) {
            this.expectedPhrase = expectedPhrase;
            this.promptBody = promptBody;
            this.title = title;
        }
    }

    DialogModel buildDialog(String selectedLabel, boolean linked) {
        StringBuilder body = new StringBuilder();
        body.append("Selected profile: ").append(selectedLabel != null ? selectedLabel : "").append("\n\n");
        body.append("Local wipe deletes local trade history.\n");
        body.append("Wiped profiles will not re-import old trades via GE history sync.\n\n");
        body.append("Before wiping: open the GE History tab and wait for it to load.\n");
        if (linked) {
            body.append("Local wipe does not delete website My Statistics.\n");
            body.append("\nWebsite wipe deletes your My Statistics data (account-wide).\n");
            body.append("Close all FlipHub clients or stats can repopulate from uploads.\n");
        }

        List<String> options = new ArrayList<>();
        options.add(OPTION_WIPE_PROFILE);
        options.add(OPTION_WIPE_ALL_LOCAL);
        if (linked) {
            options.add(OPTION_WIPE_WEBSITE);
        }
        options.add(OPTION_CANCEL);
        return new DialogModel(body.toString(), options, OPTION_WIPE_PROFILE);
    }

    Action resolveAction(Object selectedOption) {
        if (selectedOption == null) {
            return Action.CANCEL;
        }
        String option = selectedOption.toString();
        if (OPTION_WIPE_PROFILE.equals(option)) {
            return Action.WIPE_SELECTED_PROFILE;
        }
        if (OPTION_WIPE_ALL_LOCAL.equals(option)) {
            return Action.WIPE_ALL_LOCAL_PROFILES;
        }
        if (OPTION_WIPE_WEBSITE.equals(option)) {
            return Action.WIPE_WEBSITE;
        }
        return Action.CANCEL;
    }

    String validateSelectedProfileSelection(long selectedKey, long accountwideKey) {
        if (selectedKey == accountwideKey) {
            return "Select a non-accountwide profile to wipe a single profile.";
        }
        return null;
    }

    String resolveProfileLabel(long selectedKey, String selectedLabel) {
        String label = selectedLabel != null ? selectedLabel.trim() : "";
        if (!label.isEmpty()) {
            return label;
        }
        return "Profile " + selectedKey;
    }

    ConfirmationRequest confirmationForSelectedProfile(String profileLabel) {
        String expected = "WIPE " + (profileLabel != null ? profileLabel : "");
        return new ConfirmationRequest(
            expected,
            "Type:\n" + expected,
            "Confirm Local Wipe"
        );
    }

    ConfirmationRequest confirmationForAllProfiles() {
        String expected = "WIPE ALL";
        return new ConfirmationRequest(
            expected,
            "Type:\n" + expected,
            "Confirm Local Wipe (All Profiles)"
        );
    }

    ConfirmationRequest confirmationForWebsite() {
        String expected = "WIPE WEBSITE";
        return new ConfirmationRequest(
            expected,
            "Type:\n" + expected + "\n\nWarning: this resets My Statistics for your entire FlipHub account.",
            "Confirm Website Wipe"
        );
    }

    boolean confirmationMatches(String input, String expectedPhrase) {
        return normalizePhrase(input).equals(normalizePhrase(expectedPhrase));
    }

    String normalizePhrase(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.US);
    }
}
