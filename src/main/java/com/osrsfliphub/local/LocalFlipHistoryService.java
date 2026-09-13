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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@javax.inject.Singleton
final class LocalFlipHistoryService {
    private final Ledger conversionLedger;

    @javax.inject.Inject
    private final RecipeFlipStore recipeFlips;

    LocalFlipHistoryService(Ledger conversionLedger) {
        this(conversionLedger, null);
    }

    /** The store is passed in by tests; in the running plugin it is looked up. */
    LocalFlipHistoryService(Ledger conversionLedger, RecipeFlipStore recipeFlips) {
        this.recipeFlips = recipeFlips;
        this.conversionLedger = conversionLedger;
    }

    /**
     * Unit-test seam. Without a ledger there are no conversions, which is
     * exactly how this service behaved before they existed - so the flip cases
     * are asserted against the original matcher, not a version of it.
     */
    LocalFlipHistoryService() {
        this(null);
    }

    private List<RecipeFlip> recordedFlips(long accountKey) {
        RecipeFlipStore store = recipeFlips != null ? recipeFlips : Bridge.get(RecipeFlipStore.class);
        return store != null ? store.applicable(accountKey) : java.util.Collections.emptyList();
    }

    /** One entry per conversion the player recorded, filed against what it produced. */
    private void appendRecordedConversions(Map<Integer, List<StatsFlipInstance>> byItem,
                                                  RecipeFlipLedger.Result recorded,
                                                  Long sinceMs,
                                                  long accountKey) {
        for (RecipeFlipLedger.Activity activity : recorded.activities) {
            if (sinceMs != null && activity.completionTsMs < sinceMs) {
                continue;
            }
            int quantity = Math.max(1, activity.quantity);
            byItem.computeIfAbsent(activity.itemId, ignored -> new ArrayList<>())
                .add(new StatsFlipInstance(
                    activity.itemId,
                    activity.costGp / quantity,
                    activity.revenueGp / quantity,
                    activity.costGp,
                    activity.revenueGp,
                    activity.profitGp(),
                    activity.quantity,
                    activity.completionTsMs,
                    null,
                    false,
                    activity.taxGp));
        }
    }

    Map<Integer, List<StatsFlipInstance>> buildHistory(List<Delta> deltas, Long sinceMs) {
        return buildHistory(deltas, sinceMs, 0L);
    }

