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
final class ProfileSelectionPresentation {
    private final ProfileSelectionState profileSelection;
    private final Map<Long, String> profileDisplayNames;

    @Inject
    ProfileSelectionPresentation(PluginState pluginState) {
        this.profileSelection = pluginState.getProfileSelection();
        this.profileDisplayNames = pluginState.getProfileDisplayNames();
    }

    private long resolveAccountHash() {
        TradeSession facade = Bridge.get(TradeSession.class);
        return facade != null ? facade.resolveAccountHash() : -1L;
    }

    String resolveSelectedProfileKeyForUi() {
        return Bridge.get(ProfileSelectionResolver.class) != null
            ? Bridge.get(ProfileSelectionResolver.class).resolveSelectedProfileKeyForUi(profileSelection)
            : String.valueOf(Const.ACCOUNTWIDE_KEY);
    }

    String resolveProfileHeaderLabel() {
        return resolveSelectedProfileLabel();
    }

    boolean hasSessionToken() {
        return Bridge.get(LinkSessionGuard.class) != null
            && Bridge.get(LinkSessionGuard.class).hasSessionToken();
    }

    LinkSessionGuard.Credentials resolveLinkedCredentials() {
        return Bridge.get(LinkSessionGuard.class) != null
            ? Bridge.get(LinkSessionGuard.class).resolveLinkedCredentials()
            : null;
    }

    boolean isLinked() {
        return Bridge.get(LinkSessionGuard.class) != null
            && Bridge.get(LinkSessionGuard.class).isLinked();
    }

    long resolveSelectedProfileKey() {
        return Bridge.get(ProfileSelectionResolver.class) != null
            ? Bridge.get(ProfileSelectionResolver.class).resolveSelectedProfileKey(profileSelection)
            : Const.ACCOUNTWIDE_KEY;
    }

    String resolveSelectedProfileLabel() {
        long key = resolveSelectedProfileKey();
        return Bridge.get(ProfilePresentation.class) != null
            ? Bridge.get(ProfilePresentation.class).resolveSelectedProfileLabel(
                key,
                profileDisplayNames
            )
            : "Accountwide";
    }

    String buildProfileKey(long accountHash) {
        return Bridge.get(ProfileSelectionResolver.class) != null
            ? Bridge.get(ProfileSelectionResolver.class).buildProfileKey(profileSelection, accountHash)
            : String.valueOf(accountHash);
    }

    String resolveDisplayName() {
        return Bridge.get(AccountSession.class) != null
            ? Bridge.get(AccountSession.class).resolveDisplayName()
            : null;
    }

    List<ProfileOption> buildProfileOptions() {
        Map<Long, String> diskProfiles = loadProfilesFromDisk();
        long currentHash = resolveAccountHash();
        String display = resolveDisplayName();
        return Bridge.get(ProfilePresentation.class) != null
            ? Bridge.get(ProfilePresentation.class).buildProfileOptions(
                profileDisplayNames,
                diskProfiles,
                currentHash,
                display
            )
            : java.util.Collections.emptyList();
    }

    Map<Long, String> loadProfilesFromDisk() {
        return Bridge.get(ProfileCatalog.class) != null
            ? Bridge.get(ProfileCatalog.class).loadProfiles(profileDisplayNames)
            : java.util.Collections.emptyMap();
    }
}

