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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AccountwideProfileKeyCollectorTest {
    @Test
    public void collectIncludesDirAndLocalTradeKeys() throws Exception {
        Path baseDir = Files.createTempDirectory("accountwide-key-collector");
        try {
            Path dirA = Files.createDirectories(baseDir.resolve("a"));
            Path dirB = Files.createDirectories(baseDir.resolve("b"));
            Files.writeString(dirA.resolve("hash_111.json"), "{}");
            Files.writeString(dirB.resolve("hash_222.json"), "{}");
            Files.writeString(dirA.resolve("hash_invalid.json"), "{}");

            Map<Long, List<LocalTradeDelta>> localTrades = new HashMap<>();
            List<LocalTradeDelta> deltas = new ArrayList<>();
            deltas.add(new LocalTradeDelta(1L, 1, 1, true, 1, 1L, "OFFER_UPDATED", 1, false));
            localTrades.put(333L, deltas);
            localTrades.put(444L, new ArrayList<>());

            AccountwideProfileKeyCollector collector = new AccountwideProfileKeyCollector();
            Set<Long> keys = collector.collect(
                dirA,
                dirB,
                localTrades,
                new Object(),
                HashMap::new
            );

            assertTrue(keys.contains(111L));
            assertTrue(keys.contains(222L));
            assertTrue(keys.contains(333L));
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void collectUsesFallbackWhenNoKeysFound() {
        Map<Long, List<LocalTradeDelta>> localTrades = new HashMap<>();
        AccountwideProfileKeyCollector collector = new AccountwideProfileKeyCollector();
        Set<Long> keys = collector.collect(
            null,
            null,
            localTrades,
            new Object(),
            () -> {
                Map<Long, String> fallback = new HashMap<>();
                fallback.put(555L, "fallback");
                return fallback;
            }
        );

        assertTrue(keys.contains(555L));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
