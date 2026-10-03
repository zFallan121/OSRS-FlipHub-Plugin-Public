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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The first history sync after the update that reads a purchase past max cash.
 *
 * <p>The place the player's last sync saved was written by the version before, which left such
 * a purchase out of its lists: a 3rd age pickaxe bought for 2,394,000,000 was never a row to
 * it. The update must not cost anybody the trades they made since that sync, and must not hand
 * over as new a row that was already in the list then.
 */
public class GeHistoryFirstSyncAfterUpdateTest {
    private static final Trade PICKAXE_BOUGHT = new Trade(20011, true, 1, 2_394_000_000L, 2_394_000_000L);

    // The list as it stood at the last sync, newest first, and the place that sync saved.
    private static final Trade LOGS_SOLD = new Trade(1513, false, 70_000, 1_100, 77_000_000L);
    private static final Trade LAWS_BOUGHT = new Trade(563, true, 500, 120, 60_000L);
    private static final Trade BOWS_SOLD = new Trade(855, false, 100, 600, 58_800L);
    private static final String OLD_PLACE = "v2,1513|S|70000|77000000,563|B|500|60000,855|S|100|58800";

    // Made on a phone since.
    private static final Trade RUNES_SOLD = new Trade(561, false, 1_000, 120, 118_000L);
    private static final Trade WHIP_SOLD = new Trade(4151, false, 1, 1_500_000, 1_470_000L);
    private static final Trade BOND_BOUGHT = new Trade(13190, true, 1, 14_000_000, 14_000_000L);

    /** The rows a sync hands to the matcher as newer than the saved place, by the sync's own steps. */
    private static List<Trade> handedOver(String savedPlace, Trade... rows) {
        List<Trade> historyTrades = Arrays.asList(rows);
        GeHistoryCursorService service = new GeHistoryCursorService();
        GeHistoryCursorService.StoredCursor stored = GeHistoryCursorService.decode(savedPlace);
        List<Trade> listed = stored.listed(historyTrades);
        List<String> linedUp = service.buildCursorSignatures(listed);
        int overlap = service.computeOverlap(linedUp, stored.signatures);
        WipeBaselineDecision.Decision decision = new WipeBaselineDecision()
            .decide(false, linedUp, stored.signatures, listed.size(), overlap);
        assertEquals(WipeBaselineDecision.Outcome.PROCEED, decision.outcome);
        return AutoSyncCoordinator.eligibleTrades(historyTrades, listed, decision.eligibleTradeCount);
    }

    /**
     * Runes sold, then a pickaxe bought, then two more trades, all on a phone since the last
     * sync. The saved place used to be thrown away at the sight of the pickaxe: that sync
     * imported nothing, and all four were below the fresh baseline for good.
     */
    @Test
    public void everyTradeMadeSinceTheLastSyncIsHandedOverThePickaxeAmongThem() {
        List<Trade> rows = handedOver(OLD_PLACE,
            BOND_BOUGHT, WHIP_SOLD, PICKAXE_BOUGHT, RUNES_SOLD, LOGS_SOLD, LAWS_BOUGHT, BOWS_SOLD);

        assertEquals(Arrays.asList(BOND_BOUGHT, WHIP_SOLD, PICKAXE_BOUGHT, RUNES_SOLD), rows);
        assertEquals(4, AutoSyncTradeMatcher.planMissingTrades(rows, Collections.emptyList(),
            new AutoSyncTradeMatcher.LastSync(1_000_000L, Collections.emptyMap())).missingTrades.size());
    }

    /**
     * A pickaxe with nothing new beneath it may have been in the list at the last sync, where
     * the version before passed over it. Had it been bought with the plugin watching, its
     * record is older than that sync and is no longer compared, so the row would be imported
     * beside it: 2,394,000,000 spent twice. It is left out, and the trades above it are not.
     */
    @Test
    public void aPickaxeWithNothingNewBeneathItIsLeftOut() {
        assertEquals(Arrays.asList(BOND_BOUGHT, WHIP_SOLD, RUNES_SOLD), handedOver(OLD_PLACE,
            BOND_BOUGHT, WHIP_SOLD, RUNES_SOLD, PICKAXE_BOUGHT, LOGS_SOLD, LAWS_BOUGHT, BOWS_SOLD));
        assertTrue(handedOver(OLD_PLACE, PICKAXE_BOUGHT, LOGS_SOLD, LAWS_BOUGHT, BOWS_SOLD).isEmpty());
    }

