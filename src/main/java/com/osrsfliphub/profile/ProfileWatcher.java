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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ProfileWatcher {
    private static final long SCAN_INTERVAL_MS = 2_000L;

    private final ScheduledExecutorService scheduler;
    private final long debounceMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, ScheduledFuture<?>> pendingReloads = new ConcurrentHashMap<>();
    private final Map<WatchKey, Path> watchRoots = new ConcurrentHashMap<>();
    private volatile WatchService watchService;
    private volatile Thread watchThread;
    private volatile ScheduledFuture<?> scanTask;

    ProfileWatcher(ScheduledExecutorService scheduler, long debounceMs) {
        this.scheduler = scheduler;
        this.debounceMs = debounceMs;
    }

    private Path getProfilesDir() {
        ProfileStorageFacadeService service = PluginInjectorBridge.get(ProfileStorageFacadeService.class);
        return service != null ? service.getProfilesDir() : null;
    }

    private Path getLegacyProfilesDir() {
        ProfileStorageFacadeService service = PluginInjectorBridge.get(ProfileStorageFacadeService.class);
        return service != null ? service.getLegacyProfilesDir() : null;
    }

    private long parseAccountKey(Path file) {
        if (file != null) {
            Path fileName = file.getFileName();
            if (fileName != null && "accountwide.json".equalsIgnoreCase(fileName.toString())) {
                return GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
            }
        }
        ProfileStore store = PluginInjectorBridge.get(ProfileStore.class);
        return store != null ? store.parseAccountKeyFromProfileFile(file) : -1L;
    }

    private long getProfileFileModifiedMs(Path file) {
        ProfileStore store = PluginInjectorBridge.get(ProfileStore.class);
        return store != null ? store.getProfileFileModifiedMs(file) : 0L;
    }

    private Long getLoadedProfileFileMs(long accountKey) {
        GeLifecyclePlugin plugin = PluginAccess.pluginOrNull();
        return plugin != null ? plugin.loadedProfileFileMs.get(accountKey) : null;
    }

    private void reloadProfile(long accountKey) {
        PluginAccess.plugin().getProfileWorkflowService().reloadProfileFromDisk(accountKey);
    }

    void start() {
        if (running.get()) {
            return;
        }
        Path profilesDir = getProfilesDir();
        Path legacyDir = getLegacyProfilesDir();
        if (profilesDir == null && legacyDir == null) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            int registered = 0;
            if (profilesDir != null && Files.exists(profilesDir)) {
                registerDir(profilesDir);
                registered++;
            }
            if (legacyDir != null && Files.exists(legacyDir)) {
                registerDir(legacyDir);
                registered++;
            }
            if (registered == 0) {
                closeWatchService();
                return;
            }
        } catch (IOException ignored) {
            closeWatchService();
            return;
        }
        running.set(true);
        watchThread = new Thread(this::runLoop, "fliphub-profile-watch");
        watchThread.setDaemon(true);
        watchThread.start();
        startPeriodicScan();
    }

    void stop() {
        running.set(false);
        Thread thread = watchThread;
        if (thread != null) {
            thread.interrupt();
            watchThread = null;
        }
        ScheduledFuture<?> task = scanTask;
        if (task != null) {
            task.cancel(true);
            scanTask = null;
        }
        closeWatchService();
        for (ScheduledFuture<?> future : pendingReloads.values()) {
            if (future != null) {
                future.cancel(true);
            }
        }
        pendingReloads.clear();
        watchRoots.clear();
    }

    private void runLoop() {
        while (running.get()) {
            WatchService service = watchService;
            if (service == null) {
                return;
            }
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                return;
            }
            Path root = watchRoots.get(key);
            if (root != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event == null || event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Object context = event.context();
                    if (!(context instanceof Path)) {
                        continue;
                    }
                    Path file = root.resolve((Path) context);
                    long accountKey = parseAccountKey(file);
                    if (accountKey >= 0) {
                        scheduleReload(accountKey, file, true);
                    }
                }
            }
            if (!key.reset()) {
                watchRoots.remove(key);
            }
        }
    }

    private void scheduleReload(long accountKey, Path file, boolean allowEqualTimestamp) {
        if (scheduler == null || accountKey < 0) {
            return;
        }
        long fileMs = getProfileFileModifiedMs(file);
        Long loadedMs = getLoadedProfileFileMs(accountKey);
        if (fileMs > 0 && loadedMs != null) {
            // Watch events can arrive with equal-millisecond mtimes; allow those.
            if (allowEqualTimestamp ? fileMs < loadedMs : fileMs <= loadedMs) {
                return;
            }
        }
        if (fileMs <= 0) {
            return;
        }
        ScheduledFuture<?> existing = pendingReloads.remove(accountKey);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingReloads.remove(accountKey);
            reloadProfile(accountKey);
        }, debounceMs, TimeUnit.MILLISECONDS);
        pendingReloads.put(accountKey, future);
    }

    private void registerDir(Path dir) throws IOException {
        WatchService service = watchService;
        if (service == null) {
            return;
        }
        WatchKey key = dir.register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
        watchRoots.put(key, dir);
    }

    private void startPeriodicScan() {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        scanTask = scheduler.scheduleWithFixedDelay(
            this::scanForExternalChanges,
            debounceMs,
            SCAN_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    private void scanForExternalChanges() {
        if (!running.get()) {
            return;
        }
        scanDirectory(getProfilesDir());
        scanDirectory(getLegacyProfilesDir());
    }

    private void scanDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                if (file == null) {
                    continue;
                }
                long accountKey = parseAccountKey(file);
                if (accountKey < 0) {
                    continue;
                }
                scheduleReload(accountKey, file, false);
            }
        } catch (IOException ignored) {
        }
    }

    private void closeWatchService() {
        WatchService service = watchService;
        if (service == null) {
            return;
        }
        try {
            service.close();
        } catch (IOException ignored) {
        } finally {
            watchService = null;
        }
    }
}
