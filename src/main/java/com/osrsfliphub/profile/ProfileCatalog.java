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
import javax.inject.*;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ProfileCatalog {
    private final ProfileStore profileStore;

    Map<Long, String> loadProfiles(Map<Long, String> profileDisplayNames) {
        Map<Long, String> profiles = new HashMap<>();
        if (profileDisplayNames != null) {
            profiles.putAll(profileDisplayNames);
        }
        mergeProfilesFromDir(profiles, profileStore.getProfilesDir());
        mergeProfilesFromDir(profiles, profileStore.getLegacyProfilesDir());
        if (profileDisplayNames != null) {
            profileDisplayNames.putAll(profiles);
        }
        return profiles;
    }

    /**
     * Every account with a file on this computer, by the name it is already known by. The files are
     * listed, not read: {@link #loadProfiles} parses every one in full, which the recorder did on
     * the Swing thread each time it opened, only for names that are almost always in memory.
     */
    Map<Long, String> listed(Map<Long, String> known) {
        Map<Long, String> out = new HashMap<>();
        for (Path dir : new Path[] {profileStore.getProfilesDir(), profileStore.getLegacyProfilesDir()}) {
            ProfileHashFileWalker.walk(dir, (hash, path) ->
                out.putIfAbsent(hash, known.getOrDefault(hash, ProfileDisplayNames.placeholderFor(hash))));
        }
        return out;
    }

    private void mergeProfilesFromDir(Map<Long, String> profiles, Path dir) {
        if (profiles == null) {
            return;
        }
        ProfileHashFileWalker.walk(dir, (hash, path) -> {
            ProfileData data = profileStore.readProfileData(path);
            String diskName = data != null ? data.displayName : null;
            // Older builds persisted the "Profile <key>" placeholder into displayName; treat
            // it as absent so it can never overwrite the real name already in memory.
            if (!ProfileDisplayNames.isPlaceholder(diskName)) {
                profiles.put(hash, diskName.trim());
            } else if (!profiles.containsKey(hash)) {
                profiles.put(hash, ProfileDisplayNames.placeholderFor(hash));
            }
        });
    }
}
