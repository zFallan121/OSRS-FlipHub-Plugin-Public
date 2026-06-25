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

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class GameStateChangedPluginHooks implements GameStateChangedHandlerService.Hooks {
    private final Runnable persistOfferUpdateTimes;
    private final Runnable resetOfferUpdateStamps;
    private final Runnable clearSnapshots;
    private final Supplier<GeHistoryAutoSyncStateService> geHistoryAutoSyncStateServiceSupplier;
    private final Supplier<RecentTradeDeduper> recentTradeDeduperSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Runnable updateProfileOptionsUI;
    private final Runnable updateProfileHeader;
    private final Runnable setLastLoginNow;
    private final Runnable loadOfferUpdateTimesForCurrentAccount;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Runnable updateProfileForLogin;
    private final Runnable primeOfferSnapshots;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Runnable resetLocalTradesLoadState;
    private final Runnable scheduleLocalTradesLoad;
    private final Supplier<WikiPriceService> wikiPriceServiceSupplier;
    private final Supplier<LinkAttemptService> linkAttemptServiceSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final BooleanSupplier isPanelVisible;
    private final Consumer<Boolean> setPanelVisible;
    private final Runnable triggerPanelRefresh;
    private final Runnable triggerStatsRefresh;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final BiConsumer<Integer, Boolean> requestBackfillAttempt;

    GameStateChangedPluginHooks(
        Runnable persistOfferUpdateTimes,
        Runnable resetOfferUpdateStamps,
        Runnable clearSnapshots,
        Supplier<GeHistoryAutoSyncStateService> geHistoryAutoSyncStateServiceSupplier,
        Supplier<RecentTradeDeduper> recentTradeDeduperSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Runnable updateProfileOptionsUI,
        Runnable updateProfileHeader,
        Runnable setLastLoginNow,
        Runnable loadOfferUpdateTimesForCurrentAccount,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Runnable updateProfileForLogin,
        Runnable primeOfferSnapshots,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Runnable resetLocalTradesLoadState,
        Runnable scheduleLocalTradesLoad,
        Supplier<WikiPriceService> wikiPriceServiceSupplier,
        Supplier<LinkAttemptService> linkAttemptServiceSupplier,
        Supplier<PluginConfig> configSupplier,
        BooleanSupplier isPanelVisible,
        Consumer<Boolean> setPanelVisible,
        Runnable triggerPanelRefresh,
        Runnable triggerStatsRefresh,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        BiConsumer<Integer, Boolean> requestBackfillAttempt
    ) {
        this.persistOfferUpdateTimes = persistOfferUpdateTimes;
        this.resetOfferUpdateStamps = resetOfferUpdateStamps;
        this.clearSnapshots = clearSnapshots;
        this.geHistoryAutoSyncStateServiceSupplier = geHistoryAutoSyncStateServiceSupplier;
        this.recentTradeDeduperSupplier = recentTradeDeduperSupplier;
        this.panelSupplier = panelSupplier;
        this.updateProfileOptionsUI = updateProfileOptionsUI;
        this.updateProfileHeader = updateProfileHeader;
        this.setLastLoginNow = setLastLoginNow;
        this.loadOfferUpdateTimesForCurrentAccount = loadOfferUpdateTimesForCurrentAccount;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.updateProfileForLogin = updateProfileForLogin;
        this.primeOfferSnapshots = primeOfferSnapshots;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.resetLocalTradesLoadState = resetLocalTradesLoadState;
        this.scheduleLocalTradesLoad = scheduleLocalTradesLoad;
        this.wikiPriceServiceSupplier = wikiPriceServiceSupplier;
        this.linkAttemptServiceSupplier = linkAttemptServiceSupplier;
        this.configSupplier = configSupplier;
        this.isPanelVisible = isPanelVisible;
        this.setPanelVisible = setPanelVisible;
        this.triggerPanelRefresh = triggerPanelRefresh;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.schedulerSupplier = schedulerSupplier;
        this.requestBackfillAttempt = requestBackfillAttempt;
    }

    @Override
    public void persistOfferUpdateTimes() {
        if (persistOfferUpdateTimes != null) {
            persistOfferUpdateTimes.run();
        }
    }

    @Override
    public void resetOfferUpdateStamps() {
        if (resetOfferUpdateStamps != null) {
            resetOfferUpdateStamps.run();
        }
    }

    @Override
    public void clearSnapshots() {
        if (clearSnapshots != null) {
            clearSnapshots.run();
        }
    }

    @Override
    public void disarmGeHistoryAutoSync() {
        GeHistoryAutoSyncStateService service = resolveGeHistoryAutoSyncStateService();
        if (service != null) {
            service.disarm();
        }
    }

    @Override
    public void clearRecentTradeDeduper() {
        RecentTradeDeduper service = recentTradeDeduperSupplier != null ? recentTradeDeduperSupplier.get() : null;
        if (service != null) {
            service.clearAll();
        }
    }

    @Override
    public boolean isPanelAvailable() {
        return panelSupplier != null && panelSupplier.get() != null;
    }

    @Override
    public void updateProfileOptionsUI() {
        if (updateProfileOptionsUI != null) {
            updateProfileOptionsUI.run();
        }
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
    }

    @Override
    public void armGeHistoryAutoSync() {
        GeHistoryAutoSyncStateService service = resolveGeHistoryAutoSyncStateService();
        if (service != null) {
            service.arm();
        }
    }

    @Override
    public void setLastLoginNow() {
        if (setLastLoginNow != null) {
            setLastLoginNow.run();
        }
    }

    @Override
    public void loadOfferUpdateTimesForCurrentAccount() {
        if (loadOfferUpdateTimesForCurrentAccount != null) {
            loadOfferUpdateTimesForCurrentAccount.run();
        }
    }

    @Override
    public void updateLocalAccountSessionStart() {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.updateLocalAccountSessionStart();
        }
    }

    @Override
    public void updateProfileForLogin() {
        if (updateProfileForLogin != null) {
            updateProfileForLogin.run();
        }
    }

    @Override
    public void primeOfferSnapshots() {
        if (primeOfferSnapshots != null) {
            primeOfferSnapshots.run();
        }
    }

    @Override
    public boolean hasSessionToken() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null && service.hasSessionToken();
    }

    @Override
    public void resetLocalTradesLoadState() {
        if (resetLocalTradesLoadState != null) {
            resetLocalTradesLoadState.run();
        }
    }

    @Override
    public void scheduleLocalTradesLoad() {
        if (scheduleLocalTradesLoad != null) {
            scheduleLocalTradesLoad.run();
        }
    }

    @Override
    public void refreshWikiLatestPrices() {
        WikiPriceService service = wikiPriceServiceSupplier != null ? wikiPriceServiceSupplier.get() : null;
        if (service != null) {
            service.refreshPrices();
        }
    }

    @Override
    public String getLinkInput() {
        LinkAttemptService linkAttemptService = linkAttemptServiceSupplier != null ? linkAttemptServiceSupplier.get() : null;
        PluginConfig pluginConfig = configSupplier != null ? configSupplier.get() : null;
        if (linkAttemptService == null || pluginConfig == null) {
            return null;
        }
        return linkAttemptService.resolveLinkInput(pluginConfig.licenseKey(), pluginConfig.linkCode());
    }

    @Override
    public void attemptLink(String linkInput) {
        LinkAttemptService linkAttemptService = linkAttemptServiceSupplier != null ? linkAttemptServiceSupplier.get() : null;
        if (linkAttemptService != null) {
            linkAttemptService.attemptLink(linkInput);
        }
    }

    @Override
    public boolean isPanelVisible() {
        return isPanelVisible != null && isPanelVisible.getAsBoolean();
    }

    @Override
    public void setPanelVisible(boolean visible) {
        if (setPanelVisible != null) {
            setPanelVisible.accept(visible);
        }
    }

    @Override
    public void triggerPanelRefresh() {
        if (triggerPanelRefresh != null) {
            triggerPanelRefresh.run();
        }
    }

    @Override
    public void triggerStatsRefresh() {
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }

    @Override
    public boolean hasScheduler() {
        return schedulerSupplier != null && schedulerSupplier.get() != null;
    }

    @Override
    public boolean isLinked() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null && service.isLinked();
    }

    @Override
    public void requestBackfillAttempt(int delaySeconds, boolean forceRefresh) {
        if (requestBackfillAttempt != null) {
            requestBackfillAttempt.accept(delaySeconds, forceRefresh);
        }
    }

    private GeHistoryAutoSyncStateService resolveGeHistoryAutoSyncStateService() {
        return geHistoryAutoSyncStateServiceSupplier != null
            ? geHistoryAutoSyncStateServiceSupplier.get()
            : null;
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionFacadeService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }
}
