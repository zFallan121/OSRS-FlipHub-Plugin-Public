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

import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

@Singleton
final class OfferPreviewRuntimeFacadeService {
    void pollAndUpdate(ClientThread clientThread,
                       OfferPreviewItemResolver resolver,
                       IntPredicate setPreviewItem,
                       Runnable clearPreview) {
        if (clientThread == null) {
            return;
        }
        clientThread.invokeLater(() -> updateOfferPreviewItem(resolver, setPreviewItem, clearPreview));
    }

    void updateOfferPreviewItem(OfferPreviewItemResolver resolver,
                                IntPredicate setPreviewItem,
                                Runnable clearPreview) {
        OfferPreviewItemResolver.Resolution resolution = resolver != null ? resolver.resolve() : null;
        if (resolution == null || resolution.shouldClear()) {
            if (clearPreview != null) {
                clearPreview.run();
            }
            return;
        }
        Integer itemId = resolution.getItemId();
        if (itemId == null || itemId <= 0) {
            if (clearPreview != null) {
                clearPreview.run();
            }
            return;
        }
        if (setPreviewItem != null) {
            setPreviewItem.test(itemId);
        }
    }

    boolean isOfferStatusOpen(Widget geRoot, String[] offerStatusMarkers) {
        return OfferPreviewWidgetParser.widgetTreeContainsAnyText(geRoot, offerStatusMarkers);
    }

    Widget getVisibleGeRoot(Client client, int geRootComponentId) {
        if (client == null) {
            return null;
        }
        Widget geRoot = client.getWidget(geRootComponentId);
        if (geRoot == null || geRoot.isHidden()) {
            return null;
        }
        return geRoot;
    }

    GrandExchangeOffer getSelectedOffer(Client client, int selectedSlotVarbitId) {
        if (client == null) {
            return null;
        }
        int rawSlot = client.getVarbitValue(selectedSlotVarbitId);
        if (rawSlot <= 0) {
            return null;
        }
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null || offers.length == 0) {
            return null;
        }
        int slot = rawSlot;
        if (slot >= 1 && slot <= offers.length) {
            slot -= 1;
        }
        if (slot < 0 || slot >= offers.length) {
            return null;
        }
        return offers[slot];
    }

    int findFirstItemId(Widget widget) {
        return OfferPreviewWidgetParser.findFirstItemId(widget);
    }

    String findItemNameCandidate(Widget root,
                                 String[] itemNameExcludes,
                                 ToIntFunction<String> resolveItemIdFromName) {
        return OfferPreviewWidgetParser.findItemNameCandidate(
            root,
            itemNameExcludes,
            name -> resolveItemIdFromName != null ? resolveItemIdFromName.applyAsInt(name) : -1
        );
    }
}
