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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProfileHashFileWalkerTest {
    @Test
    public void walkVisitsOnlyValidHashProfileFiles() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-hash-walker");
        try {
            Files.writeString(baseDir.resolve("hash_111.json"), "{}");
            Files.writeString(baseDir.resolve("hash_invalid.json"), "{}");
            Files.writeString(baseDir.resolve("profile_222.json"), "{}");

            List<Long> visitedHashes = new ArrayList<>();
            List<String> visitedNames = new ArrayList<>();
            ProfileHashFileWalker.walk(baseDir, (hash, path) -> {
                visitedHashes.add(hash);
                visitedNames.add(path.getFileName().toString());
            });

            assertEquals(1, visitedHashes.size());
            assertEquals(Long.valueOf(111L), visitedHashes.get(0));
            assertEquals(1, visitedNames.size());
            assertEquals("hash_111.json", visitedNames.get(0));
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void walkNoOpsForMissingDirectoryOrNullVisitor() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        Path missingDir = Path.of("C:\\__fliphub_missing_profile_hash_walker__");
        ProfileHashFileWalker.walk(missingDir, (hash, path) -> visits.incrementAndGet());
        assertEquals(0, visits.get());

        Path baseDir = Files.createTempDirectory("profile-hash-walker-null-visitor");
        try {
            Files.writeString(baseDir.resolve("hash_123.json"), "{}");
            ProfileHashFileWalker.walk(baseDir, null);
            assertEquals(0, visits.get());
        } finally {
            deleteRecursively(baseDir);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }
}
