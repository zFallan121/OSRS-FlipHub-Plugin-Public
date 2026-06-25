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

import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class ProfileSelectionPresentationFacadePluginHooks implements ProfileSelectionPresentationFacadeService.Hooks {
    private final Supplier<ProfileSelectionResolverService> profileSelectionResolverServiceSupplier;
    private final Supplier<ProfilePresentationService> profilePresentationServiceSupplier;
    private final Supplier<ProfileCatalogService> profileCatalogServiceSupplier;
    private final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<LinkSessionGuardService> linkSessionGuardServiceSupplier;
    private final LongSupplier accountHashResolver;

    ProfileSelectionPresentationFacadePluginHooks(
        Supplier<ProfileSelectionResolverService> profileSelectionResolverServiceSupplier,
        Supplier<ProfilePresentationService> profilePresentationServiceSupplier,
        Supplier<ProfileCatalogService> profileCatalogServiceSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<LinkSessionGuardService> linkSessionGuardServiceSupplier,
        LongSupplier accountHashResolver
    ) {
        this.profileSelectionResolverServiceSupplier = profileSelectionResolverServiceSupplier;
        this.profilePresentationServiceSupplier = profilePresentationServiceSupplier;
        this.profileCatalogServiceSupplier = profileCatalogServiceSupplier;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.linkSessionGuardServiceSupplier = linkSessionGuardServiceSupplier;
        this.accountHashResolver = accountHashResolver;
    }

    @Override
    public ProfileSelectionResolverService getProfileSelectionResolverService() {
        return profileSelectionResolverServiceSupplier != null ? profileSelectionResolverServiceSupplier.get() : null;
    }

    @Override
    public ProfilePresentationService getProfilePresentationService() {
        return profilePresentationServiceSupplier != null ? profilePresentationServiceSupplier.get() : null;
    }

    @Override
    public ProfileCatalogService getProfileCatalogService() {
        return profileCatalogServiceSupplier != null ? profileCatalogServiceSupplier.get() : null;
    }

    @Override
    public LegacyLocalTradesStore getLegacyLocalTradesStore() {
        return legacyLocalTradesStoreSupplier != null ? legacyLocalTradesStoreSupplier.get() : null;
    }

    @Override
    public LocalAccountSessionService getLocalAccountSessionService() {
        return localAccountSessionServiceSupplier != null ? localAccountSessionServiceSupplier.get() : null;
    }

    @Override
    public LinkSessionGuardService getLinkSessionGuardService() {
        return linkSessionGuardServiceSupplier != null ? linkSessionGuardServiceSupplier.get() : null;
    }

    @Override
    public long resolveAccountHash() {
        return accountHashResolver != null ? accountHashResolver.getAsLong() : 0L;
    }
}
