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
import java.util.Map;
import java.util.function.Supplier;

final class AccountwideTradesMergePluginHooks implements AccountwideTradesMergeService.Hooks {
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier;
    private final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;

    AccountwideTradesMergePluginHooks(
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier
    ) {
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.legacyLocalTradesFilterServiceSupplier = legacyLocalTradesFilterServiceSupplier;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
    }

    @Override
    public Path getProfilesDir() {
        ProfileStorageFacadeService service = resolveProfileStorageFacadeService();
        return service != null ? service.getProfilesDir() : null;
    }

    @Override
    public Path getLegacyProfilesDir() {
        ProfileStorageFacadeService service = resolveProfileStorageFacadeService();
        return service != null ? service.getLegacyProfilesDir() : null;
    }

    @Override
    public ProfileData readProfileData(Path file) {
        ProfileStorageFacadeService service = resolveProfileStorageFacadeService();
        return service != null ? service.readProfileData(file) : null;
    }

    @Override
    public Map<String, String> getLegacyLocalTradesCache() {
        LegacyLocalTradesFilterService filterService = legacyLocalTradesFilterServiceSupplier != null
            ? legacyLocalTradesFilterServiceSupplier.get()
            : null;
        LegacyLocalTradesStore store = legacyLocalTradesStoreSupplier != null
            ? legacyLocalTradesStoreSupplier.get()
            : null;
        if (filterService == null || store == null) {
            return null;
        }
        return filterService.filter(store.getEntries());
    }

    private ProfileStorageFacadeService resolveProfileStorageFacadeService() {
        return profileStorageFacadeServiceSupplier != null ? profileStorageFacadeServiceSupplier.get() : null;
    }
}
