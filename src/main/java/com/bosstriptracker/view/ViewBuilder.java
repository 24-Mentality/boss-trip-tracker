package com.bosstriptracker.view;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.DropKind;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.boss.KillContext;
import com.bosstriptracker.boss.LootCategories;
import com.bosstriptracker.boss.LootChoice;
import com.bosstriptracker.boss.TripStat;
import com.bosstriptracker.model.AllTimeCounts;
import com.bosstriptracker.model.ChargeType;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.DryStreak;
import com.bosstriptracker.model.EggPop;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.KillGoal;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripClock;
import com.bosstriptracker.model.TripMath;
import com.bosstriptracker.model.VariantFilter;
import com.bosstriptracker.pricing.PriceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.util.QuantityFormatter;

/**
 * Turns stored trips into immutable view objects for the panel. Runs on the client thread,
 * because item names come from ItemManager.
 */
public class ViewBuilder
{
	private static final Comparator<ItemView> BY_VALUE = Comparator
		.comparing(ItemView::isUnique).reversed()
		.thenComparing(Comparator.comparingLong(ItemView::getTotalValue).reversed())
		.thenComparing(ItemView::getName);

	/**
	 * Categories shown in a loot header: the top four and Other.
	 */
	static final int MAX_LOOT_CATEGORIES = 5;

	private final PriceService prices;
	private final Cache cache = new Cache();
	private final GoalCache goalCache = new GoalCache();
	private Set<Integer> runeIds = Collections.emptySet();
	/**
	 * The "Typical team size for past raids" setting, for all-time raid records that don't say the team size.
	 */
	private int pastTeamSize = 4;

	public ViewBuilder(PriceService prices)
	{
		this.prices = prices;
	}

	/**
	 * Item ids that count as runes in the supply breakdown (every rune the rune pouch can hold).
	 */
	public void setRuneIds(Set<Integer> runeIds)
	{
		this.runeIds = runeIds;
	}

	public void setPastTeamSize(int pastTeamSize)
	{
		this.pastTeamSize = Math.max(1, pastTeamSize);
	}

	/**
	 * @param currentTrip this boss's open trip, whose running segment keeps the goal clock going; null if none
	 */
	public GoalView goal(BossHistory history, Trip currentTrip, long now)
	{
		return goal(history, currentTrip, now, -1);
	}

	/**
	 * @param historyVersion as for the cached lifetime: kills of other trips are only counted again when it changes;
	 *                       -1 counts every time
	 */
	public GoalView goal(BossHistory history, Trip currentTrip, long now, long historyVersion)
	{
		KillGoal goal = history.getGoal();
		if (goal == null)
		{
			return null;
		}

		boolean cached = historyVersion >= 0 && goalCache.history == history && goalCache.historyVersion == historyVersion
			&& goalCache.startedAt == goal.getStartedAt() && goalCache.currentTrip == currentTrip;
		if (!cached)
		{
			int done = 0;
			for (Trip trip : history.getTrips())
			{
				if (trip != currentTrip)
				{
					done += killsSince(trip, goal.getStartedAt());
				}
			}
			goalCache.history = history;
			goalCache.historyVersion = historyVersion;
			goalCache.startedAt = goal.getStartedAt();
			goalCache.currentTrip = currentTrip;
			goalCache.otherTripsDone = done;
		}
		int done = goalCache.otherTripsDone
			+ (currentTrip != null && history.getTrips().contains(currentTrip) ? killsSince(currentTrip, goal.getStartedAt()) : 0);
		Long segmentStart = TripClock.goalSegmentStart(goal, currentTrip);
		return new GoalView(goal.getTarget(), done, goal.getActiveMs(), segmentStart != null ? segmentStart : now,
			segmentStart != null, goal.getStartedAt());
	}

	private static int killsSince(Trip trip, long startedAt)
	{
		int done = 0;
		for (Kill kill : trip.getKills())
		{
			if (kill.getEndedAt() >= startedAt)
			{
				done++;
			}
		}
		return done;
	}