    /**
     * @param accountKey whose trades these are. A repair fee depends on the
     *                   player's Smithing level; 0 when unknown, which prices
     *                   the fee as the NPC would.
     */
    Map<Integer, List<StatsFlipInstance>> buildHistory(List<Delta> deltas, Long sinceMs, long accountKey) {
        Map<Integer, List<StatsFlipInstance>> byItem = new HashMap<>();
        if (deltas == null || deltas.isEmpty()) {
            return byItem;
        }

        // Conversions the player recorded are priced first, and the trades they used are taken
        // out of what follows, so the ordinary replay below never sees a unit that has already
        // been accounted for and needs to know nothing about conversions at all.
        RecipeFlipLedger.Result recorded = RecipeFlipLedger.apply(deltas, recordedFlips(accountKey));
        List<Delta> snapshot = new ArrayList<>(recorded.remainingTrades(deltas));
        snapshot.sort(TradeDeltaUtils.replayOrder());
        appendRecordedConversions(byItem, recorded, sinceMs, accountKey);

        Map<Integer, InventoryState> inventoryByItem = new HashMap<>();
        Map<Integer, PendingSellFlip> pendingSellBySlot = new HashMap<>();
        // Sales the purchase ledger could not cover. Not losses, and not yet
        // activities: if the ingredient buys turn up later from GE History, they
        // become activities then.
        Map<Integer, DeferredSale> deferredSaleBySlot = new HashMap<>();
        List<DeferredSale> completedDeferredSales = new ArrayList<>();
        SyncedBatches batches = new SyncedBatches();
        for (Delta delta : snapshot) {
            if (delta == null || delta.itemId <= 0) {
                continue;
            }
            // Which read of the history this came from, if any. Inside one read
            // the order is the game's own, and the synced retry has to honour it.
            int batch = batches.batchOf(delta);
            boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
            if (delta.deltaQty <= 0 && !isCompletion) {
                continue;
            }

            InventoryState inventory = inventoryByItem.computeIfAbsent(delta.itemId, ignored -> new InventoryState());
            if (delta.isBuy) {
                if (delta.deltaQty <= 0) {
                    continue;
                }
                inventory.qty += delta.deltaQty;
                inventory.cost += Math.max(0L, delta.deltaGp);
                if (batch != SyncedBatches.LIVE) {
                    inventory.synced.add(batch, delta.slot, delta.deltaQty);
                    resolveDeferredSales(byItem, inventoryByItem, completedDeferredSales, sinceMs, accountKey);
                }
                continue;
            }

            // A sale the purchase ledger cannot cover is the only thing that asks
            // whether this item was made rather than bought. Real inventory is
            // always consumed first, so an ordinary flip never reaches here.
            if (delta.deltaQty > 0 && inventory.qty < delta.deltaQty) {
                coverShortfallByConversion(inventoryByItem, delta, delta.deltaQty - inventory.qty, accountKey, batch);
            }

            long matchQty = Math.max(0L, Math.min((long) delta.deltaQty, inventory.qty));
            deferUnmatchedSale(deferredSaleBySlot, delta, delta.deltaQty - matchQty, batch);
            if (isCompletion) {
                promoteDeferredSale(deferredSaleBySlot, completedDeferredSales, delta);
            }

            if (inventory.qty <= 0 || delta.deltaQty <= 0 || matchQty <= 0) {
                if (isCompletion) {
                    finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
                }
                continue;
            }

            long matchRevenue = Math.max(0L, delta.deltaGp);
            if (matchQty < delta.deltaQty) {
                matchRevenue = (matchRevenue * matchQty) / delta.deltaQty;
            }

            // Stock bought outright is always spent first, so a break's pieces
            // are only reached once there is nothing else to sell. What comes
            // off a break is not a flip and gets no entry here: the coins go to
            // the break, and the break is filed as one activity against the
            // thing that was taken apart, once its last piece has gone.
            long boughtAvailable = Math.max(0L, inventory.qty - inventory.breakQty);
            long boughtQty = Math.min(matchQty, boughtAvailable);
            long fromBreak = matchQty - boughtQty;
            if (fromBreak > 0) {
                long breakRevenue = fromBreak >= matchQty
                    ? matchRevenue
                    : (matchRevenue * fromBreak) / matchQty;
                sellFromBreaks(byItem, inventory, delta.itemId, fromBreak, breakRevenue,
                    GeTax.forSale(delta.itemId, delta.price, fromBreak), delta.closedAtMs(), sinceMs,
                    TradeKey.of(delta));
                matchRevenue -= breakRevenue;
                matchQty = boughtQty;
            }
            if (matchQty <= 0) {
                if (isCompletion) {
                    finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
                }
                continue;
            }

            // Only units out of a bucket holding nothing but what one conversion
            // made can be explained by that conversion; anything else - stock
            // bought outright, a leftover from a bigger batch - is a blended
            // cost basis. Whether the offer as a whole is explained is settled
            // when it completes, because one offer can take a conversion's
            // output over several fills, or one run of the recipe per fill.
            Match matchedConversion = null;
            if (inventory.conversion != null && inventory.convertedQty >= inventory.qty) {
                matchedConversion = inventory.conversion;
            }

            long matchCost;
            if (matchQty >= inventory.qty) {
                matchCost = inventory.cost;
                inventory.qty = 0L;
                inventory.cost = 0L;
                inventory.convertedQty = 0L;
                // An emptied bucket keeps no memory of what used to be in it.
                inventory.conversion = null;
            } else if (matchQty >= boughtAvailable) {
                // Every bought unit is going, so the whole remaining cost was
                // theirs. Leaving a residue on the break pieces that stay would
                // hand it to an unrelated later purchase of the same item.
                matchCost = inventory.cost;
                inventory.qty -= matchQty;
                inventory.cost = 0L;
                inventory.convertedQty = Math.min(inventory.convertedQty, inventory.qty);
            } else {
                // Divided across bought units only; see the cache ledger, which
                // splits it the same way for the same reason.
                matchCost = (inventory.cost * matchQty) / boughtAvailable;
                inventory.qty -= matchQty;
                inventory.cost = Math.max(0L, inventory.cost - matchCost);
                inventory.convertedQty = Math.min(inventory.convertedQty, inventory.qty);
            }

            int slotKey = resolveSlotKey(delta);
            PendingSellFlip pending = pendingSellBySlot.computeIfAbsent(slotKey, ignored -> new PendingSellFlip(delta.itemId));
            if (pending.itemId != delta.itemId) {
                pending = new PendingSellFlip(delta.itemId);
                pendingSellBySlot.put(slotKey, pending);
            }
            pending.matchedQty += matchQty;
            pending.matchedCost += matchCost;
            pending.matchedRevenue += matchRevenue;
            // Per fill, on the units matched here, exactly as the stats cache
            // books it - so the two ledgers name the same tax for the same offer.
            pending.matchedTax += GeTax.forSale(delta.itemId, delta.price, matchQty);
            pending.noteConversion(matchedConversion, matchQty);
            pending.lastSellTsMs = Math.max(pending.lastSellTsMs, delta.closedAtMs());
            if (delta.price > 0) {
                pending.lastSellPriceGp = delta.price;
            }

            if (isCompletion) {
                finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
            }
        }

        resolveDeferredSales(byItem, inventoryByItem, completedDeferredSales, sinceMs, accountKey);
        flushOpenSellFlips(byItem, pendingSellBySlot, sinceMs);
        appendOpenBreaks(byItem, inventoryByItem, sinceMs);
        appendDismissedGuesses(byItem, snapshot, sinceMs, accountKey);

        for (List<StatsFlipInstance> history : byItem.values()) {
            history.sort(Comparator.comparingLong((StatsFlipInstance instance) -> instance.completionTsMs).reversed());
        }
        return byItem;
    }

