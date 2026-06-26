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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleLinkServices {
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final String configGroup;
    private final BooleanSupplier isClientLoggedIn;
    private final Supplier<ApiClient> apiClientSupplier;
    private final String username;
    private final String pluginVersion;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Runnable refreshPanelData;
    private final Runnable updateProfileHeader;
    private final Predicate<Throwable> timeoutExceptionPredicate;
    private final Runnable logTimeout;
    private final Consumer<Throwable> logFailure;
    private final Consumer<Runnable> executeIoConsumer;

    private LinkSessionGuardService linkSessionGuardService;
    private LinkSessionConfigStore linkSessionConfigStore;
    private LinkAttemptService linkAttemptService;

    GeLifecycleLinkServices(
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        String configGroup,
        BooleanSupplier isClientLoggedIn,
        Supplier<ApiClient> apiClientSupplier,
        String username,
        String pluginVersion,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Runnable refreshPanelData,
        Runnable updateProfileHeader,
        Predicate<Throwable> timeoutExceptionPredicate,
        Runnable logTimeout,
        Consumer<Throwable> logFailure,
        Consumer<Runnable> executeIoConsumer
    ) {
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.configGroup = configGroup;
        this.isClientLoggedIn = isClientLoggedIn;
        this.apiClientSupplier = apiClientSupplier;
        this.username = username;
        this.pluginVersion = pluginVersion;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.refreshPanelData = refreshPanelData;
        this.updateProfileHeader = updateProfileHeader;
        this.timeoutExceptionPredicate = timeoutExceptionPredicate;
        this.logTimeout = logTimeout;
        this.logFailure = logFailure;
        this.executeIoConsumer = executeIoConsumer;
    }

    LinkSessionGuardService getLinkSessionGuardService() {
        return PluginInjectorBridge.get(LinkSessionGuardService.class);
    }

    LinkSessionConfigStore getLinkSessionConfigStore() {
        return PluginInjectorBridge.get(LinkSessionConfigStore.class);
    }

    LinkAttemptService getLinkAttemptService() {
        return PluginInjectorBridge.get(LinkAttemptService.class);
    }
}