	public TripView trip(BossDefinition boss, Trip trip)
	{
		List<ItemEntry> loot = new ArrayList<>();
		boolean pet = false;
		for (Kill kill : trip.getKills())
		{
			loot.addAll(kill.getLoot());
			pet |= kill.isPet();
		}

		TripStat stat = boss.getProfitCell();
		return TripView.builder()
			.id(trip.getId())
			.startedAt(trip.getStartedAt())
			.endedAt(trip.getEndedAt())
			.endReason(trip.getEndReason())
			.activeMs(trip.getActiveMs())
			.segmentStartedAt(trip.getSegmentStartedAt())
			.kills(trip.getKills().size())
			.detail(boss.tripDetail(trip))
			.bossStat(new StatView(stat.getLabel(), stat.valueOf(trip), stat.getHelp()))
			.deaths(trip.getDeaths().size())
			.pet(pet)
			.lootValue(TripMath.lootValue(trip))
			.supplyCost(TripMath.supplyCost(trip))
			.droppedCost(TripMath.droppedCost(trip))
			.deathCost(TripMath.deathCost(trip))
			.netProfit(TripMath.netProfit(trip))
			.averageKillMs(TripMath.averageKillMs(Collections.singletonList(trip)))
			.fastestKillMs(TripMath.fastestKillMs(Collections.singletonList(trip)))
			.lastKillMs(lastKillMs(trip))
			.loot(items(boss, loot))
			.lootCategories(lootCategories(boss, loot))
			.supplies(items(boss, trip.getSupplies()))
			.dropped(items(boss, trip.getDropped()))
			.supplyCategories(supplyCategories(trip.getSupplies()))
			.build();
	}

	/**
	 * The Loot Tracker's record as item lines at today's prices (it keeps quantities, not prices).
	 */
	private List<ItemView> allTimeLoot(BossDefinition boss, AllTimeCounts allTime)
	{
		if (allTime == null)
		{
			return null;
		}
		List<ItemEntry> entries = new ArrayList<>();
		allTime.getDrops().forEach((itemId, quantity) -> entries.add(new ItemEntry(itemId, quantity, prices.price(itemId))));
		return items(boss, entries);
	}

	private List<LootCategory> allTimeLootCategories(BossDefinition boss, AllTimeCounts allTime)
	{
		if (allTime == null)
		{
			return Collections.emptyList();
		}
		List<ItemEntry> entries = new ArrayList<>();
		allTime.getDrops().forEach((itemId, quantity) -> entries.add(new ItemEntry(itemId, quantity, prices.price(itemId))));
		return lootCategories(boss, entries);
	}

	private long allTimeLootValue(AllTimeCounts allTime)
	{
		long value = 0;
		for (Map.Entry<Integer, Integer> e : allTime.getDrops().entrySet())
		{
			value += e.getValue() * prices.price(e.getKey());
		}
		return value;
	}

	private static Long lastKillMs(Trip trip)
	{
		for (int i = trip.getKills().size() - 1; i >= 0; i--)
		{
			Long duration = trip.getKills().get(i).getDurationMs();
			if (duration != null)
			{
				return duration;
			}
		}
		return null;
	}

	/**
	 * Adds up every trip; see {@link #lifetime(BossDefinition, BossHistory, String, AllTimeCounts, boolean, long, Trip, long)}
	 * for the cached version used on every update.
	 *
	 * @param variant variant chip selected, or null for All
	 * @param allTime records from RuneLite's core plugins; null if there are none
	 * @param now current time, for the running segment of an open trip
	 */
	public LifetimeView lifetime(BossDefinition boss, BossHistory history, String variant, AllTimeCounts allTime,
		boolean includeTodayValue, long now)
	{
		List<Trip> trips = filtered(history, variant);
		LifetimeTotals totals = new LifetimeTotals();
		for (Trip trip : trips)
		{
			totals.add(trip, now);
		}
		return lifetime(boss, history, totals, dryness(boss, history, trips, variant, allTime), allTime, includeTodayValue);
	}

