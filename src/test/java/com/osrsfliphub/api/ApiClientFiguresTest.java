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

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.Okio;
import okio.Source;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The two questions the plugin asks the website about its figures, and what it reads of the
 * answers: {@code GET /api/plugin/figures} and {@code GET /api/plugin/figures/flips}.
 *
 * <p>Every answer here is one the real website gave ({@link FigureFixtures}).
 */
public class ApiClientFiguresTest {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String MAINS_TAG = GeEvent.characterId(4242L);

    /** Every request that reached the website. */
    private final List<Request> sent = new ArrayList<>();

    // ---- the question ----

    /** The whole account, all time: one GET, the session token in a header, no body and so nothing signed. */
    @Test
    public void theAccountsFiguresAreAskedForWithOneGet() throws Exception {
        website(live("account_all_time")).fetchFigures("token", "account", null);

        assertEquals(1, sent.size());
        Request request = sent.get(0);
        assertEquals("GET", request.method());
        assertEquals("/api/plugin/figures", request.url().encodedPath());
        assertEquals("all time is no since_ms at all", names("scope"), request.url().queryParameterNames());
        assertEquals("account", request.url().queryParameter("scope"));
        assertEquals("token", request.header("X-Plugin-Token"));
        assertNull("a GET has no body", request.body());
        assertNull("and so nothing to sign", request.header("X-Signature"));
    }

    /** A range is its start, in milliseconds. */
    @Test
    public void aRangeIsAskedForByItsStart() throws Exception {
        website(live("account_since_40000")).fetchFigures("token", "account", 40_000L);

        Request request = sent.get(0);
        assertEquals(names("scope", "since_ms"), request.url().queryParameterNames());
        assertEquals("account", request.url().queryParameter("scope"));
        assertEquals("40000", request.url().queryParameter("since_ms"));
    }

    /** One character is its tag, the code its uploads carry. */
    @Test
    public void oneCharacterIsAskedForByItsTag() throws Exception {
        website(live("main_all_time")).fetchFigures("token", MAINS_TAG, null);

        Request request = sent.get(0);
        assertEquals("/api/plugin/figures", request.url().encodedPath());
        assertEquals(names("scope"), request.url().queryParameterNames());
        assertEquals(MAINS_TAG, request.url().queryParameter("scope"));
    }

    /** One item's flips are asked for on a path of their own, by the item's id. */
    @Test
    public void oneItemsFlipsAreAskedForOnTheFlipsPath() throws Exception {
        ApiClient client = website(live("flips_whip_account"));

        client.fetchFigureFlips("token", "account", 4151, null);
        client.fetchFigureFlips("token", MAINS_TAG, 4151, 40_000L);

        Request allTime = sent.get(0);
        assertEquals("GET", allTime.method());
        assertEquals("/api/plugin/figures/flips", allTime.url().encodedPath());
        assertEquals(names("scope", "item_id"), allTime.url().queryParameterNames());
        assertEquals("account", allTime.url().queryParameter("scope"));
        assertEquals("4151", allTime.url().queryParameter("item_id"));
        assertEquals("token", allTime.header("X-Plugin-Token"));
        assertNull(allTime.body());
        Request ranged = sent.get(1);
        assertEquals(names("scope", "item_id", "since_ms"), ranged.url().queryParameterNames());
        assertEquals(MAINS_TAG, ranged.url().queryParameter("scope"));
        assertEquals("40000", ranged.url().queryParameter("since_ms"));
    }

    /**
     * RuneLite's HTTP client can keep a GET's answer and give it again, and it files one under
     * its address alone: the same for every account, the session being a header. Both questions
     * say that theirs is never to be kept, so a new link is never answered with the last link's.
     */
    @Test
    public void neitherQuestionsAnswerMayBeKeptByTheHttpClient() throws Exception {
        ApiClient client = website(live("account_all_time"));

        client.fetchFigures("token", "account", null);
        client.fetchFigureFlips("token", "account", 4151, 40_000L);

        assertEquals(2, sent.size());
        for (Request request : sent) {
            assertEquals(request.url().encodedPath(), "no-store", request.header("Cache-Control"));
        }
    }

