<p align="center">
  <img src="docs/01-hero-get-fliphub-on-runelite-large.png" alt="Get FlipHub on RuneLite — make smarter flips" width="100%">
</p>

# OSRS FlipHub

Track your Grand Exchange flips — margins, buy limits and live Wiki prices, right in the sidebar.

Works entirely offline. Linking a [FlipHub](https://www.osrsfliphub.com) account is optional and
**off by default**.

## Contents

- [Features](#features)
  - [Merchant levels](#merchant-levels)
  - [Activity panel](#activity-panel)
  - [You can see how old a price is](#you-can-see-how-old-a-price-is)
  - [Bookmarks](#bookmarks)
  - [Grand Exchange suggestions](#grand-exchange-suggestions)
  - [Decimal prices](#decimal-prices)
  - [Profile](#profile)
  - [Recipes](#recipes)
  - [Sync with FlipHub OSRS](#sync-with-fliphub-osrs)
  - [Also](#also) — offer preview, and one character or all of them
- [Getting started](#getting-started)
- [Privacy](#privacy)
- [Support](#support)
- [License](#license)

## Features

#### Merchant levels

Your lifetime profit across every character is a Merchant level, read on the game's own experience
curve: level 99 is 10B profit, and level 92 is 5B because 92 is halfway to 99 in experience.

FlipHub's ten ranks, from Lumbridge Looter to Gielinor Elite, are bands of those levels. **FLIP
RANK** on the Profile tab shows where you stand, in your rank's colour, and hovering it names the
next rank and the level it starts at.

Merchant also sits in the game's own skills tab as a twenty-fifth skill, on a row of its own with
the Total level beside it. Hovering reads like any other skill and clicking opens a guide listing
the ten ranks and the profit each one takes. Turn it off with **Show Merchant in the skills tab**
in the plugin's settings and the tab goes straight back to the game's own twenty-four.

When a sale earns a level, the game congratulates you the way it does for a skill: the message in
the chatbox, the level-up fireworks and a dance. Only you see them. Turn it off with **Celebrate
level-ups** in the plugin's settings.

<img src="docs/rank-up.png" width="520" alt="A character dancing in the Grand Exchange with orange fireworks around them, above the chatbox message: Congratulations, you just advanced a Flipping rank. You are now a Trader. Click here to continue">

<img src="docs/divider.png" width="100%" alt="">

#### Activity panel

Sell and buy price, the last price each side actually traded at, margin, margin × buy limit, ROI,
and how much of your 4-hour buy limit is left with a countdown to the reset.

Search the whole Grand Exchange, not only the items you have already flipped, and sort the list by
completion, profit or ROI.

![The FlipHub Activity panel in the RuneLite sidebar, showing sell and buy price, last traded prices, margin, margin x limit, ROI and remaining GE buy limit](docs/panel-activity.png)

<img src="docs/divider.png" width="100%" alt="">

#### You can see how old a price is

A live price is only ever the last trade someone made, and plenty of items go an hour between
trades. So the two live prices are coloured by the age of the trade behind them:

- **white** — traded within the last half hour
- **amber** — nothing for 30 minutes, and going cold
- **red** — nothing for an hour, so don't type it into an offer without checking

Each side is judged on its own. An item that sells briskly but is bought rarely shows one of each,
which is the whole point: it is the stale side that costs you.

![Three Grand Exchange items in the panel: one with a red sell price beside a white buy price, one with both prices amber, and prices in white on an item still trading](docs/panel-price-age.png)

Hover either price for the exact age of both.

![A tooltip over the sell price reading "Sell price age: 01:30:14" in red and "Buy price age: 00:06:54" in white](docs/panel-price-age-tooltip.png)

The same three colours run the **offer timers** in game: each Grand Exchange slot carries the time
since that offer last moved — green under five minutes, yellow under thirty, red beyond. Turn them
off with **Show GE offer timers**.

<img src="docs/divider.png" width="100%" alt="">

#### Bookmarks

Star the items you flip often and filter the list down to just those. An item you never want to see
again can be hidden from its icon.

![The panel filtered to bookmarked items, the star in the search row lit gold and the list headed "Bookmarked items"](docs/panel-bookmarks.png)

<img src="docs/divider.png" width="100%" alt="">

#### Grand Exchange suggestions

Setting up an offer fills the prompts in with the numbers you'd otherwise alt-tab for.

![The GE price prompt showing "Current Buy Price: 702 gp"](docs/ge-suggestion-buy-price.png)

![The GE price prompt showing "Current Sell Price: 183,211 gp"](docs/ge-suggestion-sell-price.png)

On a buy, the quantity prompt adds your remaining limit and how many you can afford.

![The GE quantity prompt showing "Remaining GE limit: 3,000" and "Cash limit: 5,585"](docs/ge-suggestion-buy-limit.png)

<img src="docs/divider.png" width="100%" alt="">

#### Decimal prices

The game reads `9m` in a price box but refuses the decimal point that would let you write `9.4m`.
This adds it, in any *enter an amount* prompt — Grand Exchange price and quantity, bank withdraw-X,
trade, coffers.

- `9.4m` → 9,400,000
- `1.21b` → 1,210,000,000
- `2.5325k` → 2,532 — anything past a whole coin is dropped

Turn it off with **Type decimal amounts** in the plugin settings.

<img src="docs/divider.png" width="100%" alt="">

#### Profile

Completed flips totalled per item over **Session**, **Last 1h**, **4h**, **24h**, **7d** or **All
time**. Sort by completion, profit or ROI.

![The Profile tab, showing FLIP RANK in gold beside a Trader helm next to the range picker, then total profit, ROI, flips made and tax paid over the selected range, above a sortable list of per-item totals](docs/panel-profile.png)

Open an item for what those totals are made of — average buy and sell, quantity, how long a flip
took to fill — and every flip behind them, listed one by one.

![An opened item showing total profit, total cost, average sell and buy, ROI, flips, quantity and average time to complete, over a flip history listing each flip's quantity, buy, sell and profit](docs/panel-profile-item.png)

<img src="docs/divider.png" width="100%" alt="">

#### Recipes

Bought a blade and a hilt, made a godsword, sold it? The game never tells a plugin that two items
became one, so the three trades would otherwise be counted as three separate flips — one of them
looking like a windfall and the others like losses.

**Record a recipe** on the Profile tab. Your finished trades are listed newest first; tick the ones
the recipe was made from — the purchases that went in and the sales the result went out through.
Assembling, disassembling, repairing and making or breaking sets are all covered.

![The recipe recorder, set to "Broke up a set", with a set purchase and four piece sales ticked and the count reading "1 in, 4 out"](docs/panel-recipe-pick.png)

A repair fee counts towards the cost, and the total is worked out in front of you before anything
is written down. Everything you have recorded is listed underneath, and any of it can be undone.

![The recorder's total: cost 159,995 gp, received 129,819 gp, tax 2,202 gp and a profit of -30,176 gp, above the Record button and a list of recipes already recorded, each with a Forget link](docs/panel-recipe-record.png)

Afterwards the trades are one activity with one profit, filed under the item the recipe was about,
and the Profile tab says which kind each one was.

![An item's activity list: "Broken into" and "Combined from" entries alongside an ordinary flip, each with its own quantity, buy, sell and profit](docs/panel-recipe-activity.png)

Nothing is guessed and nothing is recorded for you.

<img src="docs/divider.png" width="100%" alt="">

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

<img src="docs/divider.png" width="100%" alt="">

#### Also

- **Offer preview** — open an offer in game and the panel jumps to that item.
- **One character or all of them** — the name above the tabs picks whose trades you are looking at,
  or adds them all together.

<img src="docs/divider.png" width="100%" alt="">

## Getting started

Install the plugin and open the FlipHub panel from the sidebar. Offer tracking, buy limits and Wiki
prices work immediately — no account, no setup.

To sync to the dashboard as well, open the **Link** tab in the FlipHub panel, paste your license
key from [osrsfliphub.com/my-statistics](https://www.osrsfliphub.com/my-statistics) and click
**Link account**. The tab shows whether the device is linked; **Unlink this device** stops it —
uploads end immediately and the plugin returns to local-only.

<img src="docs/divider.png" width="100%" alt="">

## Privacy

The plugin is local-first. It watches Grand Exchange events the client already exposes and performs
no automation — it never clicks, moves, or trades for you. The only thing it writes into the game is
text in a chatbox prompt you opened yourself: a suggested price when you click one, or the decimal
point you just typed. You still confirm every offer. No RuneScape or Jagex credentials are
requested, read, or transmitted.

- **Without linking (default)** — No trade data leaves your machine. The only network calls are
  read-only price lookups to `prices.runescape.wiki`.
- **With sync enabled and linked** — Your Grand Exchange offer events (item, quantity, price, offer
  state, timestamps) are uploaded over HTTPS to `osrsfliphub.com` to power your dashboard, along
  with any recipes you record (which of those trades went into which, and the fee you entered), so
  the dashboard stops showing the parts as still held. As with any request to a third-party server,
  this exposes your IP address to it.

<img src="docs/divider.png" width="100%" alt="">

## Support

[Contact support](https://www.osrsfliphub.com/support).

<img src="docs/divider.png" width="100%" alt="">

## License

Released under the [BSD 2-Clause License](LICENSE).
