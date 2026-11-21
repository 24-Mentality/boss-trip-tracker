package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.boss.TripStat;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripMath;
import com.bosstriptracker.persistence.HistoryLayout;
import com.bosstriptracker.persistence.HistoryStore;
import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

/**
 * Saving and the history's shape on disk: what changed since the last save (months of finished trips, the trip in
 * progress), finished trips replaced by copies when they change, exports and the checks on an import. A save that
 * couldn't be written is tried again, with the same months, until it is. Client thread.
 */
@Slf4j
class HistoryKeeper
{
	private static final long SAVE_DELAY_MS = 1_000;
	private static final long FIRST_RETRY_MS = 5_000;
	private static final long MAX_RETRY_MS = 30_000;

	private final TripSession s;
	private final HistoryStore store;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final ClientThread clientThread;
	private final BossRegistry registry;
	private ScheduledFuture<?> saveFuture;
	/**
	 * Months whose finished trips changed since the last save that was written, so their files are written again.
	 */
	private final PendingMonths pending = new PendingMonths();
	private long retryDelayMs = FIRST_RETRY_MS;

	HistoryKeeper(TripSession s, HistoryStore store, Gson gson, ScheduledExecutorService executor,
		ClientThread clientThread, BossRegistry registry)
	{
		this.s = s;
		this.store = store;
		this.gson = gson;
		this.executor = executor;
		this.clientThread = clientThread;
		this.registry = registry;
	}

	/**
	 * Forgets the months waiting to be written, e.g. when another account's history loads.
	 */
	void clearPending()
	{
		pending.clear();
		retryDelayMs = FIRST_RETRY_MS;
		s.unsaved = false;
	}

	void requestSave()
	{
		scheduleSave(SAVE_DELAY_MS);
	}

	private void scheduleSave(long delayMs)
	{
		if (s.history == null || s.readOnly || (saveFuture != null && !saveFuture.isDone()))
		{
			return;
		}
		try
		{
			saveFuture = executor.schedule(() -> clientThread.invokeLater(() -> saveNow()), delayMs, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Plugin is shutting down
		}
	}

	void saveNow()
	{
		saveNow(() -> { });
	}

	void saveNow(Runnable written)
	{
		if (s.history == null || s.readOnly)
		{
			written.run();
			return;
		}
		if (s.currentTrip != null && s.currentTrip.getSegmentStartedAt() != null)
		{
			s.currentTrip.setLastActiveAt(System.currentTimeMillis());
		}
		long hash = s.accountHash;
		PendingMonths.Batch batch = pending.take();
		store.save(hash, snapshot(batch), saved ->
		{
			try
			{
				clientThread.invokeLater(() -> saveFinished(hash, batch, saved));
			}
			finally
			{
				written.run();
			}
		});
	}

	/**
	 * A save was written, or couldn't be: then its months wait for the next save, which is tried again soon (the
	 * account and the trip in progress are part of every save).
	 */
	private void saveFinished(long hash, PendingMonths.Batch batch, boolean saved)
	{
		if (hash != s.accountHash)
		{
			if (!saved)
			{
				log.warn("The last save of the account just left couldn't be written");
			}
			return;
		}
		if (saved)
		{
			pending.written(batch);
			if (!pending.isUnsaved())
			{
				retryDelayMs = FIRST_RETRY_MS;
			}
		}
		else
		{
			pending.failed(batch);
			long delay = retryDelayMs;
			retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_MS);
			scheduleSave(delay);
		}
		if (s.unsaved != pending.isUnsaved())
		{
			s.unsaved = pending.isUnsaved();
			s.viewDirty = true;
		}
	}

	/**
	 * Saves now instead of waiting for a pending save. Client thread.
	 *
	 * @param written runs once the file is written (or at once if there is nothing to save)
	 */
	public void finalSave(Runnable written)
	{
		if (saveFuture != null)
		{
			saveFuture.cancel(false);
			saveFuture = null;
		}
		if (s.history == null || s.readOnly)
		{
			written.run();
			return;
		}
		saveNow(written);
	}

	/**
	 * What to write, made without turning anything into JSON here: the account without trips (copied), the months
	 * that changed (finished trips are never changed in place, so they're shared, not copied) and a copy of the trip
	 * in progress.
	 */
	private HistoryLayout.Snapshot snapshot(PendingMonths.Batch batch)
	{
		Map<String, Map<String, List<Trip>>> months = new LinkedHashMap<>();
		for (String key : batch.getMonths())
		{
			int slash = key.lastIndexOf('/');
			months.computeIfAbsent(key.substring(0, slash), k -> new LinkedHashMap<>()).put(key.substring(slash + 1),
				new ArrayList<>());
		}
		months.forEach((bossId, byMonth) ->
		{
			BossHistory boss = s.history.getBosses().get(bossId);
			if (boss == null)
			{
				return;
			}
			for (Trip trip : boss.getTrips())
			{
				List<Trip> month = trip.isOpen() ? null : byMonth.get(HistoryLayout.month(trip));
				if (month != null)
				{
					month.add(trip);
				}
			}
		});

		List<HistoryLayout.OpenTrip> open = new ArrayList<>();
		if (s.currentTrip != null && s.tripBoss != null && s.history.boss(s.tripBoss.getId()).getTrips().contains(s.currentTrip))
		{
			open.add(new HistoryLayout.OpenTrip(s.tripBoss.getId(), s.currentTrip.copy()));
		}
		return new HistoryLayout.Snapshot(HistoryLayout.accountWithoutTrips(s.history), months, open);
	}

