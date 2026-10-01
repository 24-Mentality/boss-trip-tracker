package com.bosstriptracker.model;

import java.util.Collection;

/**
 * Totals derived from stored trips. Nothing derived is stored, so the file stays the single source of truth.
 */
public final class TripMath
{
	private TripMath()
	{
	}

	public static long lootValue(Trip trip)
	{
		long total = 0;
		for (Kill kill : trip.getKills())
		{
			total += sum(kill.getLoot());
		}
		return total;
	}

	public static long supplyCost(Trip trip)
	{
		return sum(trip.getSupplies());
	}

	public static long droppedCost(Trip trip)
	{
		return sum(trip.getDropped());
	}

	public static long deathCost(Trip trip)
	{
		long total = 0;
		for (DeathRecord death : trip.getDeaths())
		{
			total += death.totalCost();
		}
		return total;
	}

	public static long totalCost(Trip trip)
	{
		return supplyCost(trip) + droppedCost(trip) + deathCost(trip);
	}

	public static long netProfit(Trip trip)
	{
		return lootValue(trip) - totalCost(trip);
	}

	/**
	 * Supplies and charges worth less than this on a trip without kills are left out (a sip before walking in and
	 * straight back out).
	 */
	public static final long MIN_KEPT_SUPPLY_COST = 1_000;

	/**
	 * A trip worth keeping has at least one kill, death or dropped item, or used supplies or charges worth
	 * {@link #MIN_KEPT_SUPPLY_COST} or more (a raid reset at the first room, a teleport out before the kill).
	 * Walking in and straight back out leaves none of these.
	 */
	public static boolean isEmpty(Trip trip)
	{
		return trip.getKills().isEmpty() && trip.getDeaths().isEmpty() && trip.getDropped().isEmpty()
			&& supplyCost(trip) < MIN_KEPT_SUPPLY_COST;
	}

	public static int countChoice(Trip trip, String choice)
	{
		int count = 0;
		for (Kill kill : trip.getKills())
		{
			if (choice.equals(kill.getChoice()))
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * @return mean fight duration over kills with a known duration, or null if none
	 */
	public static Long averageKillMs(Collection<Trip> trips)
	{
		long total = 0;
		int count = 0;
		for (Trip trip : trips)
		{
			for (Kill kill : trip.getKills())
			{
				if (kill.getDurationMs() != null)
				{
					total += kill.getDurationMs();
					count++;
				}
			}
		}
		return count == 0 ? null : total / count;
	}

	/**
	 * @return shortest fight duration over kills with a known duration, or null if none
	 */
	public static Long fastestKillMs(Collection<Trip> trips)
	{
		Long fastest = null;
		for (Trip trip : trips)
		{
			for (Kill kill : trip.getKills())
			{
				if (kill.getDurationMs() != null && (fastest == null || kill.getDurationMs() < fastest))
				{
					fastest = kill.getDurationMs();
				}
			}
		}
		return fastest;
	}

	public static long gpPerHour(long net, long activeMs)
	{
		if (activeMs < 1000)
		{
			return 0;
		}
		return (long) (net * (3_600_000.0 / activeMs));
	}

	private static long sum(Collection<ItemEntry> entries)
	{
		long total = 0;
		for (ItemEntry entry : entries)
		{
			total += entry.totalValue();
		}
		return total;
	}
}
