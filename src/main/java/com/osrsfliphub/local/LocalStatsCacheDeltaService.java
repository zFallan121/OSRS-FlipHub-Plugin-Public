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
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class LocalStatsCacheDeltaService {
    private static final long COMPLETION_MARKER_MAX_AGE_MS = 5_000L;

    private final Map<Integer, LocalItemAgg> itemAggs;
    private final Map<Integer, LocalInventoryState> inventory;
    private final Map<Integer, MatchedSellMarker> recentMatchedSellBySlot;
    private final Totals totals;
    private final ConversionLedger conversionLedger;
    /** Whose trades these are; a repair fee depends on the player's Smithing. */
    private final long accountKey;
    // Completed sales nothing could cover, kept in case the ingredient buys
    // arrive later from GE History. This cache is fed one delta at a time and
    // has no end of stream to sweep at, so the retry is driven by the arrival
    // of a synced buy, or of stock a conversion made, instead.
    private final Map<Integer, DeferredSale> deferredSaleBySlot = new HashMap<>();
    private final List<DeferredSale> completedDeferredSales = new ArrayList<>();
    /** Which read of the history each synced delta came from, as they arrive. */
    private final ConversionSyncedBatches batches = new ConversionSyncedBatches();

    LocalStatsCacheDeltaService(
        Map<Integer, LocalItemAgg> itemAggs,
        Map<Integer, LocalInventoryState> inventory,
        Map<Integer, MatchedSellMarker> recentMatchedSellBySlot,
        Totals totals
    ) {
        this(itemAggs, inventory, recentMatchedSellBySlot, totals, null);
    }

    LocalStatsCacheDeltaService(
        Map<Integer, LocalItemAgg> itemAggs,
        Map<Integer, LocalInventoryState> inventory,
        Map<Integer, MatchedSellMarker> recentMatchedSellBySlot,
        Totals totals,
        ConversionLedger conversionLedger
    ) {
        this(itemAggs, inventory, recentMatchedSellBySlot, totals, conversionLedger, 0L);
    }

    LocalStatsCacheDeltaService(
        Map<Integer, LocalItemAgg> itemAggs,
        Map<Integer, LocalInventoryState> inventory,
        Map<Integer, MatchedSellMarker> recentMatchedSellBySlot,
        Totals totals,
        ConversionLedger conversionLedger,
        long accountKey
    ) {
        this.itemAggs = itemAggs;
        this.inventory = inventory;
        this.recentMatchedSellBySlot = recentMatchedSellBySlot;
        this.totals = totals;
        this.conversionLedger = conversionLedger;
        this.accountKey = accountKey;
    }

    void reset() {
        itemAggs.clear();
        inventory.clear();
        recentMatchedSellBySlot.clear();
        deferredSaleBySlot.clear();
        completedDeferredSales.clear();
        batches.reset();
        totals.reset();
    }

    void applyDelta(LocalTradeDelta delta) {
        applyDelta(delta, null);
    }

    void applyDelta(LocalTradeDelta delta, Long sellSinceMs) {
        if (delta == null) {
            return;
        }
        // Which read of the history this came from, if any. Inside one read the
        // order is the game's own, and the synced retry has to honour it.
        int batch = batches.batchOf(delta);
        boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
        if (delta.deltaQty <= 0 && !isCompletion) {
            return;
        }
        LocalInventoryState state = inventory.computeIfAbsent(delta.itemId, LocalInventoryState::new);
        if (delta.isBuy) {
            if (delta.deltaQty <= 0) {
                return;
            }
            state.qty += delta.deltaQty;
            state.cost += Math.max(0L, delta.deltaGp);
            state.positionQty += delta.deltaQty;
            if (state.firstBuyTs == null || delta.tsClientMs < state.firstBuyTs) {
                state.firstBuyTs = delta.tsClientMs;
            }
            if (batch != ConversionSyncedBatches.LIVE) {
                state.synced.add(batch, delta.slot, delta.deltaQty);
                resolveDeferredSales();
            }
            return;
        }

        // Same rule as the flip-history ledger: purchased stock is consumed
        // first, and only a shortfall asks whether this item was made.
        if (delta.deltaQty > 0 && state.qty < delta.deltaQty) {
            if (coverShortfallByConversion(delta.itemId, delta.deltaQty - state.qty, LocalTradeKey.of(delta), batch)) {
                // A conversion that makes several things credits every one of
                // them, and a sibling piece sold earlier - deferred because two
                // routes could have made it - is explicable from this moment.
                // The flip-history ledger sweeps for that at its end of stream;
                // this cache has none, so it looks the moment new stock is made.
                resolveDeferredSales();
            }
        }

        // Read before anything is consumed: this is what the purchase ledger
        // could not cover, and the match below is about to zero the stock it
        // measures against.
        boolean includeInStats = sellSinceMs == null || delta.closedAtMs() >= sellSinceMs;
        deferUnmatchedSale(delta, delta.deltaQty - Math.min((long) delta.deltaQty, state.qty), includeInStats, batch);
        if (isCompletion) {
            promoteDeferredSale(delta);
        }

        if (state.qty <= 0 && !isCompletion) {
            return;
        }

        // Stock bought outright is spent first, so a break's pieces are only
        // reached once there is nothing else. Their coins go to the break, which
        // is booked whole against the thing that was taken apart the moment its
        // last piece sells - never piece by piece.
        long remainingQty = delta.deltaQty;
        long remainingGp = delta.deltaGp;
        if (delta.deltaQty > 0 && state.qty > 0 && state.breakQty > 0) {
            long sellable = Math.min((long) delta.deltaQty, state.qty);
            long boughtAvailable = Math.max(0L, state.qty - state.breakQty);
            long fromBreak = Math.max(0L, sellable - boughtAvailable);
            if (fromBreak > 0) {
                long revenue = (Math.max(0L, delta.deltaGp) * fromBreak) / Math.max(1L, delta.deltaQty);
                long tax = salesTax(delta.itemId, delta.price, fromBreak);
                sellFromBreaks(state, delta.itemId, fromBreak, revenue, tax, delta.closedAtMs(), includeInStats,
                    LocalTradeKey.of(delta));
                // The break has those units and their coins. Only the rest of
                // the offer is left to match, with only its share of the gp.
                remainingQty -= fromBreak;
                remainingGp -= revenue;
            }
        }

        long matchQty = 0L;
        long matchCost = 0L;
        long matchRevenue = 0L;
        Long matchedBuyTs = state.firstBuyTs;
        long positionQty = state.positionQty;
        // Same as the flip-history ledger: only stock bought outright is matched
        // here. What is left of a break's pieces belongs to a break that is
        // still open, and matching it would invent a flip with no cost.
        long boughtAvailable = Math.max(0L, state.qty - state.breakQty);
        if (remainingQty > 0 && boughtAvailable > 0) {
            matchQty = Math.min(remainingQty, boughtAvailable);
            if (matchQty > 0) {
                matchRevenue = remainingGp;
                if (matchQty < remainingQty) {
                    matchRevenue = (remainingGp * matchQty) / remainingQty;
                }
                if (matchQty >= state.qty) {
                    // Only reachable with no break stock in the bucket, so no
                    // open break loses its count here.
                    matchCost = state.cost;
                    state.qty = 0L;
                    state.cost = 0L;
                    state.convertedQty = 0L;
                    state.positionQty = 0L;
                    state.firstBuyTs = null;
                } else if (matchQty >= boughtAvailable) {
                    // Every bought unit is going. The whole remaining cost was
                    // theirs, so none of it may be left behind on the break
                    // pieces that stay: they carry no cost, and a residue on an
                    // empty bucket is silently inherited by the next purchase.
                    matchCost = state.cost;
                    state.qty -= matchQty;
                    state.cost = 0L;
                    state.convertedQty = Math.min(state.convertedQty, state.qty);
                    state.breakQty = Math.min(state.breakQty, state.qty);
                    state.positionQty = 0L;
                } else {
                    // Divided across bought units only. Dividing across the whole
                    // bucket handed part of the cost to zero-cost break pieces,
                    // so the unit actually sold carried less than it cost.
                    matchCost = (state.cost * matchQty) / boughtAvailable;
                    state.qty -= matchQty;
                    state.cost = Math.max(0L, state.cost - matchCost);
                    state.convertedQty = Math.min(state.convertedQty, state.qty);
                    state.breakQty = Math.min(state.breakQty, state.qty);
                    if (state.qty <= state.breakQty) {
                        // Only a break's pieces are left, and they are not a
                        // position of their own: the next purchase opens a new one.
                        state.positionQty = 0L;
                    }
                }
            }
        }

        if (includeInStats && matchQty > 0) {
            rememberMatchedSell(delta);
        }
        boolean completionMarked = includeInStats && isCompletion && !delta.isBuy && hasRecentMatchedSell(delta);
        if (!includeInStats || matchQty <= 0) {
            if (completionMarked) {
                LocalItemAgg agg = itemAggs.get(delta.itemId);
                if (agg != null && agg.sellQty > 0) {
                    agg.completedSells += 1;
                    totals.totalCompleted += 1;
                    if (agg.lastSellTs == null || delta.closedAtMs() > agg.lastSellTs) {
                        agg.lastSellTs = delta.closedAtMs();
                    }
                    if (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs) {
                        totals.lastSellTs = agg.lastSellTs;
                    }
                }
                clearMatchedSell(delta);
            }
            return;
        }

        LocalItemAgg agg = itemAggs.computeIfAbsent(delta.itemId, LocalItemAgg::new);
        agg.buyCost += matchCost;
        agg.buyQty += matchQty;
        agg.sellRevenue += Math.max(0L, matchRevenue);
        agg.sellQty += matchQty;
        long tax = salesTax(delta.itemId, delta.price, matchQty);
        agg.taxPaid += Math.max(0L, tax);
        if (matchedBuyTs == null) {
            matchedBuyTs = delta.tsClientMs;
        }
        if (agg.firstBuyTs == null || matchedBuyTs < agg.firstBuyTs) {
            agg.firstBuyTs = matchedBuyTs;
        }
        long duration = heldShare(delta.closedAtMs() - matchedBuyTs, matchQty, positionQty);
        agg.activeMs += duration;
        totals.totalProfit += (Math.max(0L, matchRevenue) - matchCost);
        totals.totalCost += matchCost;
        totals.totalQty += matchQty;
        totals.totalTax += Math.max(0L, tax);
        totals.totalActiveMs += duration;
        if (agg.lastSellTs == null || delta.closedAtMs() > agg.lastSellTs) {
            agg.lastSellTs = delta.closedAtMs();
        }
        if (isCompletion && !delta.isBuy) {
            agg.completedSells += 1;
            totals.totalCompleted += 1;
            clearMatchedSell(delta);
        }
        if (agg.firstBuyTs != null && (totals.firstBuyTs == null || agg.firstBuyTs < totals.firstBuyTs)) {
            totals.firstBuyTs = agg.firstBuyTs;
        }
        if (agg.lastSellTs != null && (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs)) {
            totals.lastSellTs = agg.lastSellTs;
        }
    }

    private static long salesTax(int itemId, int unitPrice, long quantity) {
        return GeTax.forSale(itemId, unitPrice, quantity);
    }

    /**
     * The part of a hold that one sale closes.
     */
    private static long heldShare(long holdMs, long soldQty, long positionQty) {
        if (holdMs <= 0L || soldQty <= 0L) {
            return 0L;
        }
        if (positionQty <= soldQty) {
            return holdMs;
        }
        return (holdMs * soldQty) / positionQty;
    }

    private void deferUnmatchedSale(LocalTradeDelta delta, long unmatchedQty, boolean includeInStats, int syncBatch) {
        if (unmatchedQty <= 0 || delta.deltaQty <= 0) {
            return;
        }
        long revenue = Math.max(0L, delta.deltaGp);
        long unmatchedRevenue = unmatchedQty >= delta.deltaQty
            ? revenue
            : (revenue * unmatchedQty) / delta.deltaQty;
        DeferredSale sale = deferredSaleBySlot.get(delta.slot);
        if (sale == null || sale.itemId != delta.itemId) {
            sale = new DeferredSale(delta.itemId, LocalTradeKey.of(delta), syncBatch);
            deferredSaleBySlot.put(delta.slot, sale);
        }
        sale.qty += unmatchedQty;
        sale.revenue += unmatchedRevenue;
        sale.unitPrice = delta.price > 0 ? delta.price : sale.unitPrice;
        sale.lastSellTsMs = Math.max(sale.lastSellTsMs, delta.closedAtMs());
        sale.includeInStats = sale.includeInStats || includeInStats;
    }

    private void promoteDeferredSale(LocalTradeDelta completion) {
        DeferredSale sale = deferredSaleBySlot.remove(completion.slot);
        if (sale == null || sale.itemId != completion.itemId || sale.qty <= 0) {
            return;
        }
        sale.completionTsMs = completion.closedAtMs() > 0 ? completion.closedAtMs() : sale.lastSellTsMs;
        completedDeferredSales.add(sale);
    }

    /**
     * Retry deferred sales against history-synced stock only. See the same
     * method on the flip-history ledger: this is the trade made on a phone and
     * sold on the desktop, where the ingredient buys reach the plugin stamped
     * after the sale they paid for - and where a sale that was itself synced is
     * refused the parts its own import lists after it.
     */
    private void resolveDeferredSales() {
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
                LocalInventoryState state = inventory.computeIfAbsent(sale.itemId, LocalInventoryState::new);
                // A shortfall of zero usually means stock arrived some other way -
                // a plain buy made after the sale - and that ordering is real
                // evidence, so the ledger is asked for nothing and the sale stays
                // dropped. The exception is stock a conversion already made: one
                // that produces several things credits every one of them at once,
                // so a sibling piece is covered before its own sale is ever
                // looked at.
                long shortfall = sale.qty - Math.min(sale.qty, state.qty);
                boolean covered;
                if (shortfall > 0) {
                    covered = conversionLedger.coverShortfall(
                        sale.itemId, shortfall, new CacheBuckets(sale.syncBatch, sale.key.slot),
                        ConversionEvidence.SYNCED, accountKey, sale.key);
                } else {
                    covered = state.qty > 0
                        && (state.breakQty >= state.qty || state.convertedQty >= state.qty);
                }
                if (!covered) {
                    continue;
                }
                long matchQty = Math.min(sale.qty, state.qty);
                if (matchQty <= 0) {
                    continue;
                }
                long revenue = matchQty >= sale.qty ? sale.revenue : (sale.revenue * matchQty) / sale.qty;

                long boughtAvailable = Math.max(0L, state.qty - state.breakQty);
                long fromBreak = Math.max(0L, matchQty - boughtAvailable);
                if (fromBreak > 0) {
                    long breakRevenue = fromBreak >= matchQty ? revenue : (revenue * fromBreak) / matchQty;
                    sellFromBreaks(state, sale.itemId, fromBreak, breakRevenue,
                        salesTax(sale.itemId, sale.unitPrice, fromBreak), sale.completionTsMs, sale.includeInStats,
                        sale.key);
                    revenue -= breakRevenue;
                    matchQty -= fromBreak;
                }
                if (matchQty <= 0) {
                    iterator.remove();
                    resolvedAny = true;
                    continue;
                }
                Long buyTs = state.firstBuyTs;
                long positionQty = state.positionQty;
                long cost;
                if (matchQty >= state.qty) {
                    cost = state.cost;
                    state.qty = 0L;
                    state.cost = 0L;
                    state.synced.clear();
                    state.convertedQty = 0L;
                    state.breakQty = 0L;
                    state.positionQty = 0L;
                    state.firstBuyTs = null;
                } else {
                    cost = (state.cost * matchQty) / state.qty;
                    state.qty -= matchQty;
                    state.cost = Math.max(0L, state.cost - cost);
                    state.synced.trimTo(state.qty);
                    state.convertedQty = Math.min(state.convertedQty, state.qty);
                    state.breakQty = Math.min(state.breakQty, state.qty);
                    if (state.qty <= state.breakQty) {
                        state.positionQty = 0L;
                    }
                }
                iterator.remove();
                resolvedAny = true;
                if (sale.includeInStats) {
                    recordDeferredSale(sale, matchQty, cost, revenue, buyTs, positionQty);
                }
            }
        }
    }

    private void recordDeferredSale(DeferredSale sale,
                                    long matchQty,
                                    long matchCost,
                                    long matchRevenue,
                                    Long buyTs,
                                    long positionQty) {
        LocalItemAgg agg = itemAggs.computeIfAbsent(sale.itemId, LocalItemAgg::new);
        agg.buyCost += matchCost;
        agg.buyQty += matchQty;
        agg.sellRevenue += Math.max(0L, matchRevenue);
        agg.sellQty += matchQty;
        long tax = salesTax(sale.itemId, sale.unitPrice, matchQty);
        agg.taxPaid += tax;
        agg.completedSells += 1;

        long saleTsMs = sale.completionTsMs > 0 ? sale.completionTsMs : sale.lastSellTsMs;
        Long matchedBuyTs = buyTs != null ? buyTs : saleTsMs;
        if (agg.firstBuyTs == null || matchedBuyTs < agg.firstBuyTs) {
            agg.firstBuyTs = matchedBuyTs;
        }
        // The buys were replayed after the sale, so their timestamps are later
        // than it. A negative hold is a fiction of the replay, not a duration.
        long duration = heldShare(saleTsMs - matchedBuyTs, matchQty, positionQty);
        agg.activeMs += duration;
        if (agg.lastSellTs == null || saleTsMs > agg.lastSellTs) {
            agg.lastSellTs = saleTsMs;
        }

        totals.totalProfit += (Math.max(0L, matchRevenue) - matchCost);
        totals.totalCost += matchCost;
        totals.totalQty += matchQty;
        totals.totalTax += tax;
        totals.totalActiveMs += duration;
        totals.totalCompleted += 1;
        if (agg.firstBuyTs != null && (totals.firstBuyTs == null || agg.firstBuyTs < totals.firstBuyTs)) {
            totals.firstBuyTs = agg.firstBuyTs;
        }
        if (agg.lastSellTs != null && (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs)) {
            totals.lastSellTs = agg.lastSellTs;
        }
    }

    /**
     * Sell units that came out of a break: hand the coins over, and book the
     * whole break the moment its last piece has gone.
     */
    private void sellFromBreaks(LocalInventoryState state,
                                int itemId,
                                long quantity,
                                long revenue,
                                long tax,
                                long tsMs,
                                boolean includeInStats,
                                LocalTradeKey sale) {
        long remaining = quantity;
        long allocated = 0L;
        long allocatedTax = 0L;
        while (remaining > 0L && state.breaks != null && !state.breaks.isEmpty()) {
            ConversionBreak pending = state.breaks.peekFirst();
            long owed = pending.outstandingOf(itemId);
            if (owed <= 0L) {
                state.breaks.pollFirst();
                continue;
            }
            long take = Math.min(remaining, owed);
            long share = take >= remaining ? revenue - allocated : (revenue * take) / quantity;
            long shareTax = take >= remaining ? tax - allocatedTax : (tax * take) / quantity;
            allocated += share;
            allocatedTax += shareTax;
            remaining -= take;
            pending.sell(itemId, take, share, shareTax, tsMs, sale);
            state.qty = Math.max(0L, state.qty - take);
            state.breakQty = Math.max(0L, state.breakQty - take);
            if (pending.isComplete()) {
                state.breaks.pollFirst();
                if (includeInStats) {
                    recordCompletedBreak(pending);
                }
            }
        }
    }

    /**
     * One activity for the whole break, filed against the thing taken apart.
     * The pieces contribute their coins and nothing else - no piece is a flip.
     */
    private void recordCompletedBreak(ConversionBreak pending) {
        int itemId = pending.inputItemId();
        if (itemId <= 0) {
            return;
        }
        long runs = Math.max(1L, pending.runs());
        long cost = pending.costGp();
        long revenue = Math.max(0L, pending.revenueGp());
        long tax = Math.max(0L, pending.taxGp());
        long tsMs = pending.lastSellTsMs();

        LocalItemAgg agg = itemAggs.computeIfAbsent(itemId, LocalItemAgg::new);
        agg.buyCost += cost;
        agg.buyQty += runs;
        agg.sellRevenue += revenue;
        agg.sellQty += runs;
        agg.taxPaid += tax;
        agg.completedSells += 1;

        Long buyTs = pending.firstBuyTs();
        long matchedBuyTs = buyTs != null ? buyTs : tsMs;
        if (agg.firstBuyTs == null || matchedBuyTs < agg.firstBuyTs) {
            agg.firstBuyTs = matchedBuyTs;
        }
        long duration = Math.max(0L, tsMs - matchedBuyTs);
        agg.activeMs += duration;
        if (agg.lastSellTs == null || tsMs > agg.lastSellTs) {
            agg.lastSellTs = tsMs;
        }

        totals.totalProfit += revenue - cost;
        totals.totalCost += cost;
        totals.totalQty += runs;
        totals.totalTax += tax;
        totals.totalActiveMs += duration;
        totals.totalCompleted += 1;
        if (agg.firstBuyTs != null && (totals.firstBuyTs == null || agg.firstBuyTs < totals.firstBuyTs)) {
            totals.firstBuyTs = agg.firstBuyTs;
        }
        if (agg.lastSellTs != null && (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs)) {
            totals.lastSellTs = agg.lastSellTs;
        }
    }

    /** Whether the ledger made anything - a credit, or a break of something. */
    private boolean coverShortfallByConversion(int itemId, long shortfallQty, LocalTradeKey trigger, int syncBatch) {
        if (conversionLedger == null) {
            return false;
        }
        return conversionLedger.coverShortfall(
            itemId, shortfallQty, new CacheBuckets(syncBatch, trigger.slot), ConversionEvidence.ORDERED, accountKey,
            trigger);
    }

    /**
     * {@link ConversionBuckets} over the cache's inventory, answering for one
     * sale: which import it came from, if any, and its place in it.
     */
    private final class CacheBuckets implements ConversionBuckets {
        private final int saleBatch;
        private final int saleSlot;
        private Long earliestInputBuyTs;

        private CacheBuckets(int saleBatch, int saleSlot) {
            this.saleBatch = saleBatch;
            this.saleSlot = saleSlot;
        }

        @Override
        public long quantityOf(int itemId) {
            LocalInventoryState state = inventory.get(itemId);
            return state != null ? state.qty : 0L;
        }

        @Override
        public long convertibleQuantityOf(int itemId) {
            LocalInventoryState state = inventory.get(itemId);
            return state != null ? Math.max(0L, state.qty - state.breakQty) : 0L;
        }

        @Override
        public long syncedQuantityOf(int itemId) {
            LocalInventoryState state = inventory.get(itemId);
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
            LocalInventoryState state = inventory.get(itemId);
            return state != null ? state.cost : 0L;
        }

        @Override
        public void consume(int itemId, long quantity, long cost) {
            LocalInventoryState state = inventory.get(itemId);
            if (state == null) {
                return;
            }
            if (state.firstBuyTs != null
                && (earliestInputBuyTs == null || state.firstBuyTs < earliestInputBuyTs)) {
                earliestInputBuyTs = state.firstBuyTs;
            }
            state.qty = Math.max(0L, state.qty - quantity);
            state.cost = Math.max(0L, state.cost - cost);
            // Synced stock is spent first; among it, what this sale was
            // entitled to before anything it was not.
            state.synced.consume(quantity, saleBatch, saleSlot);
            state.synced.trimTo(state.qty);
            state.convertedQty = Math.min(state.convertedQty, state.qty);
            state.breakQty = Math.min(state.breakQty, state.qty);
            if (state.qty <= state.breakQty) {
                state.positionQty = 0L;
            }
            if (state.qty <= 0L) {
                state.cost = 0L;
                state.convertedQty = 0L;
                state.firstBuyTs = null;
            }
        }

        @Override
        public void credit(int itemId, long quantity, long cost, ConversionMatch match) {
            // The match is the panel's business, and this cache only keeps the
            // numbers. What made the stock is re-derived by the flip history.
            LocalInventoryState state = inventory.computeIfAbsent(itemId, LocalInventoryState::new);
            state.qty += quantity;
            state.cost += cost;
            state.convertedQty += quantity;
            state.positionQty += quantity;
            if (earliestInputBuyTs != null
                && (state.firstBuyTs == null || earliestInputBuyTs < state.firstBuyTs)) {
                state.firstBuyTs = earliestInputBuyTs;
            }
        }

        @Override
        public void creditBreak(int itemId, long quantity, ConversionBreak pending) {
            // The pieces carry no cost. The break keeps it, along with the time
            // the input was bought, so a set broken and sold reports the hold it
            // really had rather than starting the clock at the first sale.
            pending.rememberFirstBuyTs(earliestInputBuyTs);
            LocalInventoryState state = inventory.computeIfAbsent(itemId, LocalInventoryState::new);
            state.qty += quantity;
            state.breakQty += quantity;
            if (state.breaks == null) {
                state.breaks = new ArrayDeque<>();
            }
            state.breaks.addLast(pending);
        }
    }

    private void rememberMatchedSell(LocalTradeDelta delta) {
        if (delta == null || delta.isBuy || delta.slot < 0) {
            return;
        }
        recentMatchedSellBySlot.put(delta.slot, new MatchedSellMarker(delta.itemId, delta.price, delta.closedAtMs()));
    }

    private boolean hasRecentMatchedSell(LocalTradeDelta delta) {
        if (delta == null || delta.isBuy || delta.slot < 0) {
            return false;
        }
        MatchedSellMarker marker = recentMatchedSellBySlot.get(delta.slot);
        if (marker == null) {
            return false;
        }
        if (marker.itemId != delta.itemId || marker.price != delta.price) {
            return false;
        }
        return Math.abs(delta.closedAtMs() - marker.tsClientMs) <= COMPLETION_MARKER_MAX_AGE_MS;
    }

    private void clearMatchedSell(LocalTradeDelta delta) {
        if (delta == null || delta.slot < 0) {
            return;
        }
        recentMatchedSellBySlot.remove(delta.slot);
    }

    static final class Totals {
        long totalProfit;
        long totalCost;
        long totalQty;
        long totalTax;
        long totalActiveMs;
        int totalCompleted;
        Long firstBuyTs;
        Long lastSellTs;

        void reset() {
            totalProfit = 0L;
            totalCost = 0L;
            totalQty = 0L;
            totalTax = 0L;
            totalActiveMs = 0L;
            totalCompleted = 0;
            firstBuyTs = null;
            lastSellTs = null;
        }
    }

    static final class LocalInventoryState {
        private final int itemId;
        private long qty;
        private long cost;
        /** The part of the quantity that came from a GE-history replay, and which. */
        private final ConversionSyncedStock synced = new ConversionSyncedStock();
        /**
         * How much of the quantity was made rather than bought. A deferred sale
         * covered by a sibling's conversion has no shortfall left to ask about,
         * so this is what tells it apart from stock simply bought afterwards.
         */
        private long convertedQty;
        /** How much of the quantity belongs to a break that is still open. */
        private long breakQty;
        /** Those breaks, oldest first. Null until something is taken apart. */
        private Deque<ConversionBreak> breaks;
        /**
         * Everything that has entered the pool since it was last empty: the
         * size of the open position, which is what a sale closes a share of.
         */
        private long positionQty;
        private Long firstBuyTs;

        private LocalInventoryState(int itemId) {
            this.itemId = itemId;
        }
    }

    /** A completed sale nothing could cover, waiting to see if its parts show up. */
    private static final class DeferredSale {
        private final int itemId;
        private long qty;
        private long revenue;
        private int unitPrice;
        private long lastSellTsMs;
        private long completionTsMs;
        private boolean includeInStats;
        /** The stored sale this stands for: the first fill of its offer. */
        private final LocalTradeKey key;
        /** Which read of the history it came from; {@link ConversionSyncedBatches#LIVE} if watched. */
        private final int syncBatch;

        private DeferredSale(int itemId, LocalTradeKey key, int syncBatch) {
            this.itemId = itemId;
            this.key = key;
            this.syncBatch = syncBatch;
        }
    }

    static final class LocalItemAgg {
        final int itemId;
        long buyCost;
        long sellRevenue;
        long buyQty;
        long sellQty;
        long taxPaid;
        long activeMs;
        int completedSells;
        Long firstBuyTs;
        Long lastSellTs;

        private LocalItemAgg(int itemId) {
            this.itemId = itemId;
        }
    }

    static final class MatchedSellMarker {
        private final int itemId;
        private final int price;
        private final long tsClientMs;

        private MatchedSellMarker(int itemId, int price, long tsClientMs) {
            this.itemId = itemId;
            this.price = price;
            this.tsClientMs = tsClientMs;
        }
    }
}
