# Boss Trip Tracker

> **No combat or mechanic assistance, for any supported boss.** This plugin only tracks loot,
> supplies, costs, deaths, time and luck. It has no attack, prayer, phase or hazard cues, no
> tile or NPC highlighting, and no alerts tied to boss mechanics. The only thing it can draw on
> the game screen is an optional box, off by default, with your kill goal progress, trip
> times and profit; it never shows anything about the boss or its mechanics. It only listens to game
> events and never creates input or menu actions.

Tracks loot, supplies used and net profit for each boss trip, with persistent per-account
history in a side panel. Formerly **Maggot King Trip Tracker**.

**Supported bosses:** the Maggot King in Vampyrium, Phosani's Nightmare, and the Theatre of
Blood (Entry, Normal and Hard Mode). The regular Nightmare's kills aren't tracked yet. More
bosses are planned.

## Requirements

- The core RuneLite **Loot Tracker** plugin must be enabled. This plugin reads loot from the
  Loot Tracker's loot events (for the Maggot King, if no loot event arrives, it falls back to
  inventory changes after you open the corpse).

## Side panel

- **Share card** (camera button next to the boss dropdown): makes an image of the shown
  boss's stats (kill count, uniques received vs expected with the luck tier, kills since
  your last unique and how far past the drop rate you are, each unique, the drop chances
  with both the Expected and Received bars, the loot, costs, net
  profit and GP/hr of the kills tracked since the plugin was installed (with the kill count
  tracking began at), and your last 5 trips), copies it
  to the clipboard ready to paste into Discord, and saves it to your RuneLite screenshots
  folder under "Boss Trip Tracker". Nothing is uploaded. Your name is on the card unless you
  turn off **Show my name on share cards** (Configuration → Display).
- **Overlay** (off by default): a small box on the game screen built like RuneLite's XP
  tracker box, with the boss icon and up to three rows, each picked from one list: kill goal
  stats (KPH, TTG, kills done or left), trip stats (current kill, trip time, kills, average
  kill, PB), the trip's net profit or net GP/hr, and your luck status (the Luck card's tier);
  rows 2 and 3 can also show nothing. With a goal set there is an optional progress bar.
  Turn it on with **Show overlay** in Configuration → Overlay, or right-click the goal card,
  the trip time card or the profit card and choose **Add to canvas** (or **Remove from
  canvas**). Hold Alt and drag the box to move it. By default it only shows during a trip.
- **Boss dropdown** (top of the panel): picks the boss shown in all three tabs. Entering a
  tracked boss's area selects it; otherwise your last choice is kept, so you can browse any
  boss's trips while a trip keeps tracking in the background. A green dot marks the boss with
  a trip in progress. Bosses with modes get a row of chips under the dropdown.
