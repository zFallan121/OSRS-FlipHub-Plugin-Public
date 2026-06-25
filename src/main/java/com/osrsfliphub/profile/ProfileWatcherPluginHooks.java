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
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

final class ProfileWatcherPluginHooks implements ProfileWatcher.Hooks {
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Supplier<ProfileStore> profileStoreSupplier;
    private final LongFunction<Long> loadedProfileFileMsLookup;
    private final LongConsumer reloadProfile;

    ProfileWatcherPluginHooks(
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<ProfileStore> profileStoreSupplier,
        LongFunction<Long> loadedProfileFileMsLookup,
        LongConsumer reloadProfile
    ) {
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.profileStoreSupplier = profileStoreSupplier;
        this.loadedProfileFileMsLookup = loadedProfileFileMsLookup;
        this.reloadProfile = reloadProfile;
    }

    @Override
    public Path getProfilesDir() {
        ProfileStorageFacadeService service = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        return service != null ? service.getProfilesDir() : null;
    }

    @Override
    public Path getLegacyProfilesDir() {
        ProfileStorageFacadeService service = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        return service != null ? service.getLegacyProfilesDir() : null;
    }

    @Override
    public long parseAccountKey(Path file) {
        if (file != null) {
            Path fileName = file.getFileName();
            if (fileName != null && "accountwide.json".equalsIgnoreCase(fileName.toString())) {
                return GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
            }
        }
        ProfileStore store = profileStoreSupplier != null ? profileStoreSupplier.get() : null;
        return store != null ? store.parseAccountKeyFromProfileFile(file) : -1L;
    }

    @Override
    public long getProfileFileModifiedMs(Path file) {
        ProfileStore store = profileStoreSupplier != null ? profileStoreSupplier.get() : null;
        return store != null ? store.getProfileFileModifiedMs(file) : 0L;
    }

    @Override
    public Long getLoadedProfileFileMs(long accountKey) {
        return loadedProfileFileMsLookup != null ? loadedProfileFileMsLookup.apply(accountKey) : null;
    }

    @Override
    public void reloadProfile(long accountKey) {
        if (reloadProfile != null) {
            reloadProfile.accept(accountKey);
        }
    }
}
