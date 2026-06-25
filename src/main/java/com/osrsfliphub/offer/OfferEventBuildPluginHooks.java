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

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class OfferEventBuildPluginHooks implements OfferEventBuildService.Hooks {
    private final Supplier<OfferUpdateStampService> offerUpdateStampServiceSupplier;
    private final BooleanSupplier withinLoginGraceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final LongSupplier nowMsSupplier;

    OfferEventBuildPluginHooks(
        Supplier<OfferUpdateStampService> offerUpdateStampServiceSupplier,
        BooleanSupplier withinLoginGraceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        LongSupplier nowMsSupplier
    ) {
        this.offerUpdateStampServiceSupplier = offerUpdateStampServiceSupplier;
        this.withinLoginGraceSupplier = withinLoginGraceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.nowMsSupplier = nowMsSupplier;
    }

    @Override
    public boolean stampMatchesSnapshot(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
        OfferUpdateStampService service = offerUpdateStampServiceSupplier != null
            ? offerUpdateStampServiceSupplier.get()
            : null;
        return service != null && service.stampMatchesSnapshot(stamp, snapshot);
    }

    @Override
    public long resolveBaselineTradeTimestamp(OfferUpdateStamp stamp, long lastLoginMs) {
        OfferUpdateStampService service = offerUpdateStampServiceSupplier != null
            ? offerUpdateStampServiceSupplier.get()
            : null;
        return service != null ? service.resolveBaselineTradeTimestamp(stamp, lastLoginMs) : 0L;
    }

    @Override
    public boolean isWithinLoginGrace() {
        return withinLoginGraceSupplier != null && withinLoginGraceSupplier.getAsBoolean();
    }

    @Override
    public boolean hasRecentLocalBuy(int itemId, long nowMs) {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        if (service == null) {
            return false;
        }
        long accountKey = service.resolveAccountHash();
        return accountKey > 0 && service.hasRecentLocalBuy(accountKey, itemId, nowMs);
    }

    @Override
    public long nowMs() {
        return nowMsSupplier != null ? nowMsSupplier.getAsLong() : System.currentTimeMillis();
    }
}
