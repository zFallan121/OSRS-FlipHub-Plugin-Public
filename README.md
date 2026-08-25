# OSRS FlipHub

Track your Grand Exchange flips — margins, buy limits and live Wiki prices, right in the sidebar.

Works entirely offline as a local flip tracker. Linking a [FlipHub](https://www.osrsfliphub.com)
account is optional and **off by default**.

## Features

#### Activity panel

Live prices for the items you care about, with everything you need to judge a flip at a glance:
current sell and buy price, the last price each side actually traded at, margin, margin × buy
limit, and ROI.

Prices come from the Old School RuneScape Wiki price API and refresh continuously. Hover a
Sell/Buy price to see exactly how old each side of the quote is — a five-minute-old buy price and
a two-hour-old sell price are very different things.

#### GE buy limits

Every item shows how much of your 4-hour buy limit is left and a live countdown to when it resets,
so you stop guessing whether you can still buy in.

#### Offer preview

Open a Grand Exchange offer in game and the panel jumps straight to that item, so the numbers you
need are already on screen while you set your price.

#### Grand Exchange suggestions

While you're searching for an item in the GE, the chatbox shows the current buy and sell price,
your remaining buy limit, and how many you can afford with the cash you're carrying.

#### Flip Profile

Your completed flips, totalled per item — profit and flip count over **Session**, **Last 1h**,
**4h**, **24h**, **7d** or **All time**. Sort by completion, profit or ROI, and search to narrow it
down.

#### Bookmarks

Star the items you flip regularly and filter the list to just those. Hide the ones you never trade.

#### Optional cloud sync

Link a FlipHub account to sync completed flips to the [osrsfliphub.com](https://www.osrsfliphub.com)
dashboard and see your history across devices. Entirely optional — see below.

## Getting started

Install the plugin and open the FlipHub panel from the sidebar. That's it — offer tracking, buy
limits and Wiki prices all work immediately with no account and no setup.

To sync to the web dashboard as well:

1. Tick **Enable FlipHub sync** in the plugin settings.
2. Paste your license key from [osrsfliphub.com/profile/manage](https://www.osrsfliphub.com/profile/manage) into **License Key**.

Your flips start syncing from that point. To stop, click **Unlink** or clear the license key —
uploads stop immediately and the plugin returns to local-only mode.

## Configuration

##### FlipHub account (optional cloud sync)

- **Enable FlipHub sync** — Off by default. While off, the plugin never connects to FlipHub's
  servers and everything stays on your computer.
- **License Key** — Links the plugin to your FlipHub account. Leave blank to stay local-only.
- **Unlink (click)** — Clears the link, stops all uploads, and returns to local-only stats.

##### General

- **Show GE Offer Timers** — Show how long since each Grand Exchange offer last updated. On by
  default.

## Privacy

The plugin is local-first and read-only. It watches Grand Exchange events the client already
exposes, performs no automation, and never sends input to the game. No RuneScape or Jagex
credentials are requested, read, or transmitted.

- **Without linking (default)** — No trade data leaves your machine. The only network calls are
  read-only price lookups to `prices.runescape.wiki`.
- **With sync enabled and linked** — Your Grand Exchange offer events (item, quantity, price, offer
  state, timestamps) are uploaded over HTTPS to `osrsfliphub.com` to power your dashboard. As with
  any request to a third-party server, this exposes your IP address to it.

## Support

Found a bug or have a suggestion? Open an issue on
[GitHub](https://github.com/zFallan121/OSRS-FlipHub-Plugin-Public/issues).

## Building from source

```sh
./gradlew build
```

The jar is written to `build/libs/`. To launch a RuneLite developer client with the plugin loaded,
run `./gradlew run`.

## License

Released under the [BSD 2-Clause License](LICENSE).
