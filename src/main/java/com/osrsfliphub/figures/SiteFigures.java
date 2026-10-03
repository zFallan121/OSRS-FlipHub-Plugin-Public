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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

/**
 * The website's figures, which a linked plugin shows in place of its own sums.
 *
 * <p>Two computers of one account each added up their own files, and the website a third figure
 * from what both had uploaded: three totals for the same trades. Now the website works out every
 * money figure and each linked plugin shows what it is sent: the Profile tab's total, its item
 * cards, each card's flips and the Merchant level, alike on every computer.
 *
 * <p><b>Until the website says so, nothing changes.</b> It answers {@code {"live": false}} until
 * its switch is on, and the tab then shows this computer's own sums exactly as before, as it
 * does for a player who is not linked, who is never asked about at all. Once an answer says
 * live, no sum of this computer's own is shown for a linked player again until one says it is
 * not: a view not answered yet is dashes, and one whose last question failed is the answer it
 * has, with its time. That the website is live, and each level's last figure, are remembered in
 * RuneLite's config, so a restart shows dashes and then the figures, never the files' own sum.
 *
 * <p><b>When it asks.</b> The view the Profile tab shows, once a minute while it shows it, and a
 * level's own figure once a minute while the level is looked at, Profile tab or none. Again when
 * an upload has been accepted, since the figures have just moved: the view on show, and the level
 * a sale that upload carried is waiting on. But no view twice in twenty seconds: one asked for
 * too lately is asked for when they are up, without waiting for another upload or a redraw. A
 * 429 stops every question for as long as it says, ten minutes at most, or a minute if it does
 * not say: the account's computers share 180 in ten minutes. So does a session the website still
 * refuses after it has been renewed, for a minute. Every question leaves from the IO pool.
 *
 * <p><b>Whose figures.</b> An answer is kept only under the link it was asked under, and
 * everything is forgotten when that changes: an unlink, a new link (perhaps to another website
 * account), a session the website refused, a website wipe, the switch going off. A session
 * renewed is the same link, and its answers stay. One that changes any other way, as when a
 * RuneLite profile linked to another website account is switched to, is another link: its views
 * start empty, and what is remembered in the config is that profile's own.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class SiteFigures {
    private static final String GROUP = FliphubConfigGroups.CONFIG_GROUP;
    /** Whether the website's last answer said its figures are live. Not a setting: nothing shows it. */
    static final String LIVE_KEY = "siteFiguresLive";
    /** Before a character's key, or 0 for every character's: the lifetime profit the website last gave for it. */
    static final String LIFETIME_KEY = "siteFiguresLifetime_";
    private static final long MINUTE_MS = 60_000L;

    private final ApiClient apiClient;
    private final PluginConfig config;
    private final ConfigManager configManager;
    private final LinkSessionGuard link;
    private final RankUp rankUp;

    /** The time, which a test can set: the minute, the twenty seconds and a rate limit's wait all read it. */
    volatile LongSupplier clock = System::currentTimeMillis;
    private final Map<String, View> views = new ConcurrentHashMap<>();
    /** The session {@link #views} were asked under, or the one it has been renewed as. */
    volatile String token;
    /** No question before this: what a 429 asked for. */
    private volatile long waitUntilMs;
    /** When an upload was last accepted. A view asked before it is behind the website. */
    private volatile long uploadedMs;
    /**
     * When that upload's batch left the queue: a sale made by then is in what the website now
     * has. Not by {@link #clock}, as the sales' own times below are not: by the computer's.
     */
    private volatile long carriedMs;
    /** When the one question put off by the twenty seconds is due, for whichever views are behind. */
    private volatile long againMs;
    /**
     * The level sales made on this computer are waiting to be judged on, as the key of its
     * figures, or -1 for none; and when the first of them was made, and the last. A sale is
     * noted, and an answer takes what it has answered, under {@link #sales}: one landing in the
     * middle of the other would be cleared with what was answered.
     */
    private volatile long soldKey = -1L;
    private volatile long soldMs;
    private volatile long soldLastMs;
    /**
     * A lock of its own, held for a few reads and writes: the client thread, where a sale is
     * noted, must never wait on the one an answer is taken in under, which can read the files.
     */
    final Object sales = new Object();

    /** One scope and range as the Profile tab can show it, and what the website last said of it. */
    @RequiredArgsConstructor
    static final class View {
        final long accountKey;
        /** "account", or the tag the character's uploads carry. */
        final String scope;
        /** The range's start as it was last asked for: to the minute, a session's exactly, or null for all time. */
        private volatile Long sinceMs;
        /** The website's last answer, or null while it has given none: the tab shows dashes. */
        volatile ApiClient.FiguresResponse answer;
        /** Whether the last question failed: {@link #answer} is from before it. */
        volatile boolean stale;
        private volatile long askedMs;
        /** Each item's flips as last fetched. Kept on show while they are fetched again under a new answer. */
        final Map<Integer, ApiClient.FlipsResponse> flips = new ConcurrentHashMap<>();
        /** The items whose flips have been asked for since their row in {@link #answer} last changed. */
        private final Set<Integer> flipsAsked = ConcurrentHashMap.newKeySet();
    }

    private interface Question<T> {
        T ask(String sessionToken) throws IOException;
    }

    /** Whether the figures to show are the website's: linked, and its last answer said so. */
    boolean live() {
        return link.isLinked() && Boolean.TRUE.equals(configManager.getConfiguration(GROUP, LIVE_KEY, Boolean.class));
    }

    /**
     * What the Profile tab shows for one character, or for every character under the accountwide
     * key, over a range: the website's view of it, or null for this computer's own sums. Asks the
     * website when its answer is due.
     */
    View shown(long accountKey, StatsRange range, Long sinceMs) {
        if (!link.isLinked()) {
            return null;
        }
        // A range that ends now starts a little later at every look. It is one view all the same,
        // asked for by where it starts at the time, to the minute: were each start a view of its
        // own, the tab would go back to dashes, and shut the card that was open, every minute. A
        // session is the one that began at its start, to the millisecond, and is asked for from
        // that start exactly: the minute it falls in can hold a sale another character made, or
        // one of a session that ended in it, which is no part of this session.
        boolean session = range == StatsRange.SESSION;
        View view = view(accountKey, session ? sinceMs : range);
        ask(view, session || sinceMs == null ? sinceMs : (Long) (sinceMs / MINUTE_MS * MINUTE_MS));
        return live() ? view : null;
    }

    /**
     * The lifetime profit the website last gave for a level's figures, or null while the level is
     * this computer's own to work out: not linked, not live, or live with nothing said of this
     * scope yet. Every answer carries its scope's lifetime, whatever range it was asked for.
     *
     * <p>Every look at the level asks for it, which comes to once a minute at most: the Profile
     * tab may never be opened on this computer, and the figure moves when the account's other
     * computer sells, or when the website's switch goes on or off. Until the website has said it
     * is live there is no figure to ask for, only whether there is one: that is asked of the
     * whole account, whichever level it is, and the answer that says so brings the level's own
     * question with it ({@link RankUp#onFigures}).
     */
    Long lifetime(long accountKey) {
        if (!link.isLinked()) {
            return null;
        }
        boolean live = live();
        ask(view(live ? accountKey : Const.ACCOUNTWIDE_KEY, StatsRange.ALL_TIME), null);
        return live ? configManager.getConfiguration(GROUP, LIFETIME_KEY + accountKey, Long.class) : null;
    }

    /**
     * A sale was made on this computer, under a level's figures. The answer asked for once the
     * upload that carries it is accepted is the one a level-up is celebrated on. Called on the
     * client thread.
     *
     * @param madeMs the time on the sale's own event, given it before it was queued for upload.
     *               Not the time now: the sale is filed after it is queued, which can take long
     *               enough for the upload to have taken it meanwhile, and that upload's answer
     *               is the sale's all the same
     */
    void sold(long accountKey, long madeMs) {
        synchronized (sales) {
            soldLastMs = madeMs;
            // One still waiting keeps its place: the answer that has the first of them is theirs too.
            if (soldKey != accountKey) {
                soldMs = madeMs;
                soldKey = accountKey;
            }
        }
    }

    /**
     * An upload was accepted, so the website's figures have just moved. Called on the IO pool.
     *
     * @param takenMs when its batch left the queue, by the clock a trade's own time is told by:
     *                what was made after that is not in it, however lately it was accepted
     */
    void uploaded(long takenMs) {
        // Before the switch nothing has moved that is shown: the tab goes on asking once a minute.
        if (!live()) {
            return;
        }
        uploadedMs = clock.getAsLong();
        carriedMs = takenMs;
        catchUp();
    }

    /**
     * Asks for what an accepted upload has left behind the website: the level a sale it carried
     * is waiting on, and whatever the tab shows. One asked too lately is put off ({@link #ask}),
     * and this runs again, on the scheduler, when it may be asked.
     */
    private void catchUp() {
        long waiting = soldKey;
        if (waiting >= 0 && carriedMs >= soldMs) {
            ask(view(waiting, StatsRange.ALL_TIME), null);
        }
        redraw();
    }

    /**
     * Forgets everything the website has said, in memory and in the config, and draws the tab
     * again. What it said was of one link, at one moment: none of it may be shown under another.
     */
    synchronized void forget() {
        views.clear();
        // A wait was the last link's to sit out: a new one asks at once.
        waitUntilMs = 0L;
        soldKey = -1L;
        configManager.unsetConfiguration(GROUP, LIVE_KEY);
        for (String key : configManager.getConfigurationKeys(GROUP + "." + LIFETIME_KEY)) {
            configManager.unsetConfiguration(GROUP, key.substring(GROUP.length() + 1));
        }
        redraw();
    }

    /**
     * Asks for an item's flips as its list is drawn open, unless they have been asked for since
     * the item's row last changed: so once, and once more each time an answer moves its count,
     * profit, quantity or last sale. One that fails is asked for again at the next answer.
     * Called on the Swing thread.
     */
    void flips(View view, int itemId) {
        if (clock.getAsLong() < waitUntilMs || !view.flipsAsked.add(itemId)) {
            return;
        }
        io(() -> {
            try {
                ApiClient.FlipsResponse flips =
                    fetch(token -> apiClient.fetchFigureFlips(token, view.scope, itemId, view.sinceMs));
                // One with a flip missing from it is not kept: the list stays as it was.
                if (flips.live && flips.flips != null && !flips.flips.contains(null) && views.containsValue(view)) {
                    view.flips.put(itemId, flips);
                    redraw();
                }
            } catch (IOException | RuntimeException ex) {
                // The list stays as it was.
            }
        });
    }

    /**
     * A kind as the tab's filters know it: null for a flip. Stock moved from another character
     * and sold is a flip too; the tab has no filter for it.
     */
    static ConversionKind kind(String raw) {
        ConversionKind kind = ConversionKind.parse(raw);
        return kind == ConversionKind.TRANSFER ? null : kind;
    }

    private View view(long accountKey, Object range) {
        String scope = accountKey > 0 ? GeEvent.characterId(accountKey) : "account";
        // Another session's views are another link's, however the session came to change: a
        // RuneLite profile linked to another website account has been switched to, say. All but
        // a session renewed, which is the same link's and is told here as it is stored.
        String now = config.sessionToken();
        if (!now.equals(token)) {
            views.clear();
            token = now;
        }
        return views.computeIfAbsent(scope + range, key -> new View(accountKey, scope));
    }

    private synchronized void ask(View view, Long since) {
        long now = clock.getAsLong();
        // Once a minute; sooner when an upload was accepted since, but not twice in twenty seconds.
        boolean behind = view.askedMs < uploadedMs;
        long due = Math.max(waitUntilMs, view.askedMs + (behind ? MINUTE_MS / 3 : MINUTE_MS));
        if (now < due) {
            // Behind the website and too soon to ask. Nothing else need ever ask again: no more
            // uploads, no redraw. So it is asked when it may be, by one task for every such view.
            ScheduledExecutorService scheduler = Access.plugin().scheduler;
            if (behind && againMs <= now && scheduler != null) {
                againMs = due;
                try {
                    scheduler.schedule(() -> {
                        againMs = 0L;
                        catchUp();
                    }, due - now, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException stopping) {
                    // On its way down: nothing more is asked.
                }
            }
            return;
        }
        long last = view.askedMs;
        long carried = carriedMs;
        view.askedMs = now;
        view.sinceMs = since;
        // Every login leaves a session's view behind, and each holds a whole answer.
        views.values().removeIf(old -> now - old.askedMs > 10 * MINUTE_MS);
        boolean taken = io(() -> {
            ApiClient.FiguresResponse answer;
            try {
                answer = fetch(token -> apiClient.fetchFigures(token, view.scope, since));
                if (answer.live) {
                    // Live, and not all of it came: no answer at all, so the last one stays, with
                    // its time. One with no items, or no kinds, fails here and below by itself.
                    if (answer.summary == null || answer.by_kind.containsValue(null)) {
                        throw new IOException("Incomplete answer");
                    }
                    for (StatsItem item : answer.items) {
                        if (item.kinds == null) {
                            // Sent without its kinds: a plain flip.
                            item.hasPlainFlip = true;
                            continue;
                        }
                        for (String raw : item.kinds) {
                            ConversionKind kind = kind(raw);
                            if (kind == null) {
                                item.hasPlainFlip = true;
                            } else {
                                item.conversionKinds.add(kind);
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException ex) {
                // Out of reach, refused, or not an answer that can be read: a question that failed.
                answer = null;
            }
            answered(view, answer, carried);
        });
        if (!taken) {
            // Not asked after all: the pools are not up yet, or are on their way down.
            view.askedMs = last;
        }
    }

    /**
     * Asks under the session in use. One the website refuses is renewed the way the uploads renew
     * it, and the question asked again; the link is never thrown away here.
     */
    private <T> T fetch(Question<T> question) throws IOException {
        String token = config.sessionToken();
        T answer;
        try {
            try {
                answer = question.ask(token);
            } catch (ApiClient.ApiException ex) {
                if (!ApiStatusPolicy.isAuthStatus(ex.statusCode)
                    || SessionRefresh.refreshOrUnavailable(token) != SessionRefresh.Outcome.REFRESHED) {
                    throw ex;
                }
                token = config.sessionToken();
                answer = question.ask(token);
            }
        } catch (ApiClient.ApiException ex) {
            // Asked too much, the first time or under the new session. One that does not say
            // how long to wait is a minute: no wait at all would be asking too much again. Ten
            // minutes at most, whatever it says: that is as long as the website counts over.
            // A session still refused after its one renewal waits too: every other view would
            // be asked for in its turn, and the session renewed again for each.
            // Never a shorter wait than one already begun: a refused session's minute can come
            // in, from the pool's other thread, a moment after a 429 that asked for longer.
            if (ex.statusCode == 429 || ApiStatusPolicy.isAuthStatus(ex.statusCode)) {
                waitUntilMs = Math.max(waitUntilMs, clock.getAsLong()
                    + Math.min(600, ex.retryAfterSeconds > 0 ? ex.retryAfterSeconds : 60) * 1000L);
            }
            throw ex;
        }
        // Not if a new link was made while it was on its way: this is another account's answer.
        if (answer == null || !token.equals(config.sessionToken())) {
            throw new IOException("No answer for this link");
        }
        return answer;
    }

    /**
     * @param carried when the batch of the last upload accepted before the question was asked
     *                left the queue: a sale made by then is in the answer
     */
    private synchronized void answered(View view, ApiClient.FiguresResponse answer, long carried) {
        // Forgotten while the question was on its way: the answer is from before whatever forgot it.
        if (!views.containsValue(view)) {
            return;
        }
        if (answer == null) {
            view.stale = true;
        } else if (!answer.live) {
            // Before the switch, every minute: nothing to draw. After a rollback: this computer's own sums again.
            if (live()) {
                forget();
            }
            return;
        } else {
            String lifetimeKey = LIFETIME_KEY + view.accountKey;
            Long was = configManager.getConfiguration(GROUP, lifetimeKey, Long.class);
            configManager.setConfiguration(GROUP, LIVE_KEY, true);
            configManager.setConfiguration(GROUP, lifetimeKey, answer.lifetime_profit_gp);
            // An item's flips are asked for again when its row has changed, or when they never came.
            ApiClient.FiguresResponse old = view.answer;
            view.flipsAsked.removeIf(id -> !view.flips.containsKey(id) || !row(answer, id).equals(row(old, id)));
            view.answer = answer;
            view.stale = false;
            // A sale's own answer: asked after the upload that carried it was accepted, whichever
            // batch that was. One accepted meanwhile that left the queue before the sale is not it.
            boolean sold;
            synchronized (sales) {
                sold = view.accountKey == soldKey && carried >= soldMs;
                if (sold && carried < soldLastMs) {
                    // A sale made since is still to come.
                    soldMs = soldLastMs;
                } else if (sold) {
                    soldKey = -1L;
                }
            }
            // Drawn whatever becomes of the level: the answer is taken in, and the tab would
            // otherwise show the one before it until the next minute's refresh.
            try {
                rankUp.onFigures(view.accountKey, was != null ? was : answer.lifetime_profit_gp,
                    answer.lifetime_profit_gp, sold);
            } finally {
                redraw();
            }
            return;
        }
        redraw();
    }

    /** What an item's flips add up to in an answer: its count, profit, quantity and last sale. */
    private static String row(ApiClient.FiguresResponse answer, int itemId) {
        for (StatsItem item : answer.items) {
            if (item.item_id == itemId) {
                return item.fill_count + "," + item.total_profit_gp + "," + item.total_qty + "," + item.last_sell_ts_ms;
            }
        }
        return "";
    }

    /**
     * The network is the IO pool's alone: never the scheduler, which runs the offer poll four
     * times a second, and never the thread that draws the panel.
     *
     * @return whether the pool took the work
     */
    private static boolean io(Runnable work) {
        ExecutorService pool = Access.plugin().ioExecutor;
        if (pool == null) {
            return false;
        }
        try {
            pool.execute(work);
            return true;
        } catch (RejectedExecutionException stopping) {
            return false;
        }
    }

    private static void redraw() {
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.getPanelRefreshCoordinator().triggerStatsRefresh(plugin.scheduler);
    }
}
