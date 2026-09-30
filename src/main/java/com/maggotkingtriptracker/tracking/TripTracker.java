package com.maggotkingtriptracker.tracking;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.maggotkingtriptracker.EyeOfAyakCharge;
import com.maggotkingtriptracker.MaggotKingTripTrackerConfig;
import com.maggotkingtriptracker.boss.AllTimeSource;
import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.boss.BossRegistry;
import com.maggotkingtriptracker.boss.DropKind;
import com.maggotkingtriptracker.boss.ExpectedDrop;
import com.maggotkingtriptracker.boss.LootChoice;
import com.maggotkingtriptracker.boss.RaidCompletion;
import com.maggotkingtriptracker.boss.TripModel;
import com.maggotkingtriptracker.boss.TripStat;
import com.maggotkingtriptracker.model.AccountHistory;
import com.maggotkingtriptracker.model.AllTimeCounts;
import com.maggotkingtriptracker.model.BossHistory;
import com.maggotkingtriptracker.model.ChargeType;
import com.maggotkingtriptracker.model.DeathRecord;
import com.maggotkingtriptracker.model.EggPop;
import com.maggotkingtriptracker.model.ItemEntry;
import com.maggotkingtriptracker.model.Kill;
import com.maggotkingtriptracker.model.KillGoal;
import com.maggotkingtriptracker.model.Trip;
import com.maggotkingtriptracker.model.TripEndReason;
import com.maggotkingtriptracker.model.TripClock;
import com.maggotkingtriptracker.model.SupplyCorrections;
import com.maggotkingtriptracker.model.TripMath;
import com.maggotkingtriptracker.model.VariantFilter;
import com.maggotkingtriptracker.persistence.HistoryStore;
import com.maggotkingtriptracker.pricing.PriceService;
import com.maggotkingtriptracker.view.BossOption;
import com.maggotkingtriptracker.view.PanelState;
import com.maggotkingtriptracker.view.TripView;
import com.maggotkingtriptracker.view.ViewBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Tracks boss trips from game events: trip boundaries, kills, loot, supplies, drops and deaths. What is boss
 * specific comes from the {@link BossDefinition} of the area you're in. All state lives on the client thread. It only listens to events and never creates input or draws anything.
 */
@Slf4j
public class TripTracker
{
	private static final int CLICK_MATCH_TICKS = RecentClicks.MATCH_TICKS;
	/**
	 * After a corpse click, ground spawns count as loot overflow and pet messages count for this kill.
	 */
	private static final int CORPSE_WINDOW_TICKS = 10;
	/**
	 * If the Loot Tracker has not reported the kill this many ticks after the corpse click,
	 * inventory gains are used instead.
	 */
	private static final int LOOT_FALLBACK_TICKS = 8;
	/**
	 * Paying the aranei scout goes through dialogue, so allow about a minute after clicking it.
	 */
	private static final int GRAVE_MOVE_WINDOW_TICKS = 100;
	/**
	 * Container changes this soon after respawning are the death itself, not consumption.
	 */
	private static final int POST_DEATH_IGNORE_TICKS = 5;
	private static final long PRE_ENTRY_WINDOW_MS = 60_000;
	/**
	 * Dropped items worth less than this each (empty vials are 2 gp) are junk, not a cost.
	 */
	private static final long JUNK_PRICE = 100;
	private static final long SAVE_DELAY_MS = 1_000;
	private static final long ACTIVE_SAVE_INTERVAL_MS = 60_000;

	private static final String OPTION_DROP = "Drop";
	private static final String OPTION_POLISH = PolishTracker.OPTION_POLISH;
	private static final String OPTION_CAST = "Cast";
	private static final Set<String> CONSUME_OPTIONS = ImmutableSet.of("Eat", "Drink", "Cast");

	private final Client client;
	private final ClientThread clientThread;
	private final MaggotKingTripTrackerConfig config;
	private final PriceService prices;
	private final ViewBuilder viewBuilder;
	private final HistoryStore store;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Consumer<PanelState> stateListener;
	private final Consumer<String> alerter;
	private final Runnable onLairEntered;
	private final AllTimeRecords allTimeRecords;
	private final InventoryLedger ledger;
	private final BossRegistry registry;
	private final RecentClicks recentClicks = new RecentClicks();
	private final EggTracker eggTracker;
	private final PolishTracker polishTracker;
	/**
	 * Items any boss converts rather than uses up (eggs, tarnished items), wherever they are converted.
	 */
	private final Set<Integer> convertedItems = new HashSet<>();
	private final Set<Integer> bossNpcIds = new HashSet<>();
	private final Map<String, String> bossNames = new HashMap<>();

	/**
	 * The boss the panel shows. Changing it never affects tracking.
	 */
	private BossDefinition selectedBoss;
	/**
	 * Variant chip selected for the shown boss; null for All.
	 */
	private String selectedVariant;

	private AccountHistory history;
	private boolean readOnly;
	private long accountHash = -1;
	private boolean loading;

	private Trip currentTrip;
	/**
	 * The boss of the current trip; null when there is none.
	 */
	private BossDefinition tripBoss;
	/**
	 * When the player logged out mid-trip; the trip resumes if they are back within the grace period.
	 */
	private Long suspendedAt;
	private Trip lastEndedTrip;
	private BossDefinition lastEndedBoss;

	private boolean inArea;
	/**
	 * The boss whose area you're in; null outside.
	 */
	private BossDefinition areaBoss;
	private boolean dead;
	private int ignoreDeltasUntilTick = -1;
	private DeathRecord pendingDeath;
	private BossDefinition deathBoss;
	private int graveWindowEndTick = -1;

	private Kill lootKill;
	private int lastKillTick = -100;
	private int lastCorpseClickTick = -100;
	private boolean lootReceived;
	private int fallbackEndTick = -1;
	private final Map<Integer, Long> fallbackGains = new HashMap<>();
	private Long bossSpawnedAt;
	private Long bossDiedAt;
	/**
	 * When the boss you're fighting spawned, for the live kill timer; null between kills. The game's Fight
	 * duration is the time from the spawn to the kill-count message (checked against the diagnostic logs).
	 */
	private Long fightStartedAt;

	private int lastBankTick = -100;
	private final List<GroundEntry> groundItems = new ArrayList<>();
	private final List<GroundEntry> recentDespawns = new ArrayList<>();
	private final Map<Integer, Long> pendingDrops = new HashMap<>();
	private final ChargeCounter chargeCounter;
	/**
	 * Why the clock is stopped while in the lair on a trip; null while it runs. Kills, loot and supplies still count.
	 */
	private InLairPause inLairPause;
	/**
	 * Last time the player dealt a hitsplat in the lair (or entered it), for the idle pause.
	 */
	private long lastActivityAt;
	/**
	 * The open trip is waiting outside the lair (walked out), rather than logged out.
	 */
	private boolean suspendedOutside;
	private boolean runeIdsLoaded;
	private final Deque<PreEntryUse> preEntryUses = new ArrayDeque<>();

	// ---- Raids (TripModel.ONE_RAID) ----
	private final RaidState raid = new RaidState();
	private final FreeSupplies freeSupplies = new FreeSupplies();
	/**
	 * The mode named by the entry message, which comes a tick before you're inside.
	 */
	private String enteredRaidMode;
	private int enteredRaidModeTick = -100;
	/**
	 * A completed raid whose reward hasn't been claimed yet: it can be claimed after leaving (the chest by the bank),
	 * until logging out.
	 */
	private Kill unclaimedRaid;
	private BossDefinition unclaimedRaidBoss;

	private ScheduledFuture<?> saveFuture;
	private long lastPeriodicSave;
	private boolean viewDirty = true;
	private boolean historyDirty = true;
	private List<TripView> historyViews = Collections.emptyList();