    /** One bought before the last sync sits among the rows that sync saw, and the place is still found. */
    @Test
    public void aPickaxeBoughtBeforeTheLastSyncDoesNotLoseThePlace() {
        assertEquals(Collections.singletonList(RUNES_SOLD), handedOver(OLD_PLACE,
            RUNES_SOLD, LOGS_SOLD, PICKAXE_BOUGHT, LAWS_BOUGHT, BOWS_SOLD));
    }

    /**
     * Why the pickaxe is taken out before the place is looked for. A flipper's list repeats
     * itself: five identical sales, saved by the last sync, and a pickaxe bought among them
     * before it. Lined up with the pickaxe in, the last three sales match the saved place's
     * first three, and the top three rows - all of them there at the last sync - are handed
     * over as new.
     */
    @Test
    public void rowsThatLookAlikeAreNotLinedUpInTheWrongPlace() {
        Trade[] rows = new Trade[6];
        Arrays.fill(rows, BOWS_SOLD);
        rows[2] = PICKAXE_BOUGHT;

        assertTrue(handedOver("v2,855|S|100|58800,855|S|100|58800,855|S|100|58800,855|S|100|58800,855|S|100|58800",
            rows).isEmpty());
    }

    /** Two new trades that look alike are two rows, and the one beneath is not mistaken for the one above. */
    @Test
    public void twoNewTradesThatLookAlikeAreBothHandedOver() {
        Trade again = new Trade(561, false, 1_000, 120, 118_000L);

        assertEquals(Arrays.asList(RUNES_SOLD, PICKAXE_BOUGHT, again), handedOver(OLD_PLACE,
            RUNES_SOLD, PICKAXE_BOUGHT, again, LOGS_SOLD, LAWS_BOUGHT, BOWS_SOLD));
    }

    /** A place this version saved has the pickaxe in it wherever one was, and it is a row like any other. */
    @Test
    public void underThisVersionsOwnPlaceAPickaxeIsARowLikeAnyOther() {
        String place = "v3,1513|S|70000|77000000,563|B|500|60000,855|S|100|58800";

        assertEquals(Collections.singletonList(PICKAXE_BOUGHT),
            handedOver(place, PICKAXE_BOUGHT, LOGS_SOLD, LAWS_BOUGHT, BOWS_SOLD));
        assertEquals(Collections.singletonList(RUNES_SOLD),
            handedOver("v3,1513|S|70000|77000000,20011|B|1|2394000000,563|B|500|60000",
                RUNES_SOLD, LOGS_SOLD, PICKAXE_BOUGHT, LAWS_BOUGHT));
    }

    /**
     * A pickaxe bought on RuneLite since the last sync is handed over like the trades around
     * it, and is known by its record: stored with its coins whole, and newer than that sync.
     */
    @Test
    public void aPickaxeWatchedLiveSinceTheLastSyncIsNotImportedAgain() {
        List<Trade> rows = handedOver(OLD_PLACE,
            BOND_BOUGHT, PICKAXE_BOUGHT, RUNES_SOLD, LOGS_SOLD, LAWS_BOUGHT, BOWS_SOLD);
        Delta watched = new Delta(2_000_000L, 3, 20011, true, 1, 2_394_000_000L, "OFFER_COMPLETED",
            Integer.MAX_VALUE, false, 1_900_000L, 2_000_001L);

        List<Trade> imported = AutoSyncTradeMatcher.planMissingTrades(rows, Collections.singletonList(watched),
            new AutoSyncTradeMatcher.LastSync(1_000_000L, Collections.emptyMap())).missingTrades;

        assertEquals(Arrays.asList(RUNES_SOLD, BOND_BOUGHT), imported);
    }
}
