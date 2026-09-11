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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A repair fee is what this player would have paid, not what the table says.
 *
 * <p>The table carries the NPC price because it is the only one that is the
 * same for everybody. An armour stand takes half a percent off it per Smithing
 * level, and at 99 that is the difference between a Karil's top flip reading as
 * 17k of profit and reading as the 62k it was.
 */
public class ConversionRepairFeeTest {
    private static final int KARILS_TOP = 4736;
    private static final int KARILS_TOP_0 = 4956;
    private static final int VOIDWAKER = 27690;
    private static final int VOIDWAKER_BLADE = 27681;
    private static final long ACCOUNT = 1_001L;
    private static final long OTHER_ACCOUNT = 1_002L;

    private static PluginConfig config(boolean armourStand) {
        return new PluginConfig() {
            @Override
            public boolean repairAtArmourStand() {
                return armourStand;
            }
        };
    }

    private static ConversionFeeService feeService(boolean armourStand, int smithing) {
        ConversionFeeService service = new ConversionFeeService(config(armourStand));
        service.onSmithingLevel(ACCOUNT, smithing);
        return service;
    }

    private static ConversionRecipe karilsRepair() {
        return new ConversionRecipe(
            ConversionKind.REPAIR,
            "Karil's leathertop",
            Collections.singletonList(new ConversionItem(KARILS_TOP_0, 1)),
            Collections.singletonList(new ConversionItem(KARILS_TOP, 1)),
            90_000L);
    }

