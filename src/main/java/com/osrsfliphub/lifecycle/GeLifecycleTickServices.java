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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
final class GeLifecycleTickServices {
    private static final Logger log = LoggerFactory.getLogger(GeLifecycleTickServices.class);

    /** Steps already complained about, so a failure every tick is said once, not forever. */
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private final PluginState pluginState;

    @Inject
    GeLifecycleTickServices(PluginState pluginState) {
        this.pluginState = pluginState;
    }

    /**
     * Run one step of the tick, and let the rest of the tick carry on if it fails.
     *
     * <p>These steps have nothing to do with one another, but they shared a fate: the first to
     * throw took every later one with it, every tick, in silence. A service that could not be
     * built at all stopped the in-game history sync, the display-name stamp, the wiki price
     * refresh and the panel refresh together, and the only visible symptom was that the
     * history sync said nothing any more.
     */
    private void step(String name, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            if (reportedFailures.add(name)) {
                log.warn("FlipHub tick step '{}' failed and will be skipped from now on", name, ex);
            }
        }
    }

    /**
     * Whether this tick should ask for a panel refresh, given the panel is on screen now.
     *
     * @param wasVisible whether it was already on screen on the previous tick
     * @param rendered   whether a refresh has actually reached it since it was shown
     */
    static boolean shouldAskForRefresh(boolean wasVisible, boolean rendered) {
        return !wasVisible || !rendered;
    }

    boolean handlePostClientTick(boolean panelVisible) {
        step("chatbox suggestions", () -> {
            ChatboxSuggestionCycleService suggestionCycleService =
                PluginInjectorBridge.get(ChatboxSuggestionCycleService.class);
            if (suggestionCycleService != null) {
                suggestionCycleService.update();
            }
        });
        step("history sync", () -> {
            GeHistoryAutoSyncCoordinatorService autoSync =
                PluginInjectorBridge.get(GeHistoryAutoSyncCoordinatorService.class);
            if (autoSync != null) {
                autoSync.attemptAutoSync();
            }
        });
        step("display name stamp", this::maybeStampCurrentProfileDisplayName);

        GeLifecyclePlugin plugin = PluginAccess.plugin();
        boolean visible = plugin.runtimeUtilityServices.isPanelVisible(plugin.panel);
        // The wiki price refresh only runs while the panel is open, and nothing had ever told
        // it the panel was open, so its periodic fetch and its login fetch never ran at all.
        PluginRuntime runtime = PluginInjectorBridge.get(PluginRuntime.class);
        if (runtime != null) {
            runtime.setPanelVisible(visible);
            // Photograph "logged in with a local player" here, on the client thread, so the
            // upload pool and the scheduler can consult it without calling getLocalPlayer()
            // themselves off-thread.
            runtime.setClientFullyReady(
                plugin.runtimeUtilityServices.isClientFullyReady(plugin.client));
        }
        PanelRefreshCoordinator coordinator = PluginInjectorBridge.get(PanelRefreshCoordinator.class);
        if (visible) {
            // Ask on the tick the panel appears, and keep asking until one actually lands. A
            // refresh triggered before the client is ready is dropped, and at login it usually
            // is: the panel is up a moment before the world is. One ask meant an empty panel
            // until something unrelated rebuilt it. This stops the moment a refresh sticks.
            if (shouldAskForRefresh(panelVisible, coordinator == null || coordinator.hasRenderedSincePanelShown())) {
                plugin.runtimeUtilityServices.triggerPanelRefresh(coordinator, plugin.scheduler);
            }
            // The Profile tab is asked for separately, because it is skipped entirely while it
            // is not the tab on screen. Selecting it is not an event anything listened for, so
            // without this it arrived empty and waited for something unrelated to fill it.
            if (coordinator != null && coordinator.needsStatsRefresh()) {
                plugin.runtimeUtilityServices.triggerStatsRefresh(coordinator, plugin.scheduler);
            }
            return true;
        }
        if (panelVisible) {
            if (coordinator != null) {
                coordinator.notePanelHidden();
            }
            return false;
        }
        return panelVisible;
    }

    void maybeStampCurrentProfileDisplayName() {
        GeLifecyclePlugin plugin = PluginAccess.plugin();
        Client client = plugin.client;
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (client.getLocalPlayer() == null) {
            return;
        }
        String name = client.getLocalPlayer().getName();
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String trimmed = name.trim();
        LocalAccountSessionService accountSession =
            PluginInjectorBridge.get(LocalAccountSessionService.class);
        if (accountSession == null) {
            return;
        }
        long accountKey = accountSession.resolveLocalAccountKey();
        if (accountKey <= 0) {
            return;
        }
        Map<Long, String> profileDisplayNames =
            pluginState != null ? pluginState.getProfileDisplayNames() : null;
        if (profileDisplayNames == null) {
            return;
        }
        String existing = profileDisplayNames.get(accountKey);
        if (!ProfileDisplayNames.isPlaceholder(existing) && existing.trim().equalsIgnoreCase(trimmed)) {
            return;
        }

        profileDisplayNames.put(accountKey, trimmed);
        GeLifecycleLocalTradesRuntimeService localTradesRuntime =
            PluginInjectorBridge.get(GeLifecycleLocalTradesRuntimeService.class);
        if (localTradesRuntime != null) {
            localTradesRuntime.ensureProfileLoaded(accountKey);
        }
        ProfileStorageFacadeService storage =
            PluginInjectorBridge.get(ProfileStorageFacadeService.class);
        LocalTradeSessionFacadeService localTradeSessionFacade =
            PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        if (storage != null && localTradeSessionFacade != null) {
            storage.writeProfileData(accountKey, localTradeSessionFacade.snapshotLocalTradeDeltas(accountKey));
        }
        GeLifecycleProfileWorkflowService profileWorkflow =
            PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
        if (profileWorkflow != null) {
            profileWorkflow.updateProfileOptionsUI();
            profileWorkflow.updateProfileHeader();
        }
    }
}
