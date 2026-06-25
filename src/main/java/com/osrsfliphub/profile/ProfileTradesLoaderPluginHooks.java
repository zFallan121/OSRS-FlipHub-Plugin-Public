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
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

final class ProfileTradesLoaderPluginHooks implements ProfileTradesLoader.Hooks {
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final ToLongFunction<Path> profileFileModifiedMs;
    private final Supplier<AccountwideTradesMergeService> accountwideTradesMergeServiceSupplier;
    private final Predicate<String> isPlaceholderDisplayName;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;

    ProfileTradesLoaderPluginHooks(
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        ToLongFunction<Path> profileFileModifiedMs,
        Supplier<AccountwideTradesMergeService> accountwideTradesMergeServiceSupplier,
        Predicate<String> isPlaceholderDisplayName,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier
    ) {
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.profileFileModifiedMs = profileFileModifiedMs;
        this.accountwideTradesMergeServiceSupplier = accountwideTradesMergeServiceSupplier;
        this.isPlaceholderDisplayName = isPlaceholderDisplayName;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
    }

    @Override
    public Path getProfileFile(long accountHash) {
        ProfileStorageFacadeService service = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        return service != null ? service.getProfileFile(accountHash) : null;
    }

    @Override
    public long getProfileFileModifiedMs(Path file) {
        return profileFileModifiedMs != null ? profileFileModifiedMs.applyAsLong(file) : 0L;
    }

    @Override
    public ProfileData readProfileData(long accountHash) {
        ProfileStorageFacadeService service = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        return service != null ? service.readProfileData(accountHash) : null;
    }

    @Override
    public List<LocalTradeDelta> buildAccountwideFromDisk() {
        AccountwideTradesMergeService service = accountwideTradesMergeServiceSupplier != null
            ? accountwideTradesMergeServiceSupplier.get()
            : null;
        return service != null ? service.buildAccountwideFromDisk() : null;
    }

    @Override
    public List<LocalTradeDelta> readLegacyLocalTrades(long accountHash) {
        ProfileStorageFacadeService service = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        return service != null ? service.readLegacyLocalTrades(accountHash) : null;
    }

    @Override
    public boolean isPlaceholderDisplayName(String displayName) {
        return isPlaceholderDisplayName != null && isPlaceholderDisplayName.test(displayName);
    }

    @Override
    public String displayNameFromLegacyKey(String legacyKey) {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.displayNameFromLegacyKey(legacyKey) : null;
    }

    @Override
    public String resolveLegacyDisplayNameForHash(long accountHash) {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.resolveLegacyDisplayNameForHash(accountHash) : null;
    }
}
