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

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;

final class LocalProfileWipePluginHooks implements LocalProfileWipeService.Hooks {
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<GeHistoryWidgetReadService> geHistoryWidgetReadServiceSupplier;
    private final int geHistoryGroupId;
    private final int geHistoryContainerChildId;
    private final Supplier<GeHistoryCursorService> geHistoryCursorServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Map<Long, String> profileDisplayNames;
    private final Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier;
    private final Supplier<ProfileWipeDataService> profileWipeDataServiceSupplier;
    private final BiConsumer<Long, Boolean> loadLocalTradesForAccount;
    private final Runnable refreshUiAfterWipe;
    private final Runnable markAccountwideUploadDirty;
    private final Consumer<String> pushGameMessage;
    private final Consumer<String> showError;

    LocalProfileWipePluginHooks(
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<GeHistoryWidgetReadService> geHistoryWidgetReadServiceSupplier,
        int geHistoryGroupId,
        int geHistoryContainerChildId,
        Supplier<GeHistoryCursorService> geHistoryCursorServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Map<Long, String> profileDisplayNames,
        Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier,
        Supplier<ProfileWipeDataService> profileWipeDataServiceSupplier,
        BiConsumer<Long, Boolean> loadLocalTradesForAccount,
        Runnable refreshUiAfterWipe,
        Runnable markAccountwideUploadDirty,
        Consumer<String> pushGameMessage,
        Consumer<String> showError
    ) {
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.geHistoryWidgetReadServiceSupplier = geHistoryWidgetReadServiceSupplier;
        this.geHistoryGroupId = geHistoryGroupId;
        this.geHistoryContainerChildId = geHistoryContainerChildId;
        this.geHistoryCursorServiceSupplier = geHistoryCursorServiceSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.profileDisplayNames = profileDisplayNames;
        this.geHistoryWipeStateStoreSupplier = geHistoryWipeStateStoreSupplier;
        this.profileWipeDataServiceSupplier = profileWipeDataServiceSupplier;
        this.loadLocalTradesForAccount = loadLocalTradesForAccount;
        this.refreshUiAfterWipe = refreshUiAfterWipe;
        this.markAccountwideUploadDirty = markAccountwideUploadDirty;
        this.pushGameMessage = pushGameMessage;
        this.showError = showError;
    }

    @Override
    public long resolveLocalAccountKey() {
        LocalAccountSessionService service = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return service != null ? service.resolveLocalAccountKey() : -1L;
    }

    @Override
    public List<GeHistoryTrade> tryParseCurrentGeHistoryTrades() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        Widget historyContainer = client.getWidget(geHistoryGroupId, geHistoryContainerChildId);
        if (historyContainer == null || historyContainer.isHidden()) {
            return null;
        }
        GeHistoryWidgetReadService service = geHistoryWidgetReadServiceSupplier != null
            ? geHistoryWidgetReadServiceSupplier.get()
            : null;
        return service != null ? service.tryParseReadyTrades(historyContainer.getDynamicChildren()) : null;
    }

    @Override
    public List<String> buildGeHistoryCursorSignatures(List<GeHistoryTrade> trades) {
        GeHistoryCursorService service = geHistoryCursorServiceSupplier != null
            ? geHistoryCursorServiceSupplier.get()
            : null;
        return service != null ? service.buildCursorSignatures(trades) : null;
    }

    @Override
    public Map<Long, String> loadProfilesFromDisk() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.loadProfilesFromDisk() : null;
    }

    @Override
    public String resolveProfileDisplayName(long accountKey) {
        return profileDisplayNames != null ? profileDisplayNames.get(accountKey) : null;
    }

    @Override
    public void setProfileDisplayName(long accountKey, String displayName) {
        if (profileDisplayNames == null || accountKey <= 0 || displayName == null) {
            return;
        }
        String trimmed = displayName.trim();
        if (!trimmed.isEmpty()) {
            profileDisplayNames.put(accountKey, trimmed);
        }
    }

    @Override
    public void setWipeBarrierArmed(long accountKey, boolean armed) {
        GeHistoryWipeStateStore store = geHistoryWipeStateStoreSupplier != null
            ? geHistoryWipeStateStoreSupplier.get()
            : null;
        if (store != null) {
            store.setWipeBarrierArmed(accountKey, armed);
        }
    }

    @Override
    public void persistGeHistoryCursor(long accountKey, List<String> cursor) {
        GeHistoryWipeStateStore store = geHistoryWipeStateStoreSupplier != null
            ? geHistoryWipeStateStoreSupplier.get()
            : null;
        if (store != null) {
            store.persistCursor(accountKey, cursor);
        }
    }

    @Override
    public void clearProfileData(long accountKey, String displayName, boolean clearLegacyTradeCache) {
        ProfileWipeDataService service = profileWipeDataServiceSupplier != null
            ? profileWipeDataServiceSupplier.get()
            : null;
        if (service != null) {
            service.clearProfileDataForWipe(accountKey, displayName, clearLegacyTradeCache);
        }
    }

    @Override
    public void clearAccountwideData() {
        ProfileWipeDataService service = profileWipeDataServiceSupplier != null
            ? profileWipeDataServiceSupplier.get()
            : null;
        if (service != null) {
            service.clearAccountwideDataForWipe();
        }
    }

    @Override
    public void clearAllLegacyLocalTrades() {
        ProfileWipeDataService service = profileWipeDataServiceSupplier != null
            ? profileWipeDataServiceSupplier.get()
            : null;
        if (service != null) {
            service.clearAllLegacyLocalTrades();
        }
    }

    @Override
    public void loadLocalTradesForAccount(long accountKey, boolean forceReload) {
        if (loadLocalTradesForAccount != null) {
            loadLocalTradesForAccount.accept(accountKey, forceReload);
        }
    }

    @Override
    public void refreshUiAfterWipe() {
        if (refreshUiAfterWipe != null) {
            refreshUiAfterWipe.run();
        }
    }

    @Override
    public void markAccountwideUploadDirty() {
        if (markAccountwideUploadDirty != null) {
            markAccountwideUploadDirty.run();
        }
    }

    @Override
    public void pushGameMessage(String message) {
        if (pushGameMessage != null) {
            pushGameMessage.accept(message);
        }
    }

    @Override
    public void showError(String message) {
        if (showError != null) {
            showError.accept(message);
        }
    }
}
