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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LocalTradeDeltaDedupService {
    private final LocalTradeDeltaCompletionDedupService completionDedupService = new LocalTradeDeltaCompletionDedupService();

    List<LocalTradeDelta> mergeLocalTrades(List<LocalTradeDelta> primary,
                                           List<LocalTradeDelta> secondary,
                                           List<LocalTradeDelta> tertiary,
                                           List<LocalTradeDelta> quaternary,
                                           int maxLocalTrades) {
        List<LocalTradeDelta> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addMergedTrades(merged, seen, primary);
        addMergedTrades(merged, seen, secondary);
        addMergedTrades(merged, seen, tertiary);
        addMergedTrades(merged, seen, quaternary);
        if (merged.isEmpty()) {
            return null;
        }
        merged.sort(Comparator.comparingLong(delta -> delta != null ? delta.tsClientMs : 0L));
        trimToMax(merged, maxLocalTrades);
        return merged;
    }

    List<LocalTradeDelta> dedupeLocalTrades(List<LocalTradeDelta> source,
                                            int maxLocalTrades,
                                            long localEventBucketMs,
                                            long duplicateTradeWindowMs) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<LocalTradeDelta> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addMergedTrades(merged, seen, source);
        if (merged.isEmpty()) {
            return merged;
        }
        merged.sort(Comparator.comparingLong(delta -> delta != null ? delta.tsClientMs : 0L));
        List<LocalTradeDelta> normalized = completionDedupService.normalizeCompletionDeltas(
            merged,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
        trimToMax(normalized, maxLocalTrades);
        return normalized;
    }

    boolean isCompletionUpdatePair(String previousType, String currentType) {
        return completionDedupService.isCompletionUpdatePair(previousType, currentType);
    }

    String buildLocalTradeSignature(LocalTradeDelta delta) {
        long normalizedValue = normalizeDeltaValue(delta);
        return delta.tsClientMs + "|" + delta.itemId + "|" + delta.isBuy + "|" + delta.deltaQty + "|" + normalizedValue;
    }

    String buildLimitTradeSignature(LocalTradeDelta delta, long localEventBucketMs) {
        long bucket = delta.tsClientMs / localEventBucketMs;
        return bucket + "|" + delta.itemId + "|" + delta.isBuy + "|" + delta.deltaQty + "|" + delta.price;
    }

    long normalizeDeltaValue(LocalTradeDelta delta) {
        return completionDedupService.normalizeDeltaValue(delta);
    }

    boolean isLikelyDuplicateTradeDelta(List<LocalTradeDelta> deltas,
                                        LocalTradeDelta candidate,
                                        long localEventBucketMs,
                                        long duplicateTradeWindowMs,
                                        int inspectionLimit) {
        return completionDedupService.isLikelyDuplicateTradeDelta(
            deltas,
            candidate,
            localEventBucketMs,
            duplicateTradeWindowMs,
            inspectionLimit
        );
    }

    private void addMergedTrades(List<LocalTradeDelta> merged, Set<String> seen, List<LocalTradeDelta> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (LocalTradeDelta delta : source) {
            if (delta == null) {
                continue;
            }
            if (seen.add(buildLocalTradeSignature(delta))) {
                merged.add(delta);
            }
        }
    }

    private void trimToMax(List<LocalTradeDelta> deltas, int maxLocalTrades) {
        if (deltas == null || maxLocalTrades <= 0 || deltas.size() <= maxLocalTrades) {
            return;
        }
        int trim = deltas.size() - maxLocalTrades;
        deltas.subList(0, trim).clear();
    }
}
