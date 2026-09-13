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
import net.runelite.client.config.ConfigManager;

@Singleton
final class LinkSessionConfigStore {
    static final String SESSION_TOKEN_KEY = "sessionToken";
    static final String SIGNING_SECRET_KEY = "signingSecret";
    static final String LICENSE_KEY = "licenseKey";

    private final ConfigManager configManager;
    private final String configGroup;

    @Inject
    LinkSessionConfigStore(ConfigManager configManager) {
        this.configManager = configManager;
        this.configGroup = FliphubConfigGroups.CONFIG_GROUP;
    }

    /** The panel asks for its own consent, so it turns the sync opt-in on directly. */
    void enableSync(String licenseKey) {
        setString(LICENSE_KEY, safe(licenseKey));
        setBoolean("enableFlipHubSync", true);
    }

    /**
     * The panel turns the sync opt-in on when it links, so unlinking turns it back off. Without
     * this the device keeps a live opt-in with no credentials, and every path that only checks
     * isSyncEnabled stays armed after an explicit unlink.
     */
    void disableSync() {
        setBoolean("enableFlipHubSync", false);
    }

    void clearLinkState() {
        setString(SESSION_TOKEN_KEY, "");
        setString(SIGNING_SECRET_KEY, "");
        clearLinkInputs();
        clearKeyHint();
    }

    void persistLinkedSession(String sessionToken, String signingSecret) {
        setString(SESSION_TOKEN_KEY, safe(sessionToken));
        setString(SIGNING_SECRET_KEY, safe(signingSecret));
        clearLinkInputs();
        flush();
    }

    /**
     * RuneLite only writes config out periodically and on a clean shutdown, so a link or an unlink
     * can be lost if the client is killed before that. Both are decisions the user made
     * explicitly, so they are pushed to disk immediately rather than left in memory.
     */
    void flush() {
        if (configManager != null) {
            configManager.sendConfig();
        }
    }

    /**
     * Drops a key the server would not take. Without this it stays in config in plain text and
     * is re-sent on every start and every login, forever failing the same way.
     */
    void clearRejectedLicenseKey() {
        setString(LICENSE_KEY, "");
        flush();
    }

    private void clearLinkInputs() {
        setString(LICENSE_KEY, "");
    }

    private void clearKeyHint() {
        setString(LinkStatus.LICENSE_KEY_HINT_KEY, "");
    }

    private void setString(String key, String value) {
        if (configManager != null) {
            configManager.setConfiguration(configGroup, key, value);
        }
    }

    private void setBoolean(String key, boolean value) {
        if (configManager != null) {
            configManager.setConfiguration(configGroup, key, value);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
