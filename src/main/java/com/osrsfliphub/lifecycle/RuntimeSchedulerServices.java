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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.Getter;

final class RuntimeSchedulerServices {
    private static final Logger log = LoggerFactory.getLogger(RuntimeSchedulerServices.class);

    /** Steps already complained about, so a failure every cycle is said once, not forever. */
    private static final Set<String> REPORTED_TASK_FAILURES = ConcurrentHashMap.newKeySet();

    /**
     * Wraps a repeating scheduled task so one failure cannot end it.
     *
     * <p>A ScheduledExecutorService cancels a periodic task the first time it throws, and the
     * Future is discarded here, so nothing observes the exception and nothing is logged. Every
     * one of these bodies reaches for a service through the injector, which is exactly where
     * this codebase has produced runtime-only failures before. Unguarded, a single throw ended
     * event upload, the accountwide sync, both panel refreshes or the offer preview for the
     * rest of the client session, in silence.</p>
     */
    private static Runnable guarded(String name, Runnable work) {
        return () -> {
            try {
                work.run();
            } catch (RuntimeException ex) {
                if (REPORTED_TASK_FAILURES.add(name)) {
                    log.warn("FlipHub scheduled task '{}' failed; it will keep retrying", name, ex);
                }
            }
        };
    }

    static final class RuntimeState {
        @Getter
        private final ApiClient apiClient;
        @Getter
        private final ScheduledExecutorService scheduler;
        @Getter
        private final ExecutorService ioExecutor;

        RuntimeState(ApiClient apiClient, ScheduledExecutorService scheduler, ExecutorService ioExecutor) {
            this.apiClient = apiClient;
            this.scheduler = scheduler;
            this.ioExecutor = ioExecutor;
        }
    }

