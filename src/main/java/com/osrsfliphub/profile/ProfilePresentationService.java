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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class ProfilePresentationService {
    private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
    private final String accountwideKeyString = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY_STRING;

    @Inject
    ProfilePresentationService() {
    }

    private ProfileSelectionPresentationFacadeService facade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    private String resolveLegacyDisplayNameForHash(long hash) {
        ProfileSelectionPresentationFacadeService service = facade();
        return service != null ? service.resolveLegacyDisplayNameForHash(hash) : null;
    }

    private String displayNameFromLegacyKey(String legacyKey) {
        ProfileSelectionPresentationFacadeService service = facade();
        return service != null ? service.displayNameFromLegacyKey(legacyKey) : null;
    }

    private String buildProfileKey(long accountHash) {
        ProfileSelectionPresentationFacadeService service = facade();
        return service != null ? service.buildProfileKey(accountHash) : String.valueOf(accountHash);
    }

    private boolean isPlaceholderDisplayName(String displayName) {
        GeLifecycleLocalTradesRuntimeService localTradesRuntime =
            PluginAccess.plugin().getLocalTradesRuntimeService();
        return localTradesRuntime != null && localTradesRuntime.isPlaceholderDisplayName(displayName);
    }

    String resolveSelectedProfileLabel(long key,
                                       Map<Long, String> profileDisplayNames,
                                       Map<Long, String> legacyNameKeysByHash) {
        if (key == accountwideKey) {
            return "Accountwide";
        }
        String displayName = profileDisplayNames != null ? profileDisplayNames.get(key) : null;
        if (displayName != null && !displayName.trim().isEmpty() && !isPlaceholderDisplayName(displayName)) {
            return displayName;
        }
        String legacyKey = legacyNameKeysByHash != null ? legacyNameKeysByHash.get(key) : null;
        String legacyDisplay = displayNameFromLegacyKey(legacyKey);
        if (legacyDisplay == null) {
            legacyDisplay = resolveLegacyDisplayNameForHash(key);
        }
        if (legacyDisplay != null) {
            if (profileDisplayNames != null) {
                profileDisplayNames.put(key, legacyDisplay);
            }
            return legacyDisplay;
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return "Profile " + key;
    }

    List<FlipHubProfileOption> buildProfileOptions(Map<Long, String> profileDisplayNames,
                                                         Map<Long, String> legacyNameKeysByHash,
                                                         Map<Long, String> diskProfiles,
                                                         long currentHash,
                                                         String currentDisplayName) {
        List<FlipHubProfileOption> options = new ArrayList<>();
        options.add(new FlipHubProfileOption(accountwideKeyString, "Accountwide"));
        if (diskProfiles == null) {
            return options;
        }
        if (currentHash > 0) {
            if (currentDisplayName != null && !currentDisplayName.trim().isEmpty()) {
                diskProfiles.put(currentHash, currentDisplayName.trim());
            } else if (!diskProfiles.containsKey(currentHash)) {
                diskProfiles.put(currentHash, "Profile " + currentHash);
            }
        }
        List<Map.Entry<Long, String>> entries = new ArrayList<>(diskProfiles.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getValue() != null ? entry.getValue().toLowerCase(Locale.US) : ""));
        for (Map.Entry<Long, String> entry : entries) {
            if (entry == null) {
                continue;
            }
            long hash = entry.getKey() != null ? entry.getKey() : -1L;
            if (hash <= 0) {
                continue;
            }
            String label = entry.getValue();
            if (label == null || label.trim().isEmpty()) {
                label = "Profile " + hash;
            }
            if (label.startsWith("Profile ")) {
                String legacyKey = legacyNameKeysByHash != null ? legacyNameKeysByHash.get(hash) : null;
                String legacyDisplay = displayNameFromLegacyKey(legacyKey);
                if (legacyDisplay == null) {
                    legacyDisplay = resolveLegacyDisplayNameForHash(hash);
                }
                if (legacyDisplay != null) {
                    label = legacyDisplay;
                }
            }
            if (profileDisplayNames != null && label != null && !label.trim().isEmpty()
                && !isPlaceholderDisplayName(label)) {
                profileDisplayNames.put(hash, label.trim());
            }
            options.add(new FlipHubProfileOption(buildProfileKey(hash), label));
        }
        return options;
    }
}

