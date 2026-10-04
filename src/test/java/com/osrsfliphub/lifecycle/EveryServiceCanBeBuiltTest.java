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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Asks Guice for every {@code @Singleton} the plugin has, the way the client does.
 *
 * <p>A service Guice cannot build fails at RUNTIME only: nothing at compile time notices a
 * constructor that lost its {@code @Inject}, a dependency nothing provides, or two services that
 * each want the other in their constructor. {@link InjectableSingletonsTest} builds the ones that
 * take no arguments; this builds the rest, with stand-ins for the six things the client itself
 * provides. The stand-ins are hollow -- nothing here may CALL them -- which is also the point:
 * a constructor that does real work against the client is a constructor that can fail on login.
 */
public class EveryServiceCanBeBuiltTest {
    @After
    public void forgetTheInjector() {
        Bridge.set(null);
    }

    @Test
    public void everySingletonCanBeBuiltFromTheClientsOwnServices() throws Exception {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(hollow(Client.class));
                bind(PluginConfig.class).toInstance(hollow(PluginConfig.class));
                bind(Gson.class).toInstance(new Gson());
                bind(OkHttpClient.class).toInstance(new OkHttpClient());
                bind(ClientThread.class).toInstance(unbuilt(ClientThread.class));
                bind(ConfigManager.class).toInstance(unbuilt(ConfigManager.class));
                bind(ItemManager.class).toInstance(unbuilt(ItemManager.class));
            }
        });
        // Some constructors reach for a collaborator through the bridge, as they do in the client.
        Bridge.set(injector);

        List<String> failures = new ArrayList<>();
        int built = 0;
        for (Class<?> type : singletons()) {
            try {
                injector.getInstance(type);
                built++;
            } catch (RuntimeException | LinkageError ex) {
                failures.add(type.getSimpleName() + ": " + firstLineOf(ex));
            }
        }
        assertTrue("Guice cannot build these, and the client would only find out at runtime: " + failures,
            failures.isEmpty());
        assertTrue("expected to build the plugin's services, built " + built, built >= 80);
    }

    /**
     * The same, in the two injectors the client really has.
     *
     * <p>RuneLite builds a plugin in an injector of its own, made as a child of the client's, and
     * the child alone holds the plugin and its config. Guice tries each service in the client's
     * injector first and falls back to the plugin's when something it needs is only there. A
     * service that takes another in its constructor across that fall-back can be asked for a second
     * time while it is still being worked out, and Guice then throws "Recursive load" or never
     * answers. The release of 4 October 2026 did exactly that at the first thing start-up asks
     * for, at every launch: FlipHub stayed off until it was switched off and on by hand, RuneLite
     * never tries a second time by itself, and the one injector of the test above cannot see it.
     *
     * <p>What is asked for first decides what fails, so every service takes a turn at being first,
     * each in injectors made afresh.
     */
    @Test
    public void everySingletonCanBeTheFirstAskedForInThePluginsOwnInjector() throws Exception {
        ExecutorService asker = Executors.newCachedThreadPool(work -> {
            Thread thread = new Thread(work, "first-ask");
            // One that never answers is left behind, and must not keep the test run alive.
            thread.setDaemon(true);
            return thread;
        });
        List<String> failures = new ArrayList<>();
        int built = 0;
        for (Class<?> first : singletons()) {
            Injector plugin = pluginInjector();
            Bridge.set(plugin);
            Future<?> asked = asker.submit(() -> plugin.getInstance(first));
            try {
                asked.get(10, TimeUnit.SECONDS);
                built++;
            } catch (ExecutionException ex) {
                failures.add(first.getSimpleName() + ": " + firstLineOf(ex.getCause()));
            } catch (TimeoutException ex) {
                failures.add(first.getSimpleName() + ": never answered");
                break;
            }
        }
        assertTrue("asked for first in a plugin's own injector, these cannot be built, and FlipHub would"
            + " not start: " + failures, failures.isEmpty());
        assertTrue("expected to build the plugin's services, built " + built, built >= 80);
    }

    /** RuneLite's injector with a plugin's own beneath it, as PluginManager.instantiate makes them. */
    private static Injector pluginInjector() {
        Injector client = Guice.createInjector(binder -> {
            binder.bind(Client.class).toInstance(hollow(Client.class));
            binder.bind(Gson.class).toInstance(new Gson());
            binder.bind(OkHttpClient.class).toInstance(new OkHttpClient());
            for (Class<?> type : new Class<?>[] {ClientThread.class, ConfigManager.class, ItemManager.class,
                ClientToolbar.class, OverlayManager.class, KeyManager.class}) {
                bindUnbuilt(binder, type);
            }
        });
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        return client.createChildInjector(binder -> {
            binder.bind(GeLifecyclePlugin.class).toInstance(plugin);
            // What the plugin's own provideConfig gives; RuneLite's ConfigManager is not built here to ask.
            binder.bind(PluginConfig.class).toInstance(hollow(PluginConfig.class));
            // RuneLite installs the plugin as a module too: whatever its configure() binds is bound here.
            plugin.configure(binder);
        });
    }

    private static <T> void bindUnbuilt(com.google.inject.Binder binder, Class<T> type) {
        binder.bind(type).toInstance(unbuilt(type));
    }

    /** An interface with every method answering nothing: false, zero or null. */
    @SuppressWarnings("unchecked")
    private static <T> T hollow(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> returns = method.getReturnType();
            if (returns == boolean.class) {
                return false;
            }
            if (returns == long.class) {
                return 0L;
            }
            if (returns == double.class) {
                return 0d;
            }
            if (returns == float.class) {
                return 0f;
            }
            if (returns.isPrimitive() && returns != void.class) {
                return returns == char.class ? (Object) '\0' : (returns == short.class ? (Object) (short) 0
                    : returns == byte.class ? (Object) (byte) 0 : (Object) 0);
            }
            return null;
        });
    }

    /** One of the client's own classes, allocated without running the constructor it cannot satisfy here. */
    @SuppressWarnings("unchecked")
    private static <T> T unbuilt(Class<T> type) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field handle = unsafeType.getDeclaredField("theUnsafe");
            handle.setAccessible(true);
            Object unsafe = handle.get(null);
            return (T) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not stand in for " + type.getSimpleName(), ex);
        }
    }

    private static List<Class<?>> singletons() throws Exception {
        Path root = Path.of(EveryServiceCanBeBuiltTest.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
        Path mainClasses = root.resolveSibling("main");
        assertTrue("could not find the compiled plugin classes next to " + root, Files.isDirectory(mainClasses));
        List<Class<?>> types = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainClasses)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String name = mainClasses.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                name = name.substring(0, name.length() - ".class".length());
                Class<?> type;
                try {
                    type = Class.forName(name, false, EveryServiceCanBeBuiltTest.class.getClassLoader());
                } catch (Throwable ignored) {
                    continue;
                }
                if (type.isAnnotationPresent(javax.inject.Singleton.class)
                    || type.isAnnotationPresent(com.google.inject.Singleton.class)) {
                    types.add(type);
                }
            }
        }
        types.sort((a, b) -> a.getName().compareTo(b.getName()));
        return types;
    }

    private static String firstLineOf(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return error.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
