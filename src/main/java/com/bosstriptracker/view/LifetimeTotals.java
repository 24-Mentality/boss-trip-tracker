package com.bosstriptracker.view;

import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Running totals over trips for the Lifetime tab. The totals of finished trips are kept and the trip in progress is
 * added on top of a copy, so the whole history isn't added up again on every update.
 */
final class LifetimeTotals
{
	int trips;
	int kills;
	int deaths;
	int pets;
	long activeMs;
	long loot;
	long supplies;
	long dropped;
	long deathCost;
	long firstStartedAt = Long.MAX_VALUE;
	/**
	 * Net profit of each finished trip, oldest first.
	 */
	final List<Long> netPerTrip = new ArrayList<>();
	final Map<String, Integer> choices = new HashMap<>();
	long killMsTotal;
	int killMsCount;
	final ItemTotals lootItems = new ItemTotals();
	final ItemTotals supplyItems = new ItemTotals();
	final ItemTotals droppedItems = new ItemTotals();

	/**
	 * @param now current time, for the running segment of an open trip
	 */
	void add(Trip trip, long now)
	{
		trips++;
		kills += trip.getKills().size();
		deaths += trip.getDeaths().size();
		activeMs += trip.activeMsAt(now);
		loot += TripMath.lootValue(trip);
		supplies += TripMath.supplyCost(trip);
		dropped += TripMath.droppedCost(trip);
		deathCost += TripMath.deathCost(trip);
		firstStartedAt = Math.min(firstStartedAt, trip.getStartedAt());
		supplyItems.addAll(trip.getSupplies());
		droppedItems.addAll(trip.getDropped());
		for (Kill kill : trip.getKills())
		{
			lootItems.addAll(kill.getLoot());
			if (kill.isPet())
			{
				pets++;
			}
			if (kill.getChoice() != null)
			{
				choices.merge(kill.getChoice(), 1, Integer::sum);
			}
			if (kill.getDurationMs() != null)
			{
				killMsTotal += kill.getDurationMs();
				killMsCount++;
			}
		}
		if (!trip.isOpen())
		{
			netPerTrip.add(TripMath.netProfit(trip));
		}
	}

	LifetimeTotals copy()
	{
		LifetimeTotals copy = new LifetimeTotals();
		copy.trips = trips;
		copy.kills = kills;
		copy.deaths = deaths;
		copy.pets = pets;
		copy.activeMs = activeMs;
		copy.loot = loot;
		copy.supplies = supplies;
		copy.dropped = dropped;
		copy.deathCost = deathCost;
		copy.firstStartedAt = firstStartedAt;
		copy.netPerTrip.addAll(netPerTrip);
		copy.choices.putAll(choices);
		copy.killMsTotal = killMsTotal;
		copy.killMsCount = killMsCount;
		copy.lootItems.addAll(lootItems);
		copy.supplyItems.addAll(supplyItems);
		copy.droppedItems.addAll(droppedItems);
		return copy;
	}

	Long averageKillMs()
	{
		return killMsCount == 0 ? null : killMsTotal / killMsCount;
	}

	long trackedSince()
	{
		return trips == 0 ? 0 : firstStartedAt;
	}
}
