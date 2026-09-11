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

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Every service the plugin asks for at runtime can actually be built.
 *
 * <p>Services are fetched one at a time through a static bridge, and a service that cannot be
 * built throws at the moment it is first needed rather than at startup. The caller for the
 * in-game history sync runs on a game tick, so the failure surfaced only as the sync quietly
 * never happening: no chat message, no visible error, and every unit test still passing.
 *
 * <p>What caused it was an {@code @Inject} annotation that ended up on a final field instead of
 * on the constructor below it, after a field was inserted between the two. That compiles
 * perfectly well and Guice refuses it only at injection time.
 */
public class InjectableSingletonsTest {
    /**
     * Guice cannot set a final field, so an {@code @Inject} on one fails at runtime, never at
     * compile time. On a plugin class RuneLite fills non-final fields this way, which is fine;
     * a final one is always a mistake.
     */
    @Test
    public void noInjectAnnotationSitsOnAFinalField() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : pluginClasses()) {
            for (Field field : declaredFields(type)) {
                if (!isInjectAnnotated(field.getAnnotations())) {
                    continue;
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    offenders.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertTrue("@Inject cannot set a final field, so these fail only at runtime: " + offenders,
            offenders.isEmpty());
    }

    /**
     * Every service that takes no constructor arguments must be buildable. These are the ones
     * a plain injector can settle on its own, so any failure here is the class's own fault
     * rather than a missing binding for something else.
     */
    @Test
    public void everyDependencyFreeServiceCanBeBuilt() throws Exception {
        Injector injector = Guice.createInjector();
        List<String> failures = new ArrayList<>();
        int built = 0;
        for (Class<?> type : pluginClasses()) {
            Constructor<?> injectable = injectableNoArgConstructor(type);
            if (injectable == null) {
                continue;
            }
            try {
                injector.getInstance(type);
                built++;
            } catch (RuntimeException ex) {
                failures.add(type.getSimpleName() + ": " + firstLineOf(ex));
            }
        }
        assertTrue("these services cannot be built: " + failures, failures.isEmpty());
        assertTrue("expected to have checked some services, found " + built, built >= 5);
    }

    /** The one that actually broke, named outright so a failure says so plainly. */
    @Test
    public void theHistorySyncDecisionServiceCanBeBuilt() {
        Object service = Guice.createInjector().getInstance(GeHistoryWipeBaselineDecisionService.class);
        assertFalse(service == null);
    }

    private static Constructor<?> injectableNoArgConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0 && isInjectAnnotated(constructor.getAnnotations())) {
                return constructor;
            }
        }
        return null;
    }

    private static boolean isInjectAnnotated(java.lang.annotation.Annotation[] annotations) {
        for (java.lang.annotation.Annotation annotation : annotations) {
            String name = annotation.annotationType().getName();
            if ("javax.inject.Inject".equals(name) || "com.google.inject.Inject".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (NoClassDefFoundError error) {
            return new Field[0];
        }
    }

    private static String firstLineOf(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return error.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }

    /** Every compiled class of the plugin, read off the same output the jar is built from. */
    private static List<Class<?>> pluginClasses() throws IOException, URISyntaxException {
        Path root = Path.of(InjectableSingletonsTest.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
        // The test classes and the main classes sit in sibling directories.
        Path mainClasses = root.resolveSibling("main");
        if (!Files.isDirectory(mainClasses)) {
            fail("could not find the compiled plugin classes next to " + root);
        }
        List<Class<?>> types = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainClasses)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String name = mainClasses.relativize(file).toString()
                    .replace('\\', '.').replace('/', '.');
                name = name.substring(0, name.length() - ".class".length());
                try {
                    types.add(Class.forName(name, false, InjectableSingletonsTest.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // A class the test JVM cannot resolve tells us nothing either way.
                }
            }
        }
        assertTrue("expected to find the plugin's classes, found " + types.size(), types.size() > 100);
        Collections.sort(types, (a, b) -> a.getName().compareTo(b.getName()));
        return types;
    }
}
