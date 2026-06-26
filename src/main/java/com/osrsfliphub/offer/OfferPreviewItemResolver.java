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
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

@Singleton
final class OfferPreviewItemResolver {
    interface Hooks {
        Widget getVisibleGeRoot();
        boolean isOfferStatusOpen(Widget geRoot);
        int getNewOfferTypeVarbit();
        int getSelectedSlotVarbit();
        Widget getOfferContainer();
        String normalizeOfferText(String text);
        int findFirstItemId(Widget widget);
        int getCurrentGeItemVarp();
        GrandExchangeOffer getSelectedOffer();
        String findItemNameCandidate(Widget geRoot);
        int resolveItemIdFromName(String name);
    }

    static final class Resolution {
        private final Integer itemId;
        private final String itemName;
        private final boolean clear;

        private Resolution(Integer itemId, String itemName, boolean clear) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.clear = clear;
        }

        static Resolution set(int itemId, String itemName) {
            return new Resolution(itemId, itemName, false);
        }

        static Resolution clear() {
            return new Resolution(null, null, true);
        }

        boolean shouldClear() {
            return clear;
        }

        Integer getItemId() {
            return itemId;
        }

        String getItemName() {
            return itemName;
        }
    }

    private final Hooks hooks;
    private final String[] setupBlockers;

    @Inject
    OfferPreviewItemResolver(Client client, OfferPreviewRuntimeFacadeService facade) {
        this(productionHooks(client, facade), GeLifecyclePluginConstants.OFFER_SETUP_BLOCKERS);
    }

    OfferPreviewItemResolver(Hooks hooks, String[] setupBlockers) {
        this.hooks = hooks;
        this.setupBlockers = setupBlockers != null ? setupBlockers : new String[0];
    }

    private static ItemLookupService itemLookupService() {
        return PluginAccess.plugin().getOfferUiRuntimeServices().getItemServices().getItemLookupService();
    }

    private static Hooks productionHooks(Client client, OfferPreviewRuntimeFacadeService facade) {
        return new Hooks() {
            @Override
            public Widget getVisibleGeRoot() {
                return facade != null
                    ? facade.getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER)
                    : null;
            }

            @Override
            public boolean isOfferStatusOpen(Widget geRoot) {
                return facade != null && facade.isOfferStatusOpen(geRoot, GeLifecyclePluginConstants.OFFER_STATUS_MARKERS);
            }

            @Override
            public int getNewOfferTypeVarbit() {
                return client != null ? client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) : 0;
            }

            @Override
            public int getSelectedSlotVarbit() {
                return client != null ? client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) : -1;
            }

            @Override
            public Widget getOfferContainer() {
                return client != null ? client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) : null;
            }

            @Override
            public String normalizeOfferText(String text) {
                return OfferPreviewWidgetParser.normalizeText(text);
            }

            @Override
            public int findFirstItemId(Widget widget) {
                return facade != null ? facade.findFirstItemId(widget) : -1;
            }

            @Override
            public int getCurrentGeItemVarp() {
                return client != null ? client.getVarpValue(VarPlayer.CURRENT_GE_ITEM) : -1;
            }

            @Override
            public GrandExchangeOffer getSelectedOffer() {
                return facade != null ? facade.getSelectedOffer(client, VarbitID.GE_SELECTEDSLOT) : null;
            }

            @Override
            public String findItemNameCandidate(Widget geRoot) {
                ItemLookupService itemLookupService = itemLookupService();
                if (facade == null || itemLookupService == null) {
                    return null;
                }
                return facade.findItemNameCandidate(
                    geRoot,
                    GeLifecyclePluginConstants.ITEM_NAME_EXCLUDES,
                    itemLookupService::resolveItemIdFromName);
            }

            @Override
            public int resolveItemIdFromName(String name) {
                ItemLookupService itemLookupService = itemLookupService();
                return itemLookupService != null ? itemLookupService.resolveItemIdFromName(name) : -1;
            }
        };
    }

    Resolution resolve() {
        if (hooks == null) {
            return Resolution.clear();
        }
        Widget geRoot = hooks.getVisibleGeRoot();
        Widget offerContainer = hooks.getOfferContainer();
        boolean offerVisible = offerContainer != null && !offerContainer.isHidden();
        boolean geOpen = geRoot != null;
        boolean offerStatusOpen = geOpen && hooks.isOfferStatusOpen(geRoot);
        // Some client builds can lag the setup varbit while the setup container is already visible.
        boolean setupMode = hooks.getNewOfferTypeVarbit() > 0 || offerVisible;
        int selectedSlot = hooks.getSelectedSlotVarbit();

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

        String normalized = hooks.normalizeOfferText(offerContainer.getText());
        if (normalized != null) {
            String lower = normalized.toLowerCase();
            if (OfferPreviewWidgetParser.containsAny(lower, setupBlockers)) {
                return Resolution.clear();
            }
        }

        int itemId = hooks.findFirstItemId(offerContainer);
        if (itemId <= 0) {
            return Resolution.clear();
        }
        return Resolution.set(itemId, null);
    }

    private Resolution resolveOfferStatus(Widget geRoot) {
        if (geRoot == null) {
            return null;
        }
        int itemId = hooks.findFirstItemId(geRoot);
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId, null);
    }

    private Resolution resolveFromVarp(int selectedSlot, boolean setupMode, boolean offerStatusOpen) {
        // Guard against stale CURRENT_GE_ITEM on the main GE overview.
        if (!setupMode && !offerStatusOpen && selectedSlot <= 0) {
            return null;
        }
        int itemId = hooks.getCurrentGeItemVarp();
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId, null);
    }

    private Resolution resolveFromSelectedSlot() {
        GrandExchangeOffer offer = hooks.getSelectedOffer();
        if (offer == null) {
            return null;
        }
        int itemId = offer.getItemId();
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId, null);
    }

    private Resolution resolveFromText(Widget geRoot) {
        if (geRoot == null) {
            return null;
        }
        String candidate = hooks.findItemNameCandidate(geRoot);
        if (candidate == null) {
            return null;
        }
        int itemId = hooks.resolveItemIdFromName(candidate);
        if (itemId <= 0) {
            return null;
        }
        return Resolution.set(itemId, candidate);
    }
}