    /** While "Sync flips to my FlipHub account" is off, nothing leaves the computer: these two are no exception. */
    @Test
    public void nothingIsAskedWhileSyncIsOff() {
        ApiClient client = client(new RecordSyncWorld.Reply(200, live("account_all_time").body), new PluginConfig() {
        });

        assertThrows(IllegalStateException.class, () -> client.fetchFigures("token", "account", null));
        assertThrows(IllegalStateException.class, () -> client.fetchFigureFlips("token", "account", 4151, null));

        assertTrue(sent.isEmpty());
    }

    // ---- the answer ----

    /** The account's figures, to the coin: 402,078 from six flips, split by kind, five items, no untagged block. */
    @Test
    public void theAccountsFiguresAreReadToTheCoin() throws Exception {
        ApiClient.FiguresResponse figures = website(live("account_all_time")).fetchFigures("token", "account", null);

        assertTrue(figures.live);
        assertEquals(FigureFixtures.AS_OF_MS, (long) figures.as_of_ms);
        assertEquals(402_078L, (long) figures.lifetime_profit_gp);
        assertEquals(402_078L, (long) figures.summary.total_profit_gp);
        assertEquals(4_527_502L, (long) figures.summary.total_cost_gp);
        assertEquals(8.88, figures.summary.roi_percent, 0.0001);
        assertEquals(6, (int) figures.summary.fill_count);
        assertEquals(118L, (long) figures.summary.total_qty);
        assertEquals(100_589L, (long) figures.summary.tax_paid_gp);
        assertEquals(1_000L, (long) figures.summary.first_buy_ts_ms);
        assertEquals(60_000L, (long) figures.summary.last_sell_ts_ms);

        assertEquals(new HashSet<>(Arrays.asList("ASSEMBLE", "FLIP", "REPAIR")), figures.by_kind.keySet());
        assertKind(figures.by_kind.get("ASSEMBLE"), 400_000L, 4_500_000L, 1L, 1);
        assertKind(figures.by_kind.get("FLIP"), 1_988L, 26_602L, 116L, 4);
        assertKind(figures.by_kind.get("REPAIR"), 90L, 900L, 1L, 1);

        assertEquals(5, figures.items.size());
        assertEquals("the whip, the bones and the runes were flipped; the set was assembled, the shark repaired",
            Arrays.asList("12877 [ASSEMBLE]", "4151 [FLIP]", "536 [FLIP]", "561 [FLIP]", "385 [REPAIR]"), kinds(figures.items));
        StatsItem whip = figures.items.get(1);
        assertEquals(1_208L, (long) whip.total_profit_gp);
        assertEquals(6_202L, (long) whip.total_cost_gp);
        assertEquals(19.48, whip.roi_percent, 0.0001);
        assertEquals(6, (int) whip.total_qty);
        assertEquals("one flip is one sell offer: MAIN's and ALT's", 2, (int) whip.fill_count);
        assertEquals(50_000L, (long) whip.last_sell_ts_ms);

        assertNull("the whole account has no untagged block: those sales are in its total", figures.untagged);
    }

    /** One character's figures come with the sales no character can be given, beside them and not in them. */
    @Test
    public void oneCharactersFiguresComeWithTheSalesNoCharacterCanBeGiven() throws Exception {
        ApiClient.FiguresResponse figures = website(live("main_all_time")).fetchFigures("token", MAINS_TAG, null);

        assertTrue(figures.live);
        assertEquals(401_653L, (long) figures.summary.total_profit_gp);
        assertEquals(3, (int) figures.summary.fill_count);
        assertEquals("the level reads this, and the untagged 190 is not in it", 401_653L, (long) figures.lifetime_profit_gp);
        assertEquals(190L, (long) figures.untagged.total_profit_gp);
        assertEquals(2, (int) figures.untagged.fill_count);
        assertEquals(1_300L, (long) figures.untagged.total_cost_gp);
        assertEquals(101L, (long) figures.untagged.total_qty);
        assertEquals(20L, (long) figures.untagged.tax_paid_gp);
        assertEquals(3, figures.items.size());
    }

    /** A range with no untagged sale in it has no time to give for them: a null, read as no time. */
    @Test
    public void aTimeTheWebsiteHasNoneOfIsReadAsNoTime() throws Exception {
        ApiClient.FiguresResponse figures =
            website(live("main_since_40000")).fetchFigures("token", MAINS_TAG, 40_000L);

        assertEquals(401_653L, (long) figures.summary.total_profit_gp);
        assertEquals(10_000L, (long) figures.summary.first_buy_ts_ms);
        assertEquals(0, (int) figures.untagged.fill_count);
        assertEquals(0L, (long) figures.untagged.total_profit_gp);
        assertNull(figures.untagged.first_buy_ts_ms);
        assertNull(figures.untagged.last_sell_ts_ms);
    }