	/**
	 * The same as {@link #lifetime(BossDefinition, BossHistory, String, AllTimeCounts, boolean, long)}, without adding
	 * up the history again on every update: the totals of finished trips are kept until the history changes, and the
	 * trip in progress is added on top. Luck is worked out again only when a kill or its loot changed.
	 *
	 * @param openTrip       the trip in progress if it's this boss's, else null
	 * @param historyVersion changes whenever the saved history does (a trip ends, is deleted or imported, loot is
	 *                       polished or repriced, settings behind the luck numbers change)
	 */
	public LifetimeView lifetime(BossDefinition boss, BossHistory history, String variant, AllTimeCounts allTime,
		boolean includeTodayValue, long now, Trip openTrip, long historyVersion)
	{
		boolean sameHistory = cache.boss == boss && cache.history == history && Objects.equals(cache.variant, variant)
			&& cache.historyVersion == historyVersion && cache.pastTeamSize == pastTeamSize && cache.openTrip == openTrip;
		if (!sameHistory)
		{
			cache.boss = boss;
			cache.history = history;
			cache.variant = variant;
			cache.historyVersion = historyVersion;
			cache.pastTeamSize = pastTeamSize;
			cache.openTrip = openTrip;
			cache.closed = new LifetimeTotals();
			for (Trip trip : filtered(history, variant))
			{
				if (trip != openTrip)
				{
					cache.closed.add(trip, now);
				}
			}
			cache.dryness = null;
		}

		boolean openShown = openTrip != null && VariantFilter.matches(openTrip, variant);
		LifetimeTotals totals = cache.closed;
		if (openShown)
		{
			totals = totals.copy();
			totals.add(openTrip, now);
		}

		int openKills = openShown ? killsSignature(openTrip) : 0;
		if (cache.dryness == null || cache.allTime != allTime || cache.openKills != openKills)
		{
			cache.dryness = dryness(boss, history, filtered(history, variant), variant, allTime);
			cache.allTime = allTime;
			cache.openKills = openKills;
		}
		return lifetime(boss, history, totals, cache.dryness, allTime, includeTodayValue);
	}

	/**
	 * Changes when anything the luck numbers read from the trip's kills does.
	 */
	private static int killsSignature(Trip trip)
	{
		int hash = 1;
		for (Kill kill : trip.getKills())
		{
			hash = 31 * hash + Objects.hash(kill.getKillCount(), kill.getEndedAt(), kill.getChoice(), kill.getVariant(),
				kill.getPartySize(), kill.isPet(), kill.getTeamUniques());
			for (ItemEntry entry : kill.getLoot())
			{
				hash = 31 * hash + Objects.hash(entry.getItemId(), entry.getQuantity());
			}
		}
		return hash;
	}

	private static List<Trip> filtered(BossHistory history, String variant)
	{
		List<Trip> trips = new ArrayList<>();
		for (Trip trip : history.getTrips())
		{
			if (VariantFilter.matches(trip, variant))
			{
				trips.add(trip);
			}
		}
		return trips;
	}

	private LifetimeView lifetime(BossDefinition boss, BossHistory history, LifetimeTotals totals, DrynessView dryness,
		AllTimeCounts allTime, boolean includeTodayValue)
	{
		Long today = null;
		if (includeTodayValue)
		{
			long value = 0;
			for (ItemTotals.Line line : totals.lootItems.lines())
			{
				if (!line.first.isPending())
				{
					value += line.quantity * prices.price(line.first.getItemId());
				}
			}
			today = value;
		}

		List<String> choices = new ArrayList<>();
		for (LootChoice choice : boss.getLootChoices())
		{
			choices.add(choice.getLabel() + " " + totals.choices.getOrDefault(choice.getKey(), 0));
		}

		return LifetimeView.builder()
			.trips(totals.trips)
			.trackedSince(totals.trackedSince())
			.kills(totals.kills)
			.choiceSummary(String.join(" · ", choices))
			.deaths(totals.deaths)
			.pets(totals.pets)
			.activeMs(totals.activeMs)
			.lootValue(totals.loot)
			.supplyCost(totals.supplies)
			.droppedCost(totals.dropped)
			.deathCost(totals.deathCost)
			.netProfit(totals.loot - totals.supplies - totals.dropped - totals.deathCost)
			.averageKillMs(totals.averageKillMs())
			.lootValueToday(today)
			.netPerTrip(new ArrayList<>(totals.netPerTrip))
			.dryness(dryness)
			.polish(polish(boss, history))
			.loot(items(boss, totals.lootItems))
			.lootCategories(lootCategories(boss, totals.lootItems))
			.supplies(items(boss, totals.supplyItems))
			.supplyCategories(supplyCategories(totals.supplyItems))
			.dropped(items(boss, totals.droppedItems))
			.allTimeLoot(allTimeLoot(boss, allTime))
			.allTimeLootValue(allTime == null ? 0 : allTimeLootValue(allTime))
			.allTimeLootCategories(allTimeLootCategories(boss, allTime))
			.allTimeSince(allTime == null ? 0 : allTime.getFirstRecordedAt())
			.build();
	}

