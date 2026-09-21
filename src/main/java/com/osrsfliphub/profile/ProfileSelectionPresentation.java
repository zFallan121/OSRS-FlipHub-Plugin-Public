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

import java.util.*;
import javax.inject.*;

@Singleton
final class ProfileSelectionPresentation {
    private final TradeSession tradeSession;
    private final ProfilePresentation presentation;
    private final ProfileSelectionResolver resolver;
    private final LinkSessionGuard linkSessionGuard;
    private final AccountSession accountSession;
    private final ProfileCatalog catalog;
    private final ProfileSelectionState profileSelection;
    private final Map<Long, String> profileDisplayNames;

    @Inject
    ProfileSelectionPresentation(
        PluginState pluginState,
        ProfileSelectionResolver resolver,
        LinkSessionGuard linkSessionGuard,
        AccountSession accountSession,
        ProfileCatalog catalog,
        TradeSession tradeSession,
        ProfilePresentation presentation
    ) {
        this.tradeSession = tradeSession;
        this.presentation = presentation;
        this.resolver = resolver;
        this.linkSessionGuard = linkSessionGuard;
        this.accountSession = accountSession;
        this.catalog = catalog;
        this.profileSelection = pluginState.getProfileSelection();
        this.profileDisplayNames = pluginState.getProfileDisplayNames();
    }

    String resolveSelectedProfileKeyForUi() {
        return resolver.resolveSelectedProfileKeyForUi(profileSelection);
    }

    String resolveProfileHeaderLabel() {
        return resolveSelectedProfileLabel();
    }

    boolean hasSessionToken() {
        return linkSessionGuard != null
            && linkSessionGuard.hasSessionToken();
    }

    LinkSessionGuard.Credentials resolveLinkedCredentials() {
        return linkSessionGuard.resolveLinkedCredentials();
    }

    boolean isLinked() {
        return linkSessionGuard != null
            && linkSessionGuard.isLinked();
    }

    long resolveSelectedProfileKey() {
        return resolver.resolveSelectedProfileKey(profileSelection);
    }

    String resolveSelectedProfileLabel() {
        long key = resolveSelectedProfileKey();
        return presentation.resolveSelectedProfileLabel(
                key,
                profileDisplayNames
            );
    }

    String buildProfileKey(long accountHash) {
        return resolver.buildProfileKey(profileSelection, accountHash);
    }

    String resolveDisplayName() {
        return accountSession.resolveDisplayName();
    }

    List<ProfileOption> buildProfileOptions() {
        Map<Long, String> diskProfiles = loadProfilesFromDisk();
        long currentHash = tradeSession.resolveAccountHash();
        String display = resolveDisplayName();
        return presentation.buildProfileOptions(
                profileDisplayNames,
                diskProfiles,
                currentHash,
                display
            );
    }

    Map<Long, String> loadProfilesFromDisk() {
        return catalog.loadProfiles(profileDisplayNames);
    }
}