    /**
     * Ask the conversion ledger to make up the shortfall. Nothing happens
     * unless exactly one recipe produces this item and the buckets hold every
     * input it needs, so the common case - no recipe, or no ingredients - costs
     * a map lookup and returns.
     *
     * <p>The match is not read here. A conversion can produce several items and
     * every one of their buckets has to remember what made it, so the buckets
     * record that as they are credited - including the one asked about.
     */
    private void coverShortfallByConversion(Map<Integer, InventoryState> inventoryByItem,
                                            Delta sale,
                                            long shortfallQty,
                                            long accountKey,
                                            int syncBatch) {
        if (conversionLedger == null) {
            return;
        }
        conversionLedger.coverShortfall(sale.itemId, shortfallQty,
            new InventoryBuckets(inventoryByItem, syncBatch, sale.slot),
            Evidence.ORDERED, accountKey, TradeKey.of(sale));
    }

    /**
     * The breaks still waiting on their pieces, shown where they will be filed.
     *
     * <p>A break is booked whole against the thing taken apart, once its last
     * piece has sold, so until then it reports nothing - and a wrong guess at
     * a break is then unreachable: the set is gone, its own sale finds nothing
     * to match, and there is no entry to correct it from. So an unfinished
     * break gets an entry on the card of the thing taken apart, carrying the
     * sales the guess rests on and no figures at all, because it has no honest
     * ones yet. Rejecting it is what stops the break being guessed on the next
     * replay, which leaves the whole item in stock for its own sale.
     *
     * <p>Filed against the thing taken apart, not the piece sold, because that
     * is where the finished activity goes and where its dismissal then stands
     * - and because the stranded cost is the set's. One break is credited to
     * every piece it made, so the same one sits in several buckets and is
     * collected once.
     */
    private static void appendOpenBreaks(Map<Integer, List<StatsFlipInstance>> byItem,
                                         Map<Integer, InventoryState> inventoryByItem,
                                         Long sinceMs) {
        Set<ConversionBreak> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (InventoryState inventory : inventoryByItem.values()) {
            if (inventory == null || inventory.breaks == null) {
                continue;
            }
            for (ConversionBreak pending : inventory.breaks) {
                if (pending == null || pending.isComplete() || !seen.add(pending)) {
                    continue;
                }
                int itemId = pending.inputItemId();
                if (itemId <= 0) {
                    continue;
                }
                // Placed at the latest piece sale, the last thing to happen to it.
                long tsMs = pending.lastSellTsMs();
                if (sinceMs != null && tsMs < sinceMs) {
                    continue;
                }
                byItem.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                    .add(StatsFlipInstance.openBreak(itemId, pending.toMatch(), tsMs));
            }
        }
    }