	DrynessView dryness(BossDefinition boss, BossHistory history, List<Trip> trips, String variant,
		AllTimeCounts allTime)
	{
		List<ExpectedDrop> uniqueDrops = new ArrayList<>();
		for (ExpectedDrop drop : boss.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE)
			{
				uniqueDrops.add(drop);
			}
		}
		ExpectedDrop petDrop = boss.getPet();

		List<Kill> kills = new ArrayList<>();
		int luckKills = 0;
		int petsFromKills = 0;
		Integer currentKc = null;
		Integer firstTrackedKc = null;
		double expectedAny = 0;
		double expectedPet = 0;
		double[] expected = new double[uniqueDrops.size()];
		Map<Integer, List<Integer>> received = new LinkedHashMap<>();
		for (ExpectedDrop drop : uniqueDrops)
		{
			received.put(drop.getItemId(), new ArrayList<>());
		}

		for (Trip trip : trips)
		{
			for (Kill kill : trip.getKills())
			{
				if (!VariantFilter.matches(kill, variant))
				{
					continue;
				}
				// The dry streak goes by kill count, so only kills on the boss's kill-count scale (not Entry Mode raids)
				boolean onKcScale = boss.countsTowardKillCount(kill);
				if (onKcScale)
				{
					kills.add(kill);
				}
				if (kill.isPet())
				{
					petsFromKills++;
				}
				if (onKcScale && kill.getKillCount() != null)
				{
					currentKc = currentKc == null ? kill.getKillCount() : Math.max(currentKc, kill.getKillCount());
					if (firstTrackedKc == null)
					{
						firstTrackedKc = kill.getKillCount();
					}
				}
				if (!boss.countsForLuck(kill))
				{
					continue;
				}

				// Each kill at its own rate, so "All" mixes variants correctly
				KillContext context = KillContext.of(kill);
				luckKills++;
				expectedAny += boss.anyUniqueChance(context);
				expectedPet += petDrop == null ? 0 : petDrop.chance(context);
				for (int i = 0; i < uniqueDrops.size(); i++)
				{
					expected[i] += uniqueDrops.get(i).chance(context);
				}
				for (ItemEntry entry : kill.getLoot())
				{
					List<Integer> kcs = received.get(entry.getItemId());
					if (kcs != null)
					{
						for (long i = 0; i < entry.getQuantity(); i++)
						{
							kcs.add(kill.getKillCount());
						}
					}
				}
			}
		}

		int uniquesReceived = 0;
		List<DrynessView.Drop> uniques = new ArrayList<>();
		for (int i = 0; i < uniqueDrops.size(); i++)
		{
			ExpectedDrop drop = uniqueDrops.get(i);
			List<Integer> kcs = received.get(drop.getItemId());
			uniquesReceived += kcs.size();
			uniques.add(new DrynessView.Drop(drop.getItemId(), prices.name(drop.getItemId()),
				averageRate(expected[i], luckKills, drop.chance(KillContext.DEFAULT)), expected[i], kcs.size(), kcs));
		}
		DrynessView.Drop pet = petDrop == null ? null : new DrynessView.Drop(petDrop.getItemId(),
			prices.name(petDrop.getItemId()), averageRate(expectedPet, luckKills, petDrop.chance(KillContext.DEFAULT)),
			expectedPet, petsFromKills, Collections.emptyList());

		Map<Integer, int[]> eggCounts = new LinkedHashMap<>();
		for (int eggId : boss.getEggPetRates().keySet())
		{
			eggCounts.put(eggId, new int[2]);
		}
		for (EggPop pop : history.getEggPops())
		{
			int[] counts = eggCounts.get(pop.getEggItemId());
			if (counts != null)
			{
				counts[0]++;
				counts[1] += pop.isPet() ? 1 : 0;
			}
		}

		List<DrynessView.EggTier> tiers = new ArrayList<>();
		double noPetFromEggs = 1;
		double eggPetExpected = 0;
		int petsFromEggs = 0;
		for (Map.Entry<Integer, int[]> e : eggCounts.entrySet())
		{
			double rate = boss.getEggPetRates().get(e.getKey());
			tiers.add(new DrynessView.EggTier(e.getKey(), prices.name(e.getKey()), e.getValue()[0], e.getValue()[1], rate));
			noPetFromEggs *= Math.pow(1 - rate, e.getValue()[0]);
			eggPetExpected += e.getValue()[0] * rate;
			petsFromEggs += e.getValue()[1];
		}