    private static ConversionRecipe voidwaker() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Voidwaker",
            Collections.singletonList(new ConversionItem(VOIDWAKER_BLADE, 1)),
            Collections.singletonList(new ConversionItem(VOIDWAKER, 1)),
            500_000L);
    }

    private static LocalTradeDelta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new LocalTradeDelta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private static LocalTradeDelta sell(long tsMs, int slot, int itemId, int qty, long gp, int unitPrice) {
        return new LocalTradeDelta(tsMs, slot, itemId, false, qty, gp, "OFFER_COMPLETED", unitPrice, false);
    }

    @Test
    public void anArmourStandTakesHalfAPercentOffPerSmithingLevel() {
        // 90,000 x (1 - 99/200). Every two levels is one percent, so 99 is 49.5%
        // off and not half - the odd level is the whole reason to use the formula
        // rather than a flat "half price at 99".
        assertEquals(45_450L, feeService(true, 99).feeFor(karilsRepair(), 1, ACCOUNT));
        assertEquals(67_500L, feeService(true, 50).feeFor(karilsRepair(), 1, ACCOUNT));
        assertEquals(89_550L, feeService(true, 1).feeFor(karilsRepair(), 1, ACCOUNT));
    }

    @Test
    public void withoutAnArmourStandTheNpcPriceStands() {
        assertEquals(90_000L, feeService(false, 99).feeFor(karilsRepair(), 1, ACCOUNT));
    }

    @Test
    public void anUnknownSmithingLevelPaysTheNpcPrice() {
        // Nothing has reported a level yet. The NPC price is the one that cannot
        // flatter the flip, so it is what an unknown falls back to.
        assertEquals(90_000L, new ConversionFeeService(config(true)).feeFor(karilsRepair(), 1, ACCOUNT));
    }

    @Test
    public void anAssemblyFeeIsNotARepairAndIsNeverDiscounted() {
        // 500,000 to an NPC to put a Voidwaker together. There is no armour stand
        // for that job and no Smithing level that makes it cheaper.
        assertEquals(500_000L, feeService(true, 99).feeFor(voidwaker(), 1, ACCOUNT));
    }

    @Test
    public void theFeeScalesWithTheNumberOfRuns() {
        assertEquals(90_900L, feeService(true, 99).feeFor(karilsRepair(), 2, ACCOUNT));
    }

    @Test
    public void theLevelBelongsToTheAccountThatReportedIt() {
        // Two characters on one client. Viewing the second's profile while
        // logged in as the first must not price the second's repairs with the
        // first's Smithing: its level is unknown, and unknown is NPC price.
        ConversionFeeService service = feeService(true, 99);

        assertEquals(45_450L, service.feeFor(karilsRepair(), 1, ACCOUNT));
        assertEquals(90_000L, service.feeFor(karilsRepair(), 1, OTHER_ACCOUNT));
    }

    @Test
    public void aLogoutForgetsEveryLevel() {
        ConversionFeeService service = feeService(true, 99);

        assertTrue(service.clearSmithingLevels());

        assertEquals(90_000L, service.feeFor(karilsRepair(), 1, ACCOUNT));
        assertFalse("nothing left to forget", service.clearSmithingLevels());
    }

    @Test
    public void onlyAChangedLevelAsksForTheCachesToBeRebuilt() {
        // The client reports every skill at login and again on every world
        // hop, and a change throws every aggregate away - so a report that
        // changes nothing has to say so.
        ConversionFeeService service = new ConversionFeeService(config(true));

        assertTrue(service.onSmithingLevel(ACCOUNT, 99));
        assertFalse(service.onSmithingLevel(ACCOUNT, 99));
        assertTrue(service.onSmithingLevel(ACCOUNT, 98));
        assertFalse("no level is not a level", service.onSmithingLevel(ACCOUNT, 0));
        assertEquals(98, service.smithingLevel(ACCOUNT));
    }

    @Test
    public void aLevelReportedBeforeTheAccountIsKnownWaitsForIt() {
        // At login the stat packets can land while the client is still
        // loading, before it can say whose they are. The level is kept until
        // the account is known and attached then, so a whole session is not
        // priced at NPC rates over a race at login.
        ConversionFeeService service = new ConversionFeeService(config(true));

        assertFalse(service.onSmithingLevel(-1L, 99));
        assertEquals(0, service.smithingLevel(ACCOUNT));

        assertTrue(service.adoptPendingSmithingLevel(ACCOUNT));
        assertEquals(99, service.smithingLevel(ACCOUNT));
        assertFalse("adopted once", service.adoptPendingSmithingLevel(OTHER_ACCOUNT));
        assertEquals(0, service.smithingLevel(OTHER_ACCOUNT));
    }

    @Test
    public void aLogoutForgetsAPendingLevelToo() {
        ConversionFeeService service = new ConversionFeeService(config(true));
        service.onSmithingLevel(-1L, 99);

        assertTrue(service.clearSmithingLevels());

        assertFalse(service.adoptPendingSmithingLevel(ACCOUNT));
        assertEquals(0, service.smithingLevel(ACCOUNT));
    }

    @Test
    public void bothLedgersPriceTheAccountTheyAreBuiltForNotTheOneLoggedIn() {
        ConversionLedger ledger = new ConversionLedger(
            new ConversionRecipeIndex(Collections.singletonList(karilsRepair())),
            feeService(true, 99));
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, KARILS_TOP_0, 1, 721_118L),
            sell(2_000L, 2, KARILS_TOP, 1, 828_582L, 845_441)
        );

        LocalFlipHistoryService history = new LocalFlipHistoryService(ledger);
        assertEquals(721_118L + 45_450L,
            history.buildHistory(deltas, null, ACCOUNT).get(KARILS_TOP).get(0).buyCostGp);
        assertEquals(721_118L + 90_000L,
            history.buildHistory(deltas, null, OTHER_ACCOUNT).get(KARILS_TOP).get(0).buyCostGp);

        LocalStatsCache own = new LocalStatsCache(ledger, ACCOUNT);
        own.rebuild(deltas);
        assertEquals(721_118L + 45_450L, (long) own.getSummary().total_cost_gp);
        LocalStatsCache other = new LocalStatsCache(ledger, OTHER_ACCOUNT);
        other.rebuild(deltas);
        assertEquals(721_118L + 90_000L, (long) other.getSummary().total_cost_gp);
    }

    @Test
    public void aRepairedFlipIsPricedAtWhatTheRepairCostThisPlayer() {
        // The real trade: a broken top bought for 721,118 and the repaired one
        // sold for 828,582 net of tax. At 99 Smithing on a stand the repair was
        // 45,450, not the 90,000 the table carries.
        LocalFlipHistoryService service = new LocalFlipHistoryService(new ConversionLedger(
            new ConversionRecipeIndex(Collections.singletonList(karilsRepair())),
            feeService(true, 99)));
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, KARILS_TOP_0, 1, 721_118L),
            sell(2_000L, 2, KARILS_TOP, 1, 828_582L, 845_441)
        );

        StatsFlipInstance activity = service.buildHistory(deltas, null, ACCOUNT).get(KARILS_TOP).get(0);

        assertEquals(ConversionKind.REPAIR, activity.conversionKind);
        assertEquals(721_118L + 45_450L, activity.buyCostGp);
        assertEquals(62_014L, activity.profitGp);
    }
}
