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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

@Singleton
final class ItemsAssembler {
    static final class Result {
        final List<FlipHubItem> items;
        final Set<Integer> itemsNeedingLimits;

        Result(List<FlipHubItem> items, Set<Integer> itemsNeedingLimits) {
            this.items = items != null ? items : new ArrayList<>();
            this.itemsNeedingLimits = itemsNeedingLimits != null ? itemsNeedingLimits : new HashSet<>();
        }
    }

    private final ItemManager itemManager;

    @Inject
    ItemsAssembler(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    private String getCachedItemName(int itemId) {
        if (itemManager == null) {
            return null;
        }
        ItemLookup service = Bridge.get(ItemLookup.class);
        return service != null ? service.getCachedItemName(itemId) : null;
    }

    private void cacheItemName(int itemId) {
        ItemLookup service = Bridge.get(ItemLookup.class);
        if (service != null) {
            service.cacheItemName(itemId);
        }
    }

    private void applyItemInfo(FlipHubItem item, int itemId, TradeInfo tradeInfo, LimitInfo limitInfo) {
        ItemEnrichment service = Bridge.get(ItemEnrichment.class);
        if (service == null) {
            return;
        }
        service.applyGuidePrices(item, itemId, true);
        service.applyLocalTradeInfo(item, tradeInfo);
        service.applyLocalLimitInfo(item, itemId, limitInfo);
        service.applyMarginInfo(item);
    }

    Result assemble(GrandExchangeOffer[] offers,
                    Map<Integer, TradeInfo> tradeInfo,
                    Map<Integer, LimitInfo> limitInfo,
                    String currentQuery,
                    boolean bookmarkFilterEnabled,
                    Set<Integer> bookmarkedItems) {
        List<FlipHubItem> items = new ArrayList<>();
        Set<Integer> seenItemIds = new HashSet<>();
        Set<Integer> itemsNeedingLimits = new HashSet<>();
        String needle = normalizeQuery(currentQuery);

        GrandExchangeOffer[] safeOffers = offers != null ? offers : new GrandExchangeOffer[0];
        for (GrandExchangeOffer offer : safeOffers) {
            if (offer == null) {
                continue;
            }
            GrandExchangeOfferState state = offer.getState();
            if (state == null || state == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            int itemId = offer.getItemId();
            if (itemId <= 0) {
                continue;
            }

            String name = resolveName(itemId);
            if (isFiltered(itemId, name, needle, bookmarkFilterEnabled, bookmarkedItems)) {
                continue;
            }

            FlipHubItem item = new FlipHubItem();
            item.item_id = itemId;
            item.item_name = name;
            applyItemInfo(item, itemId,
                tradeInfo != null ? tradeInfo.get(itemId) : null,
                limitInfo != null ? limitInfo.get(itemId) : null);
            if (!seenItemIds.contains(itemId)) {
                items.add(item);
                seenItemIds.add(itemId);
                itemsNeedingLimits.add(itemId);
            }
        }

        if (tradeInfo != null && !tradeInfo.isEmpty()) {
            for (Map.Entry<Integer, TradeInfo> entry : tradeInfo.entrySet()) {
                if (entry == null || entry.getKey() == null) {
                    continue;
                }
                int itemId = entry.getKey();
                if (itemId <= 0 || seenItemIds.contains(itemId)) {
                    continue;
                }
                String name = resolveName(itemId);
                if (isFiltered(itemId, name, needle, bookmarkFilterEnabled, bookmarkedItems)) {
                    continue;
                }
                FlipHubItem item = new FlipHubItem();
                item.item_id = itemId;
                item.item_name = name;
                applyItemInfo(item, itemId, entry.getValue(),
                    limitInfo != null ? limitInfo.get(itemId) : null);
                items.add(item);
                seenItemIds.add(itemId);
                itemsNeedingLimits.add(itemId);
            }
        }

        if (needle != null) {
            appendCatalogueMatches(items, seenItemIds, itemsNeedingLimits, needle,
                limitInfo, bookmarkFilterEnabled, bookmarkedItems);
        }

        return new Result(items, itemsNeedingLimits);
    }

    /**
     * Everything the Grand Exchange sells, not only what this account has already flipped.
     * Without this the search box can only narrow the list it is sitting on, which makes it a
     * filter rather than a search: there is no way to look an item up before trading it once.
     *
     * <p>These carry no trade history, so their card shows the live prices, the margin and the
     * limit, and leaves last buy/sell empty until the account actually trades one.</p>
     */
    private void appendCatalogueMatches(List<FlipHubItem> items,
                                        Set<Integer> seenItemIds,
                                        Set<Integer> itemsNeedingLimits,
                                        String needle,
                                        Map<Integer, LimitInfo> limitInfo,
                                        boolean bookmarkFilterEnabled,
                                        Set<Integer> bookmarkedItems) {
        if (itemManager == null) {
            return;
        }
        List<ItemPrice> matches;
        try {
            matches = itemManager.search(needle);
        } catch (RuntimeException ignored) {
            return;
        }
        if (matches == null) {
            return;
        }
        // The cap has to fall on the least useful matches, not on whichever ones the client
        // happened to list last: "rune" matches hundreds of items, and the one actually named
        // Rune has to survive that. Exact name first, then names that start with what was typed.
        List<ItemPrice> ranked = new ArrayList<>(matches);
        ranked.sort(Comparator
            .comparingInt((ItemPrice match) -> matchRank(match, needle))
            .thenComparing(match -> match.getName() != null ? match.getName() : "",
                String.CASE_INSENSITIVE_ORDER));

        int added = 0;
        for (ItemPrice match : ranked) {
            if (added >= Const.MAX_SEARCH_CATALOGUE_RESULTS) {
                break;
            }
            if (match == null) {
                continue;
            }
            int itemId = match.getId();
            if (itemId <= 0 || seenItemIds.contains(itemId)) {
                continue;
            }
            String name = match.getName();
            if (isFiltered(itemId, name, needle, bookmarkFilterEnabled, bookmarkedItems)) {
                continue;
            }
            FlipHubItem item = new FlipHubItem();
            item.item_id = itemId;
            item.item_name = name;
            applyItemInfo(item, itemId, null, limitInfo != null ? limitInfo.get(itemId) : null);
            items.add(item);
            seenItemIds.add(itemId);
            itemsNeedingLimits.add(itemId);
            added++;
        }
    }

    private static int matchRank(ItemPrice match, String needle) {
        String name = match != null && match.getName() != null
            ? match.getName().toLowerCase(Locale.US)
            : "";
        if (name.equals(needle)) {
            return 0;
        }
        return name.startsWith(needle) ? 1 : 2;
    }

    private String resolveName(int itemId) {
        String name = getCachedItemName(itemId);
        if (Str.isBlank(name)) {
            cacheItemName(itemId);
            name = getCachedItemName(itemId);
        }
        return name;
    }

    private boolean isFiltered(int itemId,
                               String itemName,
                               String queryNeedle,
                               boolean bookmarkFilterEnabled,
                               Set<Integer> bookmarkedItems) {
        if (queryNeedle != null && !queryNeedle.isEmpty()) {
            String hay = itemName != null ? itemName.toLowerCase(Locale.US) : "";
            if (!hay.contains(queryNeedle)) {
                return true;
            }
        }
        if (bookmarkFilterEnabled && (bookmarkedItems == null || !bookmarkedItems.contains(itemId))) {
            return true;
        }
        return false;
    }

    private String normalizeQuery(String currentQuery) {
        if (Str.isBlank(currentQuery)) {
            return null;
        }
        return currentQuery.trim().toLowerCase(Locale.US);
    }
}
