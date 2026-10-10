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

import java.nio.file.Path;
import java.nio.file.Paths;
import net.runelite.client.RuneLite;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The tests must never run in the home folder of whoever runs them.
 *
 * <p>RuneLite takes {@code .runelite} from {@code user.home}. Since the plugin keeps its files in
 * RuneLite's folder for it, a plugin that asks for that folder makes RuneLite MOVE
 * {@code .runelite/fliphub} to {@code .runelite/plugin-data/osrs-fliphub}. A test that started the
 * real plugin in the real home would do that to the trade history of the person running it. So
 * every way of running the tests gives them a home of their own ({@code -Duser.home}, set by the
 * check script and by the {@code test} task in build.gradle), and this fails when one does not.
 */
public class TestsRunInAHomeOfTheirOwnTest {
    @Test
    public void theTestsHomeIsNotTheRealHomeOfWhoeverRunsThem() {
        String real = realHome();
        assertTrue("cannot tell where the real home is (neither USERPROFILE nor HOME is set), so cannot tell"
            + " that the tests are kept out of it", real != null && !real.trim().isEmpty());
        Path realHome = Paths.get(real).toAbsolutePath().normalize();
        Path testHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();

        assertFalse("the tests are running with user.home set to the real home, " + realHome + ": a test that"
            + " starts the plugin would move the real .runelite/fliphub. Run them with -Duser.home=<a folder of"
            + " the build's own>", same(testHome, realHome));
        assertFalse("RuneLite has already taken its folder from the real home: " + RuneLite.RUNELITE_DIR,
            under(RuneLite.RUNELITE_DIR.toPath(), realHome.resolve(".runelite")));
        assertFalse("RuneLite's plugin folders are in the real home: " + RuneLite.PLUGIN_DATA,
            under(RuneLite.PLUGIN_DATA, realHome.resolve(".runelite")));
    }

    /** Where Windows, and then everything else, says the person's home is, whatever Java was told. */
    private static String realHome() {
        String profile = System.getenv("USERPROFILE");
        return profile != null && !profile.trim().isEmpty() ? profile : System.getenv("HOME");
    }

    private static boolean same(Path one, Path other) {
        return one.toString().equalsIgnoreCase(other.toString());
    }

    /** Whether a folder is another or inside it, whatever the case of the letters: Windows does not care. */
    private static boolean under(Path folder, Path parent) {
        String inner = folder.toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
        String outer = parent.toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
        return inner.equals(outer) || inner.startsWith(outer + java.io.File.separator);
    }
}
