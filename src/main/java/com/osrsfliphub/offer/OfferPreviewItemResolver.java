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

import javax.inject.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.*;

@Singleton
final class OfferPreviewItemResolver {
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static final class Resolution {
        @Getter
        private final Integer itemId;
        private final boolean clear;

        static Resolution set(int itemId) {
            return new Resolution(itemId, false);
        }

        static Resolution clear() {
            return new Resolution(null, true);
        }

        boolean shouldClear() {
            return clear;
        }
    }

    private final ItemLookup itemLookup;
    private final Client client;
    private final OfferPreviewRuntime facade;
    private final String[] setupBlockers;

    @Inject
    OfferPreviewItemResolver(Client client, OfferPreviewRuntime facade, ItemLookup itemLookup) {
        this.itemLookup = itemLookup;
        this.client = client;
        this.facade = facade;
        this.setupBlockers = Const.OFFER_SETUP_BLOCKERS != null
            ? Const.OFFER_SETUP_BLOCKERS : new String[0];
    }

    private String findItemNameCandidate(Widget geRoot) {
        return facade.findItemNameCandidate(
            geRoot,
            Const.ITEM_NAME_EXCLUDES,
            itemLookup::resolveItemIdFromName);
    }

    Resolution resolve() {
        Widget geRoot = facade.getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        boolean offerVisible = offerContainer != null && !offerContainer.isHidden();
        boolean geOpen = geRoot != null;
        boolean offerStatusOpen = geOpen && (facade.isOfferStatusOpen(geRoot, Const.OFFER_STATUS_MARKERS));
        // Some client builds can lag the setup varbit while the setup container is already visible.
        boolean setupMode = client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) > 0 || offerVisible;
        int selectedSlot = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT);

        if (geOpen && !setupMode && !offerStatusOpen && selectedSlot <= 0) {
            return Resolution.clear();
        }

        Resolution resolved = setupMode ? resolveSetupMode(offerContainer) : (offerStatusOpen ? resolveOfferStatus(geRoot) : null);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveFromVarp(selectedSlot, setupMode, offerStatusOpen);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveFromSelectedSlot();
        if (resolved != null) {
            return resolved;
        }

        if (!setupMode) {
            resolved = resolveFromText(geRoot);
            if (resolved != null) {
                return resolved;
            }
        }

        return Resolution.clear();
    }

    private Resolution resolveSetupMode(Widget offerContainer) {
        boolean offerVisible = offerContainer != null && !offerContainer.isHidden();
        if (!offerVisible) {
            return null;
        }

        // "Choose an item..." can be rendered on child widgets while container text is empty.
        if (OfferPreviewWidgetParser.widgetTreeContainsAnyText(offerContainer, setupBlockers)) {
            return Resolution.clear();
        }

        String normalized = OfferPreviewWidgetParser.normalizeText(offerContainer.getText());
        if (normalized != null) {
            String lower = normalized.toLowerCase();
            if (OfferPreviewWidgetParser.containsAny(lower, setupBlockers)) {
                return Resolution.clear();
            }
        }

        int itemId = facade.findFirstItemId(offerContainer);
        if (itemId <= 0) {
            return Resolution.clear();
        }
        return Resolution.set(itemId);
    }

    private Resolution resolveOfferStatus(Widget geRoot) {
        if (geRoot == null) {
            return null;
        }
        int itemId = facade.findFirstItemId(geRoot);
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId);
    }

    private Resolution resolveFromVarp(int selectedSlot, boolean setupMode, boolean offerStatusOpen) {
        // Guard against stale CURRENT_GE_ITEM on the main GE overview.
        if (!setupMode && !offerStatusOpen && selectedSlot <= 0) {
            return null;
        }
        int itemId = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId);
    }

    private Resolution resolveFromSelectedSlot() {
        GrandExchangeOffer offer = facade.getSelectedOffer(client, VarbitID.GE_SELECTEDSLOT);
        if (offer == null) {
            return null;
        }
        int itemId = offer.getItemId();
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId);
    }

    private Resolution resolveFromText(Widget geRoot) {
        if (geRoot == null) {
            return null;
        }
        String candidate = findItemNameCandidate(geRoot);
        if (candidate == null) {
            return null;
        }
        int itemId = itemLookup.resolveItemIdFromName(candidate);
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId);
    }
}
