package com.bosstriptracker.persistence;

import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Trip;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * One folder per account (schema 6), so a save never rewrites the whole history:
 * <ul>
 * <li>{@code account.json}: the account and each boss's data except trips (goal, last-unique KC, the game's dry
 * streaks, egg pops, polish tallies)</li>
 * <li>{@code <boss>/trips-YYYY-MM.jsonl}: finished trips by the month (UTC) they started, one JSON object per line.
 * A month is written again only when one of its trips changes.</li>
 * <li>{@code open-trips.json}: trips in progress, written on every save</li>
 * </ul>
 * Every file is written to a temporary file first and then moved into place. Only touched on the IO thread.
 */
@Slf4j
public final class HistoryLayout
{
	static final String ACCOUNT_FILE = "account.json";
	static final String OPEN_TRIPS_FILE = "open-trips.json";
	private static final String MONTH_PREFIX = "trips-";
	private static final String MONTH_SUFFIX = ".jsonl";
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	private HistoryLayout()
	{
	}

	/**
	 * What one save writes, made on the client thread from finished trips (never changed after they end) and copies
	 * of everything else, so it can be turned into JSON on the IO thread.
	 */
	@Value
	public static class Snapshot
	{
		/**
		 * The account, with each boss's data but no trips.
		 */
		AccountHistory account;
		/**
		 * Boss id to month ("2026-09") to that month's finished trips; an empty list removes the month's file.
		 */
		Map<String, Map<String, List<Trip>>> months;
		List<OpenTrip> openTrips;

		/**
		 * Everything in a history: every month of every boss (for migrating a single-file history).
		 */
		public static Snapshot of(AccountHistory history)
		{
			Map<String, Map<String, List<Trip>>> months = new LinkedHashMap<>();
			List<OpenTrip> open = new ArrayList<>();
			history.getBosses().forEach((bossId, boss) ->
			{
				Map<String, List<Trip>> byMonth = months.computeIfAbsent(bossId, k -> new LinkedHashMap<>());
				for (Trip trip : boss.getTrips())
				{
					if (trip.isOpen())
					{
						open.add(new OpenTrip(bossId, trip));
					}
					else
					{
						byMonth.computeIfAbsent(month(trip), k -> new ArrayList<>()).add(trip);
					}
				}
			});
			return new Snapshot(accountWithoutTrips(history), months, open);
		}
	}

