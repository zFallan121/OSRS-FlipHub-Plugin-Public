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

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import net.runelite.client.util.Filepath;

/**
 * Notices what another RuneLite window saved, by looking at the trade files every two seconds.
 *
 * <p>It used to be told at once as well, by a thread of its own waiting on the folder. While
 * anything waits on a folder Windows will not let it be renamed, and a rename is how RuneLite
 * moves a plugin's folder.
 */
@RequiredArgsConstructor
final class ProfileWatcher {
    private static final long SCAN_INTERVAL_MS = 2_000L;

    private final ScheduledExecutorService scheduler;
    private final long debounceMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<Long, ScheduledFuture<?>> pendingReloads = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> scanTask;

    private long getProfileFileModifiedMs(Filepath file) {
        ProfileStore store = Bridge.get(ProfileStore.class);
        return store != null ? store.getProfileFileModifiedMs(file) : 0L;
    }

    /**
     * The modification time a profile was last loaded at, from the same map the loader and
     * the writer record into. Reading any other map makes every stored timestamp look absent,
     * which turns the periodic scan into an unconditional reload of every profile.
     */
    private Long getSelfWrittenProfileFileMs(long accountKey) {
        PluginState state = Bridge.get(PluginState.class);
        return state != null ? state.getSelfWrittenProfileFileMs().get(accountKey) : null;
    }

    Long getLoadedProfileFileMs(long accountKey) {
        PluginState state = Bridge.get(PluginState.class);
        return state != null ? state.getLoadedProfileFileMs().get(accountKey) : null;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        startPeriodicScan();
    }

    void stop() {
        running.set(false);
        ScheduledFuture<?> task = scanTask;
        if (task != null) {
            task.cancel(false);
            scanTask = null;
        }
        for (ScheduledFuture<?> future : pendingReloads.values()) {
            if (future != null) {
                future.cancel(false);
            }
        }
        pendingReloads.clear();
    }

    private void scheduleReload(long accountKey, Filepath file) {
        if (scheduler == null || accountKey < 0) {
            return;
        }
        long fileMs = getProfileFileModifiedMs(file);
        Long selfWrittenMs = getSelfWrittenProfileFileMs(accountKey);
        if (selfWrittenMs != null && fileMs == selfWrittenMs) {
            // This is the plugin's own write coming back. Reloading it would replace the live
            // list with a snapshot taken before any fill recorded since the write began.
            return;
        }
        Long loadedMs = getLoadedProfileFileMs(accountKey);
        if (fileMs > 0 && loadedMs != null && fileMs <= loadedMs) {
            return;
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
            Access.plugin().getProfileWorkflowService().reloadProfileFromDisk(accountKey);
        }, debounceMs, TimeUnit.MILLISECONDS);
        pendingReloads.put(accountKey, future);
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
        ProfileStorage storage = Bridge.get(ProfileStorage.class);
        Filepath dir = storage != null ? storage.getProfilesDir() : null;
        if (dir == null) {
            return;
        }
        // The pool every character's trades are merged into, then each character's own file.
        scheduleReload(Const.ACCOUNTWIDE_KEY, dir.joinSegment("accountwide.json"));
        ProfileHashFileWalker.walk(dir, this::scheduleReload);
    }
}
