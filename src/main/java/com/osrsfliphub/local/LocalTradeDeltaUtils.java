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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local-trade merge and completion-dedup helpers. Consolidates what were three
 * pass-through layers (LocalTradeDeltaUtils -> LocalTradeDeltaDedupService ->
 * LocalTradeDeltaCompletionDedupService) into one static utility.
 */
final class LocalTradeDeltaUtils {
    private static final long LEGACY_COMPLETION_DUPLICATE_WINDOW_MS = 15L * 60L * 1000L;

    private LocalTradeDeltaUtils() {
    }

    /**
     * When a record takes its place in the replay: a purchase as its first unit was
     * bought, a sale as its last unit was sold (see {@link LocalTradeDelta#closedAtMs}).
     */
    static long replayTimeMs(LocalTradeDelta delta) {
        if (delta == null) {
            return 0L;
        }
        return delta.isBuy ? delta.tsClientMs : delta.closedAtMs();
    }

    /**
     * The order both ledgers replay deltas in, so a rebuild sees what the live
     * cache saw.
     *
     * <p>Two live fills in one client tick can be reported in either order, so
     * inside a 600 ms bucket a buy goes before a sell: the purchase a sale drew
     * on is then in the pool first. Trades imported from the in-game history
     * get no such favour. The sync invents their timestamps a few milliseconds
     * apart, which lands a whole batch in one bucket - and the order those
     * timestamps encode is the history's own, which is the evidence. Replaying
     * a batch buys-first would put every imported buy ahead of every imported
     * sale and turn a sale the history put before its parts into an assemble.
     */
    static Comparator<LocalTradeDelta> replayOrder() {
        long bucketMs = GeLifecyclePluginConstants.LOCAL_EVENT_BUCKET_MS;
        return Comparator
            .comparingLong((LocalTradeDelta delta) -> replayTimeMs(delta) / bucketMs)
            .thenComparingInt(delta -> delta != null && delta.isBuy && !isSyncedFromHistory(delta) ? 0 : 1)
            .thenComparingLong(LocalTradeDeltaUtils::replayTimeMs);
    }

    /** Whether a delta was imported from the in-game history rather than watched live. */
    static boolean isSyncedFromHistory(LocalTradeDelta delta) {
        return delta != null && delta.slot >= GeLifecyclePluginConstants.GE_HISTORY_SYNTHETIC_SLOT_START;
    }

    static List<LocalTradeDelta> dedupeLocalTrades(List<LocalTradeDelta> source,
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
        List<LocalTradeDelta> normalized = normalizeCompletionDeltas(merged, localEventBucketMs, duplicateTradeWindowMs);
        // Legacy duplicates are settled above, on the per-fill shape; only then is each
        // completed offer folded into one record. A file written fill by fill by an earlier
        // build is migrated here, once, and written back collapsed.
        return LocalTradeOfferCollapser.collapse(normalized);
    }

    static String buildLocalTradeSignature(LocalTradeDelta delta) {
        long normalizedValue = normalizeDeltaValue(delta);
        return delta.tsClientMs + "|" + delta.itemId + "|" + delta.isBuy + "|" + delta.deltaQty + "|" + normalizedValue;
    }

    static String buildLimitTradeSignature(LocalTradeDelta delta, long localEventBucketMs) {
        long bucket = delta.tsClientMs / localEventBucketMs;
        return bucket + "|" + delta.itemId + "|" + delta.isBuy + "|" + delta.deltaQty + "|" + delta.price;
    }

    static boolean isCompletionUpdatePair(String previousType, String currentType) {
        return ("OFFER_UPDATED".equals(previousType) && "OFFER_COMPLETED".equals(currentType))
            || ("OFFER_COMPLETED".equals(previousType) && "OFFER_UPDATED".equals(currentType));
    }

    static long normalizeDeltaValue(LocalTradeDelta delta) {
        if (delta == null) {
            return 0L;
        }
        int qty = delta.deltaQty;
        if (qty > 0 && delta.price > 0) {
            long total = (long) delta.price * (long) qty;
            if (!delta.isBuy) {
                long tax = computeSellTax(delta.itemId, total, qty, delta.price);
                return Math.max(0L, total - tax);
            }
            return Math.max(0L, total);
        }
        return Math.max(0L, delta.deltaGp);
    }