		List<Integer> uniqueKcs = new ArrayList<>();
		for (List<Integer> kcs : received.values())
		{
			for (Integer kc : kcs)
			{
				if (kc != null)
				{
					uniqueKcs.add(kc);
				}
			}
		}

		double anyRate = averageRate(expectedAny, luckKills, boss.anyUniqueChance(KillContext.DEFAULT));
		// Without an entered kill count, the game's own dry streak (Theatre of Blood) places your last unique
		Integer lastUniqueKc = history.getLastUniqueKc();
		boolean fromGame = lastUniqueKc == null && history.getGameDryStreak() != null && history.getGameDryStreakKc() != null;
		if (fromGame)
		{
			lastUniqueKc = Math.max(0, history.getGameDryStreakKc() - history.getGameDryStreak());
		}
		DryStreak.Result streak = DryStreak.compute(kills, boss::countsForLuck,
			kill -> kill.getLoot().stream().anyMatch(e -> boss.isUnique(e.getItemId())),
			lastUniqueKc, allTime == null ? null : allTime.getKillCount());
		DrynessView.AllTime allTimeView = allTime(boss, uniqueDrops, allTime, kills, currentKc,
			petsFromKills + petsFromEggs, eggPetExpected);

		// Never had a unique at all (RuneLite's all-time record, this plugin, and no KC entered): the streak is the
		// whole kill count, not just the kills since tracking began
		int since = streak.getSince();
		boolean wholeKillCount = allTimeView != null && allTimeView.getKillCount() != null
			&& allTimeView.getUniquesReceived() == 0 && uniquesReceived == 0 && lastUniqueKc == null;
		if (wholeKillCount)
		{
			since = Math.max(since, allTimeView.getKillCount());
		}

		Integer teamDryStreak = boss.hasTeamDryStreak() ? teamDryStreak(boss, history) : null;

