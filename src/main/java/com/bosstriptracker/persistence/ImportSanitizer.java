package com.bosstriptracker.persistence;

import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.DeathRecord;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a history file chosen for import, so a damaged or foreign file can't break the panel or fill the saved
 * history with nonsense: unknown bosses and invalid trips, kills and items are left out, and absurd numbers clamped.
 */
public final class ImportSanitizer
{
	public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

	/**
	 * More of one item than fits in a stack, or a price above the GE's maximum, can't be real.
	 */
	static final long MAX_QUANTITY = Integer.MAX_VALUE;
	static final long MAX_PRICE = Integer.MAX_VALUE;
	static final long MAX_FEE = 1_000_000_000L;
	static final int MAX_PARTY_SIZE = 100;

	private ImportSanitizer()
	{
	}

	/**
	 * A Boss Trip Tracker history has a schema version (every version since the first) or bosses.
	 */
	public static boolean isHistory(JsonObject root)
	{
		return root != null && (root.has("schemaVersion") || root.has("bosses"));
	}

	/**
	 * Cleans {@code history} in place.
	 *
	 * @param knownBossIds the bosses this version tracks; others are left out
	 * @return how many bosses, trips, kills, deaths, egg pops and item lines were left out
	 */
	public static int sanitize(AccountHistory history, Set<String> knownBossIds)
	{
		int dropped = 0;
		for (Iterator<Map.Entry<String, BossHistory>> it = history.getBosses().entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<String, BossHistory> e = it.next();
			if (e.getValue() == null || !knownBossIds.contains(e.getKey()))
			{
				it.remove();
				dropped++;
				continue;
			}
			BossHistory boss = e.getValue();
			boss.fillMissing();
			for (Iterator<Trip> trips = boss.getTrips().iterator(); trips.hasNext(); )
			{
				Trip trip = trips.next();
				if (!validTrip(trip))
				{
					trips.remove();
					dropped++;
					continue;
				}
				dropped += sanitize(trip);
			}
			int pops = boss.getEggPops().size();
			boss.getEggPops().removeIf(pop -> pop == null || pop.getEggItemId() <= 0 || pop.getAt() <= 0);
			dropped += pops - boss.getEggPops().size();
			boss.getPolishOutcomes().values().removeIf(outcomes -> outcomes == null);
			boss.getPolishOutcomes().values().forEach(outcomes ->
				outcomes.entrySet().removeIf(o -> o.getKey() == null || o.getValue() == null || o.getValue() < 0));
		}
		return dropped;
	}

	private static boolean validTrip(Trip trip)
	{
		return trip != null && trip.getId() != null && !trip.getId().isEmpty() && trip.getStartedAt() > 0
			&& (trip.getEndedAt() == null || trip.getEndedAt() >= trip.getStartedAt());
	}

	private static int sanitize(Trip trip)
	{
		int dropped = 0;
		if (trip.getKills() == null)
		{
			trip.setKills(new ArrayList<>());
		}
		if (trip.getSupplies() == null)
		{
			trip.setSupplies(new ArrayList<>());
		}
		if (trip.getDropped() == null)
		{
			trip.setDropped(new ArrayList<>());
		}
		if (trip.getDeaths() == null)
		{
			trip.setDeaths(new ArrayList<>());
		}
		long length = trip.getEndedAt() == null ? Long.MAX_VALUE : trip.getEndedAt() - trip.getStartedAt();
		trip.setActiveMs(clamp(trip.getActiveMs(), 0, length));

		int kills = trip.getKills().size();
		trip.getKills().removeIf(kill -> kill == null);
		dropped += kills - trip.getKills().size();
		for (Kill kill : trip.getKills())
		{
			if (kill.getLoot() == null)
			{
				kill.setLoot(new ArrayList<>());
			}
			if (kill.getTeamUniques() == null)
			{
				kill.setTeamUniques(new ArrayList<>());
			}
			kill.getTeamUniques().removeIf(id -> id == null || id <= 0);
			if (kill.getKillCount() != null && kill.getKillCount() < 0)
			{
				kill.setKillCount(null);
			}
			if (kill.getDurationMs() != null && kill.getDurationMs() < 0)
			{
				kill.setDurationMs(null);
			}
			if (kill.getPartySize() != null && (kill.getPartySize() < 1 || kill.getPartySize() > MAX_PARTY_SIZE))
			{
				kill.setPartySize(null);
			}
			dropped += sanitize(kill.getLoot());
		}
		dropped += sanitize(trip.getSupplies());
		dropped += sanitize(trip.getDropped());

		int deaths = trip.getDeaths().size();
		trip.getDeaths().removeIf(death -> death == null);
		dropped += deaths - trip.getDeaths().size();
		for (DeathRecord death : trip.getDeaths())
		{
			death.setReclaimFee(clamp(death.getReclaimFee(), 0, MAX_FEE));
			death.setGraveMoveCost(clamp(death.getGraveMoveCost(), 0, MAX_FEE));
		}
		return dropped;
	}

	private static int sanitize(List<ItemEntry> lines)
	{
		int before = lines.size();
		lines.removeIf(e -> e == null || e.getItemId() <= 0 || e.getQuantity() <= 0
			|| e.getChargeItemId() < 0 || e.getChargesPerItem() < 0);
		for (ItemEntry entry : lines)
		{
			entry.setQuantity(clamp(entry.getQuantity(), 0, MAX_QUANTITY));
			entry.setPriceEach(clamp(entry.getPriceEach(), 0, MAX_PRICE));
		}
		return before - lines.size();
	}

	private static long clamp(long value, long min, long max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