	/**
	 * A finished trip was added, removed or replaced: its month is written again at the next save.
	 */
	void monthChanged(String bossId, Trip trip)
	{
		if (bossId != null)
		{
			pending.add(bossId, HistoryLayout.month(trip));
		}
	}

	void allMonthsChanged()
	{
		s.history.getBosses().forEach((bossId, boss) ->
		{
			for (Trip trip : boss.getTrips())
			{
				monthChanged(bossId, trip);
			}
		});
	}

	String bossIdOf(BossHistory boss)
	{
		for (Map.Entry<String, BossHistory> e : s.history.getBosses().entrySet())
		{
			if (e.getValue() == boss)
			{
				return e.getKey();
			}
		}
		return null;
	}

	/**
	 * The trip to change. The trip in progress is changed as it is; a finished trip is replaced in the history by a
	 * copy, which is changed instead (the saver may be writing the original from another thread), and its month is
	 * saved again.
	 *
	 * @param trip the trip, or an earlier copy of it (found by id)
	 * @return the trip to change, or null if it's no longer in the history
	 */
	Trip editable(Trip trip)
	{
		if (trip == null || s.history == null)
		{
			return null;
		}
		if (trip == s.currentTrip)
		{
			return trip;
		}
		for (Map.Entry<String, BossHistory> e : s.history.getBosses().entrySet())
		{
			List<Trip> trips = e.getValue().getTrips();
			for (int i = 0; i < trips.size(); i++)
			{
				Trip found = trips.get(i);
				if (!found.getId().equals(trip.getId()))
				{
					continue;
				}
				if (found == s.currentTrip || found.isOpen())
				{
					return found;
				}
				Trip copy = found.copy();
				trips.set(i, copy);
				if (s.lastEndedTrip == found)
				{
					s.lastEndedTrip = copy;
				}
				monthChanged(e.getKey(), copy);
				s.historyChanged();
				return copy;
			}
		}
		return null;
	}

	/**
	 * @return this account's full history (every boss) as JSON, made when called (on the IO thread), or null if none
	 * is loaded. Client thread.
	 */
	public Supplier<String> exportJson()
	{
		if (s.history == null)
		{
			return null;
		}
		// Finished trips never change in place, so they're shared; the rest is copied now
		AccountHistory export = HistoryLayout.accountWithoutTrips(s.history);
		s.history.getBosses().forEach((bossId, boss) ->
		{
			List<Trip> trips = new ArrayList<>();
			for (Trip trip : boss.getTrips())
			{
				trips.add(trip == s.currentTrip ? trip.copy() : trip);
			}
			export.getBosses().get(bossId).setTrips(trips);
		});
		return () -> gson.toJson(export);
	}

	/**
	 * @return the shown boss's completed trips as CSV (oldest first), or null if no history is loaded. Client thread.
	 */
	public Supplier<String> exportCsv()
	{
		if (s.history == null)
		{
			return null;
		}
		String csv = csv();
		return () -> csv;
	}

	private String csv()
	{
		BossDefinition boss = s.selectedBoss;
		StringBuilder csv = new StringBuilder("start,end,active_minutes,end_reason,kills,");
		for (TripStat column : boss.getCsvColumns())
		{
			csv.append(column.getLabel()).append(',');
		}
		csv.append("deaths,pet,loot_gp,supplies_gp,dropped_gp,death_costs_gp,net_gp,gp_per_hour,avg_kill_seconds\n");
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		for (Trip trip : s.history.boss(boss.getId()).getTrips())
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

	static boolean isImportable(Trip trip, BossHistory mine)
	{
		return trip.getId() != null && !trip.isOpen() && (mine == null || findTrip(mine, trip.getId()) == null);
	}

	/**
	 * Describes what importing would do, or returns an error message starting with "!". Client thread.
	 */
	public String describeImport(AccountHistory imported)
	{
		if (s.history == null)
		{
			return "!Log in first so the history can be imported into your account.";
		}
		if (s.readOnly)
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
			BossHistory mine = s.history.getBosses().get(e.getKey());
			for (Trip trip : e.getValue().getTrips())
			{
				total++;
				if (isImportable(trip, mine))
				{
					added++;
				}
			}
		}
		String account = imported.getAccountHash() != 0 && imported.getAccountHash() != s.accountHash
			? " The file is from a different account" + (imported.getLastDisplayName() != null
			? " (" + imported.getLastDisplayName() + ")" : "") + "."
			: "";
		return "Add " + added + " of " + total + " trips from the file to this account?"
			+ " Trips you already have are skipped." + account;
	}

	static Trip findTrip(BossHistory boss, String id)
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

	/**
	 * The bosses this version tracks, for checking an import. Any thread.
	 */
	public Set<String> knownBossIds()
	{
		Set<String> ids = new HashSet<>();
		for (BossDefinition boss : registry.all())
		{
			ids.add(boss.getId());
		}
		return ids;
	}
}