    /**
     * Whether a live record repeats one just stored: the same event, in the same 600 ms
     * bucket, for the same slot, item, side, quantity, price and value.
     *
     * <p>Only the same event type counts. An update followed by a completion of the
     * same size used to be taken for a repeat too, but the pipeline computes a
     * completion's quantity as what was still outstanding, so two equal chunks are two
     * chunks - and the completion is what folds the offer's fills into one record, so
     * dropping it lost both the last chunk and the collapse. A completion that really
     * does repeat the update's cumulative fill is zeroed upstream by
     * {@link RecentTradeDeduper}, which can see the cumulative figures this cannot.
     */
    static boolean isLikelyDuplicateTradeDelta(List<LocalTradeDelta> deltas,
                                               LocalTradeDelta candidate,
                                               long localEventBucketMs,
                                               long duplicateTradeWindowMs,
                                               int inspectionLimit) {
        if (deltas == null || deltas.isEmpty() || candidate == null) {
            return false;
        }
        long bucketSize = Math.max(1L, localEventBucketMs);
        long windowMs = Math.max(0L, duplicateTradeWindowMs);
        int maxInspected = inspectionLimit > 0 ? inspectionLimit : 12;
        long bucket = candidate.tsClientMs / bucketSize;
        long normalizedValue = normalizeDeltaValue(candidate);
        String candidateType = candidate.eventType != null ? candidate.eventType : "";
        int inspected = 0;
        for (int i = deltas.size() - 1; i >= 0 && inspected < maxInspected; i--, inspected++) {
            LocalTradeDelta prev = deltas.get(i);
            if (prev == null) {
                continue;
            }
            if (Math.abs(candidate.tsClientMs - prev.tsClientMs) > windowMs) {
                continue;
            }
            long prevBucket = prev.tsClientMs / bucketSize;
            if (prev.slot != candidate.slot
                || prev.itemId != candidate.itemId
                || prev.isBuy != candidate.isBuy
                || prev.deltaQty != candidate.deltaQty
                || prev.price != candidate.price) {
                continue;
            }
            if (normalizeDeltaValue(prev) != normalizedValue) {
                continue;
            }
            String prevType = prev.eventType != null ? prev.eventType : "";
            if (bucket == prevBucket && prevType.equals(candidateType)) {
                return true;
            }
        }
        return false;
    }

    private static List<LocalTradeDelta> normalizeCompletionDeltas(List<LocalTradeDelta> source,
                                                                   long localEventBucketMs,
                                                                   long duplicateTradeWindowMs) {
        if (source == null || source.isEmpty()) {
            return source != null ? new ArrayList<>(source) : new ArrayList<>();
        }
        List<LocalTradeDelta> normalized = new ArrayList<>(source.size());
        Map<String, LocalTradeDelta> recentBySignature = new HashMap<>();
        Map<String, LocalTradeDelta> recentByTradeKey = new HashMap<>();
        for (LocalTradeDelta delta : source) {
            if (delta == null) {
                continue;
            }
            if (delta.endMs > 0) {
                // Written by the collapser, so already one normalised record per offer.
                // The heuristics below read the per-fill shape of older files - a
                // completion repeating its update, a gross total - and applied to a whole
                // offer they misfire: two same-sized offers on one slot a quarter of an
                // hour apart would be taken for a repeated completion and one dropped.
                normalized.add(delta);
                continue;
            }
            delta = normalizeSellDeltaIfGross(delta);
            String signature = buildCompletionDedupSignature(delta, localEventBucketMs);
            String tradeKey = buildCompletionDedupTradeKey(delta);
            LocalTradeDelta previous = recentBySignature.get(signature);
            boolean strictMatch = previous != null;
            if (previous == null) {
                LocalTradeDelta fallback = recentByTradeKey.get(tradeKey);
                if (fallback != null) {
                    String previousType = fallback.eventType != null ? fallback.eventType : "";
                    String currentType = delta.eventType != null ? delta.eventType : "";
                    if (isCompletionUpdatePair(previousType, currentType)
                        || ("OFFER_COMPLETED".equals(previousType) && "OFFER_COMPLETED".equals(currentType))) {
                        previous = fallback;
                    }
                }
            }
            LocalTradeDelta effective = delta;
            if (previous != null) {
                String previousType = previous.eventType != null ? previous.eventType : "";
                String currentType = delta.eventType != null ? delta.eventType : "";
                long tsDiffMs = Math.abs(delta.tsClientMs - previous.tsClientMs);
                if (tsDiffMs <= duplicateTradeWindowMs) {
                    if ("OFFER_COMPLETED".equals(currentType) && "OFFER_UPDATED".equals(previousType) && delta.deltaQty > 0) {
                        effective = zeroedDelta(delta);
                    } else if ("OFFER_COMPLETED".equals(currentType) && "OFFER_COMPLETED".equals(previousType)) {
                        continue;
                    } else if (currentType.equals(previousType)
                        || ("OFFER_UPDATED".equals(currentType) && "OFFER_COMPLETED".equals(previousType))) {
                        if (strictMatch || isCompletionUpdatePair(previousType, currentType)) {
                            continue;
                        }
                    }
                } else if ("OFFER_COMPLETED".equals(currentType)
                    && "OFFER_UPDATED".equals(previousType)
                    && delta.deltaQty > 0
                    && delta.deltaQty == previous.deltaQty
                    && isLikelyLegacyCompletionDuplicate(previous, delta, duplicateTradeWindowMs)) {
                    effective = zeroedDelta(delta);
                } else if ("OFFER_COMPLETED".equals(currentType)
                    && "OFFER_COMPLETED".equals(previousType)
                    && delta.deltaQty > 0
                    && delta.deltaQty == previous.deltaQty
                    && isLikelyLegacyCompletionDuplicate(previous, delta, duplicateTradeWindowMs)) {
                    continue;
                }
            }
            normalized.add(effective);
            recentBySignature.put(signature, effective);
            recentByTradeKey.put(tradeKey, effective);
        }
        return normalized;
    }