    RuntimeState start(
        OkHttpClient httpClient,
        Gson gson,
        Runnable refreshPanelData,
        Runnable refreshStatsData,
        long accountwideUploadIntervalSeconds,
        long offerPollIntervalMs,
        Runnable startProfileWatcher
    ) {
        ApiClient apiClient = new ApiClient(httpClient, gson, Access.plugin().config);
        // Graceful shutdown() must not leave queued delayed tasks to fire later
        // (thread interruption is not allowed), so drop them at shutdown instead.
        ScheduledThreadPoolExecutor schedulerImpl = new ScheduledThreadPoolExecutor(1);
        schedulerImpl.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ScheduledExecutorService scheduler = schedulerImpl;
        ExecutorService ioExecutor = Executors.newFixedThreadPool(2);

        scheduler.scheduleAtFixedRate(
            guarded("event flush", () -> {
                UploadBackfillDispatch service = Bridge.get(UploadBackfillDispatch.class);
                if (service != null) {
                    service.requestEventFlush();
                }
            }),
            2,
            2,
            TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(
            guarded("accountwide sync", () -> {
                UploadBackfillDispatch service = Bridge.get(UploadBackfillDispatch.class);
                if (service != null) {
                    service.requestAccountwideSync();
                }
            }),
            60,
            accountwideUploadIntervalSeconds,
            TimeUnit.SECONDS
        );
        if (refreshPanelData != null) {
            Runnable guardedPanelRefresh = guarded("panel refresh", refreshPanelData);
            scheduler.scheduleAtFixedRate(guardedPanelRefresh, 5, 60, TimeUnit.SECONDS);
            scheduler.execute(guardedPanelRefresh);
        }
        if (refreshStatsData != null) {
            Runnable guardedStatsRefresh = guarded("stats refresh", refreshStatsData);
            scheduler.scheduleAtFixedRate(guardedStatsRefresh, 5, 60, TimeUnit.SECONDS);
            scheduler.execute(guardedStatsRefresh);
        }
        scheduler.scheduleAtFixedRate(
            guarded("offer preview poll", () -> {
                OfferPreviewRuntime runtime = Bridge.get(OfferPreviewRuntime.class);
                if (runtime == null) {
                    return;
                }
                runtime.pollAndUpdate(
                    Access.plugin().clientThread,
                    Bridge.get(OfferPreviewItemResolver.class),
                    itemId -> {
                        OfferPreviewSync sync = Bridge.get(OfferPreviewSync.class);
                        if (sync == null) {
                            return false;
                        }
                        sync.setPreviewItem(itemId);
                        return true;
                    },
                    () -> {
                        OfferPreviewSync sync = Bridge.get(OfferPreviewSync.class);
                        if (sync != null) {
                            sync.clearPreview();
                        }
                    }
                );
            }),
            1,
            offerPollIntervalMs,
            TimeUnit.MILLISECONDS
        );

        ProfileSelectionPresentation profileSelection = Bridge.get(ProfileSelectionPresentation.class);
        if (profileSelection != null && profileSelection.isLinked()) {
            UploadBackfillDispatch service = Bridge.get(UploadBackfillDispatch.class);
            if (service != null) {
                service.requestBackfillAttempt(scheduler, 12, true);
                scheduler.schedule(service::requestAccountwideSync, 10, TimeUnit.SECONDS);
            }
        }

        WikiPrice wikiPriceService = Bridge.get(WikiPrice.class);
        if (wikiPriceService != null) {
            wikiPriceService.start(scheduler);
        }
        if (startProfileWatcher != null) {
            startProfileWatcher.run();
        }

        LinkAttempt linkAttemptService = Bridge.get(LinkAttempt.class);
        PluginConfig config = Access.plugin().config;
        if (linkAttemptService != null && config != null) {
            linkAttemptService.attemptLink(config.licenseKey());
        }

        return new RuntimeState(apiClient, scheduler, ioExecutor);
    }

    void shutDown(
        ApiClient apiClient,
        ScheduledExecutorService scheduler,
        ExecutorService ioExecutor,
        Runnable stopProfileWatcher,
        Map<Integer, OfferSnapshot> snapshots,
        Runnable persistOfferUpdateTimes,
        Map<Integer, Stamp> offerUpdateStamps,
        UploadDiagnosticsState uploadState
    ) {
        ClientThread clientThread = Access.plugin().clientThread;
        if (clientThread != null) {
            clientThread.invokeLater(this::clearSuggestions);
        }

        WikiPrice wikiPriceService = Bridge.get(WikiPrice.class);
        if (wikiPriceService != null) {
            wikiPriceService.stop();
        }
        if (stopProfileWatcher != null) {
            stopProfileWatcher.run();
        }

        UploadBackfillDispatch backfillDispatch = Bridge.get(UploadBackfillDispatch.class);
        if (backfillDispatch != null) {
            backfillDispatch.resetBackfillRetryState();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }

        // RuneLite disables a plugin on the thread that draws its window, so this method runs
        // there. A final upload therefore has to be handed to the IO pool rather than run here,
        // and handed over before that pool is shut down: shutdown() lets already-queued work
        // finish, it just refuses anything new.
        UploadEventDispatch uploadDispatch = Bridge.get(UploadEventDispatch.class);
        if (uploadDispatch != null && ioExecutor != null && !ioExecutor.isShutdown()) {
            PluginConfig shutdownConfig = Access.plugin().config;
            Logger shutdownLog = GeLifecyclePlugin.log;
            try {
                ioExecutor.execute(() ->
                    uploadDispatch.flushEvents(apiClient, shutdownConfig, shutdownLog));
            } catch (RejectedExecutionException ignored) {
                // Already stopping. The queue is drained on client shutdown as well.
            }
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
        }

        if (snapshots != null) {
            snapshots.clear();
        }
        if (persistOfferUpdateTimes != null) {
            persistOfferUpdateTimes.run();
        }
        if (offerUpdateStamps != null) {
            offerUpdateStamps.clear();
        }

        RecentTradeDeduper deduper = Bridge.get(RecentTradeDeduper.class);
        if (deduper != null) {
            deduper.clearAll();
        }
        if (uploadState != null) {
            uploadState.resetForPluginStop();
        }
        if (uploadDispatch != null) {
            uploadDispatch.updateUploadDiagnosticsUi();
        }
    }

    private void clearSuggestions() {
        ChatboxSuggestionPresentation presentation =
            Bridge.get(ChatboxSuggestionPresentation.class);
        presentation.clearPriceSuggestion();
        presentation.clearLimitSuggestion();
        presentation.clearAffordableLimitSuggestion();
        Bridge.get(RemainingLimitSuggestion.class).clearCache();
        ChatboxSuggestionRuntimeState runtimeState =
            Bridge.get(ChatboxSuggestionRuntimeState.class);
        runtimeState.clearPromptWidgetCache();
        runtimeState.setSuggestionDirty(false);
    }

}