	public TripTracker(Client client, ClientThread clientThread, MaggotKingTripTrackerConfig config,
		PriceService prices, HistoryStore store, Gson gson, ScheduledExecutorService executor,
		Consumer<PanelState> stateListener, Consumer<String> alerter, Runnable onLairEntered,
		ConfigManager configManager, BossRegistry registry)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.prices = prices;
		this.viewBuilder = new ViewBuilder(prices);
		this.store = store;
		this.gson = gson;
		this.executor = executor;
		this.stateListener = stateListener;
		this.alerter = alerter;
		this.onLairEntered = onLairEntered;
		this.allTimeRecords = new AllTimeRecords(configManager, gson);
		this.ledger = new InventoryLedger(client);
		this.chargeCounter = new ChargeCounter(prices::isMeleeWeapon);
		this.registry = registry;
		TrackerHost host = new Host();
		this.eggTracker = new EggTracker(registry, prices, recentClicks, host);
		this.polishTracker = new PolishTracker(registry, prices, host);
		for (BossDefinition boss : registry.all())
		{
			convertedItems.addAll(boss.getConvertedItems());
			bossNpcIds.addAll(boss.getBossNpcIds());
		}
		BossDefinition saved = registry.byId(config.selectedBoss());
		this.selectedBoss = saved != null ? saved : registry.first();
	}

	/**
	 * Call on the client thread after registering, to pick up a session that is already logged in.
	 */
	public void start()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			ensureAccountLoaded();
			chargeCounter.gearChanged(client.getTickCount(), gear(client.getItemContainer(InventoryID.WORN)));
		}
		pushState();
	}

	public void shutDown()
	{
		if (saveFuture != null)
		{
			saveFuture.cancel(false);
			saveFuture = null;
		}
	}

	// ---- Panel actions (call on the client thread) ----

	/**
	 * Shows this boss's trips and stats in the panel. Tracking carries on whatever is shown.
	 */
	public void selectBoss(String bossId)
	{
		BossDefinition boss = registry.byId(bossId);
		if (boss == null || boss == selectedBoss)
		{
			return;
		}
		selectedBoss = boss;
		selectedVariant = null;
		config.setSelectedBoss(boss.getId());
		historyChanged();
		pushState();
	}

	/**
	 * @param variant a variant id of the shown boss, or null for All
	 */
	public void selectVariant(String variant)
	{
		selectedVariant = variant;
		historyChanged();
		pushState();
	}

	/**
	 * Sets (or with null clears) the kill count of your last unique from before tracking, for the shown boss.
	 */
	public void setLastUniqueKc(Integer killCount)
	{
		BossHistory boss = writableHistory(selectedBoss);
		if (boss == null)
		{
			return;
		}
		boss.setLastUniqueKc(killCount);
		historyChanged();
		saveNow();
		pushState();
	}

	public void deleteTrip(String tripId)
	{
		if (history == null || readOnly)
		{
			return;
		}

		for (BossHistory boss : history.getBosses().values())
		{
			Trip trip = findTrip(boss, tripId);
			if (trip == null)
			{
				continue;
			}
			boss.getTrips().remove(trip);
			if (trip == currentTrip)
			{
				forgetCurrentTrip();
			}
			if (trip == lastEndedTrip)
			{
				lastEndedTrip = null;
			}
			if (pendingDeath != null && trip.getDeaths().contains(pendingDeath))
			{
				pendingDeath = null;
			}
		}
		historyChanged();
		saveNow();
		pushState();
	}

	/**
	 * Deletes every trip of the shown boss.
	 */
	public void clearHistory()
	{
		BossHistory boss = writableHistory(selectedBoss);
		if (boss == null)
		{
			return;
		}

		boss.getTrips().clear();
		if (tripBoss == selectedBoss)
		{
			forgetCurrentTrip();
			pendingDeath = null;
			pendingDrops.clear();
			groundItems.clear();
			recentDespawns.clear();
		}
		if (lastEndedBoss == selectedBoss)
		{
			lastEndedTrip = null;
		}
		historyChanged();
		saveNow();
		pushState();
	}

	private void forgetCurrentTrip()
	{
		currentTrip = null;
		tripBoss = null;
		suspendedAt = null;
		suspendedOutside = false;
		inLairPause = null;
		lootKill = null;
	}

	/**
	 * @return this account's full history (every boss) as JSON, or null if none is loaded. Client thread.
	 */
	public String exportJson()
	{
		return history == null ? null : gson.toJson(history);
	}

	/**
	 * @return the shown boss's completed trips as CSV (oldest first), or null if no history is loaded. Client thread.
	 */
	public String exportCsv()
	{
		if (history == null)
		{
			return null;
		}

		BossDefinition boss = selectedBoss;
		StringBuilder csv = new StringBuilder("start,end,active_minutes,end_reason,kills,");
		for (TripStat column : boss.getCsvColumns())
		{
			csv.append(column.getLabel()).append(',');
		}
		csv.append("deaths,pet,loot_gp,supplies_gp,dropped_gp,death_costs_gp,net_gp,gp_per_hour,avg_kill_seconds\n");
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		for (Trip trip : history.boss(boss.getId()).getTrips())
		{
			if (trip.isOpen())
			{
				continue;
			}
			long net = TripMath.netProfit(trip);
			Long averageKill = TripMath.averageKillMs(Collections.singletonList(trip));
			csv.append(format.format(Instant.ofEpochMilli(trip.getStartedAt()).atZone(ZoneId.systemDefault()))).append(',')
				.append(format.format(Instant.ofEpochMilli(trip.getEndedAt()).atZone(ZoneId.systemDefault()))).append(',')
				.append(String.format(Locale.ROOT, "%.1f", trip.getActiveMs() / 60_000.0)).append(',')
				.append(trip.getEndReason()).append(',')
				.append(trip.getKills().size()).append(',');
			for (TripStat column : boss.getCsvColumns())
			{
				csv.append(column.valueOf(trip)).append(',');
			}
			csv.append(trip.getDeaths().size()).append(',')
				.append(trip.getKills().stream().anyMatch(Kill::isPet)).append(',')
				.append(TripMath.lootValue(trip)).append(',')
				.append(TripMath.supplyCost(trip)).append(',')
				.append(TripMath.droppedCost(trip)).append(',')
				.append(TripMath.deathCost(trip)).append(',')
				.append(net).append(',')
				.append(TripMath.gpPerHour(net, trip.getActiveMs())).append(',')
				.append(averageKill == null ? "" : String.format(Locale.ROOT, "%.1f", averageKill / 1000.0))
				.append('\n');
		}
		return csv.toString();
	}

	/**
	 * Describes what importing would do, or returns an error message starting with "!". Client thread.
	 */
	public String describeImport(AccountHistory imported)
	{
		if (history == null)
		{
			return "!Log in first so the history can be imported into your account.";
		}
		if (readOnly)
		{
			return "!This account's history is read-only.";
		}
		if (imported.getSchemaVersion() > AccountHistory.CURRENT_SCHEMA_VERSION)
		{
			return "!This file is from a newer version of the plugin.";
		}

		int added = 0;
		int total = 0;
		for (Map.Entry<String, BossHistory> e : imported.getBosses().entrySet())
		{
			BossHistory mine = history.getBosses().get(e.getKey());
			for (Trip trip : e.getValue().getTrips())
			{
				total++;
				if (isImportable(trip, mine))
				{
					added++;
				}
			}
		}
		String account = imported.getAccountHash() != 0 && imported.getAccountHash() != accountHash
			? " The file is from a different account" + (imported.getLastDisplayName() != null
			? " (" + imported.getLastDisplayName() + ")" : "") + "."
			: "";
		return "Add " + added + " of " + total + " trips from the file to this account?"
			+ " Trips you already have are skipped." + account;
	}

	private static boolean isImportable(Trip trip, BossHistory mine)
	{
		return trip.getId() != null && !trip.isOpen() && (mine == null || findTrip(mine, trip.getId()) == null);
	}

	/**
	 * Adds trips, egg pops and polish outcomes from an export that aren't already here, boss by boss. Client thread.
	 */
	public void importHistory(AccountHistory imported, int sourceVersion)
	{
		if (describeImport(imported).startsWith("!"))
		{
			return;
		}
		if (sourceVersion < SupplyCorrections.SCYTHE_FIXED_IN_SCHEMA)
		{
			SupplyCorrections.repriceScythe(imported, prices.price(ItemID.BLOODRUNE));
		}

		imported.getBosses().forEach((bossId, theirs) ->
		{
			BossHistory mine = history.boss(bossId);
			for (Trip trip : theirs.getTrips())
			{
				if (isImportable(trip, mine))
				{
					mine.getTrips().add(trip);
				}
			}
			mine.getTrips().sort(Comparator.comparingLong(Trip::getStartedAt));

			for (EggPop pop : theirs.getEggPops())
			{
				boolean known = mine.getEggPops().stream()
					.anyMatch(p -> p.getAt() == pop.getAt() && p.getEggItemId() == pop.getEggItemId());
				if (!known)
				{
					mine.getEggPops().add(pop);
				}
			}
			mine.getEggPops().sort(Comparator.comparingLong(EggPop::getAt));

			// The game's own dry streak counts: keep the most recent
			if (theirs.getGameDryStreakKc() != null
				&& (mine.getGameDryStreakKc() == null || theirs.getGameDryStreakKc() > mine.getGameDryStreakKc()))
			{
				mine.setGameDryStreak(theirs.getGameDryStreak());
				mine.setGameDryStreakKc(theirs.getGameDryStreakKc());
			}
			if (theirs.getGameTeamDryStreakAt() != null
				&& (mine.getGameTeamDryStreakAt() == null || theirs.getGameTeamDryStreakAt() > mine.getGameTeamDryStreakAt()))
			{
				mine.setGameTeamDryStreak(theirs.getGameTeamDryStreak());
				mine.setGameTeamDryStreakAt(theirs.getGameTeamDryStreakAt());
			}

			// Tallies can't be told apart, so keep the larger count rather than adding (re-importing is safe)
			theirs.getPolishOutcomes().forEach((tarnished, outcomes) ->
			{
				Map<Integer, Integer> tally = mine.getPolishOutcomes().computeIfAbsent(tarnished, k -> new LinkedHashMap<>());
				outcomes.forEach((result, count) -> tally.merge(result, count, Math::max));
			});
		});

		historyChanged();
		saveNow();
		pushState();
	}

	/**
	 * Sets the shown boss's kill goal target, keeping progress if a goal is already running. 0 or less removes it.
	 */
	public void setGoal(int target)
	{
		BossHistory boss = writableHistory(selectedBoss);
		if (boss == null)
		{
			return;
		}
		if (target <= 0)
		{
			boss.setGoal(null);
		}
		else if (boss.getGoal() == null)
		{
			boss.setGoal(new KillGoal(target, System.currentTimeMillis(), 0));
		}
		else
		{
			boss.getGoal().setTarget(target);
		}
		viewDirty = true;
		saveNow();
		pushState();
	}

	/**
	 * Pause or resume the trip clock (and with it the goal clock) while in the boss's area on a trip.
	 */
	public void togglePause()
	{
		if (!inArea || currentTrip == null || suspendedAt != null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (inLairPause == null)
		{
			pauseInLair(InLairPause.MANUAL, now);
		}
		else
		{
			resumeInLair(now);
		}
	}

	/**
	 * @param end when the clock stops; for an idle pause this is the last hit, so the idle time is left out
	 */
	private void pauseInLair(InLairPause reason, long end)
	{
		TripClock.stop(currentTrip, goal(), end);
		currentTrip.setLastActiveAt(end);
		inLairPause = reason;
		viewDirty = true;
		requestSave();
		pushState();
	}

	private void resumeInLair(long now)
	{
		inLairPause = null;
		lastActivityAt = now;
		TripClock.start(currentTrip, now);
		currentTrip.setLastActiveAt(now);
		viewDirty = true;
		requestSave();
		pushState();
	}

	/**
	 * The goal whose clock runs with the current trip.
	 */
	private KillGoal goal()
	{
		return history == null || tripBoss == null ? null : history.boss(tripBoss.getId()).getGoal();
	}

	private long idlePauseMs()
	{
		return TimeUnit.SECONDS.toMillis(config.idlePauseSeconds());
	}

	/**
	 * Restarts the shown boss's goal kill count and clock from now.
	 */
	public void resetGoal()
	{
		BossHistory boss = writableHistory(selectedBoss);
		if (boss == null || boss.getGoal() == null)
		{
			return;
		}
		boss.getGoal().setStartedAt(System.currentTimeMillis());
		boss.getGoal().setActiveMs(0);
		viewDirty = true;
		saveNow();
		pushState();
	}

	public void refreshView()
	{
		historyChanged();
		pushState();
	}

	/**
	 * RuneLite's Loot Tracker or Chat Commands saved a record, which it does some seconds after a drop or kill.
	 * The luck numbers read those records, so show the new values.
	 */
	public void allTimeRecordsChanged(String group, String key)
	{
		for (BossDefinition boss : registry.all())
		{
			for (AllTimeSource source : boss.getAllTimeSources())
			{
				boolean match = AllTimeRecords.LOOT_TRACKER_GROUP.equals(group) ? source.getLootTrackerKey().equals(key)
					: key.equals(source.getKillCountKey());
				if (match && boss == selectedBoss)
				{
					viewDirty = true;
					pushState();
					return;
				}
			}
		}
	}

	/**
	 * @return whether a config change in this group can be one of the records the luck numbers read
	 */
	public static boolean isAllTimeRecordGroup(String group)
	{
		return AllTimeRecords.LOOT_TRACKER_GROUP.equals(group) || AllTimeRecords.KILL_COUNT_GROUP.equals(group);
	}

	// ---- Events ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				ensureAccountLoaded();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				if (inArea)
				{
					suspendTrip(System.currentTimeMillis());
				}
				ledger.reset();
				chargeCounter.reset();
				recentClicks.clear();
				preEntryUses.clear();
				// Unclaimed raid rewards are lost on logout
				unclaimedRaid = null;
				unclaimedRaidBoss = null;
				pushState();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		int tick = client.getTickCount();
		long now = System.currentTimeMillis();

		// The player's name isn't available yet when the history loads at login
		String name = player.getName();
		if (history != null && name != null && !name.equals(history.getLastDisplayName()))
		{
			history.setLastDisplayName(name);
			viewDirty = true;
			requestSave();
		}

		if (!runeIdsLoaded)
		{
			loadRuneIds();
		}

		// Attacks from the previous tick are complete, including gear switched in that tick
		chargeCounter.process(tick - 1, this::chargesUsed);

		// Changes are attributed to where the player was before any region change this tick
		Map<Integer, Long> delta = ledger.poll();
		if (!delta.isEmpty())
		{
			processDelta(delta, tick, now);
		}
		applyLootFallback(tick);
		polishTracker.tick(tick);

		int region = WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID();
		BossDefinition regionBoss = registry.forRegion(region);
		if (inArea && regionBoss != areaBoss)
		{
			// Left the area (or went straight into another boss's)
			inArea = false;
			areaBoss = null;
			leaveLair(region, tick, now);
		}
		if (regionBoss != null && !inArea)
		{
			inArea = true;
			areaBoss = regionBoss;
			enterLair(regionBoss, now);
			onLairEntered.run();
		}

		if (!inArea && currentTrip != null && suspendedAt != null)
		{
			if (suspendedOutside)
			{
				// The trip only waits while you stay just outside
				if (!tripBoss.getWaitingRegions().contains(region)
					|| now - suspendedAt > TimeUnit.MINUTES.toMillis(config.outsideGraceMinutes()))
				{
					endTrip(TripEndReason.WALKED_OUT, suspendedAt);
				}
			}
			else if (now - suspendedAt > TimeUnit.MINUTES.toMillis(config.logoutGraceMinutes()))
			{
				endTrip(TripEndReason.LOGOUT, suspendedAt);
			}
		}

		if (inArea && currentTrip != null && isRaid(tripBoss))
		{
			int players = 0;
			for (int varbit : tripBoss.getTeamSlotVarbits())
			{
				if (client.getVarbitValue(varbit) != 0)
				{
					players++;
				}
			}
			raid.teamSeen(players);
		}

		// Raids don't pause while idle: the time between rooms is part of the raid
		if (inArea && currentTrip != null && suspendedAt == null && inLairPause == null && !isRaid(tripBoss)
			&& TripClock.idle(lastActivityAt, now, idlePauseMs()))
		{
			pauseInLair(InLairPause.IDLE, lastActivityAt);
		}

		if (inArea && currentTrip != null && now - lastPeriodicSave > ACTIVE_SAVE_INTERVAL_MS)
		{
			lastPeriodicSave = now;
			requestSave();
		}

		prune(tick, now);
		if (viewDirty || historyDirty)
		{
			pushState();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.INV || containerId == InventoryID.WORN)
		{
			ledger.markDirty();
		}
		if (containerId == InventoryID.WORN)
		{
			chargeCounter.gearChanged(client.getTickCount(), gear(event.getItemContainer()));
		}
		else if (containerId == InventoryID.BANK)
		{
			lastBankTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (InventoryLedger.RUNE_POUCH_VARBITS.contains(event.getVarbitId()))
		{
			ledger.markDirty();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer() && actor.getAnimation() != -1)
		{
			chargeCounter.animation(client.getTickCount(), actor.getAnimation());
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		Set<Integer> ids = new HashSet<>();
		for (ActorSpotAnim spotAnim : actor.getSpotAnims())
		{
			ids.add(spotAnim.getId());
		}
		chargeCounter.spotAnimsChanged(client.getTickCount(), ids);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat.isMine() && event.getActor() instanceof NPC)
		{
			chargeCounter.hitsplat(client.getTickCount(), hitsplat.getHitsplatType(), hitsplat.getAmount());
			if (inArea && currentTrip != null && suspendedAt == null)
			{
				long now = System.currentTimeMillis();
				lastActivityAt = now;
				boolean onBoss = tripBoss.getBossNpcIds().contains(((NPC) event.getActor()).getId()) && hitsplat.getAmount() > 0;
				if (inLairPause == InLairPause.IDLE
					|| (inLairPause == InLairPause.MANUAL && config.autoResumeOnAttack() && onBoss))
				{
					resumeInLair(now);
				}
			}
		}
	}

	/**
	 * Charges used in the lair are supplies, priced from the item that recharges them.
	 */
	private void chargesUsed(ChargeType type, int used)
	{
		if (!inArea || currentTrip == null || dead)
		{
			return;
		}

		// Priced from everything one recharge takes (e.g. a vial of blood and 200 blood runes for 100 scythe charges)
		if (type == ChargeType.EYE_OF_AYAK && config.eyeOfAyakCharge() == EyeOfAyakCharge.RUNES)
		{
			type = ChargeType.EYE_OF_AYAK_RUNES;
		}
		int chargeItemId = type == ChargeType.TOME_OF_FIRE ? config.tomePage().getItemId()
			: type.isBlowpipeDarts() ? config.blowpipeDarts().getItemId()
			: type.getChargeItemId();
		long rechargePrice = 0;
		for (ChargeType.Component component : type.getComponents())
		{
			int itemId = component == type.getComponents().get(0) ? chargeItemId : component.getItemId();
			rechargePrice += component.getQuantity() * prices.price(itemId);
		}
		ItemEntries.merge(currentTrip.getSupplies(), ItemEntry.charges(type.getSourceItemId(), used,
			chargeItemId, rechargePrice, type.getChargesPerRecharge()));
		viewDirty = true;
		requestSave();
	}

	private static ChargeCounter.Gear gear(ItemContainer worn)
	{
		if (worn == null)
		{
			return ChargeCounter.Gear.NONE;
		}
		return new ChargeCounter.Gear(
			wornId(worn, EquipmentInventorySlot.WEAPON),
			wornId(worn, EquipmentInventorySlot.SHIELD),
			wornId(worn, EquipmentInventorySlot.AMULET),
			wornId(worn, EquipmentInventorySlot.CAPE));
	}

	private static int wornId(ItemContainer worn, EquipmentInventorySlot slot)
	{
		Item item = worn.getItem(slot.getSlotIdx());
		return item == null ? -1 : item.getId();
	}


	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		int tick = client.getTickCount();
		String option = event.getMenuOption();
		int itemId = event.getItemId();
		recentClicks.add(tick, option, itemId);
		polishTracker.menuClicked(option, itemId, tick);
		eggTracker.menuClicked(option, itemId, tick);

		NPC npc = event.getMenuEntry().getNpc();
		if (npc == null)
		{
			return;
		}

		if (inArea && currentTrip != null && tripBoss.getLootTriggerNpcs().contains(npc.getId()))
		{
			LootChoice choice = tripBoss.choiceForOption(option);
			if (choice == null && !tripBoss.getLootChoices().isEmpty())
			{
				return;
			}

			if (tick - lastCorpseClickTick <= CORPSE_WINDOW_TICKS)
			{
				// Repeated clicks on the same corpse
				return;
			}

			Kill kill = lootKill != null && lootKill.getChoice() == null ? lootKill : newKill();
			kill.setChoice(choice == null ? null : choice.getKey());
			viewDirty = true;
			lastCorpseClickTick = tick;
			if (!lootReceived)
			{
				fallbackEndTick = tick + LOOT_FALLBACK_TICKS;
				fallbackGains.clear();
			}
		}
		else if (pendingDeath != null && deathBoss.getGraveHelperNpcs().contains(npc.getId()))
		{
			graveWindowEndTick = tick + GRAVE_MOVE_WINDOW_TICKS;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// The Theatre of Blood also sends some of its messages as friends chat notifications (its damage summaries);
		// the purple broadcast may be one of them (unverified)
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}

		String message = event.getMessage();
		long now = System.currentTimeMillis();
		int tick = client.getTickCount();

		if (ChatPatterns.isDeathMessage(message))
		{
			markDead(now);
			return;
		}
		if (ChatPatterns.isPetMessage(message))
		{
			petMessage(tick);
			return;
		}
		Boolean thrall = ChatPatterns.thrall(message);
		if (thrall != null)
		{
			// A thrall's hitsplats look like the player's own; blood fury counting leaves them out
			chargeCounter.thrallChanged(tick, thrall);
			return;
		}
		String text = Text.removeTags(message);
		if (raidMessage(text, tick, now))
		{
			return;
		}
		if (pendingDeath != null && tick <= graveWindowEndTick)
		{
			// Some reclaim fees come out of the bank, so only the message shows them (Sister Senga)
			Long fee = deathBoss.reclaimFee(text);
			if (fee != null)
			{
				pendingDeath.setReclaimFee(pendingDeath.getReclaimFee() + fee);
				historyChanged();
				requestSave();
				return;
			}
		}
		if (!inArea || currentTrip == null)
		{
			return;
		}

		Long fightDelay = tripBoss.fightStartDelayMs(text);
		if (fightDelay != null)
		{
			fightStartedAt = now + fightDelay;
			bossSpawnedAt = fightStartedAt;
			bossDiedAt = null;
			viewDirty = true;
			return;
		}

		ChatPatterns.KillCount killCount = ChatPatterns.killCount(message);
		if (killCount != null && isKillName(tripBoss, killCount.getName()))
		{
			recordKill(killCount.getCount(), variantForKillName(tripBoss, killCount.getName()), tick, now);
			return;
		}

		Long duration = ChatPatterns.fightDurationMs(message);
		if (duration != null)
		{
			if (lootKill != null && tick - lastKillTick <= 2)
			{
				lootKill.setDurationMs(duration);
				viewDirty = true;
			}
			return;
		}

	}

	/**
	 * Raid progress messages: the mode, rooms cleared, your deaths, the raid's time and completion, purples and the
	 * game's dry streak.
	 *
	 * @return whether the message was one of them
	 */
	private boolean raidMessage(String text, int tick, long now)
	{
		if (!inArea)
		{
			// The entry message comes a tick before you're inside
			for (BossDefinition boss : registry.all())
			{
				String mode = isRaid(boss) ? boss.raidMode(text) : null;
				if (mode != null)
				{
					enteredRaidMode = mode;
					enteredRaidModeTick = tick;
					return true;
				}
			}
			return false;
		}
		if (currentTrip == null || !isRaid(tripBoss))
		{
			return false;
		}

		BossDefinition boss = tripBoss;
		String mode = boss.raidMode(text);
		if (mode != null)
		{
			raid.modeSeen(mode);
			if (boss.isRaidRoomComplete(text))
			{
				raid.roomCleared();
			}
			return true;
		}
		if (boss.isOwnRaidDeath(text))
		{
			markDead(now);
			return true;
		}
		Long raidTime = boss.raidTimeMs(text);
		if (raidTime != null)
		{
			raid.raidTime(raidTime);
			return true;
		}
		RaidCompletion completion = boss.raidCompletion(text);
		if (completion != null)
		{
			recordRaid(completion, tick, now);
			return true;
		}
		String uniqueName = boss.teamUniqueName(text);
		if (uniqueName != null)
		{
			teamUnique(boss, uniqueName);
			return true;
		}
		Integer dryStreak = boss.gameDryStreak(text);
		if (dryStreak != null)
		{
			BossHistory bossHistory = writableHistory(boss);
			Integer killCount = lootKill != null && raid.isCompleted() ? lootKill.getKillCount() : null;
			if (bossHistory != null && killCount != null)
			{
				bossHistory.setGameDryStreak(dryStreak);
				bossHistory.setGameDryStreakKc(killCount);
				historyChanged();
				requestSave();
			}
			return true;
		}
		Integer teamDryStreak = boss.gameTeamDryStreak(text);
		if (teamDryStreak != null)
		{
			BossHistory bossHistory = writableHistory(boss);
			if (bossHistory != null && lootKill != null && raid.isCompleted())
			{
				bossHistory.setGameTeamDryStreak(teamDryStreak);
				bossHistory.setGameTeamDryStreakAt(lootKill.getEndedAt());
				historyChanged();
				requestSave();
			}
			return true;
		}
		return false;
	}

	/**
	 * A completed raid is its trip's one kill. Its reward is claimed from a chest, maybe after leaving.
	 */
	private void recordRaid(RaidCompletion completion, int tick, long now)
	{
		recordKill(tripBoss.raidKillCount(completion, allTimeRecords::killCount), completion.getVariant(), tick, now);
		lootKill.setPartySize(raid.getTeamSize());
		if (raid.getRaidTimeMs() != null)
		{
			lootKill.setDurationMs(raid.getRaidTimeMs());
		}
		lootKill.getTeamUniques().addAll(raid.getTeamUniques());
		raid.getTeamUniques().clear();
		raid.completed();
	}

	/**
	 * A purple for anyone in the raid. Only the item is kept, never who got it.
	 */
	private void teamUnique(BossDefinition boss, String name)
	{
		int itemId = uniqueNamed(boss, name);
		if (itemId < 0)
		{
			return;
		}
		if (raid.isCompleted() && lootKill != null)
		{
			lootKill.getTeamUniques().add(itemId);
			viewDirty = true;
			requestSave();
		}
		else
		{
			raid.getTeamUniques().add(itemId);
		}
	}

	/**
	 * @return the boss's unique with this name ("(uncharged)" or not), or -1
	 */
	private int uniqueNamed(BossDefinition boss, String name)
	{
		String wanted = baseName(name);
		for (ExpectedDrop drop : boss.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE && baseName(prices.name(drop.getItemId())).equals(wanted))
			{
				return drop.getItemId();
			}
		}
		return -1;
	}

	private static String baseName(String name)
	{
		return name.toLowerCase(Locale.ROOT).replace("(uncharged)", "").replaceAll("[.!]+$", "").trim();
	}

	private static boolean isRaid(BossDefinition boss)
	{
		return boss != null && boss.getTripModel() == TripModel.ONE_RAID;
	}

	/**
	 * Pet messages are shared by every pet, so they only count right after a corpse or egg interaction.
	 */
	private void petMessage(int tick)
	{
		// Right after the corpse click, or for bosses without a corpse, right after the kill
		int lootTick = currentTrip != null && tripBoss.getLootTriggerNpcs().isEmpty() ? lastKillTick : lastCorpseClickTick;
		if (inArea && currentTrip != null && lootKill != null && tick - lootTick <= CORPSE_WINDOW_TICKS)
		{
			lootKill.setPet(true);
			int petItem = tripBoss.getPet() == null ? -1 : tripBoss.getPet().getItemId();
			boolean listed = lootKill.getLoot().stream().anyMatch(e -> e.getItemId() == petItem);
			if (!listed && petItem > 0)
			{
				lootKill.getLoot().add(new ItemEntry(petItem, 1, 0));
			}
			alertPet(tripBoss.getDisplayName() + (tripBoss.getLootTriggerNpcs().isEmpty() ? " pet!" : " pet from the corpse!"));
			viewDirty = true;
			requestSave();
		}
		else if (eggTracker.recentlyClicked(tick))
		{
			eggTracker.petMessage(tick);
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (raidLoot(event))
		{
			return;
		}
		if (event.getType() == LootRecordType.EVENT)
		{
			polishTracker.lootEvent(event, client.getTickCount());
			return;
		}
		if (!inArea || currentTrip == null || !tripBoss.isLootEvent(event.getName(), event.getType(), bossName(tripBoss)))
		{
			return;
		}

		Kill kill = lootKillOrCreate();
		for (ItemStack stack : event.getItems())
		{
			addLoot(kill, stack.getId(), stack.getQuantity());
			alertForDrop(tripBoss, stack.getId(), stack.getQuantity());
		}
		lootReceived = true;
		fallbackEndTick = -1;
		fallbackGains.clear();
		viewDirty = true;
		requestSave();
	}

	/**
	 * A raid's reward, claimed in the raid or later from the chest outside (until logging out).
	 *
	 * @return whether the event was a raid reward
	 */
	private boolean raidLoot(LootReceived event)
	{
		boolean inRaid = currentTrip != null && isRaid(tripBoss);
		BossDefinition boss = inRaid ? tripBoss : unclaimedRaidBoss;
		if (boss == null || !boss.isRaidLootEvent(event.getName(), event.getType()))
		{
			return false;
		}
		Kill kill = inRaid ? (raid.isCompleted() ? lootKill : null) : unclaimedRaid;
		unclaimedRaid = null;
		unclaimedRaidBoss = null;
		if (kill == null)
		{
			// The reward of a raid this plugin didn't see completed
			return true;
		}
		for (ItemStack stack : event.getItems())
		{
			ItemEntries.merge(kill.getLoot(), stack.getId(), stack.getQuantity(), prices.price(stack.getId()), false);
			alertForDrop(boss, stack.getId(), stack.getQuantity());
		}
		historyChanged();
		requestSave();
		return true;
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer())
		{
			markDead(System.currentTimeMillis());
		}
		else if (actor instanceof NPC && bossNpcIds.contains(((NPC) actor).getId()))
		{
			bossDiedAt = System.currentTimeMillis();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		BossDefinition boss = registry.forBossNpc(event.getNpc().getId());
		if (boss != null && boss.isFightStartOnSpawn())
		{
			bossSpawnedAt = System.currentTimeMillis();
			bossDiedAt = null;
			fightStartedAt = bossSpawnedAt;
			viewDirty = true;
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		if (!inArea || currentTrip == null || item.getOwnership() != TileItem.OWNERSHIP_SELF)
		{
			return;
		}

		int tick = client.getTickCount();
		GroundKind kind;
		Kill kill = null;
		if (recentClicks.has(OPTION_DROP, item.getId(), tick) || tripBoss.getRecoverableItems().contains(item.getId()))
		{
			kind = GroundKind.OWN_DROP;
		}
		else if (tripBoss.isGroundOverflowLoot() && lootKill != null && tick - lastCorpseClickTick <= CORPSE_WINDOW_TICKS)
		{
			kind = GroundKind.LOOT_OVERFLOW;
			kill = lootKill;
			alertForDrop(tripBoss, item.getId(), item.getQuantity());
		}
		else
		{
			return;
		}

		groundItems.add(new GroundEntry(item.getId(), item.getQuantity(), event.getTile().getWorldLocation(), kind, kill, tick));
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		TileItem item = event.getItem();
		WorldPoint location = event.getTile().getWorldLocation();
		for (Iterator<GroundEntry> it = groundItems.iterator(); it.hasNext(); )
		{
			GroundEntry entry = it.next();
			if (entry.itemId == item.getId() && entry.quantity == item.getQuantity() && entry.location.equals(location))
			{
				it.remove();
				entry.tick = client.getTickCount();
				recentDespawns.add(entry);
				return;
			}
		}
	}

	// ---- Trip lifecycle ----

	private void enterLair(BossDefinition boss, long now)
	{
		// Entering a boss's area shows that boss
		if (boss != selectedBoss)
		{
			selectedBoss = boss;
			selectedVariant = null;
			config.setSelectedBoss(boss.getId());
			historyChanged();
		}

		if (history == null)
		{
			// Picked up in onHistoryLoaded
			return;
		}

		dead = false;
		ignoreDeltasUntilTick = -1;
		pendingDeath = null;
		graveWindowEndTick = -1;
		inLairPause = null;
		lastActivityAt = now;
		resetKillState();

		if (currentTrip != null && (tripBoss != boss || (suspendedAt != null && (isRaid(boss) || now - suspendedAt > TimeUnit.MINUTES.toMillis(
			suspendedOutside ? config.outsideGraceMinutes() : config.logoutGraceMinutes())))))
		{
			// Past the grace period, a different boss's trip left open, or a raid left open by a client exit (you can't
			// rejoin a raid)
			endTrip(suspendedOutside ? TripEndReason.WALKED_OUT : TripEndReason.LOGOUT,
				suspendedAt != null ? suspendedAt : now);
		}
		List<Trip> trips = history.boss(boss.getId()).getTrips();

		if (currentTrip != null)
		{
			// Back from just outside the lair, or from logging out, within the grace period
			suspendedAt = null;
			suspendedOutside = false;
		}
		else if (config.mergeReentries() && !isRaid(boss) && lastEndedTrip != null && lastEndedBoss == boss
			&& lastEndedTrip.getEndedAt() != null
			&& now - lastEndedTrip.getEndedAt() <= TimeUnit.MINUTES.toMillis(config.mergeWindowMinutes())
			&& trips.contains(lastEndedTrip))
		{
			currentTrip = lastEndedTrip;
			currentTrip.setEndedAt(null);
			currentTrip.setEndReason(null);
		}
		else
		{
			currentTrip = new Trip();
			currentTrip.setId(UUID.randomUUID().toString());
			currentTrip.setStartedAt(now);
			trips.add(currentTrip);
		}
		tripBoss = boss;
		lastEndedTrip = null;
		lastEndedBoss = null;
		if (isRaid(boss))
		{
			raid.start(client.getTickCount() - enteredRaidModeTick <= CLICK_MATCH_TICKS ? enteredRaidMode : null);
			enteredRaidMode = null;
			freeSupplies.clear();
			// A new raid: an earlier one's reward can't be claimed any more
			unclaimedRaid = null;
			unclaimedRaidBoss = null;
		}
		TripClock.start(currentTrip, now);
		currentTrip.setLastActiveAt(now);
		lastPeriodicSave = now;

		if (config.countPreEntrySupplies())
		{
			for (PreEntryUse use : preEntryUses)
			{
				if (now - use.at <= PRE_ENTRY_WINDOW_MS)
				{
					for (ItemEntry entry : use.items)
					{
						ItemEntries.merge(currentTrip.getSupplies(), entry);
					}
				}
			}
		}
		preEntryUses.clear();

		historyChanged();
		requestSave();
	}

	private void leaveLair(int region, int tick, long now)
	{
		fightStartedAt = null;
		TripEndReason reason = dead ? TripEndReason.DEATH
			: tripBoss != null && tripBoss.getWaitingRegions().contains(region) ? TripEndReason.WALKED_OUT
			: TripEndReason.TELEPORT;
		if (dead)
		{
			dead = false;
			ignoreDeltasUntilTick = tick + POST_DEATH_IGNORE_TICKS;
		}

		if (currentTrip == null)
		{
			return;
		}

		if (isRaid(tripBoss))
		{
			TripEndReason raidEnd = raid.endReason();
			if (raidEnd != null)
			{
				reason = raidEnd;
			}
			if (raidEnd == TripEndReason.WIPED)
			{
				// Items are reclaimed from the chest for a fee (no payment has been logged yet, so it's added here)
				List<DeathRecord> deaths = currentTrip.getDeaths();
				DeathRecord last = deaths.get(deaths.size() - 1);
				last.setReclaimFee(last.getReclaimFee() + tripBoss.getWipeFee());
			}
			else if (raidEnd == TripEndReason.COMPLETED && lootKill != null && lootKill.getLoot().isEmpty())
			{
				// Left without claiming: the reward waits in the chest outside until you log out
				unclaimedRaid = lootKill;
				unclaimedRaidBoss = tripBoss;
			}
		}

		// The instance is gone either way, so anything left on the floor is lost
		finalizeDrops();
		commitSegment(now);
		inLairPause = null;
		if (reason == TripEndReason.WALKED_OUT && config.outsideGraceMinutes() > 0)
		{
			// AFK just outside: the trip stays open, paused, until you go back in or the grace period ends
			suspendedAt = now;
			suspendedOutside = true;
			resetKillState();
			viewDirty = true;
			requestSave();
			return;
		}
		endTrip(reason, now);
	}

	private void suspendTrip(long now)
	{
		fightStartedAt = null;
		inArea = false;
		areaBoss = null;
		if (currentTrip == null)
		{
			return;
		}

		finalizeDrops();
		commitSegment(now);
		inLairPause = null;
		if (isRaid(tripBoss))
		{
			// Logging out leaves the raid
			endTrip(TripEndReason.LOGOUT, now);
			saveNow();
			return;
		}
		suspendedAt = now;
		suspendedOutside = false;
		resetKillState();
		viewDirty = true;
		saveNow();
	}

	private void endTrip(TripEndReason reason, long at)
	{
		inLairPause = null;
		suspendedOutside = false;
		currentTrip.setEndedAt(at);
		currentTrip.setEndReason(reason);
		currentTrip.setLastActiveAt(at);
		if (TripMath.isEmpty(currentTrip))
		{
			// Nothing happened (e.g. walked in and straight back out): don't keep it
			history.boss(tripBoss.getId()).getTrips().remove(currentTrip);
			lastEndedTrip = null;
			lastEndedBoss = null;
		}
		else
		{
			lastEndedTrip = currentTrip;
			lastEndedBoss = tripBoss;
		}
		currentTrip = null;
		tripBoss = null;
		suspendedAt = null;
		resetKillState();
		historyChanged();
		requestSave();
	}

	private void commitSegment(long now)
	{
		TripClock.stop(currentTrip, goal(), now);
		currentTrip.setLastActiveAt(now);
	}

	private void markDead(long now)
	{
		if (dead || !inArea)
		{
			return;
		}
		if (!areaBoss.isDeathEndsTrip())
		{
			// You keep your items and carry on (a Theatre of Blood room); only a wipe costs anything
			if (currentTrip != null && raid.died(client.getTickCount()))
			{
				DeathRecord death = new DeathRecord();
				death.setAt(now);
				currentTrip.getDeaths().add(death);
				viewDirty = true;
				requestSave();
			}
			return;
		}
		dead = true;
		fightStartedAt = null;
		ignoreDeltasUntilTick = Integer.MAX_VALUE;
		if (currentTrip != null)
		{
			deathBoss = tripBoss;
			DeathRecord death = new DeathRecord();
			death.setAt(now);
			currentTrip.getDeaths().add(death);
			pendingDeath = death;
			viewDirty = true;
			requestSave();
		}
	}

	// ---- Kills and loot ----

	/**
	 * @param killCount the game's kill count, or null if the kill isn't on the boss's kill-count scale
	 */
	private void recordKill(Integer killCount, String variant, int tick, long now)
	{
		Kill kill = new Kill();
		kill.setKillCount(killCount);
		kill.setVariant(variant);
		kill.setEndedAt(now);
		if (bossSpawnedAt != null)
		{
			long end = bossDiedAt != null ? bossDiedAt : now;
			kill.setDurationMs(Math.max(0, end - bossSpawnedAt));
		}
		currentTrip.getKills().add(kill);
		lootKill = kill;
		lastKillTick = tick;
		fightStartedAt = null;
		lootReceived = false;
		fallbackEndTick = -1;
		fallbackGains.clear();
		viewDirty = true;
		requestSave();
	}

	/**
	 * The kill that loot should be attached to. Creates one if the kill-count message was missed.
	 */
	private Kill lootKillOrCreate()
	{
		return lootKill != null ? lootKill : newKill();
	}

	/**
	 * A kill without a kill-count message, so loot still has somewhere to go.
	 */
	private Kill newKill()
	{
		Kill kill = new Kill();
		kill.setEndedAt(System.currentTimeMillis());
		currentTrip.getKills().add(kill);
		lootKill = kill;
		lootReceived = false;
		fallbackEndTick = -1;
		fallbackGains.clear();
		return kill;
	}

	private void addLoot(Kill kill, int itemId, long quantity)
	{
		if (tripBoss.getTarnishedItems().contains(itemId))
		{
			// Real value is only known once polished; see PolishTracker
			for (long i = 0; i < quantity; i++)
			{
				ItemEntry pending = new ItemEntry(itemId, 1, 0);
				pending.setPending(true);
				pending.setPendingId(UUID.randomUUID().toString());
				kill.getLoot().add(pending);
			}
		}
		else
		{
			ItemEntries.merge(kill.getLoot(), itemId, quantity, prices.price(itemId), false);
		}
	}

	private void applyLootFallback(int tick)
	{
		if (fallbackEndTick < 0 || tick <= fallbackEndTick)
		{
			return;
		}

		if (!lootReceived && lootKill != null && currentTrip != null && !fallbackGains.isEmpty())
		{
			log.debug("No Loot Tracker event for this kill; using inventory changes");
			for (Map.Entry<Integer, Long> e : fallbackGains.entrySet())
			{
				addLoot(lootKill, e.getKey(), e.getValue());
				alertForDrop(tripBoss, e.getKey(), e.getValue());
			}
			lootReceived = true;
			viewDirty = true;
			requestSave();
		}
		fallbackEndTick = -1;
		fallbackGains.clear();
	}

	private void resetKillState()
	{
		lootKill = null;
		lootReceived = false;
		fallbackEndTick = -1;
		fallbackGains.clear();
		lastCorpseClickTick = -100;
		groundItems.clear();
		recentDespawns.clear();
	}

	private void loadRuneIds()
	{
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runeEnum == null)
		{
			return;
		}
		Set<Integer> ids = new HashSet<>();
		for (int id : runeEnum.getIntVals())
		{
			if (id > 0)
			{
				ids.add(id);
			}
		}
		viewBuilder.setRuneIds(ids);
		runeIdsLoaded = true;
		viewDirty = true;
	}

	// ---- Alerts ----

	private void alertForDrop(BossDefinition boss, int itemId, long quantity)
	{
		if (boss.isUnique(itemId))
		{
			if (config.alertUniques())
			{
				alerter.accept(boss.getDisplayName() + " unique: " + prices.name(itemId) + "!");
			}
			return;
		}
		if (boss.getHighlightedItems().contains(itemId) || boss.getTarnishedItems().contains(itemId))
		{
			return;
		}

		long value = quantity * prices.price(itemId);
		if (config.alertValue() > 0 && value >= config.alertValue())
		{
			alerter.accept(boss.getDisplayName() + " drop: " + (quantity > 1 ? QuantityFormatter.formatNumber(quantity) + " x " : "")
				+ prices.name(itemId) + " (" + QuantityFormatter.quantityToStackSize(value) + " gp)");
		}
	}

	private void alertPet(String message)
	{
		if (config.alertPet())
		{
			alerter.accept(message);
		}
	}

	// ---- Inventory changes ----

	private void processDelta(Map<Integer, Long> delta, int tick, long now)
	{
		Map<Integer, Long> removed = new HashMap<>();
		Map<Integer, Long> gained = new HashMap<>();
		for (Map.Entry<Integer, Long> e : delta.entrySet())
		{
			if (e.getValue() < 0)
			{
				removed.put(e.getKey(), -e.getValue());
			}
			else
			{
				gained.put(e.getKey(), e.getValue());
			}
		}

		recordGraveMovePayment(removed, tick);

		boolean trackingTrip = inArea && currentTrip != null && !dead;
		recordDrops(removed, tick, trackingTrip);
		if (trackingTrip)
		{
			// Thrown and usually picked back up: like a drop, only what's left behind counts
			for (int itemId : tripBoss.getRecoverableItems())
			{
				Long quantity = removed.remove(itemId);
				if (quantity != null)
				{
					pendingDrops.merge(itemId, quantity, Long::sum);
				}
			}
		}

		eggTracker.itemsRemoved(removed, tick, now);
		polishTracker.gained(gained, tick);

		// Popping eggs, polishing (tarnished items, dull ancient medals) and casting a spell on an item
		// (e.g. High Level Alchemy) convert items rather than use them up
		removed.keySet().removeAll(convertedItems);
		removeConvertedItems(removed, tick);

		if (trackingTrip)
		{
			matchPickups(gained, tick);
		}

		List<ItemEntry> used = tick <= ignoreDeltasUntilTick ? Collections.emptyList() : consumption(removed, gained);
		if (trackingTrip && tripBoss.isAcquiredInsideFree())
		{
			// Supply chest purchases and items picked up inside cost nothing: only use beyond them is paid for
			for (ItemEntry entry : acquisitions(removed, gained))
			{
				long refund = freeSupplies.acquired(entry);
				if (refund > 0)
				{
					// Makes up for use already charged this raid (e.g. arrows picked back up)
					ItemEntries.reduce(currentTrip.getSupplies(), entry.getItemId(), entry.isPerDose(), refund);
					viewDirty = true;
					requestSave();
				}
			}
			used = freeSupplies.paidFor(used);
		}

		if (trackingTrip && fallbackEndTick >= 0 && !lootReceived && !recentClicks.has(OPTION_POLISH, -1, tick))
		{
			for (Map.Entry<Integer, Long> e : gained.entrySet())
			{
				if (e.getKey() != ItemID.VIAL_EMPTY && prices.doseInfo(e.getKey()) == null)
				{
					fallbackGains.merge(e.getKey(), e.getValue(), Long::sum);
				}
			}
		}

		if (used.isEmpty())
		{
			return;
		}

		if (trackingTrip)
		{
			for (ItemEntry entry : used)
			{
				ItemEntries.merge(currentTrip.getSupplies(), entry);
			}
			viewDirty = true;
			requestSave();
		}
		else if (!inArea && !dead && currentTrip != null && suspendedOutside
			&& tick - lastBankTick > CLICK_MATCH_TICKS && recentClicks.has(-1, tick, CONSUME_OPTIONS::contains))
		{
			// Waiting just outside the lair: the trip is still open
			for (ItemEntry entry : used)
			{
				ItemEntries.merge(currentTrip.getSupplies(), entry);
			}
			viewDirty = true;
			requestSave();
		}
		else if (!inArea && !dead && config.countPreEntrySupplies()
			&& tick - lastBankTick > CLICK_MATCH_TICKS && recentClicks.has(-1, tick, CONSUME_OPTIONS::contains))
		{
			preEntryUses.addLast(new PreEntryUse(now, used));
		}
	}

	private void recordGraveMovePayment(Map<Integer, Long> removed, int tick)
	{
		if (pendingDeath == null || tick > graveWindowEndTick || inArea)
		{
			return;
		}

		for (int itemId : deathBoss.getGravePaymentItems())
		{
			Long quantity = removed.remove(itemId);
			if (quantity != null)
			{
				pendingDeath.setGraveMoveCost(pendingDeath.getGraveMoveCost() + quantity * prices.price(itemId));
				historyChanged();
				requestSave();
			}
		}
	}

	private void recordDrops(Map<Integer, Long> removed, int tick, boolean trackingTrip)
	{
		for (RecentClicks.Click click : recentClicks.all())
		{
			if (click.consumed || !OPTION_DROP.equals(click.option) || tick - click.tick > CLICK_MATCH_TICKS)
			{
				continue;
			}
			Long quantity = removed.remove(click.itemId);
			if (quantity != null)
			{
				click.consumed = true;
				if (trackingTrip)
				{
					pendingDrops.merge(click.itemId, quantity, Long::sum);
				}
			}
		}
	}

	private void removeConvertedItems(Map<Integer, Long> removed, int tick)
	{
		for (RecentClicks.Click click : recentClicks.all())
		{
			if (tick - click.tick <= CLICK_MATCH_TICKS && click.itemId > 0
				&& (OPTION_POLISH.equals(click.option) || OPTION_CAST.equals(click.option)))
			{
				removed.remove(click.itemId);
			}
		}
	}

	/**
	 * Gains that match a ground item just picked up: loot overflow becomes loot, own drops are no longer lost.
	 * Matched quantities are removed from {@code gained}.
	 */
	private void matchPickups(Map<Integer, Long> gained, int tick)
	{
		for (Iterator<GroundEntry> it = recentDespawns.iterator(); it.hasNext(); )
		{
			GroundEntry entry = it.next();
			Long available = gained.get(entry.itemId);
			if (available == null || tick - entry.tick > CLICK_MATCH_TICKS)
			{
				continue;
			}

			long taken = Math.min(available, entry.quantity);
			if (entry.kind == GroundKind.LOOT_OVERFLOW)
			{
				addLoot(entry.kill, entry.itemId, taken);
				viewDirty = true;
				requestSave();
			}
			else
			{
				pendingDrops.computeIfPresent(entry.itemId, (id, q) -> q - taken > 0 ? q - taken : null);
			}

			if (available - taken > 0)
			{
				gained.put(entry.itemId, available - taken);
			}
			else
			{
				gained.remove(entry.itemId);
			}
			entry.quantity -= taken;
			if (entry.quantity <= 0)
			{
				it.remove();
			}
		}
	}

	/**
	 * Converts removed items into supply lines. Potions are counted in doses: a Prayer potion(4) becoming
	 * a Prayer potion(3) is one dose, priced from the highest-dose variant.
	 */
	private List<ItemEntry> consumption(Map<Integer, Long> removed, Map<Integer, Long> gained)
	{
		List<ItemEntry> used = new ArrayList<>();
		Map<String, long[]> doseFamilies = new HashMap<>();

		for (Map.Entry<Integer, Long> e : removed.entrySet())
		{
			int itemId = e.getKey();
			PriceService.DoseInfo dose = prices.doseInfo(itemId);
			if (dose != null)
			{
				// [net doses used, a variant id seen, its dose count]
				long[] family = doseFamilies.computeIfAbsent(dose.getFamily(), f -> new long[]{0, itemId, dose.getDoses()});
				family[0] += e.getValue() * dose.getDoses();
			}
			else
			{
				used.add(new ItemEntry(itemId, e.getValue(), prices.price(itemId)));
			}
		}

		for (Map.Entry<Integer, Long> e : gained.entrySet())
		{
			PriceService.DoseInfo dose = prices.doseInfo(e.getKey());
			if (dose != null && doseFamilies.containsKey(dose.getFamily()))
			{
				doseFamilies.get(dose.getFamily())[0] -= e.getValue() * dose.getDoses();
			}
		}

		for (Map.Entry<String, long[]> e : doseFamilies.entrySet())
		{
			long[] family = e.getValue();
			if (family[0] > 0)
			{
				PriceService.FullDose full = prices.fullDose(e.getKey(), (int) family[1], (int) family[2]);
				ItemEntry entry = new ItemEntry(full.getItemId(), family[0], prices.pricePerDose(full));
				entry.setPerDose(true);
				used.add(entry);
			}
		}
		return used;
	}

	/**
	 * Items gained that weren't in the inventory before (not a potion going down a dose), counted the way
	 * {@link #consumption} counts supplies: per dose for potions.
	 */
	private List<ItemEntry> acquisitions(Map<Integer, Long> removed, Map<Integer, Long> gained)
	{
		List<ItemEntry> acquired = new ArrayList<>();
		// [net doses gained, a variant id seen, its dose count]
		Map<String, long[]> doseFamilies = new HashMap<>();
		for (Map.Entry<Integer, Long> e : gained.entrySet())
		{
			int itemId = e.getKey();
			PriceService.DoseInfo dose = prices.doseInfo(itemId);
			if (dose != null)
			{
				long[] family = doseFamilies.computeIfAbsent(dose.getFamily(), f -> new long[]{0, itemId, dose.getDoses()});
				family[0] += e.getValue() * dose.getDoses();
			}
			else
			{
				acquired.add(new ItemEntry(itemId, e.getValue(), prices.price(itemId)));
			}
		}
		for (Map.Entry<Integer, Long> e : removed.entrySet())
		{
			PriceService.DoseInfo dose = prices.doseInfo(e.getKey());
			if (dose != null && doseFamilies.containsKey(dose.getFamily()))
			{
				doseFamilies.get(dose.getFamily())[0] -= e.getValue() * dose.getDoses();
			}
		}
		for (Map.Entry<String, long[]> e : doseFamilies.entrySet())
		{
			long[] family = e.getValue();
			if (family[0] > 0)
			{
				PriceService.FullDose full = prices.fullDose(e.getKey(), (int) family[1], (int) family[2]);
				ItemEntry entry = new ItemEntry(full.getItemId(), family[0], prices.pricePerDose(full));
				entry.setPerDose(true);
				acquired.add(entry);
			}
		}
		return acquired;
	}

	private void finalizeDrops()
	{
		if (tripBoss.isDroppedSupplyUsed())
		{
			dropsAsUsed();
			pendingDrops.clear();
			return;
		}
		for (Map.Entry<Integer, Long> e : pendingDrops.entrySet())
		{
			long price = prices.price(e.getKey());
			if (price >= JUNK_PRICE && e.getValue() > 0)
			{
				ItemEntries.merge(currentTrip.getDropped(), e.getKey(), e.getValue(), price, false);
			}
		}
		pendingDrops.clear();
	}

	/**
	 * Dropped supplies left behind count as used (per dose for potions, less anything obtained inside); dropped
	 * equipment and junk are never a cost.
	 */
	private void dropsAsUsed()
	{
		Map<Integer, Long> removed = new HashMap<>();
		for (Map.Entry<Integer, Long> e : pendingDrops.entrySet())
		{
			int itemId = e.getKey();
			if (e.getValue() <= 0 || prices.isEquipable(itemId)
				|| (prices.doseInfo(itemId) == null && prices.price(itemId) < JUNK_PRICE))
			{
				continue;
			}
			removed.put(itemId, e.getValue());
		}
		List<ItemEntry> used = consumption(removed, Collections.emptyMap());
		if (tripBoss.isAcquiredInsideFree())
		{
			used = freeSupplies.paidFor(used);
		}
		for (ItemEntry entry : used)
		{
			ItemEntries.merge(currentTrip.getSupplies(), entry);
		}
	}

	// ---- Helpers ----

	private void prune(int tick, long now)
	{
		recentClicks.prune(tick);
		recentDespawns.removeIf(d -> tick - d.tick > CLICK_MATCH_TICKS);
		while (!preEntryUses.isEmpty() && now - preEntryUses.peekFirst().at > PRE_ENTRY_WINDOW_MS)
		{
			preEntryUses.removeFirst();
		}
		if (pendingDeath != null && !inArea && graveWindowEndTick >= 0 && tick > graveWindowEndTick)
		{
			graveWindowEndTick = -1;
		}
	}

	/**
	 * The boss's name as the game shows it in kill-count messages and Loot Tracker events.
	 */
	private String bossName(BossDefinition boss)
	{
		return bossNames.computeIfAbsent(boss.getId(), id -> client.getNpcDefinition(boss.getNameNpcId()).getName());
	}

	/**
	 * Whether a kill-count message's name is this boss's (one of its variants', or its NPC name).
	 */
	private boolean isKillName(BossDefinition boss, String name)
	{
		if (boss.getKillNames().isEmpty())
		{
			return name.equalsIgnoreCase(bossName(boss));
		}
		for (String killName : boss.getKillNames().keySet())
		{
			if (killName.equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	private static String variantForKillName(BossDefinition boss, String name)
	{
		for (Map.Entry<String, String> e : boss.getKillNames().entrySet())
		{
			if (e.getKey().equalsIgnoreCase(name))
			{
				return e.getValue();
			}
		}
		return null;
	}

	private static Trip findTrip(BossHistory boss, String id)
	{
		for (Trip trip : boss.getTrips())
		{
			if (trip.getId().equals(id))
			{
				return trip;
			}
		}
		return null;
	}

	private BossHistory writableHistory(BossDefinition boss)
	{
		return history == null || readOnly ? null : history.boss(boss.getId());
	}

	// ---- Accounts and persistence ----

	private void ensureAccountLoaded()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == accountHash)
		{
			return;
		}

		if (history != null)
		{
			if (currentTrip != null)
			{
				endTrip(TripEndReason.LOGOUT, suspendedAt != null ? suspendedAt : System.currentTimeMillis());
			}
			saveNow();
		}

		accountHash = hash;
		history = null;
		readOnly = false;
		loading = true;
		currentTrip = null;
		tripBoss = null;
		suspendedAt = null;
		lastEndedTrip = null;
		lastEndedBoss = null;
		pendingDeath = null;
		historyViews = Collections.emptyList();
		store.load(hash, result -> clientThread.invokeLater(() -> onHistoryLoaded(hash, result)));
	}

	private void onHistoryLoaded(long hash, HistoryStore.LoadResult result)
	{
		if (hash != accountHash)
		{
			return;
		}

		history = result.getHistory();
		readOnly = result.isReadOnly();
		loading = false;
		if (readOnly)
		{
			log.warn("Trip history is read-only (newer format or unreadable); changes will not be saved");
		}

		Player player = client.getLocalPlayer();
		if (player != null && player.getName() != null)
		{
			history.setLastDisplayName(player.getName());
		}
		if (!readOnly && result.getSourceVersion() < SupplyCorrections.SCYTHE_FIXED_IN_SCHEMA)
		{
			int fixed = SupplyCorrections.repriceScythe(history, prices.price(ItemID.BLOODRUNE));
			if (fixed > 0)
			{
				log.info("Repriced {} Scythe of Vitur charge lines (200 blood runes per 100 charges, not 300)", fixed);
				saveNow();
			}
		}

		// Trips left open by a client exit: close all but the most recent, which may resume within the grace period
		Trip open = null;
		BossDefinition openBoss = null;
		for (BossDefinition boss : registry.all())
		{
			for (Trip trip : history.boss(boss.getId()).getTrips())
			{
				if (!trip.isOpen())
				{
					continue;
				}
				if (trip.getSegmentStartedAt() != null)
				{
					long end = Math.max(trip.getSegmentStartedAt(), trip.getLastActiveAt());
					trip.setActiveMs(trip.getActiveMs() + end - trip.getSegmentStartedAt());
					trip.setSegmentStartedAt(null);
				}
				Trip older = trip;
				if (open == null || trip.getLastActiveAt() >= open.getLastActiveAt())
				{
					older = open;
					open = trip;
					openBoss = boss;
				}
				if (older != null)
				{
					older.setEndedAt(older.getLastActiveAt());
					older.setEndReason(TripEndReason.LOGOUT);
				}
			}
		}
		if (open != null)
		{
			currentTrip = open;
			tripBoss = openBoss;
			suspendedAt = open.getLastActiveAt();
		}

		historyChanged();
		if (inArea)
		{
			enterLair(areaBoss, System.currentTimeMillis());
		}
		pushState();
	}

	private void requestSave()
	{
		if (history == null || readOnly || (saveFuture != null && !saveFuture.isDone()))
		{
			return;
		}
		try
		{
			saveFuture = executor.schedule(() -> clientThread.invokeLater(this::saveNow), SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Plugin is shutting down
		}
	}

	private void saveNow()
	{
		if (history == null || readOnly)
		{
			return;
		}
		if (currentTrip != null && currentTrip.getSegmentStartedAt() != null)
		{
			currentTrip.setLastActiveAt(System.currentTimeMillis());
		}
		store.save(accountHash, gson.toJson(history));
	}

	private void historyChanged()
	{
		historyDirty = true;
		viewDirty = true;
	}

	private void pushState()
	{
		BossDefinition boss = selectedBoss;
		viewBuilder.setPastTeamSize(config.tobPastTeamSize());
		BossHistory bossHistory = history == null ? null : history.boss(boss.getId());
		// The Trip tab only shows the live trip for its own boss; others keep tracking in the background
		boolean live = currentTrip != null && tripBoss == boss;

		if (historyDirty && bossHistory != null)
		{
			List<TripView> views = new ArrayList<>();
			List<Trip> trips = bossHistory.getTrips();
			for (int i = trips.size() - 1; i >= 0; i--)
			{
				Trip trip = trips.get(i);
				if (!trip.isOpen() && VariantFilter.matches(trip, selectedVariant))
				{
					views.add(viewBuilder.trip(boss, trip));
				}
			}
			historyViews = Collections.unmodifiableList(views);
		}
		historyDirty = false;
		viewDirty = false;

		PanelState.Status status;
		TripView shown = null;
		if (history == null)
		{
			status = loading ? PanelState.Status.LOADING : PanelState.Status.LOGGED_OUT;
		}
		else if (live)
		{
			status = suspendedAt != null && !suspendedOutside ? PanelState.Status.PAUSED
				: suspendedAt != null || inLairPause != null ? PanelState.Status.AFK_PAUSED
				: PanelState.Status.IN_TRIP;
			shown = viewBuilder.trip(boss, currentTrip);
		}
		else
		{
			status = PanelState.Status.IDLE;
			shown = historyViews.isEmpty() ? null : historyViews.get(0);
		}

		List<BossOption> options = new ArrayList<>();
		for (BossDefinition b : registry.all())
		{
			options.add(new BossOption(b.getId(), b.getDisplayName(), b.getIconItemId(), currentTrip != null && tripBoss == b));
		}

		long now = System.currentTimeMillis();
		boolean fighting = live && inArea && suspendedAt == null;
		PanelState state = PanelState.builder()
			.boss(boss)
			.bosses(options)
			.variant(selectedVariant)
			.status(status)
			.currentTrip(shown)
			.history(historyViews)
			.lifetime(bossHistory == null ? null : viewBuilder.lifetime(boss, bossHistory, selectedVariant, allTime(boss),
				config.showCurrentValue(), now))
			.goal(bossHistory == null ? null : viewBuilder.goal(bossHistory, live ? currentTrip : null, now))
			.pauseText(live ? pauseText() : null)
			.pausedInLair(live && inLairPause != null)
			.canPause(fighting)
			.readOnly(readOnly)
			.killStartedAt(fighting ? fightStartedAt : null)
			.playerName(history == null ? null : history.getLastDisplayName())
			.luckCardStyle(config.luckCardStyle())
			.build();
		stateListener.accept(state);
	}

	/**
	 * All-time records from RuneLite's Loot Tracker and Chat Commands for the shown boss and variant.
	 */
	private AllTimeCounts allTime(BossDefinition boss)
	{
		return allTimeRecords.read(boss.getAllTimeSources(), selectedVariant);
	}

	private String pauseText()
	{
		if (currentTrip == null)
		{
			return null;
		}
		if (suspendedAt != null)
		{
			return suspendedOutside ? "Trip paused (outside the " + tripBoss.getAreaNoun() + ")" : "Trip paused (logged out)";
		}
		if (inLairPause == InLairPause.MANUAL)
		{
			return "Trip paused (AFK)";
		}
		if (inLairPause == InLairPause.IDLE)
		{
			return "Trip paused (idle)";
		}
		return null;
	}

	/**
	 * Lets the egg and polish trackers reach the history and alerts.
	 */
	private class Host implements TrackerHost
	{
		@Override
		public BossHistory writableHistory(BossDefinition boss)
		{
			return TripTracker.this.writableHistory(boss);
		}

		@Override
		public void historyChanged()
		{
			TripTracker.this.historyChanged();
			requestSave();
		}

		@Override
		public void alertPet(String message)
		{
			TripTracker.this.alertPet(message);
		}

		@Override
		public void alertForDrop(BossDefinition boss, int itemId, long quantity)
		{
			TripTracker.this.alertForDrop(boss, itemId, quantity);
		}
	}

	// ---- Small records ----

	private enum InLairPause
	{
		MANUAL,
		IDLE,
	}

	private enum GroundKind
	{
		OWN_DROP,
		LOOT_OVERFLOW,
	}

	@AllArgsConstructor
	private static class GroundEntry
	{
		final int itemId;
		long quantity;
		final WorldPoint location;
		final GroundKind kind;
		final Kill kill;
		int tick;
	}

	@AllArgsConstructor
	private static class PreEntryUse
	{
		final long at;
		final List<ItemEntry> items;
	}
}