    /**
     * The guesses the player dismissed, shown where they used to be.
     *
     * <p>A dismissed guess has already done its work by now: the sale it named
     * was refused a recipe above and stands unmatched. What is left is to show
     * that the correction exists and can be undone, so each one whose sale is
     * still in the list gets an entry on the card it used to be filed against
     * - an entry that counts for nothing. One whose sale is gone shows nothing,
     * because there is nothing left to correct.
     */
    private void appendDismissedGuesses(Map<Integer, List<StatsFlipInstance>> byItem,
                                        List<Delta> deltas,
                                        Long sinceMs,
                                        long accountKey) {
        RejectionStore rejections = conversionLedger != null ? conversionLedger.rejections() : null;
        if (rejections == null) {
            return;
        }
        List<ConversionRejection> applicable = rejections.applicable(accountKey);
        if (applicable.isEmpty()) {
            return;
        }
        Set<TradeKey> sales = new HashSet<>();
        for (Delta delta : deltas) {
            if (delta != null && !delta.isBuy && delta.deltaQty > 0) {
                sales.add(TradeKey.of(delta));
            }
        }
        for (ConversionRejection rejection : applicable) {
            if (rejection.itemId <= 0 || !rejection.touches(sales)) {
                continue;
            }
            if (sinceMs != null && rejection.completionTsMs < sinceMs) {
                continue;
            }
            byItem.computeIfAbsent(rejection.itemId, ignored -> new ArrayList<>())
                .add(StatsFlipInstance.dismissed(rejection, accountKey));
        }
    }

    private static void deferUnmatchedSale(Map<Integer, DeferredSale> deferredSaleBySlot,
                                           Delta delta,
                                           long unmatchedQty,
                                           int syncBatch) {
        if (unmatchedQty <= 0 || delta.deltaQty <= 0) {
            return;
        }
        long revenue = Math.max(0L, delta.deltaGp);
        long unmatchedRevenue = unmatchedQty >= delta.deltaQty
            ? revenue
            : (revenue * unmatchedQty) / delta.deltaQty;
        int slotKey = resolveSlotKey(delta);
        DeferredSale sale = deferredSaleBySlot.get(slotKey);
        if (sale == null || sale.itemId != delta.itemId) {
            sale = new DeferredSale(delta.itemId, TradeKey.of(delta), syncBatch);
            deferredSaleBySlot.put(slotKey, sale);
        }
        sale.qty += unmatchedQty;
        sale.revenue += unmatchedRevenue;
        sale.unitPrice = delta.price > 0 ? delta.price : sale.unitPrice;
        sale.lastSellTsMs = Math.max(sale.lastSellTsMs, delta.closedAtMs());
    }

    /** An offer only becomes a candidate once it has actually completed. */
    private static void promoteDeferredSale(Map<Integer, DeferredSale> deferredSaleBySlot,
                                            List<DeferredSale> completedDeferredSales,
                                            Delta completion) {
        DeferredSale sale = deferredSaleBySlot.remove(resolveSlotKey(completion));
        if (sale == null || sale.itemId != completion.itemId || sale.qty <= 0) {
            return;
        }
        sale.completionTsMs = completion.closedAtMs() > 0 ? completion.closedAtMs() : sale.lastSellTsMs;
        completedDeferredSales.add(sale);
    }

