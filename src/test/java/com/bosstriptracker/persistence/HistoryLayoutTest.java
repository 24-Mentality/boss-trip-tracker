package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Trip;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HistoryLayoutTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private final Gson gson = new Gson();
	private AccountHistory history;
	private Path root;
	private Filepath folder;

	@Before
	public void load() throws Exception
	{
		history = HistoryCodec.decode(gson, Fixtures.historyV1()).getHistory();
		root = temp.getRoot().toPath().resolve("history-42");
		folder = Filepath.Unchecked.getRooted(temp.getRoot().toPath()).joinSegment("history-42");
	}

	@Test
	public void writesAndReadsBackEveryTrip() throws Exception
	{
		HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(history));
		HistoryLayout.Loaded loaded = HistoryLayout.read(gson, folder);

		assertTrue(loaded.getUnreadable().isEmpty());
		assertEquals(json(history), json(loaded.getHistory()));
		// Finished trips by month, the open one apart
		assertTrue(Files.exists(root.resolve(HistoryLayout.ACCOUNT_FILE)));
		assertTrue(Files.exists(root.resolve(HistoryLayout.OPEN_TRIPS_FILE)));
		assertEquals(2, monthLines(MaggotKingBoss.ID));
	}

	@Test
	public void aMonthIsWrittenAgainWithoutADeletedTrip() throws Exception
	{
		HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(history));
		BossHistory boss = history.getBosses().get(MaggotKingBoss.ID);
		Trip deleted = boss.getTrips().remove(0);

		HistoryLayout.write(gson, folder, snapshot(MaggotKingBoss.ID, HistoryLayout.month(deleted), boss.getTrips()));
		AccountHistory back = HistoryLayout.read(gson, folder).getHistory();
		assertEquals(boss.getTrips().size(), back.getBosses().get(MaggotKingBoss.ID).getTrips().size());
		assertFalse(back.getBosses().get(MaggotKingBoss.ID).getTrips().stream().anyMatch(t -> t.getId().equals(deleted.getId())));

		// A month with no trips left has no file
		HistoryLayout.write(gson, folder, snapshot(MaggotKingBoss.ID, HistoryLayout.month(deleted), Collections.emptyList()));
		assertEquals(0, monthLines(MaggotKingBoss.ID));
	}

	@Test
	public void aTripInItsMonthAndStillOpenIsLoadedOnce() throws Exception
	{
		HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(history));
		// It ended and its month was written, but the client closed before the open trips were
		Trip open = history.getBosses().get(MaggotKingBoss.ID).getTrips().stream().filter(Trip::isOpen).findFirst().get();
		Trip ended = open.copy();
		ended.setEndedAt(open.getStartedAt() + 1000);
		List<Trip> month = new ArrayList<>();
		for (Trip trip : history.getBosses().get(MaggotKingBoss.ID).getTrips())
		{
			if (!trip.isOpen() && HistoryLayout.month(trip).equals(HistoryLayout.month(ended)))
			{
				month.add(trip);
			}
		}
		month.add(ended);
		Map<String, Map<String, List<Trip>>> months = new LinkedHashMap<>();
		months.computeIfAbsent(MaggotKingBoss.ID, k -> new LinkedHashMap<>()).put(HistoryLayout.month(ended), month);
		Path openFile = root.resolve(HistoryLayout.OPEN_TRIPS_FILE);
		byte[] openTrips = Files.readAllBytes(openFile);
		HistoryLayout.write(gson, folder, new HistoryLayout.Snapshot(HistoryLayout.accountWithoutTrips(history), months,
			Collections.emptyList()));
		Files.write(openFile, openTrips);

		List<Trip> loaded = HistoryLayout.read(gson, folder).getHistory().getBosses().get(MaggotKingBoss.ID).getTrips();
		assertEquals(1, loaded.stream().filter(t -> t.getId().equals(open.getId())).count());
		assertFalse(loaded.stream().filter(t -> t.getId().equals(open.getId())).findFirst().get().isOpen());
	}

	@Test
	public void aDamagedLineKeepsTheRestOfItsMonth() throws Exception
	{
		HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(history));
		Path month = monthFiles(MaggotKingBoss.ID).get(0);
		long before = Files.readAllLines(month).size();
		Files.write(month, "{\"id\": \"broken\", \"kills\": [\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

		HistoryLayout.Loaded loaded = HistoryLayout.read(gson, folder);

		assertEquals(1, loaded.getUnreadable().size());
		assertEquals(3, loaded.getHistory().getBosses().get(MaggotKingBoss.ID).getTrips().size());
		// The damaged file is kept aside and written back clean
		assertEquals(before, Files.readAllLines(month).size());
		assertTrue(HistoryLayout.read(gson, folder).getUnreadable().isEmpty());
		try (Stream<Path> files = Files.list(month.getParent()))
		{
			assertTrue(files.anyMatch(f -> f.getFileName().toString().contains(".corrupt-")));
		}
	}

	@Test
	public void anUnreadableAccountFileKeepsTheTrips() throws Exception
	{
		HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(history));
		Files.write(root.resolve(HistoryLayout.ACCOUNT_FILE), "{not json".getBytes(StandardCharsets.UTF_8));

		HistoryLayout.Loaded loaded = HistoryLayout.read(gson, folder);

		assertEquals(1, loaded.getUnreadable().size());
		assertEquals(3, loaded.getHistory().getBosses().get(MaggotKingBoss.ID).getTrips().size());
	}

	@Test
	public void copiesAreComplete()
	{
		for (Trip trip : history.getBosses().get(MaggotKingBoss.ID).getTrips())
		{
			assertEquals(gson.toJson(trip), gson.toJson(trip.copy()));
		}
		BossHistory boss = history.getBosses().get(MaggotKingBoss.ID);
		BossHistory meta = boss.withoutTrips();
		meta.setTrips(boss.getTrips());
		assertEquals(gson.toJson(boss), gson.toJson(meta));
	}

	private HistoryLayout.Snapshot snapshot(String bossId, String month, List<Trip> trips)
	{
		List<Trip> inMonth = trips.stream().filter(t -> !t.isOpen() && HistoryLayout.month(t).equals(month))
			.collect(Collectors.toList());
		Map<String, Map<String, List<Trip>>> months = new LinkedHashMap<>();
		months.computeIfAbsent(bossId, k -> new LinkedHashMap<>()).put(month, inMonth);
		List<HistoryLayout.OpenTrip> open = trips.stream().filter(Trip::isOpen)
			.map(t -> new HistoryLayout.OpenTrip(bossId, t)).collect(Collectors.toList());
		return new HistoryLayout.Snapshot(HistoryLayout.accountWithoutTrips(history), months, open);
	}

	private List<Path> monthFiles(String bossId) throws Exception
	{
		Path bossFolder = root.resolve(bossId);
		if (!Files.isDirectory(bossFolder))
		{
			return Collections.emptyList();
		}
		try (Stream<Path> files = Files.list(bossFolder))
		{
			return files.filter(f -> f.getFileName().toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
		}
	}

	private int monthLines(String bossId) throws Exception
	{
		int lines = 0;
		for (Path file : monthFiles(bossId))
		{
			lines += Files.readAllLines(file).size();
		}
		return lines;
	}

	private String json(AccountHistory history)
	{
		assertNotNull(history);
		return gson.toJson(history);
	}
}