    /** Before the switch the website says {@code {"live": false}} and nothing else, to both questions. */
    @Test
    public void beforeTheSwitchTheAnswerIsNotLiveAndHoldsNothing() throws Exception {
        ApiClient.FiguresResponse figures =
            website(new RecordSyncWorld.Reply(200, FigureFixtures.off("figures_off"))).fetchFigures("token", "account", null);
        ApiClient.FlipsResponse flips =
            website(new RecordSyncWorld.Reply(200, FigureFixtures.off("flips_off"))).fetchFigureFlips("token", "account", 4151, null);

        assertFalse(figures.live);
        assertNull(figures.summary);
        assertNull(figures.items);
        assertNull(figures.by_kind);
        assertFalse(flips.live);
        assertNull(flips.flips);
    }

    /** The whip's two flips, newest first: five for +973, then one for +235. A flip has no name of the player's. */
    @Test
    public void oneItemsFlipsAreReadNewestFirst() throws Exception {
        ApiClient.FlipsResponse flips = website(live("flips_whip_account")).fetchFigureFlips("token", "account", 4151, null);

        assertTrue(flips.live);
        assertFalse("fewer than a thousand: these are all of them", flips.more);
        assertEquals(2, flips.flips.size());
        ApiClient.FigureFlip newest = flips.flips.get(0);
        assertEquals(973L, (long) newest.profit_gp);
        assertEquals(5, (int) newest.qty);
        assertEquals("5,202 over five, rounded down", 1_040L, (long) newest.buy_price_gp);
        assertEquals("what was kept after tax, each", 1_235L, (long) newest.sell_price_gp);
        assertEquals(5_202L, (long) newest.buy_cost_gp);
        assertEquals(6_175L, (long) newest.sell_revenue_gp);
        assertEquals(125L, (long) newest.tax_gp);
        assertEquals(50_000L, (long) newest.completed_ms);
        assertEquals("FLIP", newest.kind);
        assertNull(newest.name);
        ApiClient.FigureFlip older = flips.flips.get(1);
        assertEquals(235L, (long) older.profit_gp);
        assertEquals(1, (int) older.qty);
        assertEquals(1_000L, (long) older.buy_price_gp);
        assertEquals(45_000L, (long) older.completed_ms);
    }

    /**
     * Grand Exchange prices can pass 2,147,483,647, and a total passes it long before a price does:
     * every gold figure the website sends is read into a long.
     */
    @Test
    public void everyGoldFigureIsReadIntoALong() {
        for (Class<?> answer : new Class<?>[] {ApiClient.FiguresResponse.class, ApiClient.KindSlice.class,
            ApiClient.FigureFlip.class}) {
            int gold = 0;
            for (Field field : answer.getFields()) {
                if (field.getName().endsWith("_gp")) {
                    gold++;
                    assertTrue(answer.getSimpleName() + "." + field.getName() + " is " + field.getType().getSimpleName(),
                        field.getType() == long.class || field.getType() == Long.class);
                }
            }
            assertTrue(answer.getSimpleName() + " has gold figures", gold > 0);
        }
    }

    /**
     * The largest answer there is comes to under a megabyte. Whatever the website sends, no more
     * than 2,000,000 bytes of it are read: here it has 20 MB to give. One cut short there cannot
     * be read, and is no answer.
     */
    @Test
    public void noMoreThanTwoMillionBytesOfAnAnswerAreRead() {
        long[] given = new long[1];
        ApiClient client = websiteWithTwentyMegabytes(200, given);

        assertThrows(RuntimeException.class, () -> client.fetchFigures("token", "account", null));

        assertTrue("it read " + given[0] + " bytes", given[0] >= 2_000_000L && given[0] <= 2_100_000L);
    }

    /** Nor of a refusal, which is read for how long to wait when no header says: cut short, it does not say. */
    @Test
    public void noMoreThanTwoMillionBytesOfARefusalAreRead() {
        long[] given = new long[1];
        ApiClient client = websiteWithTwentyMegabytes(429, given);

        ApiClient.ApiException refused =
            assertThrows(ApiClient.ApiException.class, () -> client.fetchFigureFlips("token", "account", 4151, null));

        assertEquals(429, refused.statusCode);
        assertEquals("the caller picks a wait of its own", 0L, refused.retryAfterSeconds);
        assertTrue("it read " + given[0] + " bytes", given[0] >= 2_000_000L && given[0] <= 2_100_000L);
    }