    /**
     * Second pass: retry sales nothing could cover, against history-synced stock
     * only.
     *
     * <p>This is the trade made on a phone and sold on the desktop. The sale was
     * watched live and carries a real timestamp; the ingredient buys arrive at
     * the next GE History sync, stamped after it. Refusing them throws away a
     * real activity over a timestamp the plugin made up - while the restriction
     * to synced stock is what stops a live buy that genuinely followed a sale
     * from being raided for parts.
     *
     * <p>A sale that was itself synced is placed by its own import: the parts
     * that import lists after it were bought after it, as the history says,
     * and the buckets refuse them. Parts from another import are still taken,
     * because nothing orders two imports against each other.
     */
    private void resolveDeferredSales(Map<Integer, List<StatsFlipInstance>> byItem,
                                      Map<Integer, InventoryState> inventoryByItem,
                                      List<DeferredSale> completedDeferredSales,
                                      Long sinceMs,
                                      long accountKey) {
        if (conversionLedger == null || completedDeferredSales.isEmpty()) {
            return;
        }
        // One pass is not always enough. A conversion that produces several
        // things is set off by the sale of one of them and credits the rest, and
        // a sibling's own deferred sale may already have been walked past by
        // then. Keep going while anything is still being resolved; each pass
        // either removes a sale or ends the loop.
        boolean resolvedAny = true;
        while (resolvedAny) {
            resolvedAny = false;
            Iterator<DeferredSale> iterator = completedDeferredSales.iterator();
            while (iterator.hasNext()) {
                DeferredSale sale = iterator.next();
                InventoryState inventory = inventoryByItem.computeIfAbsent(sale.itemId, ignored -> new InventoryState());
                // A shortfall of zero usually means stock arrived some other way -
                // a plain buy made after the sale - and that ordering is real
                // evidence, so the ledger is asked for nothing and the sale stays
                // dropped. The exception is stock a conversion already made: one
                // that produces several things credits every one of them at once,
                // so a sibling piece is covered before its own sale is ever
                // looked at.
                long shortfall = sale.qty - Math.min(sale.qty, inventory.qty);
                boolean covered;
                if (shortfall > 0) {
                    covered = conversionLedger.coverShortfall(
                        sale.itemId, shortfall, new InventoryBuckets(inventoryByItem, sale.syncBatch, sale.key.slot),
                        Evidence.SYNCED, accountKey, sale.key);
                } else {
                    covered = inventory.qty > 0
                        && (inventory.breakQty >= inventory.qty || inventory.convertedQty >= inventory.qty);
                }
                if (!covered) {
                    continue;
                }
                long matchQty = Math.min(sale.qty, inventory.qty);
                if (matchQty <= 0) {
                    continue;
                }
                long revenue = matchQty >= sale.qty ? sale.revenue : (sale.revenue * matchQty) / sale.qty;

                // The same order as a watched sale: bought stock first, and a
                // break's pieces hand their coins to the break instead of
                // becoming entries of their own.
                long boughtAvailable = Math.max(0L, inventory.qty - inventory.breakQty);
                long boughtQty = Math.min(matchQty, boughtAvailable);
                long fromBreak = matchQty - boughtQty;
                if (fromBreak > 0) {
                    long breakRevenue = fromBreak >= matchQty ? revenue : (revenue * fromBreak) / matchQty;
                    sellFromBreaks(byItem, inventory, sale.itemId, fromBreak, breakRevenue,
                        GeTax.forSale(sale.itemId, sale.unitPrice, fromBreak), sale.completionTsMs, sinceMs,
                        sale.key);
                    revenue -= breakRevenue;
                    matchQty = boughtQty;
                }
                if (matchQty <= 0) {
                    iterator.remove();
                    resolvedAny = true;
                    continue;
                }

                Match match = inventory.conversion;
                boolean whollyConverted = match != null
                    && inventory.convertedQty >= inventory.qty
                    && matchQty >= inventory.qty
                    && match.quantity == matchQty;
                long cost;
                if (matchQty >= inventory.qty) {
                    cost = inventory.cost;
                    inventory.qty = 0L;
                    inventory.cost = 0L;
                    inventory.synced.clear();
                    inventory.convertedQty = 0L;
                    inventory.conversion = null;
                } else if (matchQty >= Math.max(0L, inventory.qty - inventory.breakQty)) {
                    cost = inventory.cost;
                    inventory.qty -= matchQty;
                    inventory.cost = 0L;
                    inventory.synced.trimTo(inventory.qty);
                    inventory.convertedQty = Math.min(inventory.convertedQty, inventory.qty);
                } else {
                    // Across bought units only; see the cache ledger, which splits it the same
                    // way. Defensive: the guard above admits only a homogeneous bucket today.
                    cost = (inventory.cost * matchQty) / Math.max(1L, inventory.qty - inventory.breakQty);
                    inventory.qty -= matchQty;
                    inventory.cost = Math.max(0L, inventory.cost - cost);
                    inventory.synced.trimTo(inventory.qty);
                    inventory.convertedQty = Math.min(inventory.convertedQty, inventory.qty);
                }
                iterator.remove();
                resolvedAny = true;
                if (sinceMs != null && sale.completionTsMs < sinceMs) {
                    continue;
                }
                byItem.computeIfAbsent(sale.itemId, ignored -> new ArrayList<>()).add(new StatsFlipInstance(
                    sale.itemId,
                    cost / matchQty,
                    revenue / matchQty,
                    cost,
                    revenue,
                    revenue - cost,
                    (int) Math.min(Integer.MAX_VALUE, matchQty),
                    sale.completionTsMs,
                    whollyConverted ? match : null,
                    false,
                    GeTax.forSale(sale.itemId, sale.unitPrice, matchQty)
                ));
            }
        }
    }

