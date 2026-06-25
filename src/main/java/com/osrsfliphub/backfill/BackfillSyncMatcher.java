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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class BackfillSyncMatcher {
    interface Hooks {
        boolean isDebugEnabled();
        void logDebug(String message);
    }

    private final int maxBackfillProfileCount;
    private final double backfillMatchScoreThreshold;
    private final Hooks hooks;

    BackfillSyncMatcher(int maxBackfillProfileCount, double backfillMatchScoreThreshold, Hooks hooks) {
        this.maxBackfillProfileCount = Math.max(1, maxBackfillProfileCount);
        this.backfillMatchScoreThreshold = Math.max(0.0d, backfillMatchScoreThreshold);
        this.hooks = hooks;
    }

    Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                        Map<Long, StatsSummary> localSummaries,
                                        StatsSummary remoteSummary) {
        if (profileKeys == null || profileKeys.isEmpty() || localSummaries == null || remoteSummary == null) {
            return null;
        }
        List<Long> keys = profileKeys.stream()
            .filter(key -> key != null && key > 0)
            .sorted()
            .collect(Collectors.toList());
        int n = keys.size();
        if (n == 0 || n > maxBackfillProfileCount) {
            return null;
        }

        double bestScore = Double.MAX_VALUE;
        int bestMask = 0;
        int maskLimit = 1 << n;
        for (int mask = 0; mask < maskLimit; mask++) {
            long profit = 0L;
            long cost = 0L;
            long tax = 0L;
            long flips = 0L;
            for (int bit = 0; bit < n; bit++) {
                if ((mask & (1 << bit)) == 0) {
                    continue;
                }
                Long key = keys.get(bit);
                StatsSummary summary = localSummaries.get(key);
                if (summary == null) {
                    continue;
                }
                profit += summary.total_profit_gp != null ? summary.total_profit_gp : 0L;
                cost += summary.total_cost_gp != null ? summary.total_cost_gp : 0L;
                tax += summary.tax_paid_gp != null ? summary.tax_paid_gp : 0L;
                flips += summary.fill_count != null ? summary.fill_count : 0L;
            }
            double score = computeSummaryMatchScore(profit, cost, tax, flips, remoteSummary);
            if (score < bestScore) {
                bestScore = score;
                bestMask = mask;
            }
        }

        if (bestScore > backfillMatchScoreThreshold) {
            if (hooks != null && hooks.isDebugEnabled()) {
                hooks.logDebug(
                    "FlipHub backfill skipped: best summary match score " + bestScore
                        + " exceeds threshold " + backfillMatchScoreThreshold
                );
            }
            return null;
        }

        Set<Long> synced = new HashSet<>();
        for (int bit = 0; bit < n; bit++) {
            if ((bestMask & (1 << bit)) != 0) {
                synced.add(keys.get(bit));
            }
        }
        return synced;
    }

    private double computeSummaryMatchScore(long profit, long cost, long tax, long flips, StatsSummary remoteSummary) {
        if (remoteSummary == null) {
            return Double.MAX_VALUE;
        }
        long remoteProfit = remoteSummary.total_profit_gp != null ? remoteSummary.total_profit_gp : 0L;
        long remoteCost = remoteSummary.total_cost_gp != null ? remoteSummary.total_cost_gp : 0L;
        long remoteTax = remoteSummary.tax_paid_gp != null ? remoteSummary.tax_paid_gp : 0L;
        long remoteFlips = remoteSummary.fill_count != null ? remoteSummary.fill_count : 0L;

        double profitDiff = normalizeDiff(profit, remoteProfit);
        double costDiff = normalizeDiff(cost, remoteCost);
        double taxDiff = normalizeDiff(tax, remoteTax);
        double flipsDiff = normalizeDiff(flips, remoteFlips);
        return (profitDiff + costDiff + taxDiff + flipsDiff) / 4.0d;
    }

    private double normalizeDiff(long value, long target) {
        double denominator = Math.max(1.0d, Math.abs((double) target));
        return Math.abs((double) value - (double) target) / denominator;
    }
}
