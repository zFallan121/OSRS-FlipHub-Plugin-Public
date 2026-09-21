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
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class LinkAttempt {
    private static final String PLUGIN_VERSION = "1.0.0";

    private static final long RETRY_DELAY_SECONDS = 5L;
    private static final long POST_LINK_BACKFILL_DELAY_SECONDS = 5L;
    private static final long POST_LINK_SYNC_DELAY_SECONDS = 6L;

    private final Client client;
    private final PluginConfig config;
    private final ApiClient apiClient;
    private final SummaryUploader summaryUploader;
    private final UploadEventDispatch uploadEventDispatch;
    private final UploadBackfillDispatch uploadBackfillDispatch;
    private final LinkStatus linkStatus;
    private final LinkSessionConfigStore sessionConfigStore;
    private final ProfileWorkflow profileWorkflow;
    /** Config writes fan out into several link triggers; only the first should reach the network. */
    private final AtomicBoolean linkInFlight = new AtomicBoolean();

    private ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId) throws IOException {
        return apiClient.linkDevice(licenseKey, deviceId, PLUGIN_VERSION);
    }

    private void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
        ScheduledExecutorService scheduler = Access.plugin().scheduler;
        if (scheduler != null) {
            uploadBackfillDispatch.requestBackfillAttempt(scheduler, delaySeconds, resetBackoff);
        }
    }

    private void scheduleAccountwideSync(long delaySeconds) {
        ScheduledExecutorService scheduler = Access.plugin().scheduler;
        if (scheduler != null) {
            scheduler.schedule(uploadBackfillDispatch::requestAccountwideSync, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Whether FlipHub itself said no to this key, as opposed to nobody answering. Only the
     * first is a reason to forget the key.
     */

    private void logTimeout() {
        if (log.isDebugEnabled()) {
            log.debug("FlipHub link timed out");
        }
    }

    private void scheduleRetry(Runnable task, long delaySeconds) {
        ScheduledExecutorService scheduler = Access.plugin().scheduler;
        if (scheduler != null && task != null) {
            scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Linking from the account panel. Consent was already given there, so this turns the sync
     * opt-in on itself rather than leaving the user to find the settings checkbox.
     */
    void linkFromPanel(String licenseKey) {
        String normalized = normalize(licenseKey);
        if (Str.isBlank(normalized)) {
            linkStatus.markPanelMessage("Paste your license key first.");
            return;
        }
        sessionConfigStore.enableSync(normalized);
        if (!Access.loggedIn(client)) {
            linkStatus.markFailed(LinkStatus.NEEDS_LOGIN);
            return;
        }
        linkStatus.markLinking();
        if (!linkInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!Access.plugin().executeIo(() -> runLinkAttempt(normalized))) {
            // Nothing took the work, so runLinkAttempt - the only thing that lowers this flag -
            // will never run. Left raised, every later attempt dies at the check above and the
            // account panel sits on "Linking..." for the rest of the session. This happens for
            // real when the plugin is enabled while already logged in, because the pools are
            // assigned after the link is first triggered.
            linkInFlight.set(false);
            linkStatus.markFailed(LinkStatus.NEEDS_LOGIN);
        }
    }

    /**
     * Clears the link outright rather than setting a config flag and waiting for the change event
     * to come back round: an action the user just clicked should not depend on event delivery.
     */
    void performUnlink() {
        sessionConfigStore.clearLinkState();
        sessionConfigStore.disableSync();
        sessionConfigStore.flush();
        summaryUploader.resetUploadSnapshot();
        uploadEventDispatch.markBlocked("Unlinked. Event uploads paused until relinked.");
        linkStatus.refresh();
        profileWorkflow.updateProfileHeader();
    }

    void unlinkFromPanel() {
        performUnlink();
    }

    void attemptLink(String licenseKey) {
        if (syncIsOff()) {
            if (log.isDebugEnabled()) {
                log.debug("FlipHub link skipped: FlipHub sync is disabled in the plugin settings");
            }
            profileWorkflow.updateProfileHeader();
            return;
        }
        String normalized = normalize(licenseKey);
        if (Str.isBlank(normalized)) {
            return;
        }
        if (!Access.loggedIn(client)) {
            // The login handler retries the stored key, so this is a wait rather than a failure.
            linkStatus.markFailed(LinkStatus.NEEDS_LOGIN);
            profileWorkflow.updateProfileHeader();
            return;
        }

        linkStatus.markLinking();
        if (!linkInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!Access.plugin().executeIo(() -> runLinkAttempt(normalized))) {
            // Nothing took the work, so runLinkAttempt - the only thing that lowers this flag -
            // will never run. Left raised, every later attempt dies at the check above and the
            // account panel sits on "Linking..." for the rest of the session. This happens for
            // real when the plugin is enabled while already logged in, because the pools are
            // assigned after the link is first triggered.
            linkInFlight.set(false);
            linkStatus.markFailed(LinkStatus.NEEDS_LOGIN);
        }
    }

    private void runLinkAttempt(String licenseKey) {
        try {
            String deviceId = config.deviceId();
            ApiClient.LinkResponse response = linkDevice(licenseKey, deviceId);
            if (response != null && (!Str.isBlank(response.session_token)) && (!Str.isBlank(response.signing_secret))) {
                sessionConfigStore.persistLinkedSession(response.session_token, response.signing_secret);
                summaryUploader.resetUploadSnapshot();
                uploadEventDispatch.resetStatus();
                uploadEventDispatch.updateUploadDiagnosticsUi();
                requestBackfillAttempt(POST_LINK_BACKFILL_DELAY_SECONDS, true);
                scheduleAccountwideSync(POST_LINK_SYNC_DELAY_SECONDS);
                Access.plugin().refreshPanelData();
                linkStatus.markLinked(licenseKey);
            } else {
                // The call went through and FlipHub declined it, so the key itself is the problem.
                // Keeping it would re-send the same rejected key on every start and every login.
                sessionConfigStore.clearRejectedLicenseKey();
                linkStatus.markFailed(LinkStatus.REJECTED);
            }
            profileWorkflow.updateProfileHeader();
        } catch (IOException | RuntimeException ex) {
            if (isTimeoutException(ex)) {
                logTimeout();
                linkStatus.markFailed(LinkStatus.UNREACHABLE);
                profileWorkflow.updateProfileHeader();
                linkInFlight.set(false);
                scheduleRetry(licenseKey);
                return;
            }
            if (!ApiRefusedException.refusedTheRequest(ex)) {
                // Nobody said the key was wrong. The machine is offline, the name did not
                // resolve, the handshake failed, the server had a bad minute, or sync is
                // switched off. Erasing the key here made the player go and find it again for
                // a problem that had nothing to do with it.
                linkStatus.markFailed(syncIsOff() ? LinkStatus.SYNC_OFF : LinkStatus.UNREACHABLE);
                profileWorkflow.updateProfileHeader();
                log.warn("FlipHub link failed", ex);
                linkInFlight.set(false);
                scheduleRetry(licenseKey);
                return;
            }
            // FlipHub answered and refused the key itself. Keeping it would re-send the same
            // rejected key on every start and every login.
            sessionConfigStore.clearRejectedLicenseKey();
            linkStatus.markFailed(LinkStatus.FAILED);
            profileWorkflow.updateProfileHeader();
            log.warn("FlipHub link failed", ex);
        } finally {
            linkInFlight.set(false);
        }
    }

    private void scheduleRetry(String licenseKey) {
        if (Str.isBlank(licenseKey)) {
            return;
        }
        scheduleRetry(() -> attemptLink(licenseKey), RETRY_DELAY_SECONDS);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }


    /** The player has turned sync off, so there is nothing to link and nothing to send. */
    private boolean syncIsOff() {
        return !config.enableFlipHubSync();
    }

}