    /**
     * Sell units that came out of a break.
     *
     * <p>The piece gets nothing of its own. It never had a cost - the break kept
     * all of it - so there is no flip here to record, only coins to hand over.
     * Oldest break first, so two sets broken in a row are filled in the order
     * they were broken.
     */
    private static void sellFromBreaks(Map<Integer, List<StatsFlipInstance>> byItem,
                                       InventoryState inventory,
                                       int itemId,
                                       long quantity,
                                       long revenue,
                                       long tax,
                                       long tsMs,
                                       Long sinceMs,
                                       TradeKey sale) {
        long remaining = quantity;
        long allocated = 0L;
        long allocatedTax = 0L;
        while (remaining > 0L && inventory.breaks != null && !inventory.breaks.isEmpty()) {
            ConversionBreak pending = inventory.breaks.peekFirst();
            long owed = pending.outstandingOf(itemId);
            if (owed <= 0L) {
                // Already satisfied for this piece by an earlier sale; the break
                // is still waiting on one of its others.
                inventory.breaks.pollFirst();
                continue;
            }
            long take = Math.min(remaining, owed);
            long share = take >= remaining ? revenue - allocated : (revenue * take) / quantity;
            long shareTax = take >= remaining ? tax - allocatedTax : (tax * take) / quantity;
            allocated += share;
            allocatedTax += shareTax;
            remaining -= take;
            pending.sell(itemId, take, share, shareTax, tsMs, sale);
            inventory.qty = Math.max(0L, inventory.qty - take);
            inventory.breakQty = Math.max(0L, inventory.breakQty - take);
            if (pending.isComplete()) {
                inventory.breaks.pollFirst();
                recordCompletedBreak(byItem, pending, sinceMs);
            }
        }
    }

    /**
     * File a finished break as one activity against the thing that was taken
     * apart. Its cost is what that thing cost and its revenue is everything the
     * pieces fetched, so the profit is made of nothing but real trades.
     */
    private static void recordCompletedBreak(Map<Integer, List<StatsFlipInstance>> byItem,
                                             ConversionBreak pending,
                                             Long sinceMs) {
        int itemId = pending.inputItemId();
        if (itemId <= 0) {
            return;
        }
        long tsMs = pending.lastSellTsMs();
        if (sinceMs != null && tsMs < sinceMs) {
            return;
        }
        long runs = Math.max(1L, pending.runs());
        long cost = pending.costGp();
        long revenue = pending.revenueGp();
        byItem.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(new StatsFlipInstance(
            itemId,
            cost / runs,
            revenue / runs,
            cost,
            revenue,
            revenue - cost,
            (int) Math.min(Integer.MAX_VALUE, runs),
            tsMs,
            pending.toMatch(),
            false,
            pending.taxGp()
        ));
    }

    private static int resolveSlotKey(Delta delta) {
        if (delta == null) {
            return Integer.MIN_VALUE;
        }
        if (delta.slot >= 0) {
            return delta.slot;
        }
        return -1 - Math.max(0, delta.itemId);
    }

    private static void finalizePendingFlip(Map<Integer, List<StatsFlipInstance>> byItem,
                                            Map<Integer, PendingSellFlip> pendingSellBySlot,
                                            Delta completion,
                                            Long sinceMs) {
        if (byItem == null || pendingSellBySlot == null || completion == null || completion.itemId <= 0) {
            return;
        }
        int slotKey = resolveSlotKey(completion);
        PendingSellFlip pending = pendingSellBySlot.remove(slotKey);
        if (pending == null || pending.itemId != completion.itemId || pending.matchedQty <= 0) {
            return;
        }

        long completionTsMs = completion.closedAtMs() > 0
            ? completion.closedAtMs()
            : Math.max(0L, pending.lastSellTsMs);
        if (sinceMs != null && completionTsMs < sinceMs) {
            return;
        }

        recordPendingFlip(byItem, pending, completion.itemId, completionTsMs, completion.price, false);
    }

    /**
     * Sell offers that have filled part-way and are still sitting in the Grand
     * Exchange when the replay runs out of deltas.
     *
     * <p>The coins are real and the running totals already hold them, so the
     * ledger has to hold them too: the totals are reconciled against it, and
     * what the ledger has never heard of is erased. Recorded as in progress
     * rather than as a flip - the offer has not finished, and it will be
     * finalized properly by the completion when it arrives.
     */
    private static void flushOpenSellFlips(Map<Integer, List<StatsFlipInstance>> byItem,
                                           Map<Integer, PendingSellFlip> pendingSellBySlot,
                                           Long sinceMs) {
        if (byItem == null || pendingSellBySlot == null || pendingSellBySlot.isEmpty()) {
            return;
        }
        for (PendingSellFlip pending : pendingSellBySlot.values()) {
            if (pending == null || pending.itemId <= 0 || pending.matchedQty <= 0) {
                continue;
            }
            long tsMs = Math.max(0L, pending.lastSellTsMs);
            if (sinceMs != null && tsMs < sinceMs) {
                continue;
            }
            recordPendingFlip(byItem, pending, pending.itemId, tsMs, 0, true);
        }
        pendingSellBySlot.clear();
    }