		return DrynessView.builder()
			.luckKills(luckKills)
			.killsSinceUnique(since)
			.sinceFromEnteredKc(streak.isFromEnteredKc() && !fromGame)
			.sinceFromGameCount(streak.isFromEnteredKc() && fromGame)
			.teamDryStreak(teamDryStreak)
			.teamDryStreakFromGame(history.getGameTeamDryStreak() != null)
			.sinceWholeKillCount(wholeKillCount)
			.longestDryStreak(DryStreak.longest(uniqueKcs, lastUniqueKc, since))
			.chanceThisDry(Math.pow(1 - anyRate, since))
			.anyUniqueRate(anyRate)
			.uniquesReceived(uniquesReceived)
			.expectedUniques(expectedAny)
			.uniques(uniques)
			.pet(pet)
			.eggTiers(tiers)
			.eggPetChance(1 - noPetFromEggs)
			.eggPetExpected(eggPetExpected)
			.petsFromEggs(petsFromEggs)
			.currentKc(currentKc)
			.lastUniqueKc(streak.getLastUniqueKc())
			.firstTrackedKc(firstTrackedKc)
			.enteredLastUniqueKc(history.getLastUniqueKc())
			.allTime(allTimeView)
			.build();
	}

	/**
	 * Raids since anyone in the team got a unique, every mode included as the game counts them: the game's own count
	 * from the last raid it was seen in, plus raids completed since (a unique, yours or a teammate's, resets it).
	 * Without the game's count, it's counted from the raids tracked.
	 */
	private static int teamDryStreak(BossDefinition boss, BossHistory history)
	{
		List<Kill> raids = new ArrayList<>();
		for (Trip trip : history.getTrips())
		{
			raids.addAll(trip.getKills());
		}
		raids.sort(Comparator.comparingLong(Kill::getEndedAt));

		Integer fromGame = history.getGameTeamDryStreak();
		Long seenAt = history.getGameTeamDryStreakAt();
		int count = fromGame != null ? fromGame : 0;
		for (Kill raid : raids)
		{
			if (fromGame != null && seenAt != null && raid.getEndedAt() <= seenAt)
			{
				continue;
			}
			boolean unique = (raid.getTeamUniques() != null && !raid.getTeamUniques().isEmpty())
				|| raid.getLoot().stream().anyMatch(e -> boss.isUnique(e.getItemId()));
			count = unique ? 0 : count + 1;
		}
		return count;
	}

	/**
	 * All-time figures at the default rates: the Loot Tracker doesn't record the variant or party size of a kill.
	 */
	private DrynessView.AllTime allTime(BossDefinition boss, List<ExpectedDrop> uniqueDrops, AllTimeCounts counts,
		List<Kill> trackedKills, Integer trackedKc, int trackedPets, double eggPetExpected)
	{
		if (counts == null)
		{
			return null;
		}

		// Past kills at the rates of their mode and a typical team, since the records don't say which. A record shared
		// by several modes (the Theatre of Blood) is counted by each mode's kill count, at that mode's rate
		Map<KillContext, Integer> pastKills = new LinkedHashMap<>();
		int kills = counts.getLootKills();
		if (counts.getVariantKillCounts().isEmpty())
		{
			pastKills.put(boss.pastKillContext(null, pastTeamSize), kills);
		}
		else
		{
			counts.getVariantKillCounts().forEach((mode, count) -> pastKills.put(boss.pastKillContext(mode, pastTeamSize), count));
		}

		// The Loot Tracker saves its record some seconds after a drop. Tracked loot kills since its last save aren't
		// in it yet, so add them, or a unique would only show up after the save. Kill counts by mode come from Chat
		// Commands, which already has them
		Map<Integer, Integer> unsaved = new LinkedHashMap<>();
		List<Kill> unsavedKills = new ArrayList<>();
		if (counts.getLastRecordedAt() > 0)
		{
			for (Kill kill : trackedKills)
			{
				if (kill.getEndedAt() > counts.getLastRecordedAt() && boss.countsForLuck(kill))
				{
					if (counts.getVariantKillCounts().isEmpty())
					{
						kills++;
						unsavedKills.add(kill);
					}
					for (ItemEntry entry : kill.getLoot())
					{
						unsaved.merge(entry.getItemId(), (int) entry.getQuantity(), Integer::sum);
					}
				}
			}
		}

		int received = 0;
		double expectedAny = expected(pastKills, unsavedKills, boss::anyUniqueChance);
		List<DrynessView.Drop> uniques = new ArrayList<>();
		for (ExpectedDrop drop : uniqueDrops)
		{
			double expected = expected(pastKills, unsavedKills, drop::chance);
			int got = counts.dropped(drop.getItemId()) + unsaved.getOrDefault(drop.getItemId(), 0);
			received += got;
			uniques.add(new DrynessView.Drop(drop.getItemId(), prices.name(drop.getItemId()),
				averageRate(expected, kills, drop.chance(KillContext.DEFAULT)), expected, got, Collections.emptyList()));
		}

		ExpectedDrop petDrop = boss.getPet();
		DrynessView.Drop pet = null;
		if (petDrop != null)
		{
			double expected = expected(pastKills, unsavedKills, petDrop::chance);
			// The Loot Tracker doesn't record every pet, so take the larger count
			pet = new DrynessView.Drop(petDrop.getItemId(), prices.name(petDrop.getItemId()),
				averageRate(expected, kills, petDrop.chance(KillContext.DEFAULT)), expected + eggPetExpected,
				Math.max(counts.dropped(petDrop.getItemId()), trackedPets), Collections.emptyList());
		}

		return DrynessView.AllTime.builder()
			.lootKills(kills)
			// Chat Commands may also be a kill behind
			.killCount(counts.getKillCount() == null ? trackedKc
				: trackedKc == null ? counts.getKillCount() : Integer.valueOf(Math.max(counts.getKillCount(), trackedKc)))
			.firstRecordedAt(counts.getFirstRecordedAt())
			.uniquesReceived(received)
			.expectedUniques(expectedAny)
			.uniques(uniques)
			.pet(pet)
			.build();
	}

	private static double expected(Map<KillContext, Integer> pastKills, List<Kill> unsavedKills,
		ToDoubleFunction<KillContext> chance)
	{
		double expected = 0;
		for (Map.Entry<KillContext, Integer> e : pastKills.entrySet())
		{
			expected += e.getValue() * chance.applyAsDouble(e.getKey());
		}
		for (Kill kill : unsavedKills)
		{
			expected += chance.applyAsDouble(KillContext.of(kill));
		}
		return expected;
	}

	/**
	 * The average chance per kill, or the default rate when there are no kills or every kill had it.
	 */
	static double averageRate(double expected, int kills, double defaultRate)
	{
		if (kills == 0)
		{
			return defaultRate;
		}
		double average = expected / kills;
		return Math.abs(average - defaultRate) < 1e-12 ? defaultRate : average;
	}

	private List<PolishView> polish(BossDefinition boss, BossHistory history)
	{
		List<PolishView> views = new ArrayList<>();
		for (int tarnishedId : boss.getTarnishedItems())
		{
			Map<Integer, Integer> outcomes = history.getPolishOutcomes().get(tarnishedId);
			if (outcomes == null || outcomes.isEmpty())
			{
				continue;
			}

			int total = 0;
			List<ItemView> items = new ArrayList<>();
			for (Map.Entry<Integer, Integer> e : outcomes.entrySet())
			{
				int count = e.getValue();
				total += count;
				items.add(new ItemView(e.getKey(), prices.name(e.getKey()), count, count * prices.price(e.getKey()),
					false, false, false, null, 0, null, null));
			}
			items.sort(Comparator.comparingLong(ItemView::getQuantity).reversed());
			views.add(new PolishView(tarnishedId, prices.name(tarnishedId), total, items));
		}
		return views;
	}

	/**
	 * Loot grouped by category, highest value first, with all but the top {@link #MAX_LOOT_CATEGORIES} - 1 folded
	 * into Other when there are more than {@link #MAX_LOOT_CATEGORIES}.
	 */
	List<LootCategory> lootCategories(BossDefinition boss, Collection<ItemEntry> loot)
	{
		return lootCategories(boss, ItemTotals.of(loot));
	}

	private List<LootCategory> lootCategories(BossDefinition boss, ItemTotals loot)
	{
		Map<String, List<ItemTotals.Line>> grouped = new LinkedHashMap<>();
		for (ItemTotals.Line line : loot.lines())
		{
			grouped.computeIfAbsent(lootCategory(boss, line.first.getItemId(), line.first.getPolishedFrom()),
				k -> new ArrayList<>()).add(line);
		}

		List<LootCategory> categories = new ArrayList<>();
		for (Map.Entry<String, List<ItemTotals.Line>> e : grouped.entrySet())
		{
			long value = 0;
			for (ItemTotals.Line line : e.getValue())
			{
				value += line.value;
			}
			categories.add(new LootCategory(e.getKey(), value, items(boss, e.getValue())));
		}
		return foldCategories(categories);
	}

	/**
	 * The boss's drop table group for an item, else a generic one: coins, runes, gear (equipable),
	 * consumables (Eat or Drink) or other.
	 */
	String lootCategory(BossDefinition boss, int itemId, int polishedFrom)
	{
		int canonical = prices.canonicalize(itemId);
		String category = boss.lootCategory(canonical, polishedFrom);
		if (category != null)
		{
			return category;
		}
		if (canonical == ItemID.COINS)
		{
			return LootCategories.COINS;
		}
		if (runeIds.contains(canonical))
		{
			return LootCategories.RUNES;
		}
		if (prices.isEquipable(canonical))
		{
			return LootCategories.GEAR;
		}
		if (prices.isFood(canonical) || prices.isDrinkable(canonical))
		{
			return LootCategories.CONSUMABLES;
		}
		return LootCategories.OTHER;
	}

	static List<LootCategory> foldCategories(List<LootCategory> categories)
	{
		List<LootCategory> named = new ArrayList<>();
		List<ItemView> otherItems = new ArrayList<>();
		long otherValue = 0;
		for (LootCategory category : categories)
		{
			if (LootCategories.OTHER.equals(category.getName()))
			{
				otherItems.addAll(category.getItems());
				otherValue += category.getValue();
			}
			else
			{
				named.add(category);
			}
		}
		named.sort(Comparator.comparingLong(LootCategory::getValue).reversed().thenComparing(LootCategory::getName));

		boolean hasOther = !otherItems.isEmpty();
		if (named.size() + (hasOther ? 1 : 0) > MAX_LOOT_CATEGORIES)
		{
			for (LootCategory folded : named.subList(MAX_LOOT_CATEGORIES - 1, named.size()))
			{
				otherItems.addAll(folded.getItems());
				otherValue += folded.getValue();
			}
			named = new ArrayList<>(named.subList(0, MAX_LOOT_CATEGORIES - 1));
			hasOther = true;
		}
		if (hasOther)
		{
			otherItems.sort(BY_VALUE);
			named.add(new LootCategory(LootCategories.OTHER, otherValue, otherItems));
		}
		return named;
	}

	private List<SupplyCategory> supplyCategories(List<ItemEntry> supplies)
	{
		return supplyCategories(ItemTotals.of(supplies));
	}

	private List<SupplyCategory> supplyCategories(ItemTotals supplies)
	{
		long charges = 0;
		long runes = 0;
		long potions = 0;
		long food = 0;
		long other = 0;
		for (ItemTotals.Line line : supplies.lines())
		{
			ItemEntry entry = line.first;
			long value = line.value;
			if (entry.isCharges())
			{
				charges += value;
			}
			else if (runeIds.contains(entry.getItemId()))
			{
				runes += value;
			}
			else if (entry.isPerDose())
			{
				potions += value;
			}
			else if (prices.isFood(entry.getItemId()))
			{
				food += value;
			}
			else
			{
				other += value;
			}
		}

		List<SupplyCategory> categories = new ArrayList<>();
		addCategory(categories, "Charges", charges);
		addCategory(categories, "Runes", runes);
		addCategory(categories, "Potions", potions);
		addCategory(categories, "Food", food);
		addCategory(categories, "Other", other);
		return categories;
	}

	private static void addCategory(List<SupplyCategory> categories, String name, long value)
	{
		if (value > 0)
		{
			categories.add(new SupplyCategory(name, value));
		}
	}

	private static String chargeNote(BossDefinition boss, ItemEntry entry)
	{
		ChargeType type = ChargeType.forLine(entry.getItemId(), entry.getChargeItemId());
		return type == null ? null : boss.getChargeNote(type);
	}

	/**
	 * What recharges a charge line, e.g. "Blood shard" or "Vial of blood + 300 Blood rune".
	 */
	private String rechargeName(ItemEntry entry)
	{
		ChargeType type = ChargeType.forLine(entry.getItemId(), entry.getChargeItemId());
		if (type != null && type.isBlowpipeDarts())
		{
			return prices.name(entry.getChargeItemId()) + " lost (an Ava's device or Dizana's quiver saves most)";
		}
		if (type == null || type.getComponents().size() == 1)
		{
			return prices.name(entry.getChargeItemId());
		}
		List<String> parts = new ArrayList<>();
		for (ChargeType.Component component : type.getComponents())
		{
			parts.add((component.getQuantity() > 1 ? QuantityFormatter.formatNumber(component.getQuantity()) + " " : "")
				+ prices.name(component.getItemId()));
		}
		return String.join(" + ", parts);
	}

	private List<ItemView> items(BossDefinition boss, Collection<ItemEntry> entries)
	{
		return items(boss, ItemTotals.of(entries));
	}

	private List<ItemView> items(BossDefinition boss, ItemTotals totals)
	{
		return items(boss, totals.lines());
	}

	/**
	 * Lines for the same item are already combined; pending tarnished drops stay separate.
	 */
	private List<ItemView> items(BossDefinition boss, Iterable<ItemTotals.Line> lines)
	{
		Set<Integer> highlighted = boss.getHighlightedItems();
		List<ItemView> views = new ArrayList<>();
		for (ItemTotals.Line line : lines)
		{
			ItemEntry first = line.first;
			int itemId = first.getItemId();
			views.add(new ItemView(itemId, prices.name(itemId), line.quantity, line.value, first.isPerDose(),
				highlighted.contains(itemId), first.isPending(),
				first.isCharges() ? rechargeName(first) : null, first.getChargesPerItem(),
				first.getPolishedFrom() > 0 ? prices.name(first.getPolishedFrom()) : null,
				first.isCharges() ? chargeNote(boss, first) : null));
		}
		views.sort(BY_VALUE);
		return views;
	}

	/**
	 * What {@link #lifetime(BossDefinition, BossHistory, String, AllTimeCounts, boolean, long, Trip, long)} keeps
	 * between updates.
	 */
	private static final class Cache
	{
		BossDefinition boss;
		BossHistory history;
		String variant;
		long historyVersion = -1;
		int pastTeamSize;
		Trip openTrip;
		LifetimeTotals closed;
		DrynessView dryness;
		AllTimeCounts allTime;
		int openKills;
	}

	private static final class GoalCache
	{
		BossHistory history;
		long historyVersion = -1;
		long startedAt;
		Trip currentTrip;
		int otherTripsDone;
	}
}