	/**
	 * A trip in progress and its boss.
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OpenTrip
	{
		private String boss;
		private Trip trip;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class OpenTrips
	{
		private List<OpenTrip> trips = new ArrayList<>();
	}

	@Value
	static class Loaded
	{
		AccountHistory history;
		/**
		 * Files that couldn't be read and were moved aside (their readable trips were kept); empty if none.
		 */
		List<String> unreadable;
	}

	/**
	 * The month a trip is filed under: when it started, in UTC.
	 */
	public static String month(Trip trip)
	{
		return MONTH.format(Instant.ofEpochMilli(trip.getStartedAt()));
	}

	public static AccountHistory accountWithoutTrips(AccountHistory history)
	{
		AccountHistory account = new AccountHistory();
		account.setSchemaVersion(AccountHistory.CURRENT_SCHEMA_VERSION);
		account.setAccountHash(history.getAccountHash());
		account.setLastDisplayName(history.getLastDisplayName());
		history.getBosses().forEach((bossId, boss) -> account.getBosses().put(bossId, boss.withoutTrips()));
		return account;
	}

	static boolean exists(Filepath folder)
	{
		return folder.joinSegment(ACCOUNT_FILE).exists();
	}

	/**
	 * Writes the months in the snapshot, then the trips in progress, then the account, each atomically. A trip that
	 * ended between saves can be in both its month and the old open-trips file for a moment; loading keeps one.
	 */
	static void write(Gson gson, Filepath folder, Snapshot snapshot) throws IOException
	{
		folder.createDirectories();
		for (Map.Entry<String, Map<String, List<Trip>>> boss : snapshot.getMonths().entrySet())
		{
			Filepath bossFolder = folder.joinSegment(boss.getKey());
			for (Map.Entry<String, List<Trip>> month : boss.getValue().entrySet())
			{
				Filepath file = bossFolder.joinSegment(MONTH_PREFIX + month.getKey() + MONTH_SUFFIX);
				if (month.getValue().isEmpty())
				{
					file.deleteIfExists();
					continue;
				}
				bossFolder.createDirectories();
				StringBuilder lines = new StringBuilder();
				for (Trip trip : month.getValue())
				{
					lines.append(gson.toJson(trip)).append('\n');
				}
				writeAtomically(file, lines.toString());
			}
		}
		writeAtomically(folder.joinSegment(OPEN_TRIPS_FILE), gson.toJson(new OpenTrips(snapshot.getOpenTrips())));
		writeAtomically(folder.joinSegment(ACCOUNT_FILE), gson.toJson(snapshot.getAccount()));
	}

	/**
	 * Reads an account folder. A file that can't be read is moved aside ("…corrupt-…") and, for a month, its readable
	 * trips are kept and written back.
	 *
	 * @throws JsonParseException never for damaged files; IOException if the folder can't be read at all
	 */
	static Loaded read(Gson gson, Filepath folder) throws IOException
	{
		List<String> unreadable = new ArrayList<>();
		AccountHistory history = null;
		Filepath accountFile = folder.joinSegment(ACCOUNT_FILE);
		try
		{
			history = gson.fromJson(readString(accountFile), AccountHistory.class);
		}
		catch (JsonParseException e)
		{
			log.warn("Unreadable {}; trips are kept but goals and egg pops start again", ACCOUNT_FILE, e);
			unreadable.add(moveAside(accountFile));
		}
		if (history == null)
		{
			history = new AccountHistory();
		}
		if (history.getBosses() == null)
		{
			history.setBosses(new LinkedHashMap<>());
		}
		history.getBosses().values().removeIf(b -> b == null);
		history.getBosses().values().forEach(boss ->
		{
			boss.setTrips(null);
			boss.fillMissing();
		});

		Set<String> closedIds = new HashSet<>();
		for (Filepath bossFolder : children(folder, Filepath::isDirectory))
		{
			BossHistory boss = history.boss(bossFolder.getFileName());
			for (Filepath file : children(bossFolder, f -> f.getFileName().startsWith(MONTH_PREFIX)
				&& f.getFileName().endsWith(MONTH_SUFFIX)))
			{
				List<Trip> trips = new ArrayList<>();
				boolean damaged = false;
				try (BufferedReader reader = file.openBufferedReader())
				{
					String line;
					while ((line = reader.readLine()) != null)
					{
						if (line.trim().isEmpty())
						{
							continue;
						}
						try
						{
							Trip trip = gson.fromJson(line, Trip.class);
							if (trip != null && trip.getId() != null && closedIds.add(trip.getId()))
							{
								trips.add(trip);
							}
						}
						catch (JsonParseException e)
						{
							damaged = true;
						}
					}
				}
				if (damaged)
				{
					log.warn("Unreadable lines in {}; the readable trips are kept", file.getFileName());
					String aside = file.getFileName() + ".corrupt-" + STAMP.format(Instant.now());
					file.copyTo(bossFolder.joinSegment(aside), StandardCopyOption.REPLACE_EXISTING);
					unreadable.add(bossFolder.getFileName() + "/" + aside);
					StringBuilder lines = new StringBuilder();
					for (Trip trip : trips)
					{
						lines.append(gson.toJson(trip)).append('\n');
					}
					writeAtomically(file, lines.toString());
				}
				boss.getTrips().addAll(trips);
			}
		}

		Filepath openFile = folder.joinSegment(OPEN_TRIPS_FILE);
		if (openFile.exists())
		{
			try
			{
				OpenTrips open = gson.fromJson(readString(openFile), OpenTrips.class);
				if (open != null && open.getTrips() != null)
				{
					for (OpenTrip entry : open.getTrips())
					{
						// Already in its month: it ended just before the client closed
						if (entry != null && entry.getBoss() != null && entry.getTrip() != null
							&& entry.getTrip().getId() != null && !closedIds.contains(entry.getTrip().getId()))
						{
							history.boss(entry.getBoss()).getTrips().add(entry.getTrip());
						}
					}
				}
			}
			catch (JsonParseException e)
			{
				log.warn("Unreadable {}; trips in progress are lost", OPEN_TRIPS_FILE, e);
				unreadable.add(moveAside(openFile));
			}
		}

		for (BossHistory boss : history.getBosses().values())
		{
			boss.getTrips().sort(Comparator.comparingLong(Trip::getStartedAt));
		}
		return new Loaded(history, unreadable);
	}

	/**
	 * Whether two histories hold the same trips (by id) for every boss, for checking a migration.
	 */
	static boolean sameTrips(AccountHistory a, AccountHistory b)
	{
		Set<String> bosses = new HashSet<>(a.getBosses().keySet());
		bosses.addAll(b.getBosses().keySet());
		for (String bossId : bosses)
		{
			if (!tripIds(a, bossId).equals(tripIds(b, bossId)))
			{
				return false;
			}
		}
		return true;
	}

	private static Set<String> tripIds(AccountHistory history, String bossId)
	{
		BossHistory boss = history.getBosses().get(bossId);
		return boss == null ? new HashSet<>()
			: boss.getTrips().stream().map(Trip::getId).collect(Collectors.toSet());
	}

	private static List<Filepath> children(Filepath folder, java.util.function.Predicate<Filepath> filter) throws IOException
	{
		if (!folder.isDirectory())
		{
			return new ArrayList<>();
		}
		try (Stream<Filepath> files = folder.walk(1))
		{
			return files.filter(f -> !f.equals(folder) && filter.test(f)).sorted().collect(Collectors.toList());
		}
	}

	private static String moveAside(Filepath file) throws IOException
	{
		String aside = file.getFileName() + ".corrupt-" + STAMP.format(Instant.now());
		file.moveTo(file.getParent().joinSegment(aside), StandardCopyOption.REPLACE_EXISTING);
		return aside;
	}

	static String readString(Filepath file) throws IOException
	{
		try (BufferedReader reader = file.openBufferedReader())
		{
			return reader.lines().collect(Collectors.joining("\n"));
		}
	}

	static void writeAtomically(Filepath target, String content) throws IOException
	{
		Filepath temp = target.getParent().joinSegment(target.getFileName() + ".tmp");
		temp.write(content.getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		try
		{
			temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
