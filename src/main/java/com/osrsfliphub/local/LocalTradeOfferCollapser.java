package com.osrsfliphub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One stored record per Grand Exchange offer.
 */
final class LocalTradeOfferCollapser {
    enum Outcome {
        /** Stored as one more record. */
        APPENDED,
        /** Stored, and the fills it completed were folded into it. */
        COLLAPSED,
        /** Not stored: it closed nothing and carried nothing. */
        DROPPED
    }

    private LocalTradeOfferCollapser() {
    }

    static boolean isCompletion(LocalTradeDelta delta) {
        return delta != null && "OFFER_COMPLETED".equals(delta.eventType);
    }

    /** The identity rule; see the class comment. */
    static boolean sameOffer(LocalTradeDelta a, LocalTradeDelta b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.slot != b.slot || a.itemId != b.itemId || a.isBuy != b.isBuy || a.price != b.price) {
            return false;
        }
        return a.offerStartMs <= 0 || b.offerStartMs <= 0 || a.offerStartMs == b.offerStartMs;
    }

    /**
     * Store one record as it arrives. A fill is simply added. A completion walks back
     * over the list to the fills of its own offer - stopping at the slot's previous
     * completion, or at a fill that is some other offer's - and replaces them with one
     * record. A completion that finds no fills and brings no quantity of its own is the
     * collect of an offer already stored whole, and is not kept.
     */
    static Outcome append(List<LocalTradeDelta> deltas, LocalTradeDelta delta) {
        if (deltas == null || delta == null) {
            return Outcome.DROPPED;
        }
        if (!isCompletion(delta)) {
            deltas.add(delta);
            return Outcome.APPENDED;
        }
        List<Integer> runNewestFirst = new ArrayList<>();
        for (int i = deltas.size() - 1; i >= 0; i--) {
            LocalTradeDelta stored = deltas.get(i);
            if (stored == null || stored.slot != delta.slot) {
                continue;
            }
            if (isCompletion(stored) || !sameOffer(stored, delta)) {
                break;
            }
            if (stored.deltaQty > 0) {
                runNewestFirst.add(i);
            }
        }
        if (runNewestFirst.isEmpty()) {
            if (delta.deltaQty <= 0) {
                return Outcome.DROPPED;
            }
            deltas.add(stampCompleted(delta));
            return Outcome.APPENDED;
        }
        Run run = new Run();
        for (int i = runNewestFirst.size() - 1; i >= 0; i--) {
            run.absorb(deltas.get(runNewestFirst.get(i)));
        }
        for (int index : runNewestFirst) {
            deltas.remove(index);
        }
        deltas.add(run.close(delta));
        return Outcome.COLLAPSED;
    }

    /**
     * Collapse a whole list, oldest first. Every completed offer becomes one record; a
     * run of fills the slot moved on from without a completion being seen (cancelled, or
     * finished while nothing was watching) becomes one {@code OFFER_UPDATED} record; and
     * the fills of an offer still open at the end of the list are kept exactly as they
     * are, so it goes on being reported fill by fill until its completion arrives.
     *
     * @param sorted records in time order
     */
    static List<LocalTradeDelta> collapse(List<LocalTradeDelta> sorted) {
        List<LocalTradeDelta> out = new ArrayList<>(sorted != null ? sorted.size() : 0);
        if (sorted == null || sorted.isEmpty()) {
            return out;
        }
        Map<Integer, Run> openBySlot = new HashMap<>();
        for (LocalTradeDelta delta : sorted) {
            if (delta == null) {
                continue;
            }
            Run open = openBySlot.get(delta.slot);
            if (isCompletion(delta)) {
                if (open != null && open.sameOffer(delta)) {
                    out.add(open.close(delta));
                    openBySlot.remove(delta.slot);
                    continue;
                }
                if (open != null) {
                    open.flushAbandoned(out);
                    openBySlot.remove(delta.slot);
                }
                if (delta.deltaQty > 0) {
                    out.add(stampCompleted(delta));
                }
                continue;
            }
            if (delta.deltaQty <= 0) {
                // Coins with no quantity: both ledgers ignore it, and it is no offer's fill.
                out.add(delta);
                continue;
            }
            if (open != null && !open.sameOffer(delta)) {
                open.flushAbandoned(out);
                open = null;
            }
            if (open == null) {
                open = new Run();
                openBySlot.put(delta.slot, open);
            }
            open.absorb(delta);
        }
        for (Run open : openBySlot.values()) {
            open.flushOpen(out);
        }
        out.sort(Comparator.comparingLong(delta -> delta.tsClientMs));
        return out;
    }

    /** A completion arriving on its own already is the whole offer; mark it so. */
    private static LocalTradeDelta stampCompleted(LocalTradeDelta completion) {
        if (completion.endMs <= 0) {
            completion.endMs = completion.tsClientMs;
        }
        return completion;
    }

    /** The fills of one offer, oldest first, and their sum. */
    private static final class Run {
        private final List<LocalTradeDelta> fills = new ArrayList<>();
        private int qty;
        private long gp;
        private boolean baselineSynthetic;
        private long offerStartMs;
        /** When the run's latest quantity changed hands. */
        private long lastMs;

        boolean sameOffer(LocalTradeDelta delta) {
            if (!LocalTradeOfferCollapser.sameOffer(fills.get(0), delta)) {
                return false;
            }
            // The first fill may predate start tracking while a later one carries it.
            return offerStartMs <= 0 || delta.offerStartMs <= 0 || offerStartMs == delta.offerStartMs;
        }

        void absorb(LocalTradeDelta delta) {
            fills.add(delta);
            qty += delta.deltaQty;
            gp += delta.deltaGp;
            baselineSynthetic |= delta.baselineSynthetic;
            if (offerStartMs <= 0) {
                offerStartMs = delta.offerStartMs;
            }
            lastMs = Math.max(lastMs, delta.closedAtMs());
        }

        LocalTradeDelta close(LocalTradeDelta completion) {
            absorb(completion);
            return collapsed("OFFER_COMPLETED", completion.closedAtMs());
        }

        /**
         * The slot moved on without a completion: one record, still an update, ending
         * at its last fill so a sale among them is still matched against everything
         * held by the time it stopped selling.
         */
        void flushAbandoned(List<LocalTradeDelta> out) {
            if (fills.size() == 1) {
                out.add(fills.get(0));
                return;
            }
            out.add(collapsed("OFFER_UPDATED", lastMs));
        }

        /** Still filling: kept fill by fill. */
        void flushOpen(List<LocalTradeDelta> out) {
            out.addAll(fills);
        }

        private LocalTradeDelta collapsed(String eventType, long endMs) {
            LocalTradeDelta first = fills.get(0);
            return new LocalTradeDelta(
                first.tsClientMs,
                first.slot,
                first.itemId,
                first.isBuy,
                qty,
                gp,
                eventType,
                first.price,
                baselineSynthetic,
                offerStartMs,
                endMs
            );
        }
    }
}
