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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.Skill;

@Singleton
final class GameStateChangedHandler {
    private final PluginConfig config;

    @Inject
    GameStateChangedHandler(PluginConfig config) {
        this.config = config;
    }

    private static OfferStampStateServices offerStampState() {
        return Access.plugin().getOfferStampStateServices();
    }

    private static ProfileWorkflow profileWorkflow() {
        return Access.plugin().getProfileWorkflowService();
    }

    private static ProfileSelectionPresentation profileSelectionFacade() {
        return Bridge.get(ProfileSelectionPresentation.class);
    }

    /**
     * Resolve the conversion table's item names to ids once the item database
     * is up.
     *
     * <p>On the scheduler rather than the client thread: this is a few hundred
     * name lookups and none of them touch client state, since
     * {@code ItemManager} search runs against the loaded item index. Until it
     * completes the index is empty, and an empty index makes both trade ledgers
     * behave exactly as they did before conversions existed. Re-running is cheap
     * and self-healing, so a login that arrives before the item database has
     * loaded is fixed by the next one.
     */
    private static void resolveConversionRecipes(GeLifecyclePlugin plugin) {
        RecipeIndex index = Bridge.get(RecipeIndex.class);
        if (index == null || plugin == null) {
            return;
        }
        plugin.executeOnScheduler(plugin.scheduler, () -> {
            if (!index.resolve()) {
                return;
            }
            // Aggregates built while the table was empty dropped every converted
            // sale. Throw them away so the next read re-derives them from the
            // stored deltas, now that the conversions are known.
            LocalStatsCacheService statsCacheService = Bridge.get(LocalStatsCacheService.class);
            if (statsCacheService != null) {
                statsCacheService.invalidateAll();
            }
            PanelRefresh coordinator = Bridge.get(PanelRefresh.class);
            if (coordinator != null) {
                coordinator.triggerStatsRefresh(plugin.scheduler);
            }
        });
    }

    /**
     * A logged-out client knows nobody's Smithing level. The aggregates priced
     * with one go with it, so the rows the cache feeds and the header the flip
     * history feeds keep pricing repairs the same way.
     */
    private static void forgetSmithingLevels() {
        FeeService feeService = Bridge.get(FeeService.class);
        if (feeService == null || !feeService.clearSmithingLevels()) {
            return;
        }
        LocalStatsCacheService statsCacheService = Bridge.get(LocalStatsCacheService.class);
        if (statsCacheService != null) {
            statsCacheService.invalidateAll();
        }
    }

    /**
     * The Smithing level usually arrives by StatChanged with the account hash
     * beside it, but at login the stat packets can land before the client can
     * say whose they are. By LOGGED_IN it can, so attach what was held back;
     * the stats refresh this handler goes on to trigger picks it up.
     */
    private static void adoptPendingSmithingLevel(GeLifecyclePlugin plugin) {
        FeeService feeService = Bridge.get(FeeService.class);
        if (feeService == null || plugin == null || plugin.client == null) {
            return;
        }
        if (!feeService.adoptPendingSmithingLevel(plugin.client.getAccountHash())) {
            return;
        }
        LocalStatsCacheService statsCacheService = Bridge.get(LocalStatsCacheService.class);
        if (statsCacheService != null) {
            statsCacheService.invalidateAll();
        }
    }

    /**
     * Do the login work for a player who was already in the game when the plugin was
     * switched on.
     *
     * <p>RuneLite delivers events only to plugins that are running, and it does not replay
     * the login for one enabled afterwards. Everything the plugin sets up at login was
     * therefore skipped: the conversion table was never resolved, so every assemble, break
     * and repair went unrecognised; the session clock never started, so the Session range
     * showed nothing; the Grand Exchange slots were never photographed, so an offer already
     * running could be read as a brand new one; and nobody had asked the client for the
     * Smithing level, so repairs were priced at the full NPC rate. All of it lasted until the
     * player happened to log out and back in.
     */
    void catchUpWithAnAlreadyRunningGame() {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null || plugin.client == null || plugin.client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        readSmithingLevelFromClient(plugin);
        handle(GameState.LOGGED_IN);
    }

