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
    interface Hooks {
        ProfileSelectionResolverService getProfileSelectionResolverService();
        ProfilePresentationService getProfilePresentationService();
        ProfileCatalogService getProfileCatalogService();
        LegacyLocalTradesStore getLegacyLocalTradesStore();
        LocalAccountSessionService getLocalAccountSessionService();
        LinkSessionGuardService getLinkSessionGuardService();
        long resolveAccountHash();
    }

    private final ProfileSelectionState profileSelection;
    private final Map<Long, String> profileDisplayNames;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Hooks hooks;

    @Inject
    ProfileSelectionPresentationFacadeService(PluginState pluginState) {
        this(pluginState.getProfileSelection(),
            pluginState.getProfileDisplayNames(),
            pluginState.getLegacyNameKeysByHash(),
            new Hooks() {
                @Override
                public ProfileSelectionResolverService getProfileSelectionResolverService() {
                    return PluginInjectorBridge.get(ProfileSelectionResolverService.class);
                }

                @Override
                public ProfilePresentationService getProfilePresentationService() {
                    return PluginInjectorBridge.get(ProfilePresentationService.class);
                }

                @Override
                public ProfileCatalogService getProfileCatalogService() {
                    return PluginInjectorBridge.get(ProfileCatalogService.class);
                }

                @Override
                public LegacyLocalTradesStore getLegacyLocalTradesStore() {
                    return PluginInjectorBridge.get(LegacyLocalTradesStore.class);
                }

                @Override
                public LocalAccountSessionService getLocalAccountSessionService() {
                    return PluginInjectorBridge.get(LocalAccountSessionService.class);
                }

                @Override
                public LinkSessionGuardService getLinkSessionGuardService() {
                    return PluginInjectorBridge.get(LinkSessionGuardService.class);
                }

                @Override
                public long resolveAccountHash() {
                    LocalTradeSessionFacadeService facade =
                        PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                    return facade != null ? facade.resolveAccountHash() : -1L;
                }
            });
    }

    ProfileSelectionPresentationFacadeService(ProfileSelectionState profileSelection,
                                              Map<Long, String> profileDisplayNames,
                                              Map<Long, String> legacyNameKeysByHash,
                                              Hooks hooks) {
        this.profileSelection = profileSelection;
        this.profileDisplayNames = profileDisplayNames;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.hooks = hooks;
    }

    String resolveSelectedProfileKeyForUi() {
        return hooks != null && hooks.getProfileSelectionResolverService() != null
            ? hooks.getProfileSelectionResolverService().resolveSelectedProfileKeyForUi(profileSelection)
            : String.valueOf(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY);
    }

    String resolveProfileHeaderLabel() {
        return resolveSelectedProfileLabel();
    }

    boolean hasSessionToken() {
        return hooks != null
            && hooks.getLinkSessionGuardService() != null
            && hooks.getLinkSessionGuardService().hasSessionToken();
    }

    LinkSessionGuardService.Credentials resolveLinkedCredentials() {
        return hooks != null && hooks.getLinkSessionGuardService() != null
            ? hooks.getLinkSessionGuardService().resolveLinkedCredentials()
            : null;
    }

    boolean isLinked() {
        return hooks != null
            && hooks.getLinkSessionGuardService() != null
            && hooks.getLinkSessionGuardService().isLinked();
    }

    long resolveSelectedProfileKey() {
        return hooks != null && hooks.getProfileSelectionResolverService() != null
            ? hooks.getProfileSelectionResolverService().resolveSelectedProfileKey(profileSelection)
            : GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
    }

    String resolveSelectedProfileLabel() {
        long key = resolveSelectedProfileKey();
        return hooks != null && hooks.getProfilePresentationService() != null
            ? hooks.getProfilePresentationService().resolveSelectedProfileLabel(
                key,
                profileDisplayNames,
                legacyNameKeysByHash
            )
            : "Accountwide";
    }

    String buildProfileKey(long accountHash) {
        return hooks != null && hooks.getProfileSelectionResolverService() != null
            ? hooks.getProfileSelectionResolverService().buildProfileKey(profileSelection, accountHash)
            : String.valueOf(accountHash);
    }

    String resolveDisplayName() {
        return hooks != null && hooks.getLocalAccountSessionService() != null
            ? hooks.getLocalAccountSessionService().resolveDisplayName()
            : null;
    }

    List<FlipHubProfileOption> buildProfileOptions() {
        Map<Long, String> diskProfiles = loadProfilesFromDisk();
        long currentHash = hooks != null ? hooks.resolveAccountHash() : 0L;
        String display = resolveDisplayName();
        return hooks != null && hooks.getProfilePresentationService() != null
            ? hooks.getProfilePresentationService().buildProfileOptions(
                profileDisplayNames,
                legacyNameKeysByHash,
                diskProfiles,
                currentHash,
                display
            )
            : java.util.Collections.emptyList();
    }

    String resolveLegacyDisplayNameForHash(long hash) {
        return hooks != null && hooks.getLegacyLocalTradesStore() != null
            ? hooks.getLegacyLocalTradesStore().resolveDisplayNameForHash(hash)
            : null;
    }

    String displayNameFromLegacyKey(String legacyKey) {
        return LegacyLocalTradesStore.displayNameFromLegacyKey(legacyKey);
    }

    Map<Long, String> loadProfilesFromDisk() {
        return hooks != null && hooks.getProfileCatalogService() != null
            ? hooks.getProfileCatalogService().loadProfiles(profileDisplayNames, legacyNameKeysByHash)
            : java.util.Collections.emptyMap();
    }
}

