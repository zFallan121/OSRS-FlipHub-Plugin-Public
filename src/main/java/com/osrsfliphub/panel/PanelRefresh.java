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

import java.awt.EventQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;

@Singleton
final class PanelRefresh {
    private static final long REFRESH_DEBOUNCE_MILLIS = 75L;

    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    /** A refresh that arrived while one was already running, and must not be thrown away. */
    private final AtomicBoolean refreshMissed = new AtomicBoolean(false);
    /**
     * Whether a refresh has actually reached the panel since it was last shown.
     *
     * <p>The refresh is triggered once, on the tick where the panel becomes visible, and that
     * one attempt is dropped if the client is not ready yet, which at login it usually is not.
     * Nothing tried again, so the panel sat empty until some unrelated event happened to
     * rebuild it: a batch of buy limits, an item name, an offer changing. That was tens of
     * seconds of an empty Activity list on a perfectly working client.
     */
    private volatile boolean renderedSincePanelShown;
    /**
     * The same, for the Profile tab.
     *
     * <p>Its refresh is deliberately skipped while the tab is not showing, which is right, but
     * nothing asked for one when it was selected either. So the tab arrived empty and stayed
     * empty until some unrelated event fired while it happened to be the tab on screen.
     */
    private volatile boolean renderedStatsSinceShown;
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final AtomicBoolean statsRefreshInFlight = new AtomicBoolean(false);

    @Inject
    PanelRefresh() {
    }

    /**
     * Reads the readiness flag the client thread photographs each tick, because refreshes run on
     * the scheduler and {@code client.getLocalPlayer()} must not be called from there.
     */
    private boolean isClientFullyReady() {
        PluginRuntime runtime = Bridge.get(PluginRuntime.class);
        return runtime != null && runtime.isClientFullyReady();
    }

    private boolean isPanelVisible() {
        return Access.plugin().runtimeUtilityServices.isPanelVisible(Access.plugin().panel);
    }

    private boolean hasPanel() {
        return Access.plugin().panel != null;
    }

    private boolean isStatsTabSelected() {
        Panel panel = Access.plugin().panel;
        return panel != null && panel.isStatsTabSelected();
    }

    private void ensureSelectedProfileLoaded() {
        Access.plugin().getProfileWorkflowService().ensureSelectedProfileLoaded();
    }

    private void updateProfileHeader() {
        Access.plugin().getProfileWorkflowService().updateProfileHeader();
    }

    private void invokeOnClientThreadOrRun(Runnable task) {
        if (task == null) {
            return;
        }
        ClientThread clientThread = Access.plugin().clientThread;
        if (clientThread != null) {
            clientThread.invokeLater(task);
        } else {
            task.run();
        }
    }

    private void updateLocalItemsPanel() {
        Bridge.get(PanelDataRuntime.class).updateLocalItemsPanel();
    }

    private void renderLocalStats() {
        Bridge.get(PanelDataRuntime.class).renderLocalStats();
    }

    private void logWarn(String message, Throwable error) {
        if (message == null) {
            return;
        }
        if (error != null) {
            GeLifecyclePlugin.log.warn(message, error);
        } else {
            GeLifecyclePlugin.log.warn(message);
        }
    }

