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

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The recording screen builds and opens without a plugin behind it.
 *
 * <p>Thin on purpose. What the screen works out is worked out by {@link RecipeFlipLedger}, which
 * is tested directly; what this covers is the half that only fails when it is drawn - a Swing
 * layout that throws on assembly, or a service call made before its null guard. The panel is
 * built once at construction and never rebuilt, so a break here is a break that ships.
 */
public class RecipeRecorderTest {
    @Test
    public void theScreenBuildsAndOpensWithNoPluginBehindIt() {
        RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
        });

        assertNotNull(recorder.view());

        // Every service comes through Bridge, which hands back null outside the plugin. Opening
        // has to survive that, because it is also what a player sees before they have logged in.
        recorder.open();

        assertTrue(recorder.view().getViewport().getView().getPreferredSize().height > 0);
    }
}
