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
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.config.ConfigManager;

final class OfferUpdateStampPersistencePluginHooks implements OfferUpdateStampPersistenceService.Hooks {
    private final Supplier<Gson> gsonSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;

    OfferUpdateStampPersistencePluginHooks(
        Supplier<Gson> gsonSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Client> clientSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier
    ) {
        this.gsonSupplier = gsonSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.clientSupplier = clientSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
    }

    @Override
    public Gson gson() {
        return gsonSupplier != null ? gsonSupplier.get() : null;
    }

    @Override
    public boolean hasConfigurationAccess() {
        return resolveConfigManager() != null;
    }

    @Override
    public boolean isClientLoggedIn() {
        Client client = resolveClient();
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    @Override
    public long resolveCurrentAccountKey() {
        LocalTradeSessionFacadeService localTradeSessionFacadeService = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        if (localTradeSessionFacadeService != null) {
            long accountHash = localTradeSessionFacadeService.resolveAccountHash();
            if (accountHash > 0) {
                return accountHash;
            }
        }
        LocalAccountSessionService localAccountSessionService = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return localAccountSessionService != null ? localAccountSessionService.resolveLocalAccountKey() : -1L;
    }

    @Override
    public GrandExchangeOffer[] currentOffers() {
        Client client = resolveClient();
        return client != null ? client.getGrandExchangeOffers() : null;
    }

    @Override
    public String readConfiguration(String configGroup, String key) {
        ConfigManager configManager = resolveConfigManager();
        return configManager != null ? configManager.getConfiguration(configGroup, key) : null;
    }

    @Override
    public void writeConfiguration(String configGroup, String key, String value) {
        ConfigManager configManager = resolveConfigManager();
        if (configManager != null) {
            configManager.setConfiguration(configGroup, key, value);
        }
    }

    private ConfigManager resolveConfigManager() {
        return configManagerSupplier != null ? configManagerSupplier.get() : null;
    }

    private Client resolveClient() {
        return clientSupplier != null ? clientSupplier.get() : null;
    }
}
