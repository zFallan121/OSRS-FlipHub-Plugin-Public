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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The update that moves every player's trades from {@code .runelite/fliphub} to RuneLite's folder
 * for the plugin, {@code .runelite/plugin-data/osrs-fliphub}.
 *
 * <p>RuneLite makes the move itself, as one rename, when the plugin asks for its folder. While
 * another RuneLite window of the build before this one is open, Windows refuses the rename: that
 * window watches the old folder. The updated window then has nowhere to read or save, and until
 * the other window is closed it is as good as switched off: it records nothing, changes nothing,
 * and tells the player why. It asks again every two seconds, and the moment the folder is there it
 * starts, once, exactly as if it had just been switched on.
 *
 * <p>The trap is reading "no folder yet" as "this player has no history": a window that loaded
 * nothing, recorded 4 fills and then saved them over a file of 5,000.
 *
 * <p>The plugin here is the real one over RuneLite's own services faked just far enough
 * ({@link RecordSyncWorld}), with the trade files built by Guice as the client builds them. Two
 * things are stood in for. RuneLite's folder is {@link #askRuneLite}, which does to a folder of
 * this test's what RuneLite does to the player's. And the start itself is {@link #flipHubStarts},
 * because the real one builds the side panel, the skills tab and the schedulers, which need more of
 * RuneLite than a test has.
 */
public class WaitingForTheFolderTest {
    private static final long MAIN = 101L;
    private static final long ALT = 202L;
    private static final long ACCOUNTWIDE = Const.ACCOUNTWIDE_KEY;
    private static final String CLOSE_THE_OTHER_WINDOW = "FlipHub: close your other RuneLite windows to finish"
        + " its update. Nothing is recorded in this window until then.";
    /** How often the plugin asks RuneLite again here; two seconds in the client. */
    private static final long RETRY_MS = 25L;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final Gson gson = new Gson();
    private Path runelite;
    /** Where the build before this one kept everything: {@code .runelite/fliphub}. */
    private Path oldFolder;
    /** Where RuneLite puts it: {@code .runelite/plugin-data/osrs-fliphub}. */
    private Path newFolder;
    private RecordSyncWorld world;
    private GeLifecyclePlugin plugin;

    /** Another RuneLite window of the build before this one is open, watching the old folder. */
    private volatile boolean otherWindowOpen;
    /** Every thread the plugin has asked RuneLite for its folder on, in order. */
    private final List<Thread> askedOn = new CopyOnWriteArrayList<>();
    /** Every thread FlipHub has been started on, in order. */
    private final List<Thread> startedOn = new CopyOnWriteArrayList<>();
    private final AtomicInteger starts = new AtomicInteger();
    /** What the plugin writes to RuneLite's log, in a test that reads it. */
    private ListAppender<ILoggingEvent> logged;

    @Before
    public void setUp() throws Exception {
        runelite = temp.getRoot().toPath().resolve(".runelite");
        oldFolder = runelite.resolve("fliphub");
        newFolder = runelite.resolve("plugin-data").resolve("osrs-fliphub");
        // RuneLite makes this one itself, whatever becomes of the move.
        Files.createDirectories(newFolder.getParent());
        world = new RecordSyncWorld(newFolder, RecordSyncWorld.workingConfig(), false);
        // Not linked: nothing here is about the website, and nothing may be sent to it.
        world.linked = false;
        plugin = world.plugin;
        plugin.gson = gson;
        plugin.configManager = world.configManager;
        // Nothing of FlipHub is running yet: the pool for file work is made by its start.
        plugin.ioExecutor = null;
        plugin.folderRetryMs = RETRY_MS;
        plugin.folderSource = this::askRuneLite;
        plugin.start = this::flipHubStarts;
    }

    @After
    public void tearDown() throws Exception {
        if (logged != null) {
            ((ch.qos.logback.classic.Logger) GeLifecyclePlugin.log).detachAppender(logged);
        }
        if (starts.get() == 0) {
            // Still waiting: stop it asking. A started one has nothing of its own running here.
            try {
                onSwing(this::shutDown);
            } catch (AssertionError ignored) {
                // The test has already said what was wrong.
            }
        }
        RecordSyncWorld.close(world);
    }

    /**
     * What RuneLite does when the plugin asks for its folder. While the other window is open the
     * rename is refused the way Windows refuses it: thrown, the old folder as it was, nothing made.
     */
    private Filepath askRuneLite() throws IOException {
        askedOn.add(Thread.currentThread());
        if (otherWindowOpen && Files.exists(oldFolder)) {
            throw new AccessDeniedException(oldFolder.toString(), newFolder.toString(), "another window is using it");
        }
        return Folders.moveAsRuneLiteDoes(oldFolder, newFolder);
    }

    /**
     * Stands in for the plugin's real start, and does the two things of it that these tests go on to
     * use: somewhere for file work to run, and, last, the login caught up with on the game thread.
     */
    private void flipHubStarts() {
        starts.incrementAndGet();
        startedOn.add(Thread.currentThread());
        plugin.ioExecutor = MoreExecutors.newDirectExecutorService();
        plugin.invokeOnClientThread(() -> Bridge.get(GameStateChangedHandler.class).catchUpWithAnAlreadyRunningGame());
    }

    // ---- while the folder cannot be moved ----

    /**
     * The scenario in full. The main has 5,000 trades and 3 recipes, the alt 40 trades. The main's
     * window has been open since yesterday; a second window gets the update. In it the player logs
     * in, 4 fills happen, the game ticks, a setting changes, they log out and close it: none of it
     * is recorded, no file and no setting changes, and one line in the chat says why.
     */
    @Test
    public void whileAnotherWindowHoldsTheFolderNothingIsRecordedMadeOrChanged() throws Exception {
        aPlayerOfTheBuildBefore();
        slotAndHistoryPositionsAsTheOtherWindowLeftThem();
        Map<String, String> filesBefore = everythingUnderRuneLite();
        Map<String, String> settingsBefore = world.configKeys();
        otherWindowOpen = true;

        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        fourFills();
        plugin.onPostClientTick(new PostClientTick());
        plugin.onConfigChanged(changed("enableFlipHubSync"));
        plugin.onConfigChanged(changed("hiddenItems"));
        plugin.onScriptPostFired(new ScriptPostFired(ScriptID.CHAT_PROMPT_INIT));
        plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientInt.INPUT_TYPE));
        plugin.onMenuOptionClicked(new MenuOptionClicked(aMenuEntry()));
        afterAskingAgain(3);
        world.gameState = GameState.LOGIN_SCREEN;
        plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        plugin.onClientShutdown(new ClientShutdown());

        assertEquals("FlipHub was not started", 0, starts.get());
        assertEquals("no file or folder was made, changed or removed", filesBefore, everythingUnderRuneLite());
        assertEquals("no slot position, history-sync position or other setting changed",
            settingsBefore, world.configKeys());
        assertEquals("one line, at the login", Arrays.asList(CLOSE_THE_OTHER_WINDOW), world.chat);
        assertTrue("no trade is held in memory", world.state.getLocalTradeDeltasByAccount().isEmpty());
        assertTrue("no slot is being followed", world.state.getOfferUpdateStamps().isEmpty());
        assertTrue(world.state.getSnapshots().isEmpty());
        assertTrue(world.state.getLoadedProfiles().isEmpty());
        assertEquals("nothing was sent to the website", 0,
            world.website.batches.size() + world.website.other.size() + world.website.asked.size());
        assertNull("no side panel", plugin.panel);
        assertNull("no side-panel button", plugin.navButton);
        assertNull("nothing scheduled", plugin.scheduler);
        assertEquals("nothing but the chat line was handed to the game thread", 0, world.waitingForGameThread());
    }

    /**
     * The same window, and then the first one is closed. Within one more ask the folder moves and
     * FlipHub starts, once. The main shows its 5,000 trades and 3 recipes, none of the 4 fills, and
     * the file is still exactly those 5,000: not a shorter list saved over them.
     */
    @Test
    public void whenTheOtherWindowClosesFlipHubStartsOnceWithEveryTradeAndTheFileIsStillWhole() throws Exception {
        List<Delta> trades = aPlayerOfTheBuildBefore();
        otherWindowOpen = true;
        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        fourFills();
        plugin.onPostClientTick(new PostClientTick());
        afterAskingAgain(3);
        assertEquals(0, starts.get());

        otherWindowOpen = false;
        await("FlipHub's start", () -> starts.get() == 1);
        world.runGameThread();

        assertFalse("the old folder is gone: it is the new one now", Files.exists(oldFolder));
        assertEquals("5,000 trades show", 5_000, inMemory(MAIN).size());
        assertEquals(gson.toJsonTree(trades), gson.toJsonTree(inMemory(MAIN)));
        assertEquals("and the 3 recipes", 3,
            world.injector.getInstance(RecipeFlipStore.class).snapshotForFile(MAIN).size());
        ProfileData onDisk = Folders.store(newFolder).readProfileData(MAIN, ACCOUNTWIDE);
        assertEquals("the file still holds 5,000 trades", 5_000, onDisk.deltas.size());
        assertEquals(gson.toJsonTree(trades), gson.toJsonTree(onDisk.deltas));
        assertEquals(gson.toJsonTree(ProfileFolderTest.records(trades).subList(0, 3)), gson.toJsonTree(onDisk.recipeFlips));
        assertEquals("the alt's file was not touched", 40,
            Folders.store(newFolder).readProfileData(ALT, ACCOUNTWIDE).deltas.size());

        stillAfterSeveralRetryPeriods();
        assertEquals("started once", 1, starts.get());
        assertTrue("on the thread RuneLite starts plugins on", isSwingThread(startedOn.get(0)));
        assertFalse("RuneLite is never asked for the folder on the game thread",
            askedOn.contains(Thread.currentThread()));
        assertEquals("nothing more is said once it has started", Arrays.asList(CLOSE_THE_OTHER_WINDOW), world.chat);
    }

    /** Once started it records again, and the next purchase is added to what was there. */
    @Test
    public void afterTheFolderArrivesTheNextPurchaseIsAddedToTheHistory() throws Exception {
        Folders.store(oldFolder).writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", ProfileFolderTest.trades(40));
        otherWindowOpen = true;
        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        afterAskingAgain(2);

        otherWindowOpen = false;
        await("FlipHub's start", () -> starts.get() == 1);
        world.runGameThread();
        assertEquals(40, inMemory(MAIN).size());
        aPurchaseOfTenWhips(2);

        List<Delta> onDisk = Folders.store(newFolder).readProfileData(MAIN, ACCOUNTWIDE).deltas;
        assertEquals("the 40 and the one bought since", 41, onDisk.size());
        assertEquals(RecordSyncWorld.WHIP, onDisk.get(40).itemId);
        assertEquals(10, onDisk.get(40).deltaQty);
        assertEquals(1_000_000L, onDisk.get(40).deltaGp);
    }

    /** RuneLite's refusal leaves the old folder whole, try after try, and the first try once it is free takes it. */
    @Test
    public void aRefusedMoveLeavesTheOldFolderWholeAndALaterTryTakesIt() throws Exception {
        aPlayerOfTheBuildBefore();
        Map<String, String> before = everythingUnder(oldFolder);
        otherWindowOpen = true;

        onSwing(this::startUp);
        afterAskingAgain(5);

        assertEquals("the old folder is whole", before, everythingUnder(oldFolder));
        assertFalse("and nothing was made at the new place, which would stop RuneLite ever moving the old one",
            Files.exists(newFolder));
        assertEquals(0, starts.get());

        otherWindowOpen = false;
        await("FlipHub's start", () -> starts.get() == 1);

        assertFalse(Files.exists(oldFolder));
        assertEquals("every file, as it was", before, everythingUnder(newFolder));
    }

    // ---- what the player is told ----

    /** At each login while it waits, and not before one: handed to the game thread, which is where chat is written. */
    @Test
    public void thePlayerIsToldInTheChatAtEachLoginWhileItWaits() throws Exception {
        aPlayerOfTheBuildBefore();
        otherWindowOpen = true;
        onSwing(this::startUp);
        world.runGameThread();
        assertEquals("nothing is said at the login screen", new ArrayList<String>(), world.chat);

        world.accountHash = MAIN;
        world.gameState = GameState.LOGGED_IN;
        plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
        assertEquals("the line is queued for the game thread, not written from inside the login event",
            new ArrayList<String>(), world.chat);
        world.runGameThread();
        assertEquals(Arrays.asList(CLOSE_THE_OTHER_WINDOW), world.chat);

        logOut();
        logIn(MAIN);
        assertEquals(Arrays.asList(CLOSE_THE_OTHER_WINDOW, CLOSE_THE_OTHER_WINDOW), world.chat);
    }

    /** The plugin updated while the player is in the game: there is no login to wait for. */
    @Test
    public void aPlayerAlreadyInTheGameIsToldAtOnce() throws Exception {
        aPlayerOfTheBuildBefore();
        otherWindowOpen = true;
        world.accountHash = MAIN;
        world.gameState = GameState.LOGGED_IN;

        onSwing(this::startUp);
        assertEquals("chat is written on the game thread only", new ArrayList<String>(), world.chat);
        world.runGameThread();

        assertEquals(Arrays.asList(CLOSE_THE_OTHER_WINDOW), world.chat);
    }

    // ---- the folder is there ----

    /** Every start but the one after this update: the folder is there, and FlipHub starts as it always has. */
    @Test
    public void withTheFolderReadyFlipHubStartsAtOnceAndSaysNothing() throws Exception {
        List<Delta> trades = aPlayerOfTheBuildBefore();
        world.accountHash = MAIN;
        world.gameState = GameState.LOGGED_IN;

        onSwing(this::startUp);
        await("FlipHub's start", () -> starts.get() == 1);
        world.runGameThread();

        assertTrue("on the thread RuneLite starts plugins on", isSwingThread(startedOn.get(0)));
        assertFalse("the old folder was moved", Files.exists(oldFolder));
        assertEquals(gson.toJsonTree(trades), gson.toJsonTree(inMemory(MAIN)));
        assertEquals(new ArrayList<String>(), world.chat);
        stillAfterSeveralRetryPeriods();
        assertEquals("started once", 1, starts.get());
    }

    /** No old folder and no new one: RuneLite hands over a folder that is not there yet. */
    @Test
    public void aNewPlayerStartsAtOnceAndTheirFirstPurchaseIsSaved() throws Exception {
        onSwing(this::startUp);
        await("FlipHub's start", () -> starts.get() == 1);
        logIn(MAIN);
        aPurchaseOfTenWhips(0);

        List<Delta> onDisk = Folders.store(newFolder).readProfileData(MAIN, ACCOUNTWIDE).deltas;
        assertEquals(1, onDisk.size());
        assertEquals(10, onDisk.get(0).deltaQty);
        assertEquals(1_000_000L, onDisk.get(0).deltaGp);
        assertTrue(Files.isRegularFile(newFolder.resolve("profiles").resolve("hash_" + MAIN + ".json")));
        assertFalse(Files.exists(oldFolder));
    }

    // ---- while it starts, and a start that fails ----

    /**
     * The start takes a moment and the game does not wait for it. RuneLite sends a plugin nothing
     * until its start has returned, but after a wait the plugin is already being sent everything,
     * so it goes on ignoring all of it until the start is done. A fill handled part way through
     * would be recorded before there is anything to save it on, and the start would then clear the
     * place it had just marked in that slot.
     */
    @Test
    public void whatTheGameReportsWhileFlipHubIsStillStartingIsIgnored() throws Exception {
        Folders.store(oldFolder).writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", ProfileFolderTest.trades(40));
        otherWindowOpen = true;
        List<String> duringTheStart = new CopyOnWriteArrayList<>();
        plugin.start = () -> {
            duringTheStart.add(whatAHandlerWouldChange());
            oneOfEveryEvent();
            duringTheStart.add(whatAHandlerWouldChange());
            flipHubStarts();
        };
        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        afterAskingAgain(2);

        otherWindowOpen = false;
        await("FlipHub's start", () -> starts.get() == 1);
        theStartHasReturned();
        world.runGameThread();

        assertEquals("nothing reported during the start was handled", duringTheStart.get(0), duringTheStart.get(1));
        assertEquals("nor was the player told again to close a window, with the folder already there",
            Arrays.asList(CLOSE_THE_OTHER_WINDOW), world.chat);
        assertEquals("the 40 trades, and neither purchase made during the start", 40, inMemory(MAIN).size());
        assertEquals(40, Folders.store(newFolder).readProfileData(MAIN, ACCOUNTWIDE).deltas.size());

        aPurchaseOfTenWhips(2);
        assertEquals("once it has started it records again", 41,
            Folders.store(newFolder).readProfileData(MAIN, ACCOUNTWIDE).deltas.size());
    }

    /**
     * A start that throws. RuneLite leaves a plugin whose start threw out of everything it sends,
     * and reports it; after a wait the call is the plugin's own, so both are left to it. One line
     * in the log, every handler shut for good, and neither the folder nor the start tried again.
     */
    @Test
    public void aStartThatThrowsLeavesFlipHubShutAndIsNotRunAgain() throws Exception {
        aPlayerOfTheBuildBefore();
        otherWindowOpen = true;
        plugin.start = () -> {
            starts.incrementAndGet();
            throw new IllegalStateException("a service could not be built");
        };
        listenToThePluginsLog();
        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        afterAskingAgain(2);

        otherWindowOpen = false;
        await("FlipHub's start", () -> starts.get() == 1);
        theStartHasReturned();
        int asked = askedOnceItSettles();
        String asTheStartLeftIt = whatAHandlerWouldChange();

        oneOfEveryEvent();
        plugin.onClientShutdown(new ClientShutdown());
        stillAfterSeveralRetryPeriods();

        assertEquals("the start was not run again", 1, starts.get());
        assertEquals("RuneLite was not asked again", asked, askedOn.size());
        assertEquals("nothing the game reported afterwards was handled", asTheStartLeftIt, whatAHandlerWouldChange());
        assertEquals("nothing more is said: closing a window would change nothing now",
            Arrays.asList(CLOSE_THE_OTHER_WINDOW), world.chat);
        if (errorsLogged() != null) {
            assertEquals("one line in the log says so", Arrays.asList("FlipHub: unable to start"), errorsLogged());
        }
    }

    // ---- stopping while it waits ----

    /** Switched off in the plugin list while it waits: nothing to stop, nothing to flush, and it asks no more. */
    @Test
    public void switchedOffWhileItWaitsItStopsAskingAndNeverStarts() throws Exception {
        aPlayerOfTheBuildBefore();
        Map<String, String> before = everythingUnderRuneLite();
        Map<String, String> settingsBefore = world.configKeys();
        otherWindowOpen = true;
        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        afterAskingAgain(2);

        onSwing(this::shutDown);
        assertEquals("nothing was handed to the game thread to stop", 0, world.waitingForGameThread());
        int asked = askedOnceItSettles();
        otherWindowOpen = false;
        stillAfterSeveralRetryPeriods();

        assertEquals("RuneLite was not asked again", asked, askedOn.size());
        assertEquals("FlipHub was not started", 0, starts.get());
        assertEquals("the folder is where it was, as it was", before, everythingUnderRuneLite());
        assertEquals(settingsBefore, world.configKeys());
    }

    /** RuneLite closing while it waits: there is nothing unsaved to write, and it asks no more. */
    @Test
    public void theClientClosingWhileItWaitsFlushesNothingAndStopsAsking() throws Exception {
        aPlayerOfTheBuildBefore();
        Map<String, String> before = everythingUnderRuneLite();
        otherWindowOpen = true;
        onSwing(this::startUp);
        aTickPasses();
        logIn(MAIN);
        fourFills();
        afterAskingAgain(2);

        plugin.onClientShutdown(new ClientShutdown());
        int asked = askedOnceItSettles();
        otherWindowOpen = false;
        stillAfterSeveralRetryPeriods();

        assertEquals("RuneLite was not asked again", asked, askedOn.size());
        assertEquals("FlipHub was not started", 0, starts.get());
        assertEquals(before, everythingUnderRuneLite());
    }

    /** Off and on again while it waits, which a player told to "close your other windows" may well try. */
    @Test
    public void switchedOffAndOnAgainWhileItWaitsItStillStartsOnce() throws Exception {
        List<Delta> trades = aPlayerOfTheBuildBefore();
        otherWindowOpen = true;
        onSwing(this::startUp);
        afterAskingAgain(2);
        onSwing(this::shutDown);
        onSwing(this::startUp);
        afterAskingAgain(2);
        assertEquals(0, starts.get());

        otherWindowOpen = false;
        await("FlipHub's start", () -> starts.get() >= 1);
        stillAfterSeveralRetryPeriods();

        assertEquals("started once", 1, starts.get());
        assertEquals(gson.toJsonTree(trades),
            gson.toJsonTree(Folders.store(newFolder).readProfileData(MAIN, ACCOUNTWIDE).deltas));
    }

    // ---- what RuneLite is told, and how often it is asked ----

    /** The two names RuneLite moves the folder by, and the two seconds between asks. */
    @Test
    public void runeLiteIsToldBothFolderNamesAndIsAskedAgainEveryTwoSeconds() {
        PluginDescriptor descriptor = GeLifecyclePlugin.class.getAnnotation(PluginDescriptor.class);

        assertEquals("the new folder, .runelite/plugin-data/osrs-fliphub", "osrs-fliphub", descriptor.internalName());
        assertEquals("the old one, .runelite/fliphub", "fliphub", descriptor.legacyDataDirectory());
        assertEquals(2_000L, new GeLifecyclePlugin().folderRetryMs);
    }

    // ---- the player ----

    /**
     * A player of the build before this one: a main with 5,000 trades and 3 recipes, an alt with 40
     * trades, and the accountwide file, all in the old folder.
     *
     * @return the main's trades
     */
    private List<Delta> aPlayerOfTheBuildBefore() {
        List<Delta> trades = ProfileFolderTest.trades(5_000);
        ProfileStore olderBuild = Folders.store(oldFolder);
        olderBuild.writeProfileData(MAIN, ACCOUNTWIDE, "Zezima", trades, ProfileFolderTest.records(trades).subList(0, 3));
        olderBuild.writeProfileData(ALT, ACCOUNTWIDE, "Sips Potion", ProfileFolderTest.trades(40), null);
        olderBuild.writeProfileData(ACCOUNTWIDE, ACCOUNTWIDE, "Accountwide", trades, null);
        return trades;
    }

    /** What the window that is still open saved in RuneLite's settings for the main: a slot it follows, and where its history sync got to. */
    private void slotAndHistoryPositionsAsTheOtherWindowLeftThem() {
        Map<Integer, Stamp> slots = new HashMap<>();
        slots.put(3, RecordSyncWorld.positionOf(RecordSyncWorld.bought(60_000L, 3, 10)));
        world.configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP,
            world.state.getOfferUpdateStampConfigStore().perAccountKey(MAIN), OfferUpdateStampStore.serialize(slots, gson));
        world.configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, "geHistoryCursorV1_" + MAIN,
            GeHistoryCursorService.encode(Arrays.asList("a row of the in-game history")));
    }

    /** The game thread runs what the plugin has handed it, as it does every 20 ms at the login screen and in the game. */
    private void aTickPasses() {
        world.runGameThread();
    }

    private void logIn(long hash) {
        world.accountHash = hash;
        world.gameState = GameState.LOGGED_IN;
        plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
        world.runGameThread();
    }

    private void logOut() {
        world.gameState = GameState.LOGIN_SCREEN;
        plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        world.runGameThread();
    }

    /** Two purchases of ten whips, each filling in two goes. */
    private void fourFills() {
        aPurchaseOfTenWhips(0);
        aPurchaseOfTenWhips(1);
    }

    /** As the game reports it to the plugin: placed, 4 bought for 400,000, the other 6 for 600,000. */
    private void aPurchaseOfTenWhips(int slot) {
        plugin.onGrandExchangeOfferChanged(world.offerEvent(slot, 0, 0, 0L, GrandExchangeOfferState.EMPTY));
        plugin.onGrandExchangeOfferChanged(world.offerEvent(slot, 10, 0, 0L, GrandExchangeOfferState.BUYING));
        plugin.onGrandExchangeOfferChanged(world.offerEvent(slot, 10, 4, 400_000L, GrandExchangeOfferState.BUYING));
        plugin.onGrandExchangeOfferChanged(world.offerEvent(slot, 10, 10, 1_000_000L, GrandExchangeOfferState.BOUGHT));
    }

    /** One of each event the plugin is sent: two purchases, a tick, settings, the chat box, a click, a logout and a login. */
    private void oneOfEveryEvent() {
        fourFills();
        plugin.onPostClientTick(new PostClientTick());
        plugin.onConfigChanged(changed("enableFlipHubSync"));
        plugin.onConfigChanged(changed("hiddenItems"));
        plugin.onScriptPostFired(new ScriptPostFired(ScriptID.CHAT_PROMPT_INIT));
        plugin.onVarClientIntChanged(new VarClientIntChanged(VarClientInt.INPUT_TYPE));
        plugin.onMenuOptionClicked(new MenuOptionClicked(aMenuEntry()));
        logOut();
        logIn(MAIN);
    }

    /**
     * Everything a handler that ran would have left a mark on: the trades, slots and files held in
     * memory, RuneLite's settings, the website, and every file and folder on disk.
     */
    private String whatAHandlerWouldChange() {
        try {
            synchronized (world.state.getLocalStatsLock()) {
                return "trades in memory for " + new TreeSet<>(world.state.getLocalTradeDeltasByAccount().keySet())
                    + "\nslots followed " + new TreeSet<>(world.state.getOfferUpdateStamps().keySet())
                    + "\nslots seen " + new TreeSet<>(world.state.getSnapshots().keySet())
                    + "\nfiles loaded " + new TreeSet<>(world.state.getLoadedProfiles())
                    + "\nsent to the website "
                    + (world.website.batches.size() + world.website.other.size() + world.website.asked.size())
                    + "\nsettings " + new TreeMap<>(world.configKeys())
                    + "\nfiles " + everythingUnderRuneLite();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<Delta> inMemory(long key) {
        synchronized (world.state.getLocalStatsLock()) {
            List<Delta> trades = world.state.getLocalTradeDeltasByAccount().get(key);
            return trades != null ? new ArrayList<>(trades) : new ArrayList<>();
        }
    }

    // ---- RuneLite ----

    /** As RuneLite calls it: on the Swing thread. */
    private void startUp() {
        try {
            plugin.startUp();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void shutDown() {
        try {
            plugin.shutDown();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Runs it on the Swing thread and waits, and fails the test with whatever it threw. */
    private static void onSwing(Runnable work) throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                work.run();
            } catch (Throwable error) {
                thrown.set(error);
            }
        });
        if (thrown.get() != null) {
            throw new AssertionError("the plugin threw, and RuneLite would have reported it failed", thrown.get());
        }
    }

    /** The start runs on the Swing thread, so whatever is run there after it began runs once it has returned. */
    private static void theStartHasReturned() throws Exception {
        onSwing(() -> { });
    }

    /**
     * Reads the plugin's log where the logger is RuneLite's own, logback. A build that puts another
     * one first on the class path has nothing to read it by, and the log is then not looked at.
     */
    private void listenToThePluginsLog() {
        if (GeLifecyclePlugin.log instanceof ch.qos.logback.classic.Logger) {
            logged = new ListAppender<>();
            logged.start();
            ((ch.qos.logback.classic.Logger) GeLifecyclePlugin.log).addAppender(logged);
        }
    }

    /** The error lines logged since {@link #listenToThePluginsLog}, or null where the log cannot be read. */
    private List<String> errorsLogged() {
        if (logged == null) {
            return null;
        }
        return logged.list.stream()
            .filter(line -> line.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
    }

    private static boolean isSwingThread(Thread thread) throws Exception {
        AtomicReference<Thread> swing = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> swing.set(Thread.currentThread()));
        return swing.get() == thread;
    }

    private static GameStateChanged gameState(GameState state) {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
    }

    private static ConfigChanged changed(String key) {
        ConfigChanged event = new ConfigChanged();
        event.setGroup(FliphubConfigGroups.CONFIG_GROUP);
        event.setKey(key);
        return event;
    }

    private static MenuEntry aMenuEntry() {
        return (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(), new Class<?>[] {MenuEntry.class},
            (proxy, method, args) -> {
                Class<?> returns = method.getReturnType();
                if (returns == boolean.class) {
                    return false;
                }
                return returns.isPrimitive() && returns != void.class ? (Object) 0 : null;
            });
    }

    // ---- time ----

    /** Waits until the plugin has asked RuneLite for the folder this many more times: it is still trying. */
    private void afterAskingAgain(int times) throws InterruptedException {
        int from = askedOn.size();
        await("the plugin asking RuneLite for its folder again", () -> askedOn.size() >= from + times);
    }

    /** Long enough for many asks, had it gone on asking. */
    private static void stillAfterSeveralRetryPeriods() throws InterruptedException {
        Thread.sleep(RETRY_MS * 12);
    }

    /** How often RuneLite has been asked, once an ask already on its way when the plugin was stopped has landed. */
    private int askedOnceItSettles() throws InterruptedException {
        Thread.sleep(RETRY_MS * 4);
        return askedOn.size();
    }

    private static void await(String what, BooleanSupplier done) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (!done.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("never happened: " + what);
            }
            Thread.sleep(5L);
        }
    }

    // ---- the folders ----

    private Map<String, String> everythingUnderRuneLite() throws IOException {
        return everythingUnder(runelite);
    }

    /** Every folder and file beneath one, by its path from there, and for a file its size and a mark of what it holds. */
    private static Map<String, String> everythingUnder(Path root) throws IOException {
        Map<String, String> found = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.collect(Collectors.toList())) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    found.put(name, "a folder");
                } else {
                    byte[] bytes = Files.readAllBytes(path);
                    found.put(name, bytes.length + " bytes, marked " + Arrays.hashCode(bytes));
                }
            }
        }
        return found;
    }
}
