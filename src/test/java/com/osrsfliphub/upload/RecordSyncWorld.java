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

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Player;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;

/**
 * One RuneLite window for the record-confirmation tests: the plugin's real services, built by Guice
 * as the client builds them, over a folder of real trade files, RuneLite's own config kept in
 * memory, and a website that answers what a test tells it to. Nothing here reaches the network.
 *
 * <p>Two windows on one computer are two of these over one folder and one config; only one is the
 * running plugin at a time ({@link #use}), because the plugin finds itself through two statics.
 */
final class RecordSyncWorld {
    static final int WHIP = 4151;

    final Path dir;
    final ConfigManager configManager;
    final PluginState state = new PluginState();
    final Website website = new Website();
    final GeLifecyclePlugin plugin = new GeLifecyclePlugin();
    final Injector injector;
    /** The account hash the game reports; not positive for a character filed under its name. */
    volatile long accountHash;
    volatile String playerName;
    volatile GameState gameState = GameState.LOGIN_SCREEN;
    volatile boolean linked = true;

    RecordSyncWorld(Path dir, ConfigManager configManager) {
        this.dir = dir;
        this.configManager = configManager;
        Client client = client();
        PluginConfig config = config();
        Gson gson = new Gson();
        plugin.client = client;
        plugin.config = config;
        plugin.apiClient = website;
        // Work handed to the IO pool runs where it is handed over, so a test reads on.
        plugin.ioExecutor = MoreExecutors.newDirectExecutorService();
        injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(PluginConfig.class).toInstance(config);
                bind(PluginState.class).toInstance(state);
                bind(Gson.class).toInstance(gson);
                bind(ConfigManager.class).toInstance(configManager);
                bind(ProfileStore.class).toInstance(new ProfileStore(gson, "fliphub", "fliphub-dev", dir));
                bind(ApiClient.class).toInstance(website);
                bind(ItemManager.class).toInstance(unbuilt(ItemManager.class));
                bind(ClientThread.class).toInstance(queueOnly());
                bind(OkHttpClient.class).toInstance(new OkHttpClient.Builder().addInterceptor(chain -> {
                    throw new IOException("a test must not reach the network");
                }).build());
            }
        });
        use();
    }

    /** A folder of its own and a config of its own. */
    static RecordSyncWorld fresh() throws Exception {
        return new RecordSyncWorld(Files.createTempDirectory("record-sync"), workingConfig());
    }

    /** The same computer started again, or its second window: the same folder and config, nothing in memory. */
    RecordSyncWorld again() {
        return new RecordSyncWorld(dir, configManager);
    }

    /** Makes this window the running plugin. */
    RecordSyncWorld use() {
        Access.set(plugin);
        Bridge.set(injector);
        return this;
    }

    static void close(RecordSyncWorld world) throws IOException {
        Access.set(null);
        Bridge.set(null);
        if (world != null && Files.exists(world.dir)) {
            try (Stream<Path> paths = Files.walk(world.dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    // ---- the game ----

    /** Logs a character in and lets a client tick pass, which is when the plugin learns who it is. */
    RecordSyncWorld login(long hash) {
        return login(hash, null);
    }

    RecordSyncWorld login(long hash, String name) {
        accountHash = hash;
        playerName = name;
        gameState = GameState.LOGGED_IN;
        injector.getInstance(AccountSession.class).resolveLocalAccountKey();
        return this;
    }

    void logout() {
        gameState = GameState.LOGIN_SCREEN;
        injector.getInstance(AccountSession.class).resolveLocalAccountKey();
    }

    /** The game reporting one of this character's Grand Exchange offers, as it does on every change to it. */
    void offer(int slot, int totalQty, int filledQty, long spentGp, GrandExchangeOfferState offerState) {
        GrandExchangeOffer offer = (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(), new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItemId":
                        return WHIP;
                    case "getPrice":
                        return 100_000L;
                    case "getTotalQuantity":
                        return totalQty;
                    case "getQuantitySold":
                        return filledQty;
                    case "getSpent":
                        return spentGp;
                    case "getState":
                        return offerState;
                    default:
                        return nothing(method);
                }
            });
        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setSlot(slot);
        event.setOffer(offer);
        injector.getInstance(GrandExchangeOfferChangedHandler.class).handle(event);
    }

    /** A purchase of ten whips made in the game just now: placed, part filled, finished. */
    void buyTenWhips(int slot) {
        offer(slot, 0, 0, 0L, GrandExchangeOfferState.EMPTY);
        offer(slot, 10, 0, 0L, GrandExchangeOfferState.BUYING);
        offer(slot, 10, 4, 400_000L, GrandExchangeOfferState.BUYING);
        offer(slot, 10, 10, 1_000_000L, GrandExchangeOfferState.BOUGHT);
    }

    /**
     * Stands in for time passing: a character's stored trades become ones made that much earlier,
     * in memory and in its file. A stored trade waits a minute after its offer ended before it is
     * sent, and a test cannot.
     */
    void age(long key, long byMs) {
        synchronized (state.getLocalStatsLock()) {
            List<Delta> inMemory = state.getLocalTradeDeltasByAccount().get(key);
            if (inMemory != null) {
                for (Delta trade : inMemory) {
                    earlier(trade, byMs);
                }
            }
        }
        List<Delta> onDisk = stored(key);
        for (Delta trade : onDisk) {
            earlier(trade, byMs);
        }
        store(key, onDisk.toArray(new Delta[0]));
    }

    private static void earlier(Delta trade, long byMs) {
        trade.tsClientMs -= byMs;
        trade.offerStartMs = trade.offerStartMs > 0 ? trade.offerStartMs - byMs : 0L;
        trade.endMs = trade.endMs > 0 ? trade.endMs - byMs : 0L;
    }

    /** The key a character filed under its name gets: what the plugin itself works out. */
    static long nameKey(String name) {
        return Math.abs(name.trim().toLowerCase(Locale.US).hashCode());
    }

    // ---- the upload ----

    RecordSync sync() {
        return injector.getInstance(RecordSync.class);
    }

    /** The two-second upload tick: the live queue is sent, then the stored trades get their turn. */
    void tick() {
        injector.getInstance(UploadBackfillDispatch.class).requestEventFlush();
    }

    /** Ticks until a tick sends nothing, and returns every batch sent on the way. */
    List<List<GeEvent>> settle() {
        int from = website.batches.size();
        for (int i = 0; i < 200; i++) {
            int before = website.batches.size();
            tick();
            if (website.batches.size() == before && state.getUploadState().getPendingUploadEvents() == 0) {
                break;
            }
        }
        return new ArrayList<>(website.batches.subList(from, website.batches.size()));
    }

    /** A pass that left something unconfirmed waits before the next; a test does not. */
    void waitOutThePause() {
        sync().nextPassMs = 0L;
    }

    /** A live event on the upload queue, as a trade made this moment would put one there. */
    GeEvent queueLive(String id) {
        GeEvent event = new GeEvent();
        event.event_id = id;
        event.event_type = "OFFER_UPDATED";
        event.item_id = WHIP;
        injector.getInstance(UploadEventDispatch.class).enqueueEvent(event);
        return event;
    }

    // ---- the files ----

    Path file(long key) {
        return dir.resolve("fliphub").resolve("profiles").resolve("hash_" + key + ".json");
    }

    /** Writes a character's file the way the window logged in as that character does. */
    void store(long key, Delta... trades) {
        new ProfileStore(new Gson(), "fliphub", "fliphub-dev", dir)
            .writeProfileData(key, Const.ACCOUNTWIDE_KEY, "Character " + key, new ArrayList<>(Arrays.asList(trades)));
    }

    /** What a character's file holds now, read from the disk and nowhere else. */
    List<Delta> stored(long key) {
        ProfileData data = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", dir)
            .readProfileData(key, Const.ACCOUNTWIDE_KEY);
        return data != null && data.deltas != null ? data.deltas : new ArrayList<>();
    }

    String text(long key) throws IOException {
        return Files.readString(file(key), StandardCharsets.UTF_8);
    }

    /** The mark kept for a character this window is not logged in as, or 0. */
    long mark(long key) {
        Long mark = configManager.getConfiguration(FliphubConfigGroups.CONFIG_GROUP, RecordSync.MARK_KEY + key, Long.class);
        return mark != null ? mark : 0L;
    }

    // ---- trades ----

    static long now() {
        return System.currentTimeMillis();
    }

    /** A finished purchase of {@code qty} whips at 100,000 each that began {@code agoMs} ago and took a second. */
    static Delta bought(long agoMs, int slot, int qty) {
        long first = now() - agoMs;
        return new Delta(first, slot, WHIP, true, qty, qty * 100_000L, "OFFER_COMPLETED", 100_000, false, first - 5_000L,
            first + 1_000L);
    }

    /** The id the plugin sends a stored trade under. */
    static String id(long key, Delta trade) {
        return new BackfillUploader().buildBackfillEvent(key, trade, 301).event_id;
    }

    static List<GeEvent> records(List<List<GeEvent>> batches) {
        List<GeEvent> out = new ArrayList<>();
        for (List<GeEvent> batch : batches) {
            for (GeEvent event : batch) {
                if ("OFFER_RECORD".equals(event.event_type)) {
                    out.add(event);
                }
            }
        }
        return out;
    }

    static Set<String> ids(List<GeEvent> events) {
        Set<String> ids = new HashSet<>();
        for (GeEvent event : events) {
            ids.add(event.event_id);
        }
        return ids;
    }

    // ---- the website ----

    /** What the website does with a batch. The default takes every event and confirms every record. */
    static final class Website extends ApiClient {
        /** Every batch that reached the website, in order. */
        final List<List<GeEvent>> batches = new ArrayList<>();
        /** The ids it holds. */
        final Set<String> held = new HashSet<>();
        volatile Function<List<GeEvent>, EventUploadResponse> answer = this::takeAll;

        Website() {
            super(null, null, null);
        }

        @Override
        public EventUploadResponse sendEventsDetailed(String sessionToken, String signingSecret, List<GeEvent> events) {
            batches.add(new ArrayList<>(events));
            return answer.apply(events);
        }

        @Override
        public LinkResponse linkDevice(String licenseKey, String deviceId, String pluginVersion) {
            return refreshSession(null, null, deviceId);
        }

        @Override
        public LinkResponse refreshSession(String sessionToken, String signingSecret, String deviceId) {
            LinkResponse response = new LinkResponse();
            response.session_token = "session-token";
            response.signing_secret = "signing-secret";
            return response;
        }

        /** The website as it is: a record it lacks is stored, one it holds is a duplicate, and both are confirmed. */
        EventUploadResponse takeAll(List<GeEvent> events) {
            return take(events, new HashSet<>());
        }

        /** As {@link #takeAll}, but refusing the records with these ids. */
        EventUploadResponse take(List<GeEvent> events, Set<String> refused) {
            EventUploadResponse response = status(200);
            response.status = "ok";
            response.accepted = 0;
            response.duplicates = 0;
            response.rejected = 0;
            List<String> confirmed = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            boolean anyRecord = false;
            for (GeEvent event : events) {
                boolean record = "OFFER_RECORD".equals(event.event_type);
                anyRecord |= record;
                if (record && refused.contains(event.event_id)) {
                    rejected.add(event.event_id);
                    response.rejected++;
                    continue;
                }
                if (held.add(event.event_id)) {
                    response.accepted++;
                } else {
                    response.duplicates++;
                }
                if (record) {
                    confirmed.add(event.event_id);
                }
            }
            if (anyRecord) {
                response.records = new HashMap<>();
                response.records.put("confirmed", confirmed);
                response.records.put("rejected", rejected);
            }
            return response;
        }

        static EventUploadResponse status(int code) {
            EventUploadResponse response = new EventUploadResponse();
            response.status_code = code;
            return response;
        }
    }

    // ---- stand-ins for the client ----

    private Client client() {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> "getName".equals(method.getName()) ? playerName : nothing(method));
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getGameState":
                        return gameState;
                    case "getAccountHash":
                        return accountHash;
                    case "getLocalPlayer":
                        return playerName != null ? player : null;
                    case "getWorld":
                        return 301;
                    default:
                        return nothing(method);
                }
            });
    }

    private PluginConfig config() {
        return (PluginConfig) Proxy.newProxyInstance(PluginConfig.class.getClassLoader(),
            new Class<?>[] {PluginConfig.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "enableFlipHubSync":
                        return linked;
                    case "sessionToken":
                        return linked ? "session-token" : "";
                    case "signingSecret":
                        return linked ? "signing-secret" : "";
                    default:
                        return nothing(method);
                }
            });
    }

    private static Object nothing(Method method) {
        Class<?> returns = method.getReturnType();
        if (!returns.isPrimitive() || returns == void.class) {
            return null;
        }
        if (returns == boolean.class) {
            return false;
        }
        if (returns == long.class) {
            return 0L;
        }
        if (returns == double.class) {
            return 0d;
        }
        if (returns == float.class) {
            return 0f;
        }
        return 0;
    }

    /** A client thread that only queues what it is handed: nothing here needs it to run. */
    private static ClientThread queueOnly() {
        ClientThread thread = unbuilt(ClientThread.class);
        try {
            set(ClientThread.class, thread, "invokes", new ConcurrentLinkedQueue<>());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        return thread;
    }

    /** A ConfigManager that keeps what it is given in memory (as MerchantLevelUpTest builds it). */
    static ConfigManager workingConfig() throws Exception {
        ConfigManager manager = unbuilt(ConfigManager.class);
        Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
        Object data = unbuilt(dataType);
        set(dataType, data, "properties", new ConcurrentHashMap<String, String>());
        set(dataType, data, "patchChanges", new HashMap<String, String>());
        set(ConfigManager.class, manager, "configProfile", data);
        Constructor<?> handler = Class.forName("net.runelite.client.config.ConfigInvocationHandler")
            .getDeclaredConstructor(ConfigManager.class);
        handler.setAccessible(true);
        set(ConfigManager.class, manager, "handler", handler.newInstance(manager));
        set(ConfigManager.class, manager, "eventBus", new EventBus());
        set(ConfigManager.class, manager, "serializers", new HashMap<>());
        return manager;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T unbuilt(Class<T> type) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field handle = unsafeType.getDeclaredField("theUnsafe");
            handle.setAccessible(true);
            Object unsafe = handle.get(null);
            return (T) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not stand in for " + type.getSimpleName(), ex);
        }
    }

    /** For a map-shaped answer in a test that builds one by hand. */
    static Map<String, List<String>> named(String name, String... ids) {
        Map<String, List<String>> records = new HashMap<>();
        records.put(name, new ArrayList<>(Arrays.asList(ids)));
        return records;
    }
}
