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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(FliphubConfigGroups.CONFIG_GROUP)
public interface PluginConfig extends Config {
    @ConfigSection(
        name = "FlipHub account (optional cloud sync)",
        description = "Optional. Link a FlipHub account to sync your flips to the FlipHub online dashboard. "
            + "While linked, your Grand Exchange offer events (item, quantity, price, time) are uploaded to "
            + "FlipHub's servers (osrsfliphub.com). Leave the License Key blank to keep all data local on your "
            + "computer — nothing is uploaded until you link.",
        position = 0
    )
    String accountSection = "accountSection";

    @ConfigItem(
        keyName = "licenseKey",
        name = "License Key",
        description = "Optional. Paste a FlipHub license key to link your account. While linked, your Grand "
            + "Exchange offer events are uploaded to FlipHub's servers (osrsfliphub.com) to power your online "
            + "dashboard. Leave blank to keep all data local — nothing is uploaded until you link.",
        section = accountSection
    )
    default String licenseKey() {
        return "";
    }

    @ConfigItem(
        keyName = "unlinkNow",
        name = "Unlink (click)",
        description = "Clear link state, stop all uploads, and use local-only stats",
        section = accountSection,
        position = 1
    )
    default boolean unlinkNow() {
        return false;
    }

    @ConfigItem(
        keyName = "linkCode",
        name = "Link Code (legacy)",
        description = "Legacy link code field",
        hidden = true
    )
    default String linkCode() {
        return "";
    }

    @ConfigItem(
        keyName = "deviceId",
        name = "Device ID",
        description = "Unique device identifier",
        hidden = true
    )
    default String deviceId() {
        return "";
    }

    @ConfigItem(
        keyName = "sessionToken",
        name = "Session Token",
        description = "Plugin session token",
        hidden = true
    )
    default String sessionToken() {
        return "";
    }

    @ConfigItem(
        keyName = "signingSecret",
        name = "Signing Secret",
        description = "HMAC signing secret",
        hidden = true
    )
    default String signingSecret() {
        return "";
    }

    @ConfigItem(
        keyName = "bookmarks",
        name = "Bookmarked Items",
        description = "Comma-separated item ids",
        hidden = true
    )
    default String bookmarks() {
        return "";
    }

    @ConfigItem(
        keyName = "hiddenItems",
        name = "Hidden Items",
        description = "Comma-separated item ids hidden from view",
        hidden = true
    )
    default String hiddenItems() {
        return "";
    }

    @ConfigItem(
        keyName = "geOfferUpdateTimes",
        name = "GE Offer Update Times",
        description = "Internal GE offer update timestamps",
        hidden = true
    )
    default String geOfferUpdateTimes() {
        return "";
    }

    @ConfigItem(
        keyName = "showGeOfferTimers",
        name = "Show GE Offer Timers",
        description = "Show how long since each GE offer last updated"
    )
    default boolean showGeOfferTimers() {
        return true;
    }
}