    private static void recordPendingFlip(Map<Integer, List<StatsFlipInstance>> byItem,
                                          PendingSellFlip pending,
                                          int itemId,
                                          long tsMs,
                                          int fallbackSellPrice,
                                          boolean inProgress) {
        long qty = pending.matchedQty;
        long buyCost = Math.max(0L, pending.matchedCost);
        long sellRevenue = Math.max(0L, pending.matchedRevenue);
        // Use integer division so displayed per-item prices never round up above realized totals.
        long buyPrice = Math.max(0L, pending.matchedCost / qty);
        long sellPrice = Math.max(0L, pending.matchedRevenue / qty);
        if (sellPrice <= 0L) {
            sellPrice = fallbackSellPrice > 0
                ? fallbackSellPrice
                : pending.lastSellPriceGp > 0
                    ? pending.lastSellPriceGp
                    : 0L;
        }
        long profit = sellRevenue - buyCost;
        StatsFlipInstance instance = new StatsFlipInstance(
            itemId,
            buyPrice,
            sellPrice,
            buyCost,
            sellRevenue,
            profit,
            (int) Math.min(Integer.MAX_VALUE, qty),
            tsMs,
            pending.explainedBy(),
            inProgress,
            pending.matchedTax
        );
        byItem.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(instance);
    }

    /**
     * {@link Buckets} over one replay's inventory map, answering for
     * one sale: which import it came from, if any, and its place in it.
     */
    private static final class InventoryBuckets implements Buckets {
        private final Map<Integer, InventoryState> inventoryByItem;
        private final int saleBatch;
        private final int saleSlot;

        private InventoryBuckets(Map<Integer, InventoryState> inventoryByItem, int saleBatch, int saleSlot) {
            this.inventoryByItem = inventoryByItem;
            this.saleBatch = saleBatch;
            this.saleSlot = saleSlot;
        }

        @Override
        public long quantityOf(int itemId) {
            InventoryState state = inventoryByItem.get(itemId);
            return state != null ? state.qty : 0L;
        }

        @Override
        public long convertibleQuantityOf(int itemId) {
            InventoryState state = inventoryByItem.get(itemId);
            return state != null ? Math.max(0L, state.qty - state.breakQty) : 0L;
        }

        @Override
        public long syncedQuantityOf(int itemId) {
            InventoryState state = inventoryByItem.get(itemId);
            if (state == null) {
                return 0L;
            }
            // A plain sale spends bought-outright stock first and never says
            // which synced units, if any, went with it; the oldest are taken to
            // have, so the lots are brought down to what is actually held.
            state.synced.trimTo(state.qty);
            return state.synced.quantityNotAfter(saleBatch, saleSlot);
        }

        @Override
        public long costOf(int itemId) {
            InventoryState state = inventoryByItem.get(itemId);
            return state != null ? state.cost : 0L;
        }

        @Override
        public void consume(int itemId, long quantity, long cost) {
            InventoryState state = inventoryByItem.get(itemId);
            if (state == null) {
                return;
            }
            state.qty = Math.max(0L, state.qty - quantity);
            state.cost = Math.max(0L, state.cost - cost);
            // Synced stock is spent first, so a bucket only still claims synced
            // units when it has nothing else left - the conservative side of a
            // question the buckets cannot answer exactly. Among synced units,
            // the ones this sale was entitled to go before any it was not.
            state.synced.consume(quantity, saleBatch, saleSlot);
            state.synced.trimTo(state.qty);
            state.convertedQty = Math.min(state.convertedQty, state.qty);
            state.breakQty = Math.min(state.breakQty, state.qty);
            if (state.qty <= 0L) {
                state.cost = 0L;
                state.convertedQty = 0L;
                state.conversion = null;
            }
        }

        @Override
        public void credit(int itemId, long quantity, long cost, Match match) {
            // Only the conversion ledger credits a bucket; a purchase adds to it
            // directly. So everything credited here was made, not bought.
            InventoryState state = inventoryByItem.computeIfAbsent(itemId, ignored -> new InventoryState());
            state.qty += quantity;
            state.cost += cost;
            state.convertedQty += quantity;
            state.conversion = match;
        }

        @Override
        public void creditBreak(int itemId, long quantity, ConversionBreak pending) {
            // No cost travels with these. The break holds every coin the input
            // cost until the last piece it made has been sold.
            InventoryState state = inventoryByItem.computeIfAbsent(itemId, ignored -> new InventoryState());
            state.qty += quantity;
            state.breakQty += quantity;
            if (state.breaks == null) {
                state.breaks = new ArrayDeque<>();
            }
            state.breaks.addLast(pending);
        }
    }