- **Kill goal** (top of the Trip tab): set a kill target and see kills per hour (logged-in
  time), kills done and left, time to goal, and a progress bar. **Set goal** asks where to
  count from: the trip in progress (the default on a trip, so kills before you set the goal
  count), now, or a trip from the last 12 hours (that trip and every later one count).
  **Reset** (or right-click the card, **Count from...**) restarts a goal's count the same way. The clocks only run while you're fighting in the lair: after 30 seconds
  without dealing damage they pause (the idle time isn't counted) and restart on your next
  hit. **Pause** stops them straight away; it resumes when you press it again or, with
  **Auto-resume when I attack** on, when you next damage the boss.
- **Luck** (Trip tab, under the kill goal): uniques received vs expected with a luck tier (LUCKY AS RUCK, Lucky,
  On Rate, Dry, DRY AS RUCK), kills since your last unique, how far past the drop rate you
  are, the rate and a count of each unique and the pet, laid out like the share card. The eye
  icon collapses it to the title row, which keeps the tier. **Luck card** (Configuration →
  Display) switches to the Classic card, which adds the chance by now, the next unique's kill
  count and a progress bar. On either card, right-click
  the card to enter the kill count of your last unique from before you installed the plugin;
  the dry streak then counts from there (kills before tracking began are counted from your
  kill count) until the plugin tracks a newer unique.
- **Trip:** the current (or last) trip's time, kills, average kill time, fastest kill (PB)
  and a live timer for the kill in progress (it counts from the boss spawning, like the
  game's "Fight duration", and shows the last kill's time between kills), plus loot value,
  costs, net profit and net GP/hr. The profit card's eye icon collapses it to just net profit
  and net GP/hr (green or red).
  Below it are the Loot, Supplies and Dropped boxes. Each starts collapsed to its header; click
  its eye icon to show the item grid (the plugin remembers which boxes you opened, and History
  cards share one setting per box). Loot's header shows **Total GP** (the GE value of the
  trip's loot), **GP/Kill** and the loot by category from the boss's drop table (for example
  Uniques, Resources, Eggs), the four largest first and the rest under Other; hover a category
  to see its items. Supplies' header shows its Total GP and the cost of charges, runes, potions,
  food and anything else. In the item grids, uniques have a gold border and tarnished drops
  waiting to be polished have a dashed border.
- **History:** a profit-per-trip chart with the trip count and net for the selected chip, then
  one card per completed trip, the newest 50 first (**Show more** adds older ones). Click a
  card to expand it, right-click to delete it.
- **Lifetime:** totals across all trips (net profit and GP/hr from tracked trips, where costs are
  known), the Open-stomach / Take-eggs split, and a note of when tracking began (the plugin only
  knows kills from after it was installed), then:
  - **Drop chances:** Expected / Received bars for any unique, each unique and the pet,
    using your all-time kills and drops from RuneLite's Loot Tracker (and your kill count from
    Chat Commands) when available, otherwise the kills this plugin tracked. The tooltips give
    how dry you are and the kill counts of tracked uniques.
  - **All loot:** every drop, with a Tracked / All-time switch and the same header as the
    Trip tab's Loot box (Total GP, GP/Kill and the categories). All-time is RuneLite's Loot
    Tracker record for the boss, at today's prices (with when the record starts); Tracked is
    every trip this plugin tracked, at the prices then.
  - **All supplies:** everything used across tracked trips, by category, and anything dropped
    and left behind. Both only go back to when tracking began (RuneLite doesn't record
    supplies, so there's no all-time count); **From** shows the kill count it started at.
  - **Eggs popped:** eggs popped per tier with their Pop option (anywhere, not just in the
    lair), pets from eggs, and your total pet chance from the eggs popped so far.
  - **Polish results:** what each type of tarnished item has polished into.
  - **Data:** export the shown boss's trips as CSV, export or import the account's full
    history (every boss) as JSON (imports only add trips you don't already have; exports from
    older versions import too; files over 50 MB are refused, and anything in a file that
    can't be read is left out), and clear the shown boss's history. Export file names include
    the date and time. Your history is saved when you turn the plugin off or close the client.
    If the history file can't be read, it's kept aside and the panel tells you its name.

The panel opens on the Trip tab automatically when you enter a tracked boss's area
(Configuration → Display → **Open panel on entry**; on by default).

## How trips are counted

Nothing is tracked on Leagues, Deadman, beta or tournament worlds, which RuneLite keeps
separate records for. You've left a boss's area after about 2 seconds outside it (3 game
ticks), so a moment between rooms doesn't end a trip; the trip ends from when you stepped out.

For the Theatre of Blood: a trip is one raid, from entering the Theatre until you're back
in Ver Sinhaza (by the vault's teleport crystal, a teleport, a wipe or logging out).

- A completed raid counts from the game's completion-count message, with its mode, the team
  size at the start and the game's total completion time (which is also the raid's PB).
- Loot is the reward from your chest, from the Loot Tracker's event. If you leave the vault
  without claiming it, it's added when you claim it from the chest by the Ver Sinhaza bank
  (the game loses it if you log out first).
- Anything you get inside the raid is free: supply chest purchases and items you pick up.
  Only what you use beyond that is a cost, per dose for potions, so a brew bought from the
  chest costs nothing whether you drink it or not. Dropped gear (the salve amulet after
  Bloat) is never a cost; dropped potions and food count as used.
- Dying in a room costs nothing, and your deaths per raid are shown. If the raid ends in a
  wipe, the 100,000 coin reclaim fee is added.
- Purples are shown as yours / the team's. The team's come from the game's broadcast, which
  names only the item here; who got it is never stored.
- Luck: your chance per raid is the team's purple chance (1/9.1 Normal, 1/7.7 Hard) divided by
  the team size, which assumes equal contribution and no deaths. Entry Mode raids count for
  profit but not luck. For past raids from RuneLite's records, **Typical team size for past
  raids** (default 4) is used. The chips show All, Normal or Hard.
- **Team dry streak** (Luck card and Lifetime tab): raids since you last saw a purple from
  anyone in your team, starting from the game's own count ("You have completed 7 raids since
  you've seen any purple." when you enter the vault). It isn't your personal dry streak: a
  teammate's purple resets it, and Entry Mode raids count.
- The clock doesn't pause while idle during a raid; the time between rooms is part of it.

For Phosani's Nightmare: a trip runs from drinking from the Pool of Nightmares until you
leave the dream (through the barrier, which works like walking out of the Maggot King's lair,
by teleport, or by dying). Several kills per trip are normal. Loot comes from the Loot
Tracker's event (it lands on the floor), the kill timer counts like the game's fight clock
from when the Nightmare awakens, and Sister Senga's fee is recorded from the bank payment
message after you collect your items. Luck uses Phosani's rates (any unique about 1/111).

For the Maggot King:

- A trip starts when you enter the lair. It ends when you teleport out, die, walk out and
  don't come back within 5 minutes while staying just outside, or log out and don't return
  within 5 minutes (both grace periods are configurable).
- **Merge re-entries** (off by default) counts leaving and re-entering within the merge
  window as one trip.
- A trip without kills is kept if anything was dropped or lost, or if it used at least 1,000 gp
  of supplies or charges (a teleport out before the kill); History shows it as "No kills".
  Walking in and straight back out leaves nothing.
- A kill is counted from the game's kill-count message. Kill time comes from the game's
  "Fight duration" message.
- A kill is only created from the kill-count message or the Loot Tracker's loot, never from
  clicking the corpse, so clicking it again after an interruption doesn't add a kill.
- Loot includes items that overflow onto the ground, once you pick them up, unless the Loot
  Tracker already listed them. If the Loot Tracker reports a kill late, its loot replaces what
  was read from your inventory, so nothing is counted twice.
- Supplies are everything used up in the lair, across inventory, equipment and rune pouch.
  Potions are counted per dose. Gear switches don't count, and worn gear is never a supply:
  a charged weapon running dry, Barrows gear degrading or a blood fury turning back into an
  amulet of fury costs nothing (its charges are costed as they're used). Ammo still counts,
  and so does jewellery that breaks, such as a ring of recoil. Putting darts, scales, blood
  shards, pages or runes into a charged item isn't a supply either. Eating part of a pie or
  pizza costs only the part eaten. Your own arrows, bolts or knives picked back up come off
  the supply line again. With **Count supplies used
  before entry** (on by default), food, potions and spells used in the 60 seconds before
  entering are added to the trip too.
- Charges used during a trip count as supplies, counted from your attacks: Amulet of blood
  fury (one per damaging melee hit, priced from blood shards at 10,000 charges each), Tome
  of fire (one per fire spell, searing or burnt pages at 20 charges each, set by **Tome of
  fire pages**), revenant bows such as the Webweaver bow (one revenant ether per shot),
  Scythe of Vitur (one per attack unless every hit misses; a vial of blood and 200 blood runes
  per 100 charges), Tumeken's shadow (one per cast; 2 soul runes and 5 chaos runes each),
  Sanguinesti staff (2 blood runes a cast), Trident of the swamp (a death, a chaos, 5 fire
  runes and a Zulrah's scale a cast) and of the seas (the same runes and 10 coins), Eye of
  Ayak (a demon tear, or 2 death and a chaos rune, a cast: **Eye of Ayak charged with**) and
  the Toxic blowpipe (2 Zulrah's scales every 3 shots, plus the darts lost: an Ava's
  assembler or Dizana's quiver saves 80%, an accumulator 72%, an attractor 60%; the dart
  priced is set by **Blowpipe darts**) and the Crystal halberd (one per attack; crystal
  shards are untradeable, so it shows the count at 0 gp). This applies at every supported
  boss.
- Items you drop in the lair and don't pick up again are a "Dropped" cost. Items worth under
  100 gp each, such as empty vials, are ignored.
- Gravestone moves paid to the aranei scout are recorded as death costs.
- Tarnished drops are pending until you polish them (anywhere, any time later). The
  result replaces the oldest pending drop of that type and is valued at the GE price then.
- **Loot alerts** (Configuration → Loot alerts) notify you for uniques, the pet, or any drop
  worth at least a set amount. Nothing is drawn on the game screen.
- Values use the GE price at the time of the drop or use. If RuneLite's prices haven't loaded
  yet (or failed to load), items are recorded without a price and get the GE price as soon as
  prices load. The Lifetime tab can also show loot at today's prices (**Show today's
  value**).

## Data

History is saved per account to
`~/.runelite/plugin-data/boss-trip-tracker/history-<account>.json`. When a file from an older version is
upgraded, the old file is kept next to it as `history-<account>.json.v1-backup-<date>`.

## Credits

Built on the RuneLite client API. The on-screen box follows the layout of RuneLite's XP
tracker box (XpInfoBoxOverlay, BSD-2-Clause, Jasper Ketelaar and Anthony). The drop chances
card's Expected / Received look follows Speaax's Delve calculator plugin (BSD-2-Clause). The
Mokha Loot Tracker (Cameron Jewell, BSD-2-Clause) gave the idea of per-account JSON history and
per-dose supply tracking. No code from any of them is used; the code here is this plugin's own.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
