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
import java.util.*;
import lombok.RequiredArgsConstructor;

@javax.inject.Singleton
@RequiredArgsConstructor(onConstructor_ = @javax.inject.Inject)
final class ProfileStorage {
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final PluginState pluginState;

    private ProfileStore profileStore() {
        return Bridge.get(ProfileStore.class);
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

    /**
     * @return true when the trades reached disk. False means they are still only in memory
     *         and the caller has to keep the account marked unsaved.
     */
    boolean writeProfileData(long accountHash, List<Delta> deltas) {
        ProfileStore store = profileStore();
        // A file that would not parse is left for the player to repair. The load kept it, but the
        // next trade was then saved over it, and everything the file held was gone for good.
        if (store == null || pluginState.getUnreadableProfiles().contains(accountHash)) {
            return false;
        }
        String displayName = accountHash == accountwideKey
            ? "Accountwide" : pluginState.getProfileDisplayNames().get(accountHash);
        if (ProfileDisplayNames.isPlaceholder(displayName)) {
            // Persisting the placeholder would make it indistinguishable from a real name
            // on the next load, permanently masking the account's display name.
            displayName = null;
        }
        List<Delta> snapshot = deltas != null ? deltas : new ArrayList<>();
        // The corrections travel with the trades they name, in the same file.
        RecipeFlipStore recorded = Bridge.get(RecipeFlipStore.class);
        List<RecipeFlip> flips = recorded != null ? recorded.snapshotForFile(accountHash) : null;
        long fileMs = store.writeProfileData(
            accountHash, accountwideKey, displayName, snapshot, flips);
        if (fileMs > 0) {
            pluginState.getLoadedProfileFileMs().put(accountHash, fileMs);
            // Remember that this write was ours, so the watcher does not treat it as an
            // outside change and reload over trades recorded since.
            pluginState.getSelfWrittenProfileFileMs().put(accountHash, fileMs);
        }
        return fileMs >= 0;
    }
}