    private static LocalTradeDelta zeroedDelta(LocalTradeDelta delta) {
        return new LocalTradeDelta(
            delta.tsClientMs,
            delta.slot,
            delta.itemId,
            delta.isBuy,
            0,
            0L,
            delta.eventType,
            delta.price,
            delta.baselineSynthetic,
            delta.offerStartMs,
            delta.endMs
        );
    }

    private static LocalTradeDelta normalizeSellDeltaIfGross(LocalTradeDelta delta) {
        if (delta == null || delta.isBuy || delta.deltaQty <= 0 || delta.price <= 0) {
            return delta;
        }
        long grossFromPrice = (long) delta.price * (long) delta.deltaQty;
        long deltaGp = Math.max(0L, delta.deltaGp);
        if (deltaGp != grossFromPrice) {
            return delta;
        }
        long tax = computeSellTax(delta.itemId, grossFromPrice, delta.deltaQty, delta.price);
        long netFromPrice = Math.max(0L, grossFromPrice - tax);
        return new LocalTradeDelta(
            delta.tsClientMs,
            delta.slot,
            delta.itemId,
            delta.isBuy,
            delta.deltaQty,
            netFromPrice,
            delta.eventType,
            delta.price,
            delta.baselineSynthetic,
            delta.offerStartMs,
            delta.endMs
        );
    }

    private static boolean isLikelySellGrossNetDuplicate(LocalTradeDelta previous, LocalTradeDelta current) {
        if (previous == null || current == null || current.isBuy || previous.isBuy) {
            return false;
        }
        if (current.price <= 0 || current.deltaQty <= 0) {
            return false;
        }
        if (previous.slot != current.slot
            || previous.itemId != current.itemId
            || previous.isBuy != current.isBuy
            || previous.price != current.price) {
            return false;
        }
        long qty = current.deltaQty;
        long gross = (long) current.price * qty;
        long tax = computeSellTax(current.itemId, gross, qty, current.price);
        long net = Math.max(0L, gross - tax);
        if (gross <= net) {
            return false;
        }
        long previousGp = Math.max(0L, previous.deltaGp);
        long currentGp = Math.max(0L, current.deltaGp);
        return (previousGp == gross && currentGp == net)
            || (previousGp == net && currentGp == gross);
    }

    private static boolean isLikelyLegacyCompletionDuplicate(LocalTradeDelta previous,
                                                             LocalTradeDelta current,
                                                             long duplicateTradeWindowMs) {
        if (previous == null || current == null) {
            return false;
        }
        long tsDiffMs = Math.abs(current.tsClientMs - previous.tsClientMs);
        long maxWindowMs = Math.max(Math.max(0L, duplicateTradeWindowMs), LEGACY_COMPLETION_DUPLICATE_WINDOW_MS);
        if (tsDiffMs > maxWindowMs) {
            return false;
        }
        long previousGp = Math.max(0L, previous.deltaGp);
        long currentGp = Math.max(0L, current.deltaGp);
        return previousGp == currentGp || isLikelySellGrossNetDuplicate(previous, current);
    }

    private static long computeSellTax(int itemId, long grossTotal, long qty, long unitPrice) {
        if (grossTotal <= 0L || qty <= 0L) {
            return 0L;
        }
        return unitPrice > 0L
            ? GeTax.forSale(itemId, unitPrice, qty)
            : GeTax.forGrossTotal(itemId, grossTotal);
    }

    private static String buildCompletionDedupSignature(LocalTradeDelta delta, long localEventBucketMs) {
        if (delta == null) {
            return "";
        }
        long bucket = delta.tsClientMs / localEventBucketMs;
        long normalizedValue = normalizeDeltaValue(delta);
        return bucket + "|" + delta.slot + "|" + delta.itemId + "|" + delta.isBuy + "|" + delta.deltaQty + "|"
            + delta.price + "|" + normalizedValue;
    }

    private static String buildCompletionDedupTradeKey(LocalTradeDelta delta) {
        if (delta == null) {
            return "";
        }
        return delta.slot + "|" + delta.itemId + "|" + delta.isBuy + "|" + delta.price;
    }

    private static void addMergedTrades(List<LocalTradeDelta> merged, Set<String> seen, List<LocalTradeDelta> source) {
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
}
