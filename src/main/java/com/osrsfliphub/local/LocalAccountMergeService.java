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
import java.util.Map;
import java.util.Set;

@javax.inject.Singleton
final class LocalAccountMergeService {
    @javax.inject.Inject
    LocalAccountMergeService() {
    }

    static final class Result {
        final List<LocalTradeDelta> mergedSnapshot;
        final boolean changed;

        Result(List<LocalTradeDelta> mergedSnapshot, boolean changed) {
            this.mergedSnapshot = mergedSnapshot;
            this.changed = changed;
        }
    }

    Result merge(Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
                 Map<Long, Long> localSessionStartByAccount,
                 long targetKey,
                 long sourceKey,
                 int maxLocalTrades) {
        if (localTradeDeltasByAccount == null || localSessionStartByAccount == null) {
            return new Result(null, false);
        }

        List<LocalTradeDelta> mergedSnapshot = null;
        boolean changed = false;

        List<LocalTradeDelta> target = localTradeDeltasByAccount.get(targetKey);
        List<LocalTradeDelta> source = localTradeDeltasByAccount.remove(sourceKey);
        if (source != null && !source.isEmpty()) {
            changed = true;
            if (target == null || target.isEmpty()) {
                localTradeDeltasByAccount.put(targetKey, new ArrayList<>(source));
            } else {
                Set<String> seen = new HashSet<>();
                for (LocalTradeDelta delta : target) {
                    if (delta != null) {
                        seen.add(LocalTradeDeltaUtils.buildLocalTradeSignature(delta));
                    }
                }
                for (LocalTradeDelta delta : source) {
                    if (delta != null && seen.add(LocalTradeDeltaUtils.buildLocalTradeSignature(delta))) {
                        target.add(delta);
                    }
                }
                target.sort(Comparator.comparingLong(delta -> delta != null ? delta.tsClientMs : 0L));
                if (target.size() > maxLocalTrades) {
                    int trim = target.size() - maxLocalTrades;
                    target.subList(0, trim).clear();
                }
            }
        }

        List<LocalTradeDelta> updated = localTradeDeltasByAccount.get(targetKey);
        if (updated != null) {
            mergedSnapshot = new ArrayList<>(updated);
        }

        Long targetStart = localSessionStartByAccount.get(targetKey);
        Long sourceStart = localSessionStartByAccount.remove(sourceKey);
        if (sourceStart != null && (targetStart == null || targetStart <= 0)) {
            localSessionStartByAccount.put(targetKey, sourceStart);
        }

        return new Result(mergedSnapshot, changed);
    }
}
