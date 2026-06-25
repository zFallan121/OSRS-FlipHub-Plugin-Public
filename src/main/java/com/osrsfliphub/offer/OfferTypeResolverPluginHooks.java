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

import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

final class OfferTypeResolverPluginHooks implements OfferTypeResolver.Hooks {
    private final Supplier<Client> clientSupplier;
    private final Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier;

    OfferTypeResolverPluginHooks(
        Supplier<Client> clientSupplier,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.offerPreviewRuntimeFacadeServiceSupplier = offerPreviewRuntimeFacadeServiceSupplier;
    }

    @Override
    public Widget getOfferContainer() {
        Client client = resolveClient();
        return client != null ? client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) : null;
    }

    @Override
    public GrandExchangeOffer getSelectedOffer() {
        OfferPreviewRuntimeFacadeService service = resolveOfferPreviewRuntimeFacadeService();
        Client client = resolveClient();
        return service != null ? service.getSelectedOffer(client, VarbitID.GE_SELECTEDSLOT) : null;
    }

    @Override
    public int getNewOfferTypeVarbit() {
        Client client = resolveClient();
        return client != null ? client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) : 0;
    }

    @Override
    public Widget getVisibleGeRoot() {
        OfferPreviewRuntimeFacadeService service = resolveOfferPreviewRuntimeFacadeService();
        Client client = resolveClient();
        return service != null ? service.getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER) : null;
    }

    @Override
    public List<String> collectWidgetText(Widget widget) {
        return OfferPreviewWidgetParser.collectWidgetText(widget);
    }

    @Override
    public String normalizeOfferText(String text) {
        return OfferPreviewWidgetParser.normalizeText(text);
    }

    private Client resolveClient() {
        return clientSupplier != null ? clientSupplier.get() : null;
    }

    private OfferPreviewRuntimeFacadeService resolveOfferPreviewRuntimeFacadeService() {
        return offerPreviewRuntimeFacadeServiceSupplier != null
            ? offerPreviewRuntimeFacadeServiceSupplier.get()
            : null;
    }
}
