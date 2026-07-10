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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
final class BackfilledProfilesStore {
    private final String configGroup = FliphubConfigGroups.CONFIG_GROUP;
    private final String configKey = GeLifecyclePluginConstants.BACKFILLED_PROFILES_KEY;
    private final ConfigManager configManager;

    @Inject
    BackfilledProfilesStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    Set<Long> load() {
        Set<Long> keys = new HashSet<>();
        if (configManager == null) {
            return keys;
        }
        String raw = configManager.getConfiguration(configGroup, configKey);
        if (raw == null || raw.trim().isEmpty()) {
            return keys;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            try {
                long key = Long.parseLong(part.trim());
                if (key > 0) {
                    keys.add(key);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return keys;
    }

    void persist(Set<Long> keys) {
        if (configManager == null) {
            return;
        }
        if (keys == null || keys.isEmpty()) {
            configManager.setConfiguration(configGroup, configKey, "");
            return;
        }
        String raw = keys.stream()
            .filter(key -> key != null && key > 0)
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        configManager.setConfiguration(configGroup, configKey, raw);
    }
}