    private static final class InventoryState {
        private long qty;
        private long cost;
        /** The part of the quantity that came from a GE-history replay, and which. */
        private final SyncedStock synced = new SyncedStock();
        /** How much of the quantity was made rather than bought. */
        private long convertedQty;
        /** The conversion that made those units. */
        private Match conversion;
        /** How much of the quantity belongs to a break that is still open. */
        private long breakQty;
        /** Those breaks, oldest first. Null until something is taken apart. */
        private Deque<ConversionBreak> breaks;
    }

    /** A completed sale nothing could cover, waiting to see if its parts show up. */
    private static final class DeferredSale {
        private final int itemId;
        private long qty;
        private long revenue;
        /** The gross price per unit, which is what the tax is charged on. */
        private int unitPrice;
        private long lastSellTsMs;
        private long completionTsMs;
        /** The stored sale this stands for: the first fill of its offer. */
        private final TradeKey key;
        /** Which read of the history it came from; {@link SyncedBatches#LIVE} if watched. */
        private final int syncBatch;

        private DeferredSale(int itemId, TradeKey key, int syncBatch) {
            this.itemId = itemId;
            this.key = key;
            this.syncBatch = syncBatch;
        }
    }

    private static final class PendingSellFlip {
        private final int itemId;
        private long matchedQty;
        private long matchedCost;
        private long matchedRevenue;
        private long matchedTax;
        private long lastSellPriceGp;
        private long lastSellTsMs;
        /** The one recipe every fill so far was made by. */
        private ConversionRecipe conversionRecipe;
        /** Each conversion this offer drew on, and how many of its units it took. */
        private final Map<Match, long[]> takenByConversion = new LinkedHashMap<>();
        /** Once part of this offer came from elsewhere, no recipe explains it. */
        private boolean conversionDisqualified;

        private PendingSellFlip(int itemId) {
            this.itemId = itemId;
        }

        /**
         * One fill's units were the output of {@code match} - or of nothing in
         * particular, when null. One offer can fill from several sources, and
         * the moment any part of it is not one recipe's own output the whole
         * activity loses the claim: the cost basis is a blend by then.
         */
        void noteConversion(Match match, long quantity) {
            if (conversionDisqualified) {
                return;
            }
            if (match == null || match.recipe == null
                || (conversionRecipe != null && conversionRecipe != match.recipe)) {
                conversionDisqualified = true;
                conversionRecipe = null;
                takenByConversion.clear();
                return;
            }
            conversionRecipe = match.recipe;
            takenByConversion.computeIfAbsent(match, ignored -> new long[1])[0] += quantity;
        }

        /**
         * The conversion that explains this offer, or null. Every unit sold has
         * to have been made by the same recipe, every conversion drawn on has
         * to have been sold in full by this offer - a third of a chestplate is
         * not a chestplate - and what they cost has to be the cost basis to
         * the coin, or the block would not add up to the number beside it.
         */
        Match explainedBy() {
            if (conversionDisqualified || conversionRecipe == null || takenByConversion.isEmpty()) {
                return null;
            }
            long convertedQty = 0L;
            long costGp = 0L;
            long accountKey = 0L;
            boolean likely = false;
            Set<TradeKey> trades = new LinkedHashSet<>();
            // (itemId, fee) -> {itemId, fee, quantity, cost}, in first-seen order.
            Map<Long, long[]> lines = new LinkedHashMap<>();
            for (Map.Entry<Match, long[]> entry : takenByConversion.entrySet()) {
                Match match = entry.getKey();
                if (entry.getValue()[0] != match.quantity) {
                    return null;
                }
                convertedQty += match.quantity;
                costGp += match.costGp;
                accountKey = match.accountKey;
                trades.addAll(match.trades);
                likely |= match.confidence == Confidence.LIKELY;
                for (Match.Line line : match.lines) {
                    long key = ((long) line.itemId << 1) | (line.fee ? 1L : 0L);
                    long[] sum = lines.computeIfAbsent(key,
                        ignored -> new long[]{line.itemId, line.fee ? 1L : 0L, 0L, 0L});
                    sum[2] += line.quantity;
                    sum[3] += line.costGp;
                }
            }
            if (convertedQty != matchedQty || costGp != matchedCost) {
                return null;
            }
            List<Match.Line> merged = new ArrayList<>(lines.size());
            for (long[] sum : lines.values()) {
                merged.add(new Match.Line((int) sum[0], sum[2], sum[3], sum[1] == 1L));
            }
            return new Match(conversionRecipe, matchedQty, costGp, merged,
                likely ? Confidence.LIKELY : Confidence.CONFIRMED,
                new ArrayList<>(trades), accountKey);
        }
    }
}
