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

final class LocalItemEnrichmentRuntimePluginHooks implements LocalItemEnrichmentFactoryService.RuntimeHooks {
    private final Supplier<GeLimitService> geLimitServiceSupplier;
    private final Supplier<WikiPriceService> wikiPriceServiceSupplier;
    private final LongSupplier nowMsSupplier;

    LocalItemEnrichmentRuntimePluginHooks(
        Supplier<GeLimitService> geLimitServiceSupplier,
        Supplier<WikiPriceService> wikiPriceServiceSupplier,
        LongSupplier nowMsSupplier
    ) {
        this.geLimitServiceSupplier = geLimitServiceSupplier;
        this.wikiPriceServiceSupplier = wikiPriceServiceSupplier;
        this.nowMsSupplier = nowMsSupplier;
    }

    @Override
    public Integer getCachedGeLimit(int itemId) {
        GeLimitService service = geLimitServiceSupplier != null ? geLimitServiceSupplier.get() : null;
        return service != null ? service.getCachedGeLimit(itemId) : null;
    }

    @Override
    public WikiPriceEntry getWikiPriceEntry(int itemId, boolean allowRefresh) {
        WikiPriceService service = wikiPriceServiceSupplier != null ? wikiPriceServiceSupplier.get() : null;
        return service != null ? service.getPriceEntry(itemId, allowRefresh) : null;
    }

    @Override
    public long nowMs() {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }
}
