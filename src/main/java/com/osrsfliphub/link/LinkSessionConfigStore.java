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
import net.runelite.client.config.ConfigManager;

@Singleton
final class LinkSessionConfigStore {
    static final String SESSION_TOKEN_KEY = "sessionToken";
    static final String SIGNING_SECRET_KEY = "signingSecret";
    static final String LICENSE_KEY = "licenseKey";
    static final String LINK_CODE_KEY = "linkCode";
    static final String UNLINK_NOW_KEY = "unlinkNow";

    interface Hooks {
        void setString(String group, String key, String value);
        void setBoolean(String group, String key, boolean value);
    }

    private final Hooks hooks;
    private final String configGroup;

    @Inject
    LinkSessionConfigStore(ConfigManager configManager) {
        this(productionHooks(configManager), FliphubConfigGroups.CONFIG_GROUP);
    }

    LinkSessionConfigStore(Hooks hooks, String configGroup) {
        this.hooks = hooks;
        this.configGroup = configGroup;
    }

    private static Hooks productionHooks(ConfigManager configManager) {
        return new Hooks() {
            @Override
            public void setString(String group, String key, String value) {
                if (configManager != null) {
                    configManager.setConfiguration(group, key, value);
                }
            }

            @Override
            public void setBoolean(String group, String key, boolean value) {
                if (configManager != null) {
                    configManager.setConfiguration(group, key, value);
                }
            }
        };
    }

    void clearLinkState() {
        setString(SESSION_TOKEN_KEY, "");
        setString(SIGNING_SECRET_KEY, "");
        clearLinkInputs();
        setBoolean(UNLINK_NOW_KEY, false);
    }

    void persistLinkedSession(String sessionToken, String signingSecret) {
        setString(SESSION_TOKEN_KEY, safe(sessionToken));
        setString(SIGNING_SECRET_KEY, safe(signingSecret));
        clearLinkInputs();
    }

    private void clearLinkInputs() {
        setString(LICENSE_KEY, "");
        setString(LINK_CODE_KEY, "");
    }

    private void setString(String key, String value) {
        if (hooks == null) {
            return;
        }
        hooks.setString(configGroup, key, value);
    }

    private void setBoolean(String key, boolean value) {
        if (hooks == null) {
            return;
        }
        hooks.setBoolean(configGroup, key, value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
