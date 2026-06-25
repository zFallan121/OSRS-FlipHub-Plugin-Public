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
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;

final class SessionRefreshPluginHooks implements SessionRefreshFactoryService.RuntimeHooks {
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<PluginConfig> pluginConfigSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;

    SessionRefreshPluginHooks(
        Supplier<ApiClient> apiClientSupplier,
        Supplier<PluginConfig> pluginConfigSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier
    ) {
        this.apiClientSupplier = apiClientSupplier;
        this.pluginConfigSupplier = pluginConfigSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
    }

    @Override
    public ApiClient.LinkResponse refreshSession(String currentToken, String signingSecret, String deviceId)
        throws IOException {
        ApiClient apiClient = apiClientSupplier != null ? apiClientSupplier.get() : null;
        if (apiClient == null) {
            throw new IllegalStateException("Refresh failed: api client unavailable");
        }
        return apiClient.refreshSession(currentToken, signingSecret, deviceId);
    }

    @Override
    public String getSigningSecret() {
        PluginConfig pluginConfig = pluginConfigSupplier != null ? pluginConfigSupplier.get() : null;
        return pluginConfig != null ? pluginConfig.signingSecret() : null;
    }

    @Override
    public String getDeviceId() {
        PluginConfig pluginConfig = pluginConfigSupplier != null ? pluginConfigSupplier.get() : null;
        return pluginConfig != null ? pluginConfig.deviceId() : null;
    }

    @Override
    public void setConfiguration(String group, String key, String value) {
        ConfigManager configManager = configManagerSupplier != null ? configManagerSupplier.get() : null;
        if (configManager != null) {
            configManager.setConfiguration(group, key, value);
        }
    }

    @Override
    public void resetAccountwideUploadSnapshot() {
        AccountwideSummaryUploader uploader = accountwideSummaryUploaderSupplier != null
            ? accountwideSummaryUploaderSupplier.get()
            : null;
        if (uploader != null) {
            uploader.resetUploadSnapshot();
        }
    }

    @Override
    public void resetBackfillRetryState() {
        UploadBackfillDispatchService service = uploadBackfillDispatchServiceSupplier != null
            ? uploadBackfillDispatchServiceSupplier.get()
            : null;
        if (service != null) {
            service.resetBackfillRetryState();
        }
    }

    @Override
    public void setUploadBlocked(String reason) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.markBlocked(reason);
        }
    }
}
