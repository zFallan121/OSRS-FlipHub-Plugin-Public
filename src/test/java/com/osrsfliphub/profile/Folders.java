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

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.util.Filepath;

/**
 * A folder of a test's own, handed to the plugin the way RuneLite hands it the real one.
 *
 * <p>The plugin is given a {@link Filepath} and never reaches a raw path: {@code Filepath.Unchecked}
 * is for tests alone, and this is the one place they use it.
 */
final class Folders {
    private Folders() {
    }

    /** The folder as RuneLite would hand it over: nothing above it can be reached through it. */
    static Filepath rooted(Path folder) {
        return Filepath.Unchecked.getRooted(folder);
    }

    /** Where a file the plugin names really is, for a test to look at it behind the plugin's back. */
    static Path path(Filepath file) {
        return Filepath.Unchecked.getPath(file);
    }

    /** A file by its name alone, in a folder nobody looks in. */
    static Filepath named(String fileName) {
        return rooted(Path.of("no-such-folder")).joinSegment(fileName);
    }

    /** The trade files of a player whose plugin folder is {@code folder}: they are in its {@code profiles}. */
    static ProfileStore store(Path folder) {
        return store(new Gson(), folder);
    }

    static ProfileStore store(Gson gson, Path folder) {
        return new ProfileStore(gson, () -> rooted(folder));
    }

    /**
     * What RuneLite does each time the plugin asks for its folder (see {@code Plugin#getPluginDirectory}):
     * it makes {@code plugin-data}, and renames the old folder to the new place when that place is free.
     * One rename, all or nothing; a failure is thrown and leaves the old folder as it was.
     */
    static Filepath moveAsRuneLiteDoes(Path oldFolder, Path newFolder) throws IOException {
        Files.createDirectories(newFolder.getParent());
        if (!Files.exists(newFolder) && Files.exists(oldFolder)) {
            Files.move(oldFolder, newFolder);
        }
        return rooted(newFolder);
    }

    /** Writes a file as somebody other than the plugin would: a person, another build, a repair script. */
    static void write(Filepath file, String text) throws IOException {
        Path path = path(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static String read(Filepath file) throws IOException {
        return Files.readString(path(file), StandardCharsets.UTF_8);
    }
}
