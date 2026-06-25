# OSRS FlipHub

A RuneLite plugin for tracking Grand Exchange flips. It records your GE offer
history locally and shows margins, buy limits, and live Old School RuneScape
Wiki prices while you trade.

Optionally, you can link a [FlipHub](https://www.osrsfliphub.com) account to sync
your flips to the online dashboard. Linking is **off by default** — the plugin is
fully usable as a local-only tracker and uploads nothing until you choose to link.

## Features

- Local Grand Exchange offer history (buys, sells, completed, aborted)
- Per-flip and rolling profit / margin tracking
- GE buy-limit tracking with reset timers
- Live item prices from the OSRS Wiki price API
- Per-item bookmarks and price context
- Optional account linking to sync flips to the FlipHub web dashboard

## Privacy & data

This plugin is **local-first**. By default all data stays on your computer in your
RuneLite profile and nothing is sent anywhere except anonymous price lookups to the
public OSRS Wiki price API.

- **Without linking (default):** No account data and no trade data leave your
  machine. The only network calls are read-only price lookups to
  `prices.runescape.wiki`.
- **If you link a FlipHub account** (by pasting a License Key in the plugin
  settings): your Grand Exchange offer events — item, quantity, price, offer
  state, and timestamps — are uploaded over HTTPS to FlipHub's servers
  (`osrsfliphub.com`) to power your online dashboard. No RuneScape/Jagex
  credentials are ever requested, read, or transmitted.
- **To stop syncing:** clear the License Key or click **Unlink** in the plugin
  settings. Uploads stop immediately and the plugin returns to local-only mode.

The plugin is read-only and event-driven: it observes Grand Exchange events the
game client already exposes. It performs no automation and sends no input to the
game.

## Build

This project builds with the Gradle wrapper against the RuneLite client API:

```sh
./gradlew build
```

The built jar is written to `build/libs/`.

## Run in a development client

```sh
./gradlew run --no-daemon --console=plain
```

This launches the RuneLite developer client with the plugin on the classpath. See
the [RuneLite Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide)
for details on dev logins.

## License

Released under the [BSD 2-Clause License](LICENSE).
