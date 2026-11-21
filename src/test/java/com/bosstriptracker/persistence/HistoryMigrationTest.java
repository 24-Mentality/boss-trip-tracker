package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.Arrays;
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
		root.resolve("history-42/maggot_king").toFile().setWritable(true);
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
	public void aMigrationThatDidntFinishIsFinishedWithoutDeletingTheFolder() throws Exception
	{
		// A folder left by an earlier attempt that stopped before its account file (written last) and before the
		// old file was renamed. It also holds a trip the file doesn't have: nothing in a folder is ever deleted.
		write(OLD_FILE, Fixtures.historyV1());
		AccountHistory fromFile = HistoryCodec.decode(gson, Fixtures.historyV1()).getHistory();
		Filepath folder = Filepath.Unchecked.getRooted(root).joinSegment("history-42");
		HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(fromFile));
		Files.delete(root.resolve("history-42/account.json"));
		write("history-42/maggot_king/trips-2001-01.jsonl", "{\"id\":\"only-in-folder\",\"startedAt\":1,\"endedAt\":2}\n");

		HistoryStore.LoadResult result = load();

		assertFalse(result.isReadOnly());
		assertEquals(Arrays.asList("only-in-folder", "trip-1", "trip-2", "trip-3"), tripIds(result.getHistory()));
		// The folder had no account file, so the goal, egg pops and tallies are the file's
		assertNotNull(result.getHistory().getBosses().get(MaggotKingBoss.ID).getGoal());
		assertFalse(Files.exists(root.resolve(OLD_FILE)));
		assertEquals(1, files(OLD_FILE + ".v1-backup-").size());

		HistoryStore.LoadResult again = load();
		assertFalse(again.isReadOnly());
		assertEquals(gson.toJson(result.getHistory()), gson.toJson(again.getHistory()));
	}

	/**
	 * An older build of the plugin (single-file history) run once after the move to folders finds no file, starts a
	 * new one and saves it. The folder, with every trip since, used to be deleted at the next login as "left over".
	 */
	@Test
	public void anOlderFileBesideTheFolderAddsItsTripsAndDeletesNothing() throws Exception
	{
		write(OLD_FILE, Fixtures.historyV1());
		load();
		write("history-42/maggot_king/trips-2026-11.jsonl",
			"{\"id\":\"since-the-move\",\"startedAt\":1794000000000,\"endedAt\":1794000100000}\n");
		write(OLD_FILE, "{\"schemaVersion\":5,\"accountHash\":42,\"bosses\":{\"maggot_king\":{\"trips\":["
			+ "{\"id\":\"older-build\",\"startedAt\":1795000000000,\"endedAt\":1795000100000},"
			+ "{\"id\":\"trip-1\",\"startedAt\":1790000000000,\"endedAt\":1790003600000}]}}}");

		HistoryStore.LoadResult result = load();

		assertFalse(result.isReadOnly());
		assertEquals(Arrays.asList("older-build", "since-the-move", "trip-1", "trip-2", "trip-3"), tripIds(result.getHistory()));
		assertEquals(Collections.singleton("older-build"), result.getAddedFromOldFile());
		assertEquals(5, result.getOldFileVersion());
		// The folder's own trip-1 (with its kills) and its goal are kept over the file's
		Trip first = result.getHistory().getBosses().get(MaggotKingBoss.ID).getTrips().get(0);
		assertEquals("trip-1", first.getId());
		assertEquals(4, first.getKills().size());
		assertNotNull(result.getHistory().getBosses().get(MaggotKingBoss.ID).getGoal());
		// The file became a backup, and the first migration's backup is still there
		assertFalse(Files.exists(root.resolve(OLD_FILE)));
		assertEquals(1, files(OLD_FILE + ".v5-backup-").size());
		assertEquals(1, files(OLD_FILE + ".v1-backup-").size());

		HistoryStore.LoadResult again = load();
		assertFalse(again.isReadOnly());
		assertTrue(again.getAddedFromOldFile().isEmpty());
		assertEquals(tripIds(result.getHistory()), tripIds(again.getHistory()));
	}

	@Test
	public void theFolderAndTheFileAreBothKeptWhenTheirTripsCantBeWrittenTogether() throws Exception
	{
		write(OLD_FILE, Fixtures.historyV1());
		load();
		write(OLD_FILE, "{\"schemaVersion\":5,\"accountHash\":42,\"bosses\":{\"maggot_king\":{\"trips\":["
			+ "{\"id\":\"older-build\",\"startedAt\":1795000000000,\"endedAt\":1795000100000}]}}}");
		File bossFolder = root.resolve("history-42/maggot_king").toFile();
		assumeTrue(bossFolder.setWritable(false) && !Files.isWritable(bossFolder.toPath()));

		HistoryStore.LoadResult result = load();

		// Everything is shown, nothing is saved this session, and it's tried again at the next login
		assertTrue(result.isReadOnly());
		assertEquals(4, tripIds(result.getHistory()).size());
		bossFolder.setWritable(true);
		assertTrue(Files.exists(root.resolve(OLD_FILE)));
		HistoryStore.LoadResult next = load();
		assertFalse(next.isReadOnly());
		assertEquals(4, tripIds(next.getHistory()).size());
	}

	/**
	 * An unreadable account file is moved aside when the folder is read. If the client then closed before a save
	 * wrote a new one, the folder used to count as no history at all, and the next save wrote over its months.
	 */
	@Test
	public void aFolderWithoutItsAccountFileStillHasItsTrips() throws Exception
	{
		Files.createDirectories(root.resolve("history-42/maggot_king"));
		write("history-42/account.json", "{not json");
		write("history-42/maggot_king/trips-2026-09.jsonl", "{\"id\":\"t1\",\"startedAt\":1758000000000,\"endedAt\":1758001000000}\n");

		HistoryStore.LoadResult first = load();
		assertEquals(Collections.singletonList("t1"), tripIds(first.getHistory()));
		assertNotNull(first.getCorruptBackup());
		assertFalse(Files.exists(root.resolve("history-42/account.json")));

		HistoryStore.LoadResult second = load();
		assertFalse(second.isReadOnly());
		assertEquals(Collections.singletonList("t1"), tripIds(second.getHistory()));
	}

	@Test
	public void aSaveThatFailsSaysSoAndTheNextOneWritesTheTrips() throws Exception
	{
		AccountHistory history = HistoryCodec.decode(gson, Fixtures.historyV1()).getHistory();
		HistoryLayout.Snapshot snapshot = HistoryLayout.Snapshot.of(history);
		File dir = root.toFile();
		assumeTrue(dir.setWritable(false) && !Files.isWritable(root));
		AtomicReference<Boolean> written = new AtomicReference<>();

		store.save(HASH, snapshot, written::set);
		assertEquals(Boolean.FALSE, written.get());

		// The caller still holds the snapshot's trips and saves them again
		dir.setWritable(true);
		store.save(HASH, snapshot, written::set);
		assertEquals(Boolean.TRUE, written.get());
		assertEquals(Arrays.asList("trip-1", "trip-2", "trip-3"), tripIds(load().getHistory()));
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
			Collections.emptyList()), written::set);

		assertTrue(written.get());
		List<String> ids = tripIds(load().getHistory());
		assertFalse(ids.contains(first.getId()));
	}

	/**
	 * A folder written at schema 6 loads as it is, with its account file kept as a backup before the first save at 7
	 * (which adds the Collection Log page). Nothing else in the folder changes.
	 */
	@Test
	public void schema6FolderKeepsItsAccountFileBeforeTheFirstSaveAtSchema7() throws Exception
	{
		Files.createDirectories(root.resolve("history-42/maggot_king"));
		String account = "{\"schemaVersion\":6,\"accountHash\":42,\"bosses\":{\"maggot_king\":{\"lastUniqueKc\":1920,\"eggPops\":[],"
			+ "\"polishOutcomes\":{}}}}";
		write("history-42/account.json", account);
		write("history-42/maggot_king/trips-2026-09.jsonl", "{\"id\":\"t1\",\"startedAt\":1758000000000,\"endedAt\":1758001000000}\n");

		HistoryStore.LoadResult result = load();

		assertFalse(result.isReadOnly());
		assertEquals(6, result.getSourceVersion());
		assertEquals(Integer.valueOf(1920), result.getHistory().getBosses().get(MaggotKingBoss.ID).getLastUniqueKc());
		assertNull(result.getHistory().getBosses().get(MaggotKingBoss.ID).getCollectionLog());
		assertEquals(1, result.getHistory().getBosses().get(MaggotKingBoss.ID).getTrips().size());
		Path backup = root.resolve("history-42/account.json.v6-backup");
		assertTrue(Files.exists(backup));
		assertEquals(account, new String(Files.readAllBytes(backup), StandardCharsets.UTF_8));

		// Loading again doesn't touch the backup; the account file is still the schema 6 one until a save
		load();
		assertEquals(account, new String(Files.readAllBytes(backup), StandardCharsets.UTF_8));
		assertTrue(new String(Files.readAllBytes(root.resolve("history-42/account.json")), StandardCharsets.UTF_8)
			.contains("\"schemaVersion\":6"));
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
