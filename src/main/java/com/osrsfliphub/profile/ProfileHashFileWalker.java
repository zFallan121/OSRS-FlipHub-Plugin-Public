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
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

@Slf4j
final class ProfileHashFileWalker {
    /** How many times a listing is tried before it is given up. */
    private static final int LISTING_TRIES = 3;

    interface Visitor {
        void visit(long profileHash, Filepath file);
    }

    private ProfileHashFileWalker() {
    }

    static void walk(Filepath dir, Visitor visitor) {
        if (dir == null || visitor == null || !dir.exists()) {
            return;
        }
        for (Filepath path : list(dir)) {
            Long hash = ProfileHashFileParser.parsePositiveHashFromProfileFileName(path.getFileName());
            if (hash == null) {
                continue;
            }
            visitor.visit(hash, path);
        }
    }

    /**
     * Everything in the folder, or nothing: never the part that was read before a listing failed.
     *
     * <p>On Mac and Linux a listing reads each entry's details as it goes, so a save's scratch
     * file moved into place part way through stops it short. The files it had not reached yet
     * would then read as characters with nothing saved.
     */
    private static List<Filepath> list(Filepath dir) {
        for (int attempt = 1; ; attempt++) {
            // One level deep: the folder itself comes first, and its name is no character's file.
            try (Stream<Filepath> files = dir.walk(1)) {
                return files.collect(Collectors.toList());
            } catch (IOException | UncheckedIOException ex) {
                if (attempt >= LISTING_TRIES) {
                    // Silently listing nothing reads as a player with no saved characters.
                    log.warn("FlipHub: could not list the profile files in {}", dir, ex);
                    return Collections.emptyList();
                }
            }
        }
    }
}
