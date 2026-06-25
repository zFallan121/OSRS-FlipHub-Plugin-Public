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

final class AccountwideProfileBackfillFactoryService {
    interface RuntimeHooks {
        List<LocalTradeDelta> snapshotLocalTrades(long profileKey);
        Integer resolveWorld();
    }

    private final int maxBatchSize;
    private final int maxLocalTrades;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;

    AccountwideProfileBackfillFactoryService(int maxBatchSize,
                                             int maxLocalTrades,
                                             long localEventBucketMs,
                                             long duplicateTradeWindowMs) {
        this.maxBatchSize = maxBatchSize;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
    }

    AccountwideProfileBackfillService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new AccountwideProfileBackfillService(
                maxBatchSize,
                maxLocalTrades,
                localEventBucketMs,
                duplicateTradeWindowMs,
                null
            );
        }
        return new AccountwideProfileBackfillService(
            maxBatchSize,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs,
            new AccountwideProfileBackfillService.Hooks() {
                @Override
                public List<LocalTradeDelta> snapshotLocalTrades(long profileKey) {
                    return runtimeHooks.snapshotLocalTrades(profileKey);
                }

                @Override
                public Integer resolveWorld() {
                    return runtimeHooks.resolveWorld();
                }
            }
        );
    }
}
