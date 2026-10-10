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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The plugin keeps its files through RuneLite's {@code Filepath} and nothing else.
 *
 * <p>The Plugin Hub reviews an update by itself only when the plugin uses none of Java's own file
 * code; one that does is read by hand, and waits for it. One import is enough to put every later
 * update back in that queue, and nothing at compile time says so. This reads every source file the
 * plugin ships and names each line that would.
 *
 * <p>Two names from {@code java.nio.file} are allowed, each imported by name:
 * {@code StandardCopyOption}, which {@code Filepath.moveTo} takes, and
 * {@code AtomicMoveNotSupportedException}, the one failure of the one-step replace after which a
 * save tries the plain one. {@code Filepath.Unchecked} reaches raw paths and is for tests alone.
 */
public class NoJavaFileCodeInThePluginTest {
    /** Looked for in the whole line, comments and all: the plugin's sources do not contain these. */
    private static final Map<String, Pattern> ANYWHERE = new LinkedHashMap<>();
    /** Type names used bare, looked for in code only: a comment may still say "the file". */
    private static final Map<String, Pattern> IN_CODE = new LinkedHashMap<>();

    static {
        ANYWHERE.put("java.io.File and its family", Pattern.compile("java\\.io\\.File"));
        ANYWHERE.put("RandomAccessFile", Pattern.compile("RandomAccessFile"));
        ANYWHERE.put("java.nio.file (only StandardCopyOption and AtomicMoveNotSupportedException are allowed,"
            + " each imported by name)",
            Pattern.compile("java\\.nio\\.file\\.(?!(?:StandardCopyOption|AtomicMoveNotSupportedException)\\b)"));
        ANYWHERE.put("FileSystems", Pattern.compile("FileSystems"));
        ANYWHERE.put("WatchService", Pattern.compile("WatchService"));
        ANYWHERE.put("DirectoryStream", Pattern.compile("DirectoryStream"));
        ANYWHERE.put("Filepath.Unchecked", Pattern.compile("Filepath\\s*\\.\\s*Unchecked"));
        ANYWHERE.put("RUNELITE_DIR", Pattern.compile("RUNELITE_DIR"));
        ANYWHERE.put(".toFile()", Pattern.compile("\\.\\s*toFile\\s*\\("));
        ANYWHERE.put(".toPath()", Pattern.compile("\\.\\s*toPath\\s*\\("));
        for (String type : new String[] {"File", "Files", "Paths", "Path", "FileReader", "FileWriter",
            "FileInputStream", "FileOutputStream"}) {
            IN_CODE.put("the type " + type, Pattern.compile("(?<![\\w.])" + type + "(?![\\w(])"));
        }
        IN_CODE.put("new File(...)", Pattern.compile("\\bnew\\s+File\\s*\\("));
        IN_CODE.put("Unchecked, however it was imported", Pattern.compile("(?<![\\w])Unchecked\\s*\\."));
    }

    private static final Pattern LINE_COMMENT = Pattern.compile("//.*$");
    private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    @Test
    public void noSourceFileThePluginShipsUsesJavasOwnFileCode() throws IOException {
        Path sources = Paths.get("src", "main", "java");
        assertTrue("the plugin's sources were not found at " + sources.toAbsolutePath()
            + ": the tests are run from the project's folder", Files.isDirectory(sources));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sources)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        assertTrue("expected the plugin's sources, found " + files.size() + " files", files.size() > 100);

        List<String> found = new ArrayList<>();
        for (Path file : files) {
            found.addAll(forbiddenIn(sources.relativize(file).toString().replace('\\', '/'),
                Files.readAllLines(file, StandardCharsets.UTF_8)));
        }

        assertTrue(found.size() + " uses of Java's own file code, each of which sends every update of the plugin"
            + " to be reviewed by hand:\n" + String.join("\n", found), found.isEmpty());
    }

    /** The check itself: what it must catch, and what it must let through. */
    @Test
    public void theCheckCatchesEveryForbiddenNameAndNothingElse() {
        List<String> caught = forbiddenIn("Example.java", List.of(
            "import java.io.File;",
            "import java.io.FileNotFoundException;",
            "import java.nio.file.*;",
            "import java.nio.file.Path;",
            "import java.nio.file.NoSuchFileException;",
            "    Path dir = RuneLite.RUNELITE_DIR.toPath();",
            "    Files.createDirectories(dir);",
            "    WatchService service = FileSystems.getDefault().newWatchService();",
            "    try (DirectoryStream<Path> stream = null) { }",
            "    Filepath.Unchecked.getPath(file).toFile();",
            "    new RandomAccessFile(name, \"r\");",
            "    java.nio.file.Files.exists(null);"));
        assertEquals(String.join("\n", caught), 12, caught.stream().map(line -> line.split(":")[1]).distinct().count());

        assertEquals(new ArrayList<String>(), forbiddenIn("Example.java", List.of(
            "import java.io.IOException;",
            "import java.io.StringReader;",
            "import java.nio.file.AtomicMoveNotSupportedException;",
            "import java.nio.file.StandardCopyOption;",
            "import net.runelite.client.util.Filepath;",
            "    Filepath file = folder.joinSegment(\"profiles\");",
            "    // A file that could not be read is never written over. File by file, Path by path.",
            "    /* The Files and Paths of old. */ int kept = 1;",
            "    /**",
            "     * {@link ProfileData} is read from the File the player left.",
            "     */",
            "    log.warn(\"File could not be moved: Files.move failed for Path {}\", file);",
            "    temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);",
            "    } catch (AtomicMoveNotSupportedException ex) {",
            "    String encodedPath = request.url().encodedPath();",
            "    profileFile.getFileName();")));
    }

    /** Each forbidden use in one source file, as {@code file:line: what, and the line}. */
    static List<String> forbiddenIn(String name, List<String> lines) {
        List<String> found = new ArrayList<>();
        boolean inBlockComment = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (Map.Entry<String, Pattern> rule : ANYWHERE.entrySet()) {
                if (rule.getValue().matcher(line).find()) {
                    found.add(name + ":" + (index + 1) + ": " + rule.getKey() + "  |  " + line.trim());
                }
            }
            // What is left of the line once its strings and comments are taken out.
            StringBuilder code = new StringBuilder();
            String rest = STRING.matcher(line).replaceAll("\"\"");
            while (!rest.isEmpty()) {
                if (inBlockComment) {
                    int end = rest.indexOf("*/");
                    if (end < 0) {
                        rest = "";
                    } else {
                        rest = rest.substring(end + 2);
                        inBlockComment = false;
                    }
                } else {
                    int start = rest.indexOf("/*");
                    Matcher lineComment = LINE_COMMENT.matcher(rest);
                    int slashes = lineComment.find() ? lineComment.start() : -1;
                    if (start >= 0 && (slashes < 0 || start < slashes)) {
                        code.append(rest, 0, start).append(' ');
                        rest = rest.substring(start + 2);
                        inBlockComment = true;
                    } else {
                        code.append(slashes >= 0 ? rest.substring(0, slashes) : rest);
                        rest = "";
                    }
                }
            }
            for (Map.Entry<String, Pattern> rule : IN_CODE.entrySet()) {
                if (rule.getValue().matcher(code).find()) {
                    found.add(name + ":" + (index + 1) + ": " + rule.getKey() + "  |  " + line.trim());
                }
            }
        }
        return found;
    }
}
