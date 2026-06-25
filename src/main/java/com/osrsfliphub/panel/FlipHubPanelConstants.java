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

import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class FlipHubPanelConstants {
    static final String DEFAULT_BASE_URL = "https://www.osrsfliphub.com";
    static final String DISCORD_INVITE_URL = "https://discord.gg/gNakvRzXNX";
    static final Color BG = new Color(16, 19, 27);
    static final Color BG_ALT = new Color(22, 25, 34);
    static final Color PANEL = new Color(27, 31, 42);
    static final Color CARD = new Color(32, 37, 51);
    static final Color CARD_ALT = new Color(30, 34, 47);
    static final Color BORDER = new Color(56, 64, 82);
    static final Color SOFT_BORDER = new Color(40, 46, 60);
    static final Color TEXT = new Color(233, 238, 247);
    static final Color MUTED = new Color(140, 148, 167);
    static final Color ACCENT = new Color(88, 174, 255);
    static final Color ACCENT_SOFT = new Color(39, 75, 120);
    static final Color SUCCESS = new Color(34, 197, 94);
    static final Color WARNING = new Color(245, 158, 11);
    static final Color DANGER = new Color(244, 63, 94);
    static final DateTimeFormatter REFRESH_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    static final int VALUE_RIGHT_PADDING = 4;
    static final int OFFER_VALUE_RIGHT_PADDING = 4;
    static final int SCROLL_UNIT_INCREMENT = 64;
    static final int SCROLL_BLOCK_INCREMENT = 256;
    static final int CARD_ARC = 12;
    static final int INPUT_ARC = 10;
    static final int CHIP_ARC = 10;
    static final String AGE_ENTRY_KEY = FliphubConfigGroups.CONFIG_GROUP + ".ageEntry";
    static final int AGE_TOOLTIP_LEFT_GAP = 8;
    static final int AGE_TOOLTIP_OFFSET_Y = 18;
    static final int AGE_TOOLTIP_MIN_WIDTH = 150;
    static final String STATS_CARD_TOGGLE_SKIP_KEY = "fliphub.skipStatsCardToggle";

    private FlipHubPanelConstants() {
    }
}