    void scheduleRefreshSoon(ScheduledExecutorService scheduler) {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        refreshPending.set(true);
        if (!refreshQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.schedule(() -> {
                try {
                    do {
                        refreshPending.set(false);
                        refreshPanelData(scheduler);
                    } while (refreshPending.get());
                } finally {
                    refreshQueued.set(false);
                    if (refreshPending.get()) {
                        scheduleRefreshSoon(scheduler);
                    }
                }
            }, REFRESH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            // The scheduler stopped between the check above and this call. Releasing the flag
            // matters more than this one refresh: this coordinator is a singleton that survives
            // a plugin toggle, and a flag left raised silently disables every later refresh for
            // the rest of the client session.
            refreshQueued.set(false);
        }
    }

    /** Forgets any in-flight bookkeeping, so a re-enabled plugin starts from a clean slate. */
    void resetForStartUp() {
        refreshQueued.set(false);
        refreshPending.set(false);
        refreshInFlight.set(false);
        refreshMissed.set(false);
        renderedSincePanelShown = false;
        renderedStatsSinceShown = false;
        statsRefreshInFlight.set(false);
    }

    /** Whether the Profile tab is on screen and still waiting to be filled in. */
    boolean needsStatsRefresh() {
        boolean showing = hasPanel() && isStatsTabSelected();
        if (!showing) {
            // Not on screen. When it next appears it has to be filled in again.
            renderedStatsSinceShown = false;
            return false;
        }
        return statsNeedRefresh(true, renderedStatsSinceShown);
    }

    /** The rule on its own: ask while it is showing and nothing has reached it yet. */
    static boolean statsNeedRefresh(boolean statsTabShowing, boolean renderedSinceShown) {
        return statsTabShowing && !renderedSinceShown;
    }

    /** Whether the panel has been filled in since it was last shown. */
    boolean hasRenderedSincePanelShown() {
        return renderedSincePanelShown;
    }

    /** The panel went away; the next time it appears it needs filling again. */
    void notePanelHidden() {
        renderedSincePanelShown = false;
    }

    void triggerPanelRefresh(ScheduledExecutorService scheduler) {
        submit(scheduler, () -> refreshPanelData(scheduler));
    }

    void triggerStatsRefresh(ScheduledExecutorService scheduler) {
        if (hasPanel() && !isStatsTabSelected()) {
            return;
        }
        submit(scheduler, () -> refreshStatsData(scheduler));
    }

    void refreshPanelData(ScheduledExecutorService scheduler) {
        if (EventQueue.isDispatchThread()) {
            submit(scheduler, () -> refreshPanelData(scheduler));
            return;
        }
        if (!isClientFullyReady()) {
            return;
        }
        if (!isPanelVisible()) {
            return;
        }
        if (refreshInFlight.getAndSet(true)) {
            // Remember it rather than dropping it. Linking asks for a refresh the moment the
            // server answers, and if that landed on top of a running one it was lost, leaving
            // the panel showing the unlinked view for a link that had actually succeeded.
            refreshMissed.set(true);
            return;
        }

        try {
            if (!hasPanel()) {
                return;
            }
            ensureSelectedProfileLoaded();
            updateProfileHeader();
            invokeOnClientThreadOrRun(this::updateLocalItemsPanel);
            renderedSincePanelShown = true;
        } catch (RuntimeException ex) {
            logWarn("FlipHub local item refresh failed", ex);
        } finally {
            refreshInFlight.set(false);
            if (refreshMissed.getAndSet(false)) {
                submit(scheduler, () -> refreshPanelData(scheduler));
            }
        }
    }

    void refreshStatsData(ScheduledExecutorService scheduler) {
        if (EventQueue.isDispatchThread()) {
            submit(scheduler, () -> refreshStatsData(scheduler));
            return;
        }
        // Deliberately not gated on the client being ready: these figures come from the stored
        // trades, not from the game. Waiting for a login left the Profile tab showing the
        // previous range's numbers while the control said something else.
        if (!isPanelVisible() || !hasPanel() || !isStatsTabSelected()) {
            return;
        }
        if (statsRefreshInFlight.getAndSet(true)) {
            return;
        }

        try {
            renderLocalStats();
            renderedStatsSinceShown = true;
        } catch (RuntimeException ex) {
            // Without this the exception lands in a future nobody reads: no log line, and the
            // Profile tab quietly keeps showing stale numbers.
            logWarn("FlipHub local stats refresh failed", ex);
        } finally {
            statsRefreshInFlight.set(false);
        }
    }

    private void submit(ScheduledExecutorService scheduler, Runnable task) {
        if (task == null) {
            return;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(task);
            return;
        }
        Access.plugin().executeAsync(task);
    }
}
