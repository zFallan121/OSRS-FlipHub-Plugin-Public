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

/**
 * The handful of flags the client thread records for everyone else to read.
 *
 * <p>This used to hold a second copy of most of the plugin's late-bound state - panel, nav
 * button, overlay, both executors, the query, the page, the sorts - left over from the
 * hand-rolled injection it replaced. None of it was ever read, and its executor helpers fell
 * back to the common pool, which is exactly the behaviour the plugin removed elsewhere because
 * work outlived a disabled plugin. Only these two flags were ever used, so only these remain.
 */
@Singleton
final class PluginRuntime {
    private volatile boolean panelVisible;
    private volatile boolean clientFullyReady;

    @Inject
    PluginRuntime() {
    }

    boolean isPanelVisible() {
        return panelVisible;
    }

    void setPanelVisible(boolean panelVisible) {
        this.panelVisible = panelVisible;
    }

    /**
     * Whether the client was logged in with a local player present, as last seen on the client
     * thread.
     *
     * <p>Background threads must read this rather than calling {@code client.getLocalPlayer()}
     * themselves. That returns a mutable object the client can swap mid-frame, so reading it off
     * the client thread is the one call in this area a plugin hub reviewer will challenge. The
     * post-client-tick handler refreshes it every tick, so it is never more than one tick stale,
     * and every consumer only uses it to decide whether to skip work this pass.
     */
    boolean isClientFullyReady() {
        return clientFullyReady;
    }

    void setClientFullyReady(boolean clientFullyReady) {
        this.clientFullyReady = clientFullyReady;
    }
}