    /**
     * At a real login the level arrives on its own, in a stat report. Nothing sends those
     * again for a plugin that was not there to hear them, so ask the client outright.
     */
    private static void readSmithingLevelFromClient(GeLifecyclePlugin plugin) {
        FeeService feeService = Bridge.get(FeeService.class);
        if (feeService == null) {
            return;
        }
        int level = plugin.client.getRealSkillLevel(Skill.SMITHING);
        if (!feeService.onSmithingLevel(plugin.client.getAccountHash(), level)) {
            return;
        }
        // Every repair of this account was priced without a level.
        LocalStatsCacheService statsCacheService = Bridge.get(LocalStatsCacheService.class);
        if (statsCacheService != null) {
            statsCacheService.invalidateAll();
        }
    }

    void handle(GameState gameState) {
        if (gameState == null) {
            return;
        }
        GeLifecyclePlugin plugin = Access.plugin();

        if (gameState != GameState.LOGGED_IN) {
            // Only a real logout ends the session. LOADING and HOPPING also land here, and
            // resetting on those would restart the clock every world hop.
            if (gameState == GameState.LOGIN_SCREEN) {
                plugin.sessionStartMs = 0L;
                // The stats window the Session range reads has to end with the clock beside
                // it, or the next login would keep reporting against the old session.
                TradeSession endingSession =
                    Bridge.get(TradeSession.class);
                if (endingSession != null) {
                    endingSession.clearLocalAccountSessionStarts();
                }
                forgetSmithingLevels();
            }
            offerStampState().persistOfferUpdateTimes();
            offerStampState().resetOfferUpdateStampsOnLogout();
            // The live map, the one the offer handler diffs against. Clearing a copy left the
            // last offers in place, so the client's EMPTY reports at logout diffed against a
            // real offer and could emit a completion for a trade that never happened.
            PluginState offerState = Bridge.get(PluginState.class);
            if (offerState != null) {
                offerState.getSnapshots().clear();
            }
            Bridge.get(AutoSyncState.class).disarm();
            RecentTradeDeduper deduper = Bridge.get(RecentTradeDeduper.class);
            if (deduper != null) {
                deduper.clearAll();
            }
            if (plugin.panel != null) {
                profileWorkflow().updateProfileOptionsUI();
                profileWorkflow().updateProfileHeader();
            }
            return;
        }

        // Started once per session, not on every LOGGED_IN: that fires again after each world hop.
        if (plugin.sessionStartMs <= 0L) {
            plugin.sessionStartMs = System.currentTimeMillis();
        }
        Bridge.get(AutoSyncState.class).arm();
        resolveConversionRecipes(plugin);
        adoptPendingSmithingLevel(plugin);
        offerStampState().setLastLoginNow();
        offerStampState().loadOfferUpdateTimesForCurrentAccount();
        TradeSession tradeSession = Bridge.get(TradeSession.class);
        if (tradeSession != null) {
            tradeSession.updateLocalAccountSessionStart();
        }
        profileWorkflow().updateProfileForLogin();
        profileWorkflow().primeOfferSnapshots();

        ProfileSelectionPresentation selectionFacade = profileSelectionFacade();
        if (selectionFacade == null || !selectionFacade.hasSessionToken()) {
            plugin.localTradesLoadedThisLogin = false;
            PluginState loadState = Bridge.get(PluginState.class);
            if (loadState != null) {
                loadState.getLocalTradesLoadState().setLastAttemptMs(0L);
            }
            plugin.getLocalTradesRuntimeService().scheduleLocalTradesLoad();
            WikiPrice wikiPrices = Bridge.get(WikiPrice.class);
            if (wikiPrices != null) {
                wikiPrices.refreshPrices();
            }
        }

        LinkStatus linkStatusService = Bridge.get(LinkStatus.class);
        if (linkStatusService != null) {
            linkStatusService.refresh();
        }
        LinkAttempt linkAttemptService = Bridge.get(LinkAttempt.class);
        if (linkAttemptService != null && config != null) {
            linkAttemptService.attemptLink(config.licenseKey());
        }

        boolean visible = plugin.runtimeUtilityServices.isPanelVisible(plugin.panel);
        plugin.panelVisible = visible;
        if (visible) {
            PanelRefresh coordinator = plugin.getPanelRefreshCoordinator();
            if (coordinator != null) {
                coordinator.triggerPanelRefresh(plugin.scheduler);
                coordinator.triggerStatsRefresh(plugin.scheduler);
            }
        }

        if (plugin.scheduler != null && selectionFacade != null && selectionFacade.isLinked()) {
            UploadBackfillDispatch dispatch = Bridge.get(UploadBackfillDispatch.class);
            if (dispatch != null) {
                dispatch.requestBackfillAttempt(plugin.scheduler, 8, true);
            }
        }
    }
}
