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

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class ProfileSelectionPresentationFacadeService {
    private final ProfileSelectionState profileSelection;
    private final Map<Long, String> profileDisplayNames;
    private final Map<Long, String> legacyNameKeysByHash;

    @Inject
    ProfileSelectionPresentationFacadeService(PluginState pluginState) {
        this.profileSelection = pluginState.getProfileSelection();
        this.profileDisplayNames = pluginState.getProfileDisplayNames();
        this.legacyNameKeysByHash = pluginState.getLegacyNameKeysByHash();
    }

    private ProfileSelectionResolverService getProfileSelectionResolverService() {
        return PluginInjectorBridge.get(ProfileSelectionResolverService.class);
    }

    private ProfilePresentationService getProfilePresentationService() {
        return PluginInjectorBridge.get(ProfilePresentationService.class);
    }

    private ProfileCatalogService getProfileCatalogService() {
        return PluginInjectorBridge.get(ProfileCatalogService.class);
    }

    private LegacyLocalTradesStore getLegacyLocalTradesStore() {
        return PluginInjectorBridge.get(LegacyLocalTradesStore.class);
    }

    private LocalAccountSessionService getLocalAccountSessionService() {
        return PluginInjectorBridge.get(LocalAccountSessionService.class);
    }

    private LinkSessionGuardService getLinkSessionGuardService() {
        return PluginInjectorBridge.get(LinkSessionGuardService.class);
    }

    private long resolveAccountHash() {
        LocalTradeSessionFacadeService facade = PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        return facade != null ? facade.resolveAccountHash() : -1L;
    }

    String resolveSelectedProfileKeyForUi() {
        return getProfileSelectionResolverService() != null
            ? getProfileSelectionResolverService().resolveSelectedProfileKeyForUi(profileSelection)
            : String.valueOf(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY);
    }

    String resolveProfileHeaderLabel() {
        return resolveSelectedProfileLabel();
    }

    boolean hasSessionToken() {
        return getLinkSessionGuardService() != null
            && getLinkSessionGuardService().hasSessionToken();
    }

    LinkSessionGuardService.Credentials resolveLinkedCredentials() {
        return getLinkSessionGuardService() != null
            ? getLinkSessionGuardService().resolveLinkedCredentials()
            : null;
    }

    boolean isLinked() {
        return getLinkSessionGuardService() != null
            && getLinkSessionGuardService().isLinked();
    }

    long resolveSelectedProfileKey() {
        return getProfileSelectionResolverService() != null
            ? getProfileSelectionResolverService().resolveSelectedProfileKey(profileSelection)
            : GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
    }

    String resolveSelectedProfileLabel() {
        long key = resolveSelectedProfileKey();
        return getProfilePresentationService() != null
            ? getProfilePresentationService().resolveSelectedProfileLabel(
                key,
                profileDisplayNames,
                legacyNameKeysByHash
            )
            : "Accountwide";
    }

    String buildProfileKey(long accountHash) {
        return getProfileSelectionResolverService() != null
            ? getProfileSelectionResolverService().buildProfileKey(profileSelection, accountHash)
            : String.valueOf(accountHash);
    }

    String resolveDisplayName() {
        return getLocalAccountSessionService() != null
            ? getLocalAccountSessionService().resolveDisplayName()
            : null;
    }

    List<FlipHubProfileOption> buildProfileOptions() {
        Map<Long, String> diskProfiles = loadProfilesFromDisk();
        long currentHash = resolveAccountHash();
        String display = resolveDisplayName();
        return getProfilePresentationService() != null
            ? getProfilePresentationService().buildProfileOptions(
                profileDisplayNames,
                legacyNameKeysByHash,
                diskProfiles,
                currentHash,
                display
            )
            : java.util.Collections.emptyList();
    }

    String resolveLegacyDisplayNameForHash(long hash) {
        return getLegacyLocalTradesStore() != null
            ? getLegacyLocalTradesStore().resolveDisplayNameForHash(hash)
            : null;
    }

    String displayNameFromLegacyKey(String legacyKey) {
        return LegacyLocalTradesStore.displayNameFromLegacyKey(legacyKey);
    }

    Map<Long, String> loadProfilesFromDisk() {
        return getProfileCatalogService() != null
            ? getProfileCatalogService().loadProfiles(profileDisplayNames, legacyNameKeysByHash)
            : java.util.Collections.emptyMap();
    }
}

