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

final class LocalTradeDeltaUtils {
    private static final LocalTradeDeltaDedupService dedupService = new LocalTradeDeltaDedupService();

    private LocalTradeDeltaUtils() {
    }

    static List<LocalTradeDelta> mergeLocalTrades(List<LocalTradeDelta> primary,
                                                  List<LocalTradeDelta> secondary,
                                                  List<LocalTradeDelta> tertiary,
                                                  List<LocalTradeDelta> quaternary,
                                                  int maxLocalTrades) {
        return dedupService.mergeLocalTrades(primary, secondary, tertiary, quaternary, maxLocalTrades);
    }

    static List<LocalTradeDelta> dedupeLocalTrades(List<LocalTradeDelta> source,
                                                   int maxLocalTrades,
                                                   long localEventBucketMs,
                                                   long duplicateTradeWindowMs) {
        return dedupService.dedupeLocalTrades(source, maxLocalTrades, localEventBucketMs, duplicateTradeWindowMs);
    }

    static boolean isCompletionUpdatePair(String previousType, String currentType) {
        return dedupService.isCompletionUpdatePair(previousType, currentType);
    }

    static String buildLocalTradeSignature(LocalTradeDelta delta) {
        return dedupService.buildLocalTradeSignature(delta);
    }

    static String buildLimitTradeSignature(LocalTradeDelta delta, long localEventBucketMs) {
        return dedupService.buildLimitTradeSignature(delta, localEventBucketMs);
    }

    static long normalizeDeltaValue(LocalTradeDelta delta) {
        return dedupService.normalizeDeltaValue(delta);
    }

    static boolean isLikelyDuplicateTradeDelta(List<LocalTradeDelta> deltas,
                                               LocalTradeDelta candidate,
                                               long localEventBucketMs,
                                               long duplicateTradeWindowMs,
                                               int inspectionLimit) {
        return dedupService.isLikelyDuplicateTradeDelta(
            deltas,
            candidate,
            localEventBucketMs,
            duplicateTradeWindowMs,
            inspectionLimit
        );
    }
}