    // ---- refusals ----

    /** Asked too much: the 429 says how many seconds to wait, and the caller is told. */
    @Test
    public void aRateLimitSaysHowLongToWait() {
        ApiClient client = website(FigureFixtures.rateLimited());

        ApiClient.ApiException figures =
            assertThrows(ApiClient.ApiException.class, () -> client.fetchFigures("token", "account", null));
        ApiClient.ApiException flips =
            assertThrows(ApiClient.ApiException.class, () -> client.fetchFigureFlips("token", "account", 4151, null));

        assertEquals(429, figures.statusCode);
        assertEquals(37L, (long) figures.retryAfterSeconds);
        assertEquals(429, flips.statusCode);
        assertEquals(37L, (long) flips.retryAfterSeconds);
    }

    /** A session the website does not know, and a question it will not read, are told apart by their codes. */
    @Test
    public void aRefusedSessionAndARefusedQuestionCarryTheirCodes() {
        ApiClient.ApiException session = assertThrows(ApiClient.ApiException.class,
            () -> website(FigureFixtures.refusal("no_token")).fetchFigures("token", "account", null));
        ApiClient.ApiException question = assertThrows(ApiClient.ApiException.class,
            () -> website(FigureFixtures.refusal("bad_since")).fetchFigures("token", "account", 40_000L));

        assertEquals(401, session.statusCode);
        assertEquals(400, question.statusCode);
    }

    // ---- helpers ----

    private static RecordSyncWorld.Reply live(String name) {
        return new RecordSyncWorld.Reply(200, FigureFixtures.live(name));
    }

    /** A client with sync on, whose every request the website answers with this. */
    private ApiClient website(RecordSyncWorld.Reply reply) {
        return client(reply, new PluginConfig() {
            @Override
            public boolean enableFlipHubSync() {
                return true;
            }
        });
    }

    private ApiClient client(RecordSyncWorld.Reply reply, PluginConfig config) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            sent.add(request);
            Response.Builder response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(reply.status).message("from the website").body(ResponseBody.create(JSON, reply.body));
            if (reply.retryAfter != null) {
                response.header("Retry-After", reply.retryAfter);
            }
            return response.build();
        }).build();
        return new ApiClient(http, new Gson(), config);
    }

    /**
     * A client with sync on, whose every request the website answers with this status and 20 MB
     * of JSON that never ends, handed over as it is read: {@code given} counts what was taken.
     */
    private static ApiClient websiteWithTwentyMegabytes(int status, long[] given) {
        Source endless = new Source() {
            @Override
            public long read(Buffer sink, long byteCount) {
                if (given[0] >= 20_000_000L) {
                    return -1L;
                }
                byte[] more = given[0] == 0 ? "{\"live\":false,\"pad\":\"".getBytes(StandardCharsets.UTF_8)
                    : new byte[(int) Math.min(byteCount, 8_192L)];
                if (given[0] > 0) {
                    Arrays.fill(more, (byte) 'a');
                }
                sink.write(more);
                given[0] += more.length;
                return more.length;
            }

            @Override
            public okio.Timeout timeout() {
                return okio.Timeout.NONE;
            }

            @Override
            public void close() {
            }
        };
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
            .request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("from the website")
            .body(ResponseBody.create(JSON, -1L, Okio.buffer(endless))).build()).build();
        return new ApiClient(http, new Gson(), new PluginConfig() {
            @Override
            public boolean enableFlipHubSync() {
                return true;
            }
        });
    }

    private static HashSet<String> names(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static void assertKind(ApiClient.KindSlice kind, long profitGp, long costGp, long qty, int flips) {
        assertEquals(profitGp, (long) kind.profit_gp);
        assertEquals(costGp, (long) kind.cost_gp);
        assertEquals(qty, (long) kind.qty);
        assertEquals(flips, (int) kind.count);
    }

    private static List<String> kinds(List<StatsItem> items) {
        List<String> out = new ArrayList<>();
        for (StatsItem item : items) {
            out.add(item.item_id + " " + item.kinds);
        }
        return out;
    }
}
