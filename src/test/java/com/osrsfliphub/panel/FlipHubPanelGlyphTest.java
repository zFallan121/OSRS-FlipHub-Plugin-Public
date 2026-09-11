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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The panel may only draw symbols that have been seen to render.
 *
 * <p>This has bitten twice: a hammer was proposed for the recipe badge and
 * rejected on font-coverage grounds, then a small down-caret shipped in the
 * summary heading and drew as a tofu box.
 *
 * <p>Asking the font is not the test. Segoe UI reports that it cannot display
 * the bookmark star either, and the star renders perfectly - Swing falls back
 * to another face for missing glyphs, but only sometimes, and which times is
 * not something this code can find out. So the rule is evidence, not
 * prediction: a symbol is allowed here once someone has watched it draw, and a
 * new one is a deliberate addition to this list rather than a guess that got
 * through review.
 */
public class FlipHubPanelGlyphTest {
    /** The panel's own resolution order, from FlipHubUiStyler. */
    private static final String[] TEXT_FAMILIES = {
        "Inter", "Segoe UI Variable Text", "Segoe UI", "Avenir Next", "Trebuchet MS"
    };

    /** Escapes in the panel sources, which is how every symbol there is written. */
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private static java.awt.Font panelFont() {
        for (String family : TEXT_FAMILIES) {
            java.awt.Font candidate = new java.awt.Font(family, java.awt.Font.PLAIN, 12);
            if (family.equalsIgnoreCase(candidate.getFamily())) {
                return candidate;
            }
        }
        return new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12);
    }

    private static List<Path> panelSources() throws IOException {
        Path root = Paths.get("src", "main", "java", "com", "osrsfliphub", "panel");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sources = new ArrayList<>();
            files.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            return sources;
        }
    }

    /**
     * Symbols observed rendering in the RuneLite sidebar on Windows.
     *
     * <p>U+25BE is deliberately absent: it is the one that failed.
     */
    private static final java.util.Set<Integer> SEEN_TO_RENDER = new java.util.HashSet<>(java.util.Arrays.asList(
        0x25B2,  // up triangle - sort direction, card chevrons
        0x25BC,  // down triangle - the same, and the summary caret
        0x2605,  // filled star - bookmarked
        0x2606,  // hollow star - not bookmarked
        0x00B7   // middle dot
    ));

    @Test
    public void thePanelDrawsOnlySymbolsThatHaveBeenSeenToRender() throws IOException {
        List<String> unproven = new ArrayList<>();

        for (Path source : panelSources()) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            Matcher matcher = UNICODE_ESCAPE.matcher(text);
            while (matcher.find()) {
                int codePoint = Integer.parseInt(matcher.group(1), 16);
                // Escaped Latin text is a formatting choice, not a coverage risk.
                if (codePoint < 0x00B0) {
                    continue;
                }
                if (!SEEN_TO_RENDER.contains(codePoint)) {
                    unproven.add(String.format("U+%04X in %s", codePoint, source.getFileName()));
                }
            }
        }

        assertTrue(
            "no one has watched these draw: " + unproven
                + " - use a symbol already in SEEN_TO_RENDER, or add it here once it is confirmed"
                + " in the sidebar rather than assumed from the font",
            unproven.isEmpty());
    }

    @Test
    public void theCaretThatFailedStaysOutOfTheAllowedSet() {
        // The bug this file exists for. If U+25BE is ever added here, it should
        // be because someone saw it draw - not because it looked right in source.
        assertFalse("U+25BE drew as a tofu box in the summary heading",
            SEEN_TO_RENDER.contains(0x25BE));
    }
}
