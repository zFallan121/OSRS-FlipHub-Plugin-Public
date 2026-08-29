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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@javax.inject.Singleton
final class ProfileStorageFacadeService {
    private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
    private final PluginState pluginState;

    @javax.inject.Inject
    ProfileStorageFacadeService(PluginState pluginState) {
        this.pluginState = pluginState;
    }

    private ProfileStore profileStore() {
        return PluginInjectorBridge.get(ProfileStore.class);
    }

    Path getProfilesDir() {
        ProfileStore store = profileStore();
        return store != null ? store.getProfilesDir() : null;
    }

    Path getLegacyProfilesDir() {
        ProfileStore store = profileStore();
        return store != null ? store.getLegacyProfilesDir() : null;
    }

    Path getProfileFile(long accountHash) {
        ProfileStore store = profileStore();
        return store != null ? store.getProfileFile(accountHash, accountwideKey) : null;
    }

    ProfileData readProfileData(long accountHash) {
        ProfileStore store = profileStore();
        return store != null ? store.readProfileData(accountHash, accountwideKey) : null;
    }

    ProfileData readProfileData(Path file) {
        ProfileStore store = profileStore();
        return store != null ? store.readProfileData(file) : null;
    }

    void writeProfileData(long accountHash, List<LocalTradeDelta> deltas) {
        ProfileStore store = profileStore();
        if (store == null) {
            return;
        }
        String displayName = accountHash == accountwideKey
            ? "Accountwide" : pluginState.getProfileDisplayNames().get(accountHash);
        if (ProfileDisplayNames.isPlaceholder(displayName)) {
            // Persisting the placeholder would make it indistinguishable from a real name
            // on the next load, permanently masking the account's display name.
            displayName = null;
        }
        List<LocalTradeDelta> snapshot = deltas != null ? deltas : new ArrayList<>();
        long fileMs = store.writeProfileData(accountHash, accountwideKey, displayName, snapshot);
        if (fileMs > 0) {
            pluginState.getLoadedProfileFileMs().put(accountHash, fileMs);
        }
    }

}
