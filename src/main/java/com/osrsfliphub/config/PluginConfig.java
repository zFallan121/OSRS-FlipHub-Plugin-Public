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
        name = "FlipHub account",
        description = "Optional. Link an account from the Link tab in the<br>"
            + "FlipHub side panel. Leave this off and the plugin never<br>"
            + "contacts FlipHub; every flip stays on this computer.",
        position = 0
    )
    String accountSection = "accountSection";

    @ConfigItem(
        keyName = "enableFlipHubSync",
        name = "Sync flips to my FlipHub account",
        description = "Uploads your Grand Exchange offers (item, quantity, price,<br>"
            + "time) to your FlipHub dashboard at osrsfliphub.com and reads<br>"
            + "your flip history back from it, so your stats follow<br>"
            + "you between devices. While this is off the plugin never<br>"
            + "connects to FlipHub's servers and every flip stays on this<br>"
            + "computer. Linking is done in the side panel, not here.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by Runelite developers.",
        section = accountSection,
        position = 0
    )
    default boolean enableFlipHubSync() {
        return false;
    }

    @ConfigItem(
        keyName = "licenseKeyHint",
        name = "License Key Hint",
        description = "Last characters of the linked license key",
        hidden = true
    )
    default String licenseKeyHint() {
        return "";
    }

    @ConfigItem(
        keyName = "licenseKey",
        name = "License key",
        description = "Stores the key while linking; cleared once accepted",
        hidden = true
    )
    default String licenseKey() {
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
        keyName = "itemSort",
        name = "Activity Sort",
        description = "Sort applied to the activity list",
        hidden = true
    )
    default String itemSort() {
        return "";
    }

    @ConfigItem(
        keyName = "itemSortAscending",
        name = "Activity Sort Ascending",
        description = "Whether the activity list sorts ascending",
        hidden = true
    )
    default boolean itemSortAscending() {
        return false;
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
        keyName = "repairAtArmourStand",
        name = "I repair on an armour stand",
        description = "Prices a repaired item the way a player-owned house<br>"
            + "armour stand charges for it: half a percent less per<br>"
            + "Smithing level, so a little over half the NPC price at 99.<br>"
            + "Turn this off if you repair at an NPC and pay full price."
    )
    default boolean repairAtArmourStand() {
        return true;
    }

    @ConfigItem(
        keyName = "showGeOfferTimers",
        name = "Show GE offer timers",
        description = "Draws a timer on each Grand Exchange slot showing how<br>"
            + "long it has been since that offer last moved. Green<br>"
            + "under 5 minutes, yellow under 30, red beyond that."
    )
    default boolean showGeOfferTimers() {
        return true;
    }

    @ConfigItem(
        keyName = "enableDecimalAmounts",
        name = "Type decimal amounts",
        description = "Lets you type a decimal point into any \"enter an amount\"<br>"
            + "prompt - Grand Exchange price and quantity, bank<br>"
            + "withdraw-X, trade, coffers. The game already reads 9m;<br>"
            + "this adds the point, so 9.4m becomes 9,400,000 and<br>"
            + "1.21b becomes 1,210,000,000. Anything past a whole coin<br>"
            + "is dropped, so 2.5325k becomes 2,532."
    )
    default boolean enableDecimalAmounts() {
        return true;
    }
}
