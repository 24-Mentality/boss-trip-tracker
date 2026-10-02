package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.Trip;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Single-file histories (schema 1 to 5) moving into the folder layout of schema 6.
 */
public class HistoryMigrationTest
{
	private static final long HASH = 42;
	private static final String OLD_FILE = "history-42.json";

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private final Gson gson = new Gson();
	private Path root;
	private HistoryStore store;

	@Before
	public void setUp()
	{
		root = temp.getRoot().toPath();
		store = new HistoryStore(gson, () -> Filepath.Unchecked.getRooted(root), MoreExecutors.newDirectExecutorService());
	}

	@After
	public void writable()
	{
		root.toFile().setWritable(true);
	}

	@Test
	public void schema1FileMovesIntoTheFolder() throws Exception
	{
		write(OLD_FILE, Fixtures.historyV1());

		HistoryStore.LoadResult result = load();

		assertFalse(result.isReadOnly());
		assertEquals(1, result.getSourceVersion());
		assertEquals(3, result.getHistory().getBosses().get(MaggotKingBoss.ID).getTrips().size());
		assertFalse(Files.exists(root.resolve(OLD_FILE)));
		assertEquals(1, files(OLD_FILE + ".v1-backup-").size());

		// The next load reads the folder, with the same trips
		HistoryStore.LoadResult again = load();
		assertFalse(again.isReadOnly());
		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, again.getSourceVersion());
		assertEquals(gson.toJson(result.getHistory()), gson.toJson(again.getHistory()));
		assertNull(again.getCorruptBackup());
	}

	@Test
	public void schema5FileMovesIntoTheFolder() throws Exception
	{
		write(OLD_FILE, schema5());

		HistoryStore.LoadResult result = load();

		assertFalse(result.isReadOnly());
		assertEquals(5, result.getSourceVersion());
		assertEquals(1, files(OLD_FILE + ".v5-backup-").size());
		assertEquals(tripIds(HistoryCodec.decode(gson, schema5()).getHistory()), tripIds(load().getHistory()));
	}

	@Test
	public void aMigrationThatDidntFinishIsDoneAgain() throws Exception
	{
		// A folder left by an earlier attempt that couldn't rename the file: the file wins
		write(OLD_FILE, Fixtures.historyV1());
		Files.createDirectories(root.resolve("history-42/maggot_king"));
		write("history-42/maggot_king/trips-2001-01.jsonl", "{\"id\": \"stale\", \"startedAt\": 1}\n");

		HistoryStore.LoadResult result = load();

		assertFalse(result.isReadOnly());
		List<String> ids = tripIds(load().getHistory());
		assertFalse(ids.contains("stale"));
		assertEquals(3, ids.size());
	}

	@Test
	public void theOldFileIsKeptWhenTheFolderCantBeWritten() throws Exception
	{
		write(OLD_FILE, Fixtures.historyV1());
		File dir = root.toFile();
		assumeTrue(dir.setWritable(false) && !Files.isWritable(root));

		HistoryStore.LoadResult result = load();

		// Shown, but nothing is saved this session; tried again at the next login
		assertTrue(result.isReadOnly());
		assertEquals(3, result.getHistory().getBosses().get(MaggotKingBoss.ID).getTrips().size());
		dir.setWritable(true);
		assertTrue(Files.exists(root.resolve(OLD_FILE)));
		assertFalse(load().isReadOnly());
	}

	@Test
	public void savesWriteOnlyTheChangedMonth() throws Exception
	{
		write(OLD_FILE, Fixtures.historyV1());
		AccountHistory history = load().getHistory();
		List<Trip> trips = history.getBosses().get(MaggotKingBoss.ID).getTrips();
		Trip first = trips.remove(0);

		List<Trip> month = trips.stream().filter(t -> !t.isOpen() && HistoryLayout.month(t).equals(HistoryLayout.month(first)))
			.collect(Collectors.toList());
		java.util.Map<String, java.util.Map<String, List<Trip>>> months = new java.util.LinkedHashMap<>();
		months.computeIfAbsent(MaggotKingBoss.ID, k -> new java.util.LinkedHashMap<>()).put(HistoryLayout.month(first), month);
		AtomicReference<Boolean> written = new AtomicReference<>(false);
		store.save(HASH, new HistoryLayout.Snapshot(HistoryLayout.accountWithoutTrips(history), months,
			Collections.emptyList()), () -> written.set(true));

		assertTrue(written.get());
		List<String> ids = tripIds(load().getHistory());
		assertFalse(ids.contains(first.getId()));
	}

	private HistoryStore.LoadResult load()
	{
		AtomicReference<HistoryStore.LoadResult> result = new AtomicReference<>();
		store.load(HASH, result::set);
		return result.get();
	}

	private String schema5() throws Exception
	{
		JsonObject root = gson.toJsonTree(HistoryCodec.decode(gson, Fixtures.historyV1()).getHistory()).getAsJsonObject();
		root.addProperty("schemaVersion", 5);
		return gson.toJson(root);
	}

	private void write(String name, String content) throws Exception
	{
		Files.write(root.resolve(name), content.getBytes(StandardCharsets.UTF_8));
	}

	private List<Path> files(String prefix) throws Exception
	{
		try (Stream<Path> files = Files.list(root))
		{
			return files.filter(f -> f.getFileName().toString().startsWith(prefix)).collect(Collectors.toList());
		}
	}

	private static List<String> tripIds(AccountHistory history)
	{
		List<String> ids = new ArrayList<>();
		history.getBosses().values().forEach(boss -> boss.getTrips().forEach(t -> ids.add(t.getId())));
		Collections.sort(ids);
		return ids;
	}
}
