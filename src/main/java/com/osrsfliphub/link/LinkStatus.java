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
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

/**
 * Owns the one line of plain English that says whether this device is linked, and mirrors it onto
 * the account card.
 *
 * <p>Linking is asynchronous and clears the key box on success, so without this the panel would
 * look identical before and after. The status itself is derived from link state on demand, so it
 * lives in memory rather than in config - only the key hint outlives a restart.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class LinkStatus {
    static final String LICENSE_KEY_HINT_KEY = "licenseKeyHint";

    static final String SYNC_OFF = "Sync is off";
    static final String NOT_LINKED = "Not linked";
    static final String LINKING = "Linking this device...";
    static final String REJECTED = "Not linked - that license key was rejected";
    static final String UNREACHABLE = "Not linked - could not reach FlipHub, retrying";
    static final String FAILED = "Not linked - link failed, check your license key";
    static final String NEEDS_LOGIN = "Waiting to link - log in to OSRS to finish";

    private static final String LINKED = "Linked to FlipHub";
    private static final int HINT_LENGTH = 4;

    private final ConfigManager configManager;
    private final PluginConfig config;
    private final LinkSessionGuard linkGuard;
    private volatile String status = SYNC_OFF;

    /** Recomputes the status from link state. Transient messages are replaced. */
    void refresh() {
        write(resolveSettledStatus());
        pushToPanel();
    }

    /**
     * Mirrors the current status onto the account card. Unlike the settings window the panel can
     * repaint itself, so this is the surface that actually tracks a link as it happens.
     */
    void pushToPanel() {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null || plugin.panel == null) {
            return;
        }
        boolean linked = linkGuard.isLinked();
        String hint = config.licenseKeyHint();
        plugin.panel.setAccountState(linked, hint, panelMessageFor(status), panelColourFor(status));
    }

    /** A one-off note that is not a link state, such as an empty key box. */
    void markPanelMessage(String message) {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null || plugin.panel == null) {
            return;
        }
        boolean linked = linkGuard.isLinked();
        String hint = config.licenseKeyHint();
        plugin.panel.setAccountState(linked, hint, message, Skin.WARNING);
    }

    private String panelMessageFor(String status) {
        if (LINKING.equals(status)) {
            return "Linking...";
        }
        if (NEEDS_LOGIN.equals(status)) {
            return "Log in to OSRS to finish linking.";
        }
        if (REJECTED.equals(status)) {
            return "That key was rejected. Check it and try again.";
        }
        if (UNREACHABLE.equals(status)) {
            return "Could not reach osrsfliphub.com. Retrying.";
        }
        if (FAILED.equals(status)) {
            return "Linking failed. Check your key and try again.";
        }
        return "";
    }

    private java.awt.Color panelColourFor(String status) {
        if (REJECTED.equals(status) || FAILED.equals(status)) {
            return Skin.DANGER;
        }
        if (NEEDS_LOGIN.equals(status) || UNREACHABLE.equals(status)) {
            return Skin.WARNING;
        }
        return Skin.MUTED;
    }

    void markLinking() {
        if (linkGuard.isSyncEnabled()) {
            write(LINKING);
            pushToPanel();
        }
    }

    void markLinked(String licenseKey) {
        writeHint(hintFor(licenseKey));
        if (write(resolveSettledStatus())) {
            announce("FlipHub: linked to your account. Your flips now sync to osrsfliphub.com.");
        }
        pushToPanel();
    }

    /**
     * A finished attempt that did not link. The reason outlives the attempt so the user can still
     * read it later, but only while the device really is unlinked - an attempt that fails after
     * the device is already linked must not leave a stale warning behind.
     */
    void markFailed(String status) {
        if (linkGuard.isLinked()) {
            refresh();
            return;
        }
        String next = status != null ? status : FAILED;
        if (write(next)) {
            announce(chatFor(next));
        }
        pushToPanel();
    }

    private String resolveSettledStatus() {
        if (!linkGuard.isSyncEnabled()) {
            return SYNC_OFF;
        }
        if (!linkGuard.isLinked()) {
            return NOT_LINKED;
        }
        String hint = config.licenseKeyHint();
        return Str.isBlank(hint) ? LINKED : LINKED + " (key ending " + hint + ")";
    }

    private String hintFor(String licenseKey) {
        String trimmed = licenseKey == null ? "" : licenseKey.trim();
        if (trimmed.length() < HINT_LENGTH) {
            return "";
        }
        return trimmed.substring(trimmed.length() - HINT_LENGTH);
    }

    private boolean write(String next) {
        if (next == null || next.equals(status)) {
            return false;
        }
        status = next;
        return true;
    }

    /**
     * The settings window cannot repaint itself - RuneLite's config panel reads config only when
     * it is built and subscribes to no config event - so a transition is announced in game chat,
     * which is the one surface still visible while the settings window is open.
     */
    private void announce(String message) {
        if (message == null) {
            return;
        }
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null) {
            return;
        }
        plugin.invokeOnClientThread(
            () -> plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message));
    }

    private String chatFor(String status) {
        if (REJECTED.equals(status)) {
            return "FlipHub: that license key was rejected. Check it and paste it again.";
        }
        if (UNREACHABLE.equals(status)) {
            return "FlipHub: could not reach osrsfliphub.com. Retrying shortly.";
        }
        if (FAILED.equals(status)) {
            return "FlipHub: linking failed. Check your license key and try again.";
        }
        // NEEDS_LOGIN has no message: there is no chatbox to read it in.
        return null;
    }

    private void writeHint(String hint) {
        String current = config.licenseKeyHint();
        if (hint == null || hint.equals(current)) {
            return;
        }
        configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, LICENSE_KEY_HINT_KEY, hint);
    }

}
