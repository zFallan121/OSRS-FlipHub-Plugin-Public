<p align="center">
  <img src="docs/01-hero-get-fliphub-on-runelite-large.png" alt="Get FlipHub on RuneLite — make smarter flips" width="100%">
</p>

# OSRS FlipHub

Track your Grand Exchange flips — margins, buy limits and live Wiki prices, right in the sidebar.

Works entirely offline. Linking a [FlipHub](https://www.osrsfliphub.com) account is optional and
**off by default**.

## Features

#### Activity panel

Sell and buy price, the last price each side actually traded at, margin, margin × buy limit, ROI,
and how much of your 4-hour buy limit is left with a countdown to the reset.

Search the whole Grand Exchange, not only the items you have already flipped, and sort the list by
completion, profit or ROI.

![The FlipHub Activity panel in the RuneLite sidebar, showing sell and buy price, last traded prices, margin, margin x limit, ROI and remaining GE buy limit](docs/panel-activity.png)

#### Grand Exchange suggestions

Setting up an offer fills the prompts in with the numbers you'd otherwise alt-tab for.

![The GE price prompt showing "Current Buy Price: 702 gp"](docs/ge-suggestion-buy-price.png)

![The GE price prompt showing "Current Sell Price: 183,211 gp"](docs/ge-suggestion-sell-price.png)

On a buy, the quantity prompt adds your remaining limit and how many you can afford.

![The GE quantity prompt showing "Remaining GE limit: 3,000" and "Cash limit: 5,585"](docs/ge-suggestion-buy-limit.png)

#### Decimal prices

The game reads `9m` in a price box but refuses the decimal point that would let you write `9.4m`.
This adds it, in any *enter an amount* prompt — Grand Exchange price and quantity, bank withdraw-X,
trade, coffers.

- `9.4m` → 9,400,000
- `1.21b` → 1,210,000,000
- `2.5325k` → 2,532 — anything past a whole coin is dropped

Turn it off with **Type decimal amounts** in the plugin settings.

#### Profile

Completed flips totalled per item over **Session**, **1h**, **4h**, **24h**, **7d** or **All
time**. Sort by completion, profit or ROI.

![The Profile tab, showing total profit, ROI, flips made and tax paid over the selected range, above a sortable list of per-item totals](docs/panel-profile.png)

#### Recipes

Bought a blade and a hilt, made a godsword, sold it? The game never tells a plugin that two items
became one, so the three trades would otherwise be counted as three separate flips — one of them
looking like a windfall and the others like losses.

**Record a recipe** on the Profile tab. Your finished trades are listed newest first; tick the
ones the recipe was made from — the purchases that went in and the sales the result went out
through — and they become one activity with one profit. Assembling, disassembling, repairing and
making or breaking sets are all covered, and a repair fee counts towards the cost.

Nothing is guessed and nothing is recorded for you. Everything you record is listed underneath,
and any of it can be undone.

#### Sync with FlipHub OSRS

Link your plugin with your FlipHub OSRS account to sync flips and get personalised flip insights to
start flipping smarter.

Synced flips build your **My Statistics** page — the items worth going back to, profit over time,
and ranks as your total climbs.

![Know what to flip next: items worth revisiting, with profit, flip count and buy limit used](docs/07-know-what-to-flip-next.png)

![Flips worth revisiting, with profit, flip count and buy limit used](docs/08-flips-to-revisit.png)

![Cumulative profit climbing to 193M gp, with rank milestones along the way](docs/04-earn-ranks.png)

![From Beggar to The Elite — the rank ladder, topping out at Gielinor Elite](docs/06-gielinor-elite.png)

*The screenshots in this section are the FlipHub website, not the plugin.*

![The FlipHub web dashboard: buy and sell price, margin, ROI, volume, buy limit and margin x limit](docs/10-web-dashboard.png)

#### Also

- **Offer preview** — open an offer in game and the panel jumps to that item.
- **Bookmarks** — star the items you flip often, filter to just those, hide the rest.

## Getting started

Install the plugin and open the FlipHub panel from the sidebar. Offer tracking, buy limits and Wiki
prices work immediately — no account, no setup.

To sync to the dashboard as well, open the **Link** tab in the FlipHub panel, paste your license
key from [osrsfliphub.com/my-statistics](https://www.osrsfliphub.com/my-statistics) and click
**Link account**. The tab shows whether the device is linked; **Unlink this device** stops it —
uploads end immediately and the plugin returns to local-only.

## Privacy

The plugin is local-first. It watches Grand Exchange events the client already exposes and performs
no automation — it never clicks, moves, or trades for you. The only thing it writes into the game is
text in a chatbox prompt you opened yourself: a suggested price when you click one, or the decimal
point you just typed. You still confirm every offer. No RuneScape or Jagex credentials are
requested, read, or transmitted.

- **Without linking (default)** — No trade data leaves your machine. The only network calls are
  read-only price lookups to `prices.runescape.wiki`.
- **With sync enabled and linked** — Your Grand Exchange offer events (item, quantity, price, offer
  state, timestamps) are uploaded over HTTPS to `osrsfliphub.com` to power your dashboard. As with
  any request to a third-party server, this exposes your IP address to it.

## Support

[Contact support](https://www.osrsfliphub.com/support).

## License

Released under the [BSD 2-Clause License](LICENSE).
